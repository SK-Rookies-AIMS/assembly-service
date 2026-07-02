package com.aims.assembly.repository.event;

import com.aims.assembly.common.status.KafkaErrorStatus;
import com.aims.assembly.domain.enums.AnalysisStatus;
import com.aims.assembly.domain.enums.DispatchStatus;

import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.exception.KafkaException;
import com.aims.assembly.kafka.model.ManufacturingRawEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ManufacturingEventJsonRepository {

    private static final String SELECT_COLUMNS = """
            id, event_id, event_time, car_master_id, equipment_id, process_code,
            (SELECT equipment.equipment_code FROM equipment
             WHERE equipment.id = manufacturing_event_json.equipment_id) AS equipment_code,
            (SELECT equipment.equipment_type FROM equipment
             WHERE equipment.id = manufacturing_event_json.equipment_id) AS equipment_type,
            event_json,
            dispatch_status, analysis_status, is_sent, retry_count, error_message
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ManufacturingEventJsonRepository(
            @Qualifier("sampleJdbcTemplate") JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<StoredManufacturingEvent> findById(long id) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM manufacturing_event_json WHERE id = ?",
                rowMapper(), id
        ).stream().findFirst();
    }

    public Optional<StoredManufacturingEvent> findByEventId(String eventId) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM manufacturing_event_json WHERE event_id = ?",
                rowMapper(), eventId
        ).stream().findFirst();
    }

    public Optional<StoredManufacturingEvent> findReadyByIdForUpdate(long id) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM manufacturing_event_json "
                        + "WHERE id = ? AND dispatch_status = 'READY' "
                        + "AND COALESCE(is_sent, 0) = 0 FOR UPDATE",
                rowMapper(), id
        ).stream().findFirst();
    }

    public Optional<StoredManufacturingEvent> findFirstReady(
            LocalDateTime virtualNow,
            int maxRetries
    ) {
        return jdbcTemplate.query(
                """
                        SELECT %s FROM manufacturing_event_json
                        WHERE dispatch_status = 'READY' AND COALESCE(is_sent, 0) = 0
                          AND COALESCE(retry_count, 0) < ?
                        ORDER BY id ASC LIMIT 1
                        """.formatted(SELECT_COLUMNS),
                rowMapper(), maxRetries
        ).stream().findFirst();
    }

    /** Must be invoked inside the sample DB transaction and held until publish result is known. */
    public List<StoredManufacturingEvent> findReadyForUpdate(
            LocalDateTime virtualNow,
            int limit,
            int maxRetries
    ) {
        return jdbcTemplate.query(
                """
                        SELECT %s
                        FROM manufacturing_event_json
                        WHERE dispatch_status = 'READY'
                          AND COALESCE(is_sent, 0) = 0
                          AND COALESCE(retry_count, 0) < ?
                        ORDER BY id ASC
                        LIMIT ?
                        FOR UPDATE SKIP LOCKED
                        """.formatted(SELECT_COLUMNS),
                rowMapper(), maxRetries, Math.min(Math.max(limit, 1), 1_000)
        );
    }

    public List<StoredManufacturingEvent> findRecent(int limit) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS
                        + " FROM manufacturing_event_json ORDER BY event_time ASC, id ASC LIMIT ?",
                rowMapper(), limit
        );
    }

    public List<PressEventTime> findPressEventTimesByJsonEventTimeBetween(
            LocalDateTime from,
            LocalDateTime to,
            int limit
    ) {
        int size = Math.max(1, Math.min(limit, 10_000));
        return findAllPressEventTimes().stream()
                .filter(event -> event.eventTime() != null)
                .filter(event -> !event.eventTime().isBefore(from) && !event.eventTime().isAfter(to))
                .sorted(Comparator.comparing(PressEventTime::eventTime).reversed())
                .limit(size)
                .toList();
    }

    public List<PressEventDateOption> findPressEventDateOptions(int limit) {
        int size = Math.max(1, Math.min(limit, 365));
        Map<java.time.LocalDate, PressEventDateOption> byDate = new LinkedHashMap<>();
        findAllPressEventTimes().stream()
                .filter(event -> event.eventTime() != null)
                .sorted(Comparator.comparing(PressEventTime::eventTime).reversed())
                .forEach(event -> byDate.putIfAbsent(
                        event.eventTime().toLocalDate(),
                        new PressEventDateOption(event.eventTime().toLocalDate(), event.eventId())
                ));
        return byDate.values().stream().limit(size).toList();
    }

    public List<StoredManufacturingEvent> findPressEventsByEventIds(List<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(eventIds.size(), "?"));
        return jdbcTemplate.query(
                """
                        SELECT %s
                        FROM manufacturing_event_json
                        WHERE process_code = 'PRESS'
                          AND event_id IN (%s)
                        """.formatted(SELECT_COLUMNS, placeholders),
                rowMapper(),
                eventIds.toArray()
        );
    }

    private List<PressEventTime> findAllPressEventTimes() {
        return jdbcTemplate.query(
                """
                        SELECT event_id, event_json
                        FROM manufacturing_event_json
                        WHERE process_code = 'PRESS'
                        ORDER BY id DESC
                        """,
                (rs, rowNum) -> {
                    String eventId = rs.getString("event_id");
                    Map<String, Object> eventJson = parseEventJson(0L, rs.getString("event_json"));
                    return new PressEventTime(eventId, parseEventJsonEventTime(eventJson));
                }
        );
    }

    /**
     * Activates only the earliest pending event per vehicle. A preceding event with
     * NOT_ANALYZED waits, and a preceding ABNORMAL event blocks progression.
     */
    public int prepareDispatchablePendingEvents(LocalDateTime virtualNow, int limit) {
        List<PendingActivation> candidates = jdbcTemplate.query(
                """
                        SELECT e.id, e.car_master_id, e.process_code,
                               q.current_status,
                               e.process_code <> 'PRESS' AND EXISTS (
                                   SELECT 1 FROM manufacturing_event_json previous_abnormal
                                   WHERE previous_abnormal.car_master_id = e.car_master_id
                                     AND previous_abnormal.id < e.id
                                     AND previous_abnormal.analysis_status = 'ABNORMAL'
                               ) AS has_previous_abnormal
                        FROM manufacturing_event_json e
                        LEFT JOIN equipment q ON q.id = e.equipment_id
                        WHERE e.dispatch_status = 'PENDING'
                          AND COALESCE(e.is_sent, 0) = 0
                          AND (e.process_code = 'PRESS' OR NOT EXISTS (
                              SELECT 1 FROM manufacturing_event_json previous
                              WHERE previous.car_master_id = e.car_master_id
                                AND previous.id < e.id
                                AND previous.analysis_status = 'NOT_ANALYZED'
                          ))
                        ORDER BY e.id ASC
                        LIMIT ?
                        FOR UPDATE SKIP LOCKED
                        """,
                (rs, rowNum) -> new PendingActivation(
                        rs.getLong("id"),
                        rs.getLong("car_master_id"),
                        ProcessCode.valueOf(rs.getString("process_code")),
                        rs.getString("current_status"),
                        rs.getBoolean("has_previous_abnormal")),
                Math.min(Math.max(limit, 1), 1_000)
        );
        int updated = 0;
        for (PendingActivation candidate : candidates) {
            boolean blocked = "FAULT".equals(candidate.operationStatus())
                    || "STOPPED".equals(candidate.operationStatus())
                    || candidate.hasPreviousAbnormal();
            DispatchStatus nextStatus;
            if (blocked) {
                nextStatus = DispatchStatus.BLOCKED;
            } else if (hasRequiredPreviousProcessCompleted(candidate)) {
                nextStatus = DispatchStatus.READY;
            } else {
                continue;
            }
            updated += jdbcTemplate.update(
                    """
                            UPDATE manufacturing_event_json
                            SET dispatch_status = ?, updated_at = CURRENT_TIMESTAMP
                            WHERE id = ? AND dispatch_status = 'PENDING'
                              AND COALESCE(is_sent, 0) = 0
                            """,
                    nextStatus.name(),
                    candidate.id()
            );
        }
        return updated;
    }

    private boolean hasRequiredPreviousProcessCompleted(PendingActivation candidate) {
        ProcessCode requiredPrevious = switch (candidate.processCode()) {
            case PRESS -> null;
            case BODY -> ProcessCode.PRESS;
            case PAINT -> ProcessCode.BODY;
            case ASSEMBLY -> ProcessCode.PAINT;
        };
        if (requiredPrevious == null) {
            return true;
        }
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(1)
                        FROM manufacturing_event_json
                        WHERE car_master_id = ?
                          AND process_code = ?
                          AND analysis_status = 'NORMAL'
                        """,
                Integer.class,
                candidate.carMasterId(),
                requiredPrevious.name()
        );
        return count != null && count > 0;
    }

    public int markSent(long id, LocalDateTime eventTime) {
        return jdbcTemplate.update(
                """
                        UPDATE manufacturing_event_json
                        SET dispatch_status = 'SENT', is_sent = 1, error_message = NULL,
                            event_time = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = ? AND dispatch_status = 'READY' AND COALESCE(is_sent, 0) = 0
                        """, eventTime, id
        );
    }


    public int markPublishFailed(long id, String errorMessage) {
        return jdbcTemplate.update(
                """
                        UPDATE manufacturing_event_json
                        SET is_sent = 0, retry_count = COALESCE(retry_count, 0) + 1,
                            error_message = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ? AND dispatch_status = 'READY' AND COALESCE(is_sent, 0) = 0
                        """, abbreviate(errorMessage), id
        );
    }

    public int markAnalysisCompleted(String eventId, boolean abnormal) {
        return jdbcTemplate.update(
                """
                        UPDATE manufacturing_event_json
                        SET analysis_status = ?, error_message = NULL, updated_at = CURRENT_TIMESTAMP
                        WHERE event_id = ?
                        """, abnormal ? AnalysisStatus.ABNORMAL.name() : AnalysisStatus.NORMAL.name(), eventId
        );
    }

    public int markAnalysisFailed(String eventId, String errorMessage) {
        // AnalysisStatus has no failure value. Preserve NOT_ANALYZED and retain the technical error.
        return jdbcTemplate.update(
                """
                        UPDATE manufacturing_event_json
                        SET analysis_status = 'NOT_ANALYZED', error_message = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE event_id = ?
                        """, abbreviate(errorMessage), eventId
        );
    }

    public int releaseNextProcess(Long carMasterId, String nextProcessCode) {
        if (carMasterId == null || nextProcessCode == null) {
            return 0;
        }
        return jdbcTemplate.update(
                """
                        UPDATE manufacturing_event_json
                        SET dispatch_status = 'READY', updated_at = CURRENT_TIMESTAMP
                        WHERE car_master_id = ?
                          AND process_code = ?
                          AND dispatch_status = 'PENDING'
                        """,
                carMasterId,
                nextProcessCode
        );
    }

    public int countPendingNextProcessCandidates(Long carMasterId, String nextProcessCode) {
        if (carMasterId == null || nextProcessCode == null) {
            return 0;
        }
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(1)
                        FROM manufacturing_event_json
                        WHERE car_master_id = ?
                          AND process_code = ?
                          AND dispatch_status = 'PENDING'
                        """,
                Integer.class,
                carMasterId,
                nextProcessCode
        );
        return count == null ? 0 : count;
    }

    public int blockFollowingProcesses(Long carMasterId, List<ProcessCode> followingProcesses) {
        if (carMasterId == null || followingProcesses == null || followingProcesses.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", followingProcesses.stream().map(process -> "?").toList());
        Object[] params = new Object[followingProcesses.size() + 1];
        params[0] = carMasterId;
        for (int i = 0; i < followingProcesses.size(); i++) {
            params[i + 1] = followingProcesses.get(i).name();
        }
        return jdbcTemplate.update(
                """
                        UPDATE manufacturing_event_json
                        SET dispatch_status = 'BLOCKED', updated_at = CURRENT_TIMESTAMP
                        WHERE car_master_id = ?
                          AND process_code IN (%s)
                          AND dispatch_status IN ('PENDING', 'READY')
                        """.formatted(placeholders),
                params
        );
    }

    public int blockReadyEvents(Long equipmentId, String equipmentCode) {
        return jdbcTemplate.update(
                """
                        UPDATE manufacturing_event_json
                        SET dispatch_status = 'BLOCKED', updated_at = CURRENT_TIMESTAMP
                        WHERE (equipment_id = ? OR equipment_id IN (
                              SELECT id FROM equipment WHERE equipment_code = ?))
                          AND dispatch_status = 'READY' AND COALESCE(is_sent, 0) = 0
                        """, equipmentId, equipmentCode
        );
    }

    public int restoreBlockedEvents(Long equipmentId, String equipmentCode) {
        return jdbcTemplate.update(
                """
                        UPDATE manufacturing_event_json
                        SET dispatch_status = 'READY', updated_at = CURRENT_TIMESTAMP
                        WHERE (equipment_id = ? OR equipment_id IN (
                              SELECT id FROM equipment WHERE equipment_code = ?))
                          AND dispatch_status = 'BLOCKED' AND COALESCE(is_sent, 0) = 0
                        """, equipmentId, equipmentCode
        );
    }

    public boolean hasBlockedEventsByCarMasterId(long carMasterId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(1)
                        FROM manufacturing_event_json
                        WHERE car_master_id = ? AND dispatch_status = 'BLOCKED'
                        """,
                Integer.class,
                carMasterId
        );
        return count != null && count > 0;
    }

    private RowMapper<StoredManufacturingEvent> rowMapper() {
        return (rs, rowNum) -> {
            long id = rs.getLong("id");
            String rawJson = rs.getString("event_json");
            Map<String, Object> eventJson = parseEventJson(id, rawJson);
            ManufacturingRawEvent payload = new ManufacturingRawEvent(
                    id, rs.getString("event_id"), nullableDateTime(rs, "event_time"),
                    nullableLong(rs, "car_master_id"), nullableLong(rs, "equipment_id"),
                    ProcessCode.valueOf(rs.getString("process_code")),
                    rs.getString("equipment_code"), rs.getString("equipment_type"), null, null, eventJson
            );
            return new StoredManufacturingEvent(
                    payload, rawJson, findText(eventJson, "carId"),
                    DispatchStatus.valueOf(rs.getString("dispatch_status")),
                    AnalysisStatus.valueOf(rs.getString("analysis_status")),
                    rs.getBoolean("is_sent"), rs.getLong("retry_count"),
                    rs.getString("error_message")
            );
        };
    }

    private Map<String, Object> parseEventJson(long id, String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new KafkaException(KafkaErrorStatus.INVALID_EVENT_JSON,
                    "Invalid manufacturing event JSON. id=" + id, exception);
        }
    }

    private String findText(Object value, String field) {
        if (value instanceof Map<?, ?> map) {
            Object direct = map.get(field);
            if (direct != null && !direct.toString().isBlank()) return direct.toString();
            for (Object nested : map.values()) {
                String found = findText(nested, field);
                if (found != null) return found;
            }
        } else if (value instanceof Iterable<?> values) {
            for (Object nested : values) {
                String found = findText(nested, field);
                if (found != null) return found;
            }
        }
        return null;
    }

    private LocalDateTime parseEventJsonEventTime(Map<String, Object> eventJson) {
        String eventTime = findText(eventJson, "eventTime");
        if (eventTime == null || eventTime.isBlank()) {
            eventTime = findText(eventJson, "event_time");
        }
        if (eventTime == null || eventTime.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(eventTime);
        } catch (RuntimeException ignored) {
            return OffsetDateTime.parse(eventTime).toLocalDateTime();
        }
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private LocalDateTime nullableDateTime(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String abbreviate(String message) {
        if (message == null) return "Unknown technical failure";
        return message.length() <= 2_000 ? message : message.substring(0, 2_000);
    }

    private record PendingActivation(
            long id,
            long carMasterId,
            ProcessCode processCode,
            String operationStatus,
            boolean hasPreviousAbnormal
    ) {}

    public record StoredManufacturingEvent(
            ManufacturingRawEvent payload,
            String rawJson,
            String carId,
            DispatchStatus dispatchStatus,
            AnalysisStatus analysisStatus,
            boolean sent,
            long retryCount,
            String errorMessage
    ) {
        public long id() { return payload.id(); }
        public String eventId() { return payload.eventId(); }
        public String equipmentCode() { return payload.equipmentCode(); }
    }

    public record PressEventTime(String eventId, LocalDateTime eventTime) {}

    public record PressEventDateOption(java.time.LocalDate date, String sampleEventId) {}

    public int releaseNextProcessByEventId(String eventId) {
        return jdbcTemplate.update(
                """
                        UPDATE manufacturing_event_json
                        SET dispatch_status = 'READY',
                            updated_at = CURRENT_TIMESTAMP
                        WHERE dispatch_status = 'PENDING'
                          AND (car_master_id, process_code) = (
                              SELECT current_event.car_master_id,
                                     CASE current_event.process_code
                                         WHEN 'PRESS' THEN 'BODY'
                                         WHEN 'BODY' THEN 'PAINT'
                                         WHEN 'PAINT' THEN 'ASSEMBLY'
                                         ELSE NULL
                                     END
                              FROM (SELECT * FROM manufacturing_event_json) current_event
                              WHERE current_event.event_id = ?
                                AND current_event.analysis_status = 'NORMAL'
                                AND current_event.dispatch_status = 'SENT'
                          )
                        """,
                eventId
        );
    }

    public int releaseNextProcessByCurrentRowId(Long currentEventRowId) {
        if (currentEventRowId == null) {
            return 0;
        }

        return jdbcTemplate.update(
                """
                        UPDATE manufacturing_event_json
                        SET dispatch_status = 'READY',
                            updated_at = CURRENT_TIMESTAMP
                        WHERE dispatch_status = 'PENDING'
                          AND (car_master_id, process_code) = (
                              SELECT current_event.car_master_id,
                                     CASE current_event.process_code
                                         WHEN 'PRESS' THEN 'BODY'
                                         WHEN 'BODY' THEN 'PAINT'
                                         WHEN 'PAINT' THEN 'ASSEMBLY'
                                         ELSE NULL
                                     END
                              FROM (SELECT * FROM manufacturing_event_json) current_event
                              WHERE current_event.id = ?
                                AND current_event.analysis_status = 'NORMAL'
                                AND current_event.dispatch_status = 'SENT'
                          )
                        """,
                currentEventRowId
        );
    }
}

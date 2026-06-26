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
                          AND event_time <= ? AND COALESCE(retry_count, 0) < ?
                        ORDER BY event_time ASC, id ASC LIMIT 1
                        """.formatted(SELECT_COLUMNS),
                rowMapper(), virtualNow, maxRetries
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
                          AND event_time <= ?
                          AND COALESCE(retry_count, 0) < ?
                        ORDER BY event_time ASC, id ASC
                        LIMIT ?
                        FOR UPDATE SKIP LOCKED
                        """.formatted(SELECT_COLUMNS),
                rowMapper(), virtualNow, maxRetries, Math.min(Math.max(limit, 1), 1_000)
        );
    }

    public List<StoredManufacturingEvent> findRecent(int limit) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS
                        + " FROM manufacturing_event_json ORDER BY event_time ASC, id ASC LIMIT ?",
                rowMapper(), limit
        );
    }

    /**
     * Activates only the earliest pending event per vehicle. A preceding event with
     * NOT_ANALYZED blocks progression; completed NORMAL/ABNORMAL events do not.
     */
    public int prepareDispatchablePendingEvents(LocalDateTime virtualNow, int limit) {
        List<PendingActivation> candidates = jdbcTemplate.query(
                """
                        SELECT e.id, q.health_status, q.current_status
                        FROM manufacturing_event_json e
                        LEFT JOIN equipment q ON q.id = e.equipment_id
                        WHERE e.dispatch_status = 'PENDING'
                          AND COALESCE(e.is_sent, 0) = 0
                          AND e.event_time <= ?
                          AND NOT EXISTS (
                              SELECT 1 FROM manufacturing_event_json previous
                              WHERE previous.car_master_id = e.car_master_id
                                AND (previous.event_time < e.event_time
                                  OR (previous.event_time = e.event_time AND previous.id < e.id))
                                AND previous.analysis_status = 'NOT_ANALYZED'
                          )
                        ORDER BY e.event_time ASC, e.id ASC
                        LIMIT ?
                        FOR UPDATE SKIP LOCKED
                        """,
                (rs, rowNum) -> new PendingActivation(
                        rs.getLong("id"), rs.getString("health_status"),
                        rs.getString("current_status")),
                virtualNow, Math.min(Math.max(limit, 1), 1_000)
        );
        int updated = 0;
        for (PendingActivation candidate : candidates) {
            boolean faulted = "ABNORMAL".equals(candidate.healthStatus())
                    || "FAULT".equals(candidate.operationStatus())
                    || "STOPPED".equals(candidate.operationStatus());
            updated += jdbcTemplate.update(
                    """
                            UPDATE manufacturing_event_json
                            SET dispatch_status = ?, updated_at = CURRENT_TIMESTAMP
                            WHERE id = ? AND dispatch_status = 'PENDING'
                              AND COALESCE(is_sent, 0) = 0
                            """,
                    faulted ? DispatchStatus.BLOCKED.name() : DispatchStatus.READY.name(),
                    candidate.id()
            );
        }
        return updated;
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

    public int blockReadyEvents(long equipmentId, String equipmentCode) {
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

    public int restoreBlockedEvents(long equipmentId, String equipmentCode) {
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

    private record PendingActivation(long id, String healthStatus, String operationStatus) {}

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
}

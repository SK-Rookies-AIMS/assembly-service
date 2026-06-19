package com.aims.assembly.repository.event;

import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.common.status.KafkaErrorStatus;
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

/**
 * SampleDB manufacturing_event_json 원천 이벤트 조회 및 전송 상태 갱신.
 */
@Repository
public class ManufacturingEventJsonRepository {

    // raw Kafka 메시지 구성과 전송 상태 확인에 필요한 엔티티 컬럼
    private static final String SELECT_COLUMNS = """
            id, event_id, event_time, car_master_id, equipment_id, process_code,
            station_code, equipment_code, equipment_type, equipment_status,
            event_type, event_json, is_sent, sent_at
            """;

    private final JdbcTemplate sampleJdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ManufacturingEventJsonRepository(
            @Qualifier("sampleJdbcTemplate") JdbcTemplate sampleJdbcTemplate
    ) {
        this.sampleJdbcTemplate = sampleJdbcTemplate;
    }

    public Optional<StoredManufacturingEvent> findById(long id) {
        // PK 기준 단일 SampleDB 제조 이벤트 조회
        return sampleJdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM manufacturing_event_json WHERE id = ?",
                rowMapper(),
                id
        ).stream().findFirst();
    }

    public Optional<StoredManufacturingEvent> findFirstUnsent() {
        // 미전송 이벤트 중 event_time과 id 기준 가장 오래된 1건 조회
        return findUnsent(1).stream().findFirst();
    }

    public List<StoredManufacturingEvent> findUnsent(int limit) {
        // Scheduler가 순서대로 재생할 미전송 이벤트 묶음 조회
        return sampleJdbcTemplate.query(
                """
                        SELECT %s
                        FROM manufacturing_event_json
                        WHERE COALESCE(is_sent, 0) = 0
                        ORDER BY event_time ASC, id ASC
                        LIMIT ?
                        """.formatted(SELECT_COLUMNS),
                rowMapper(),
                limit
        );
    }

    public List<StoredManufacturingEvent> findRecent(int limit) {
        // 테스트 화면 확인용 제조 이벤트 목록 조회
        return sampleJdbcTemplate.query(
                """
                        SELECT %s
                        FROM manufacturing_event_json
                        ORDER BY event_time ASC, id ASC
                        LIMIT ?
                        """.formatted(SELECT_COLUMNS),
                rowMapper(),
                limit
        );
    }

    public int markSent(long id, LocalDateTime sentAt) {
        // Kafka broker 저장 성공 이후 전송 완료 상태와 시각 갱신
        return sampleJdbcTemplate.update(
                """
                        UPDATE manufacturing_event_json
                        SET is_sent = 1,
                            sent_at = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """,
                sentAt,
                id
        );
    }

    private RowMapper<StoredManufacturingEvent> rowMapper() {
        return (rs, rowNum) -> {
            // JSON 파싱 오류 메시지에 사용할 SampleDB PK 추출
            long id = rs.getLong("id");

            // 엔티티 식별·라우팅 컬럼과 event_json 상세 데이터 결합
            ManufacturingRawEvent payload = new ManufacturingRawEvent(
                    id,
                    rs.getString("event_id"),
                    rs.getTimestamp("event_time").toLocalDateTime(),
                    nullableLong(rs, "car_master_id"),
                    nullableLong(rs, "equipment_id"),
                    ProcessCode.valueOf(rs.getString("process_code")),
                    rs.getString("station_code"),
                    rs.getString("equipment_code"),
                    rs.getString("equipment_type"),
                    rs.getString("equipment_status"),
                    rs.getString("event_type"),
                    parseEventJson(id, rs.getString("event_json"))
            );

            // Kafka payload와 SampleDB 전송 상태를 함께 반환
            return new StoredManufacturingEvent(
                    payload,
                    rs.getBoolean("is_sent"),
                    rs.getTimestamp("sent_at") == null
                            ? null
                            : rs.getTimestamp("sent_at").toLocalDateTime()
            );
        };
    }

    private Map<String, Object> parseEventJson(long id, String eventJson) {
        try {
            // MySQL JSON 문자열을 Kafka 모델의 eventJson Map으로 변환
            return objectMapper.readValue(
                    eventJson,
                    new TypeReference<Map<String, Object>>() {
                    }
            );
        } catch (Exception exception) {
            // 손상된 JSON 데이터의 SampleDB 행 식별 정보 포함
            throw new KafkaException(
                    KafkaErrorStatus.INVALID_EVENT_JSON,
                    "제조 이벤트 JSON 데이터가 올바르지 않습니다. id=" + id,
                    exception
            );
        }
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        // JDBC primitive long 조회 후 SQL NULL 복원
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    public record StoredManufacturingEvent(
            ManufacturingRawEvent payload,
            boolean sent,
            LocalDateTime sentAt
    ) {
        public long id() {
            // SampleDB 상태 갱신용 PK 노출
            return payload.id();
        }

        public String eventId() {
            // Kafka 전체 파이프라인 추적 ID 노출
            return payload.eventId();
        }

        public String equipmentCode() {
            // Kafka partition 결정용 message key 노출
            return payload.equipmentCode();
        }
    }
}

package com.aims.assembly.kafka.model;

import com.aims.assembly.domain.enums.ProcessCode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * manufacturing_event_json 엔티티 컬럼 기반 raw Kafka 메시지.
 * 식별·라우팅 정보는 테이블 컬럼, 센서·공정 상세 데이터는 eventJson 사용.
 */
public record ManufacturingRawEvent(
        long id,
        String eventId,
        LocalDateTime eventTime,
        Long carMasterId,
        Long equipmentId,
        ProcessCode processCode,
        String stationCode,
        String equipmentCode,
        String equipmentType,
        String equipmentStatus,
        String eventType,
        Map<String, Object> eventJson
) {
}

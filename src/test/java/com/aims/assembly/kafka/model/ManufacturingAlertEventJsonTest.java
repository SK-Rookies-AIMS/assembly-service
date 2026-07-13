package com.aims.assembly.kafka.model;

import com.aims.assembly.domain.enums.ProcessCode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ManufacturingAlertEventJsonTest {

    @Test
    void serializesImageUrlInFinalKafkaPayload() {
        ManufacturingAlertEvent event = new ManufacturingAlertEvent(
                "ALT-1",
                "EVT-1",
                "ANL-1",
                LocalDateTime.of(2026, 7, 13, 10, 0),
                "FACTORY-1",
                "LINE-1",
                ProcessCode.PRESS,
                "PRESS-1",
                "Press 1",
                1L,
                2L,
                ManufacturingAlertEvent.TYPE_EQUIPMENT_ABNORMAL,
                "프레스 설비 이상 감지",
                "프레스 설비에서 위험 상태가 감지되었습니다.",
                "CRITICAL",
                100.0,
                "OPEN",
                true,
                List.of("FAULT"),
                "설비 확인",
                "s3://event-image-858507113889-ap-northeast-2-an/press_1.png"
        );

        String json = new ObjectMapper().writeValueAsString(event);

        assertThat(json).contains(
                "\"imageUrl\":\"s3://event-image-858507113889-ap-northeast-2-an/press_1.png\""
        );
    }
}

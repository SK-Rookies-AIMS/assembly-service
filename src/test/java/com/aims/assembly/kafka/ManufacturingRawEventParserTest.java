package com.aims.assembly.kafka;

import com.aims.assembly.domain.enums.ProcessCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ManufacturingRawEventParserTest {
    private final ManufacturingRawEventParser parser = new ManufacturingRawEventParser();

    @Test
    void readsProcessCodeFromEnvelopeWhenEventJsonDoesNotContainIt() {
        String message = """
                {
                  "id": 1,
                  "eventId": "EVT-20260616-000001",
                  "eventTime": "2026-06-16T10:00:01",
                  "carMasterId": 1,
                  "equipmentId": 10,
                  "processCode": "PRESS",
                  "equipmentCode": "EQ_PRESS_01",
                  "equipmentType": "HYDRAULIC_PRESS",
                  "eventJson": {
                    "event": {"eventType": "PROCESS_STATUS"},
                    "processData": {"press": {"targetCycleTimeSec": 40.0}}
                  }
                }
                """;

        var result = parser.parse(message);

        assertThat(result.processCode()).isEqualTo(ProcessCode.PRESS);
        assertThat(result.eventJson()).doesNotContainKeys("processCode", "process_code");
        assertThat(result.eventJson()).extracting("processData")
                .isEqualTo(Map.of("press", Map.of("targetCycleTimeSec", 40.0)));
    }

    @Test
    void readsSnakeCaseEventTimeForBackwardCompatibility() {
        String message = """
                {
                  "id": 1,
                  "eventId": "EVT-20260616-000001",
                  "event_time": "2026-06-16T10:00:01",
                  "carMasterId": 1,
                  "equipmentId": 10,
                  "processCode": "PRESS",
                  "equipmentCode": "EQ_PRESS_01",
                  "equipmentType": "HYDRAULIC_PRESS",
                  "eventJson": {
                    "event": {"eventType": "PROCESS_STATUS"}
                  }
                }
                """;

        var result = parser.parse(message);

        assertThat(result.eventTime()).isEqualTo(LocalDateTime.of(2026, 6, 16, 10, 0, 1));
    }
}

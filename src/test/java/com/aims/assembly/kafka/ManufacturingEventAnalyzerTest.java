package com.aims.assembly.kafka;

import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.kafka.model.ManufacturingAlertEvent;
import com.aims.assembly.kafka.model.ManufacturingAnalysisEvent;
import com.aims.assembly.kafka.model.ManufacturingRawEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 AI 모델이나 Kafka broker 연결 없이 예시 위험도 계산과 후속 이벤트 변환 검증.
 */
@DisplayName("제조 이벤트 분석기 단위 테스트")
class ManufacturingEventAnalyzerTest {

    private final ManufacturingEventAnalyzer analyzer = new ManufacturingEventAnalyzer();

    @Test
    @DisplayName("raw 이벤트를 분석 결과와 알림 이벤트로 변환")
    void analyzesSampleRawEventAndCreatesDownstreamEvents() {
        // Given: SampleDB 엔티티 컬럼과 eventJson 구조를 모방한 raw 이벤트
        ManufacturingRawEvent rawEvent = createRawEvent();

        // When: 위험도 분석 및 알림 이벤트 변환
        ManufacturingAnalysisEvent analysis = analyzer.analyze(rawEvent);
        ManufacturingAlertEvent alert = analyzer.toAlertEvent(analysis);

        // Then: 원본 eventId와 equipmentCode 추적 유지
        assertThat(analysis.eventId()).isEqualTo(rawEvent.eventId());
        assertThat(analysis.equipmentCode()).isEqualTo("EQ_PRESS_01");
        assertThat(analysis.riskScores().overallRiskScore()).isBetween(0.0, 100.0);
        assertThat(alert.equipmentCode()).isEqualTo(analysis.equipmentCode());
        assertThat(alert.analysisId()).isEqualTo(analysis.analysisId());
    }

    private ManufacturingRawEvent createRawEvent() {
        // PRD 구조의 processMetrics, sensor, product 상세 데이터 구성
        return new ManufacturingRawEvent(
                1L,
                "EVT-20260618-001",
                LocalDateTime.of(2026, 6, 18, 10, 0),
                100L,
                200L,
                ProcessCode.PRESS,
                "PRESS_STATION_01",
                "EQ_PRESS_01",
                "HYDRAULIC_PRESS",
                "WARNING",
                "PROCESS_STATUS",
                Map.of(
                        "location", Map.of(
                                "factoryCode", "AIMS_FACTORY_01",
                                "lineCode", "PRESS_LINE_01"
                        ),
                        "product", Map.of(
                                "productId", "PRODUCT-000001",
                                "carId", "CAR-000001"
                        ),
                        "processMetrics", Map.of(
                                "cycleTimeSec", 42.5,
                                "waitingTimeSec", 8.3,
                                "stationDelaySec", 5.7,
                                "queueLength", 7,
                                "equipmentIdleTimeSec", 12.1
                        ),
                        "sensor", Map.of(
                                "vibration", Map.of("vibrationScore", 0.21),
                                "thermal", Map.of("maxTemperature", 52.9)
                        )
                )
        );
    }
}

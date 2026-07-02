package com.aims.assembly.dto.press;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import com.aims.assembly.domain.press.PressAnalysisResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PressAnomalyDetectionResponseTest {

    @Test
    void chartPointDefaultsNullableNumbers() {
        ManufacturingAnalysisResult analysis = ManufacturingAnalysisResult.builder()
                .eventId("EVT-TEST")
                .analysisId("ANL-TEST")
                .isAbnormal(null)
                .severity(null)
                .riskScore(null)
                .build();
        PressAnalysisResult result = PressAnalysisResult.builder()
                .analysisResult(analysis)
                .targetCycleTimeSec(null)
                .actualCycleTimeSec(null)
                .cycleTimeGapSec(null)
                .timestampDelaySec(null)
                .build();

        PressAnomalyDetectionResponse.ChartPoint point = PressAnomalyDetectionResponse.ChartPoint.from(
                result,
                LocalDateTime.of(2026, 7, 1, 16, 8, 38)
        );

        assertThat(point.targetCycleTimeSec()).isNull();
        assertThat(point.actualCycleTimeSec()).isNull();
        assertThat(point.cycleTimeGapSec()).isNull();
        assertThat(point.timestampDelaySec()).isNull();
        assertThat(point.riskScore()).isNull();
        assertThat(point.isAbnormal()).isFalse();
        assertThat(point.severity()).isEqualTo("NORMAL");
    }

    @Test
    void metricsDefaultsWhenPointIsMissing() {
        PressAnomalyDetectionResponse.Metrics metrics = PressAnomalyDetectionResponse.metricsFrom(null);

        assertThat(metrics.targetCycleTimeSec()).isNull();
        assertThat(metrics.actualCycleTimeSec()).isNull();
        assertThat(metrics.cycleTimeGapSec()).isNull();
        assertThat(metrics.timestampDelaySec()).isNull();
        assertThat(metrics.riskScore()).isNull();
        assertThat(metrics.severity()).isEqualTo("NORMAL");
    }
}

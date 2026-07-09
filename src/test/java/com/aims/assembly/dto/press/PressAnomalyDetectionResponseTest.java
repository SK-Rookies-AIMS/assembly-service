package com.aims.assembly.dto.press;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import com.aims.assembly.domain.press.PressAnalysisResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

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
                .build();

        PressAnomalyDetectionResponse.ChartPoint point = PressAnomalyDetectionResponse.ChartPoint.from(
                result,
                LocalDateTime.of(2026, 7, 1, 16, 8, 38)
        );

        assertThat(point.targetCycleTimeSec()).isNull();
        assertThat(point.actualCycleTimeSec()).isNull();
        assertThat(point.cycleTimeGapSec()).isNull();
        assertThat(point.riskScore()).isNull();
        assertThat(point.isAbnormal()).isFalse();
        assertThat(point.severity()).isEqualTo("NORMAL");
    }

    @Test
    void chartPointPromotesAbnormalToWarning() {
        ManufacturingAnalysisResult analysis = ManufacturingAnalysisResult.builder()
                .eventId("EVT-TEST")
                .analysisId("ANL-TEST")
                .isAbnormal(true)
                .severity(null)
                .riskScore(42.0)
                .build();
        PressAnalysisResult result = PressAnalysisResult.builder()
                .analysisResult(analysis)
                .targetCycleTimeSec(40.0)
                .actualCycleTimeSec(44.0)
                .cycleTimeGapSec(4.0)
                .build();

        PressAnomalyDetectionResponse.ChartPoint point = PressAnomalyDetectionResponse.ChartPoint.from(
                result,
                LocalDateTime.of(2026, 7, 1, 16, 8, 38)
        );

        assertThat(point.isAbnormal()).isTrue();
        assertThat(point.severity()).isEqualTo("WARNING");
    }

    @Test
    void metricsDefaultsWhenPointIsMissing() {
        PressAnomalyDetectionResponse.Metrics metrics = PressAnomalyDetectionResponse.metricsFrom(null);

        assertThat(metrics.targetCycleTimeSec()).isNull();
        assertThat(metrics.actualCycleTimeSec()).isNull();
        assertThat(metrics.cycleTimeGapSec()).isNull();
        assertThat(metrics.riskScore()).isNull();
        assertThat(metrics.severity()).isEqualTo("NORMAL");
    }

    @Test
    void chartsFromBuildsTwoUiSeries() {
        ManufacturingAnalysisResult analysis = ManufacturingAnalysisResult.builder()
                .eventId("EVT-TEST")
                .analysisId("ANL-TEST")
                .isAbnormal(true)
                .severity(null)
                .riskScore(72.0)
                .build();
        PressAnalysisResult result = PressAnalysisResult.builder()
                .analysisResult(analysis)
                .targetCycleTimeSec(40.0)
                .actualCycleTimeSec(43.0)
                .cycleTimeGapSec(3.0)
                .countIncreaseYn(false)
                .build();

        PressAnomalyDetectionResponse.ChartPoint point = PressAnomalyDetectionResponse.ChartPoint.from(
                result,
                LocalDateTime.of(2026, 7, 1, 16, 8, 38)
        );
        PressAnomalyDetectionResponse.Charts charts = PressAnomalyDetectionResponse.chartsFrom(List.of(point));

        assertThat(charts.cycleTime().points().get(0).targetCycleTimeSec()).isEqualTo(40.0);
        assertThat(charts.cycleTime().points().get(0).actualCycleTimeSec()).isEqualTo(43.0);
        assertThat(charts.delay().points().get(0).cycleTimeGapSec()).isEqualTo(3.0);
    }
}

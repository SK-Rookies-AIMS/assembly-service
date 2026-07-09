package com.aims.assembly.dto.body;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record BodyAnomalyDetectionResponse(
        LocalDate date,
        LocalDateTime from,
        LocalDateTime to,
        LocalDateTime previousEndAt,
        List<DateOption> dateOptions,
        Metrics metrics,
        Charts charts,
        List<FrequencyBandPoint> frequencyChart,
        List<FrequencyZonePoint> frequencyZoneChart,
        FrequencyZoneAnalysis frequencyZoneAnalysis,
        AlertPanel alert
) {
    public record DateOption(
            LocalDate date,
            String sampleEventId
    ) {
    }

    public record Metrics(
            String robotMotionStatus,
            String robotOperationMode,
            Double avgRobotVibrationScore,
            Double avgVibrationPeak,
            Double avgVibrationRms,
            String frequencyPeakBand,
            Double avgFrequencyPeakValue,
            Double vibrationWarningLine,
            Double vibrationDangerLine,
            Double peakWarningLine,
            Double peakDangerLine,
            Double riskScore,
            String riskScoreScale,
            String severity,
            Map<String, Double> frequencyBands
    ) {
    }

    public record Charts(
            RobotMetricChart robotVibration,
            PeakMetricChart frequencyPeak
    ) {
    }

    public record RobotMetricChart(
            String title,
            String metricKey,
            String unit,
            List<RobotMetricPoint> points
    ) {
    }

    public record PeakMetricChart(
            String title,
            String metricKey,
            String unit,
            List<PeakMetricPoint> points
    ) {
    }

    public record RobotMetricPoint(
            String eventId,
            String analysisId,
            LocalDateTime timestamp,
            Double value,
            Double warningLine,
            Double dangerLine,
            Boolean isAbnormal,
            String severity
    ) {
    }

    public record PeakMetricPoint(
            String eventId,
            String analysisId,
            LocalDateTime timestamp,
            Double value,
            Double secondaryValue,
            Double warningLine,
            Double dangerLine,
            Boolean isAbnormal,
            String severity
    ) {
    }

    public record ChartPoint(
            String eventId,
            String analysisId,
            LocalDateTime timestamp,
            Double robotVibrationScore,
            Double frequencyPeakValue,
            Double vibrationPeak,
            Double vibrationWarningLine,
            Double vibrationDangerLine,
            Double peakWarningLine,
            Double peakDangerLine,
            Double vibrationRms,
            Double riskScore,
            Boolean isAbnormal,
            String severity
    ) {
    }

    public record FrequencyBandPoint(
            LocalDateTime timestamp,
            String band,
            Double value,
            Double targetValue,
            Double warningValue,
            Double dangerValue
    ) {
    }

    public record FrequencyZonePoint(
            String zone,
            String range,
            String description,
            Double avg,
            Double max,
            Double targetValue,
            Double warningValue,
            Double dangerValue
    ) {
    }

    public record FrequencyZoneAnalysis(
            ZoneStats low,
            ZoneStats main,
            ZoneStats high,
            ZoneStats ultra
    ) {
        public record ZoneStats(
                Double avg,
                Double max
        ) {
        }
    }

    public record AlertPanel(
            Boolean detected,
            String title,
            List<String> reasons
    ) {
    }
}

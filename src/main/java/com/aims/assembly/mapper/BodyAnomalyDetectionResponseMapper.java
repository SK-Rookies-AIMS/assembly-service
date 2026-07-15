package com.aims.assembly.mapper;

import com.aims.assembly.dto.body.BodyAnomalyDetectionResponse;
import lombok.experimental.UtilityClass;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@UtilityClass
public final class BodyAnomalyDetectionResponseMapper {

    public static BodyAnomalyDetectionResponse toResponse(
            LocalDate date,
            LocalDateTime from,
            LocalDateTime to,
            LocalDateTime previousEndAt,
            List<BodyAnomalyDetectionResponse.DateOption> dateOptions,
            BodyAnomalyDetectionResponse.Metrics metrics,
            BodyAnomalyDetectionResponse.Charts charts,
            List<BodyAnomalyDetectionResponse.FrequencyBandPoint> frequencyChart,
            List<BodyAnomalyDetectionResponse.FrequencyZonePoint> frequencyZoneChart,
            BodyAnomalyDetectionResponse.FrequencyZoneAnalysis frequencyZoneAnalysis,
            BodyAnomalyDetectionResponse.AlertPanel alert
    ) {
        return new BodyAnomalyDetectionResponse(date, from, to, previousEndAt, dateOptions, metrics, charts, frequencyChart, frequencyZoneChart, frequencyZoneAnalysis, alert);
    }

    public static BodyAnomalyDetectionResponse.DateOption toDateOption(
            LocalDate date,
            String sampleEventId
    ) {
        return new BodyAnomalyDetectionResponse.DateOption(date, sampleEventId);
    }

    public static BodyAnomalyDetectionResponse.ChartPoint toChartPoint(
            String eventId,
            String analysisId,
            String logNo,
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
        return new BodyAnomalyDetectionResponse.ChartPoint(
                eventId,
                analysisId,
                logNo,
                timestamp,
                robotVibrationScore,
                frequencyPeakValue,
                vibrationPeak,
                vibrationWarningLine,
                vibrationDangerLine,
                peakWarningLine,
                peakDangerLine,
                vibrationRms,
                riskScore,
                isAbnormal,
                severity
        );
    }

    public static BodyAnomalyDetectionResponse.RobotMetricPoint toRobotMetricPoint(
            String eventId,
            String analysisId,
            String logNo,
            LocalDateTime timestamp,
            Double value,
            Double warningLine,
            Double dangerLine,
            Boolean isAbnormal,
            String severity
    ) {
        return new BodyAnomalyDetectionResponse.RobotMetricPoint(
                eventId,
                analysisId,
                logNo,
                timestamp,
                value,
                warningLine,
                dangerLine,
                isAbnormal,
                severity
        );
    }

    public static BodyAnomalyDetectionResponse.PeakMetricPoint toPeakMetricPoint(
            String eventId,
            String analysisId,
            String logNo,
            LocalDateTime timestamp,
            Double value,
            Double secondaryValue,
            Double warningLine,
            Double dangerLine,
            Boolean isAbnormal,
            String severity
    ) {
        return new BodyAnomalyDetectionResponse.PeakMetricPoint(
                eventId,
                analysisId,
                logNo,
                timestamp,
                value,
                secondaryValue,
                warningLine,
                dangerLine,
                isAbnormal,
                severity
        );
    }

    public static BodyAnomalyDetectionResponse.RobotMetricChart toRobotMetricChart(
            String title,
            String metricKey,
            String unit,
            List<BodyAnomalyDetectionResponse.RobotMetricPoint> points
    ) {
        return new BodyAnomalyDetectionResponse.RobotMetricChart(title, metricKey, unit, points);
    }

    public static BodyAnomalyDetectionResponse.PeakMetricChart toPeakMetricChart(
            String title,
            String metricKey,
            String unit,
            List<BodyAnomalyDetectionResponse.PeakMetricPoint> points
    ) {
        return new BodyAnomalyDetectionResponse.PeakMetricChart(title, metricKey, unit, points);
    }

    public static BodyAnomalyDetectionResponse.Charts toCharts(
            BodyAnomalyDetectionResponse.RobotMetricChart robotVibration,
            BodyAnomalyDetectionResponse.PeakMetricChart frequencyPeak
    ) {
        return new BodyAnomalyDetectionResponse.Charts(robotVibration, frequencyPeak);
    }

    public static BodyAnomalyDetectionResponse.Metrics toMetrics(
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
        return new BodyAnomalyDetectionResponse.Metrics(
                robotMotionStatus,
                robotOperationMode,
                avgRobotVibrationScore,
                avgVibrationPeak,
                avgVibrationRms,
                frequencyPeakBand,
                avgFrequencyPeakValue,
                vibrationWarningLine,
                vibrationDangerLine,
                peakWarningLine,
                peakDangerLine,
                riskScore,
                riskScoreScale,
                severity,
                frequencyBands
        );
    }

    public static BodyAnomalyDetectionResponse.FrequencyBandPoint toFrequencyBandPoint(
            LocalDateTime timestamp,
            String band,
            Double value,
            Double targetValue,
            Double warningValue,
            Double dangerValue
    ) {
        return new BodyAnomalyDetectionResponse.FrequencyBandPoint(
                timestamp,
                band,
                value,
                targetValue,
                warningValue,
                dangerValue
        );
    }

    public static BodyAnomalyDetectionResponse.FrequencyZonePoint toFrequencyZonePoint(
            String zone,
            String range,
            String description,
            Double avg,
            Double max,
            Double targetValue,
            Double warningValue,
            Double dangerValue
    ) {
        return new BodyAnomalyDetectionResponse.FrequencyZonePoint(
                zone,
                range,
                description,
                avg,
                max,
                targetValue,
                warningValue,
                dangerValue
        );
    }

    public static BodyAnomalyDetectionResponse.AlertPanel toAlert(
            Boolean detected,
            String title,
            String logNo,
            List<String> reasons
    ) {
        return new BodyAnomalyDetectionResponse.AlertPanel(detected, title, logNo, reasons);
    }
}

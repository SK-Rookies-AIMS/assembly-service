package com.aims.assembly.mapper;

import com.aims.assembly.domain.body.BodyAnalysisResult;
import com.aims.assembly.dto.body.BodyAnomalyDetectionResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class BodyAnomalyDetectionResponseMapper {

    private BodyAnomalyDetectionResponseMapper() {
    }

    public static BodyAnomalyDetectionResponse toResponse(
            LocalDate date,
            LocalDateTime from,
            LocalDateTime to,
            LocalDateTime previousEndAt,
            List<BodyAnomalyDetectionResponse.DateOption> dateOptions,
            BodyAnomalyDetectionResponse.Metrics metrics,
            List<BodyAnomalyDetectionResponse.ChartPoint> chart,
            List<BodyAnomalyDetectionResponse.FrequencyBandPoint> frequencyChart,
            BodyAnomalyDetectionResponse.FrequencyZoneAnalysis frequencyZoneAnalysis,
            BodyAnomalyDetectionResponse.AlertPanel alert
    ) {
        return new BodyAnomalyDetectionResponse(date, from, to, previousEndAt, dateOptions, metrics, chart, frequencyChart, frequencyZoneAnalysis, alert);
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
            LocalDateTime timestamp,
            Double targetVibrationScore,
            Double vibrationScore,
            Double targetVibrationPeak,
            Double vibrationPeak,
            Double vibrationRms,
            Double riskScore,
            Boolean isAbnormal,
            String severity
    ) {
        return new BodyAnomalyDetectionResponse.ChartPoint(
                eventId,
                analysisId,
                timestamp,
                targetVibrationScore,
                vibrationScore,
                targetVibrationPeak,
                vibrationPeak,
                vibrationRms,
                riskScore,
                isAbnormal,
                severity
        );
    }

    public static BodyAnomalyDetectionResponse.Metrics toMetrics(
            String robotMotionStatus,
            String robotOperationMode,
            Double targetVibrationScore,
            Double vibrationScore,
            Double targetVibrationPeak,
            Double vibrationPeak,
            Double vibrationRms,
            String frequencyPeakBand,
            Double frequencyPeakValue,
            Double riskScore,
            String riskScoreScale,
            String severity,
            Map<String, Double> frequencyBands
    ) {
        return new BodyAnomalyDetectionResponse.Metrics(
                robotMotionStatus,
                robotOperationMode,
                targetVibrationScore,
                vibrationScore,
                targetVibrationPeak,
                vibrationPeak,
                vibrationRms,
                frequencyPeakBand,
                frequencyPeakValue,
                riskScore,
                riskScoreScale,
                severity,
                frequencyBands
        );
    }

    public static BodyAnomalyDetectionResponse.AlertPanel toAlert(
            Boolean detected,
            String title,
            List<String> reasons
    ) {
        return new BodyAnomalyDetectionResponse.AlertPanel(detected, title, reasons);
    }
}

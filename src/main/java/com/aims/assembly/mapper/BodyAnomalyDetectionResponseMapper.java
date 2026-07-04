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
            BodyAnomalyDetectionResponse.AlertPanel alert
    ) {
        return new BodyAnomalyDetectionResponse(date, from, to, previousEndAt, dateOptions, metrics, chart, alert);
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
            Double robotVibrationScore,
            Double frequencyPeakValue,
            Double riskScore,
            Boolean isAbnormal,
            String severity
    ) {
        return new BodyAnomalyDetectionResponse.ChartPoint(
                eventId,
                analysisId,
                timestamp,
                robotVibrationScore,
                frequencyPeakValue,
                riskScore,
                isAbnormal,
                severity
        );
    }

    public static BodyAnomalyDetectionResponse.ChartPoint toChartPoint(BodyAnalysisResult result, Double frequencyPeakValue) {
        var analysis = result.getAnalysisResult();
        return new BodyAnomalyDetectionResponse.ChartPoint(
                analysis.getEventId(),
                analysis.getAnalysisId(),
                analysis.getEventTime(),
                result.getRobotVibrationScore(),
                frequencyPeakValue,
                analysis.getRiskScore(),
                Boolean.TRUE.equals(analysis.getIsAbnormal()),
                analysis.getSeverity() == null ? "NORMAL" : analysis.getSeverity().name()
        );
    }

    public static BodyAnomalyDetectionResponse.Metrics toMetrics(
            String robotMotionStatus,
            String robotOperationMode,
            Double robotVibrationScore,
            Double frequencyPeakValue,
            String frequencyPeakBand,
            Double riskScore,
            String riskScoreScale,
            String severity,
            Map<String, Double> frequencyBands
    ) {
        return new BodyAnomalyDetectionResponse.Metrics(
                robotMotionStatus,
                robotOperationMode,
                robotVibrationScore,
                frequencyPeakValue,
                frequencyPeakBand,
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

package com.aims.assembly.mapper;

import com.aims.assembly.domain.press.PressAnalysisResult;
import com.aims.assembly.dto.press.PressAnomalyDetectionResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class PressAnomalyDetectionResponseMapper {

    private PressAnomalyDetectionResponseMapper() {
    }

    public static PressAnomalyDetectionResponse toResponse(
            LocalDate date,
            LocalDateTime from,
            LocalDateTime to,
            LocalDateTime previousEndAt,
            List<PressAnomalyDetectionResponse.DateOption> dateOptions,
            PressAnomalyDetectionResponse.Metrics metrics,
            List<PressAnomalyDetectionResponse.ChartPoint> chart,
            PressAnomalyDetectionResponse.AlertPanel alert
    ) {
        return new PressAnomalyDetectionResponse(date, from, to, previousEndAt, dateOptions, metrics, chart, alert);
    }

    public static PressAnomalyDetectionResponse.DateOption toDateOption(
            LocalDate date,
            String sampleEventId
    ) {
        return new PressAnomalyDetectionResponse.DateOption(date, sampleEventId);
    }

    public static PressAnomalyDetectionResponse.ChartPoint toChartPoint(PressAnalysisResult result) {
        return PressAnomalyDetectionResponse.ChartPoint.from(result);
    }

    public static PressAnomalyDetectionResponse.Metrics toMetrics(
            PressAnomalyDetectionResponse.ChartPoint point
    ) {
        return PressAnomalyDetectionResponse.metricsFrom(point);
    }

    public static PressAnomalyDetectionResponse.AlertPanel toAlert(
            Boolean detected,
            String title,
            List<String> reasons
    ) {
        return new PressAnomalyDetectionResponse.AlertPanel(detected, title, reasons);
    }
}

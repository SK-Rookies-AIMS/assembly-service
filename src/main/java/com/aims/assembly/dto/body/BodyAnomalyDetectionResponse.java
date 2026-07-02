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
        List<ChartPoint> chart,
        AlertPanel alert
) {
    public record DateOption(
            LocalDate date,
            String sampleEventId
    ) {
    }

    /**
     * 상단 지표: 로봇 모션 상태, 운전 모드, 진동 점수, 피크 진동값, 전체 위험도
     */
    public record Metrics(
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
    }

    /**
     * 그래프 시계열 포인트: 로봇 진동 점수, 위험도, 피크 진동값
     */
    public record ChartPoint(
            String eventId,
            String analysisId,
            LocalDateTime timestamp,
            Double robotVibrationScore,
            Double frequencyPeakValue,
            Double riskScore,
            Boolean isAbnormal,
            String severity
    ) {
    }

    public record AlertPanel(
            Boolean detected,
            String title,
            List<String> reasons
    ) {
    }
}

package com.aims.assembly.dto.press;

import com.aims.assembly.domain.enums.Severity;
import com.aims.assembly.domain.press.PressAnalysisResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PressAnomalyDetectionResponse(
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

    public record Metrics(
            Double targetCycleTimeSec,
            Double actualCycleTimeSec,
            Double cycleTimeGapSec,
            Double timestampDelaySec,
            Double riskScore,
            String riskScoreScale,
            String severity
    ) {
    }

    public record ChartPoint(
            String eventId,
            String analysisId,
            LocalDateTime timestamp,
            Double targetCycleTimeSec,
            Double actualCycleTimeSec,
            Double cycleTimeGapSec,
            Double timestampDelaySec,
            Double riskScore,
            Boolean countIncreaseYn,
            Boolean isAbnormal,
            String severity
    ) {
        public static ChartPoint from(PressAnalysisResult result) {
            return from(result, result.getAnalysisResult().getEventTime());
        }

        public static ChartPoint from(PressAnalysisResult result, LocalDateTime eventJsonEventTime) {
            var analysis = result.getAnalysisResult();
            Double score = analysis.getRiskScore();
            return new ChartPoint(
                    analysis.getEventId(),
                    analysis.getAnalysisId(),
                    eventJsonEventTime,
                    result.getTargetCycleTimeSec(),
                    result.getActualCycleTimeSec(),
                    result.getCycleTimeGapSec(),
                    result.getTimestampDelaySec(),
                    score,
                    result.getCountIncreaseYn(),
                    Boolean.TRUE.equals(analysis.getIsAbnormal()),
                    defaultSeverity(analysis.getSeverity())
            );
        }
    }

    public record AlertPanel(
            Boolean detected,
            String title,
            List<String> reasons
    ) {
    }

    public static Metrics metricsFrom(ChartPoint point) {
        if (point == null) {
            return new Metrics(null, null, null, null, null, "0-100", Severity.NORMAL.name());
        }
        return new Metrics(
                point.targetCycleTimeSec(),
                point.actualCycleTimeSec(),
                point.cycleTimeGapSec(),
                point.timestampDelaySec(),
                point.riskScore(),
                "0-100",
                point.severity() == null ? Severity.NORMAL.name() : point.severity()
        );
    }

    public static boolean critical(ChartPoint point) {
        return point != null
                && (Boolean.TRUE.equals(point.isAbnormal())
                || Severity.CRITICAL.name().equals(point.severity())
                || point.riskScore() != null && point.riskScore() >= 60.0);
    }

    private static String defaultSeverity(Severity severity) {
        return severity == null ? Severity.NORMAL.name() : severity.name();
    }
}

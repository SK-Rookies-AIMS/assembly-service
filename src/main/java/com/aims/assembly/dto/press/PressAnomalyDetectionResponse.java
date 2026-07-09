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
        Charts charts,
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

    public record Charts(
            RiskScoreChart riskScore,
            CycleTimeChart cycleTime,
            DelayChart delay
    ) {
    }

    public record RiskScoreChart(
            String title,
            String metricKey,
            String unit,
            List<RiskScorePoint> points
    ) {
    }

    public record CycleTimeChart(
            String title,
            String metricKey,
            String unit,
            List<CycleTimePoint> points
    ) {
    }

    public record DelayChart(
            String title,
            String metricKey,
            String unit,
            List<DelayPoint> points
    ) {
    }

    public record RiskScorePoint(
            String eventId,
            String analysisId,
            LocalDateTime timestamp,
            Double value,
            Boolean countIncreaseYn,
            Boolean isAbnormal,
            String severity
    ) {
    }

    public record CycleTimePoint(
            String eventId,
            String analysisId,
            LocalDateTime timestamp,
            Double targetCycleTimeSec,
            Double actualCycleTimeSec,
            Boolean countIncreaseYn,
            Boolean isAbnormal,
            String severity
    ) {
    }

    public record DelayPoint(
            String eventId,
            String analysisId,
            LocalDateTime timestamp,
            Double cycleTimeGapSec,
            Double timestampDelaySec,
            Boolean countIncreaseYn,
            Boolean isAbnormal,
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
            boolean isAbnormal = Boolean.TRUE.equals(analysis.getIsAbnormal());
            String severity = severityFor(isAbnormal, analysis.getSeverity());
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
                    isAbnormal,
                    severity
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

    public static Charts chartsFrom(List<ChartPoint> points) {
        List<ChartPoint> safePoints = points == null ? List.of() : points;
        return new Charts(
                new RiskScoreChart(
                        "프레스 위험도",
                        "riskScore",
                        "score",
                        safePoints.stream().map(PressAnomalyDetectionResponse::toRiskScorePoint).toList()
                ),
                new CycleTimeChart(
                        "프레스 사이클 타임",
                        "cycleTimeSec",
                        "sec",
                        safePoints.stream().map(PressAnomalyDetectionResponse::toCycleTimePoint).toList()
                ),
                new DelayChart(
                        "프레스 지연/갭",
                        "delaySec",
                        "sec",
                        safePoints.stream().map(PressAnomalyDetectionResponse::toDelayPoint).toList()
                )
        );
    }

    public static boolean critical(ChartPoint point) {
        return point != null
                && (Boolean.TRUE.equals(point.isAbnormal())
                || Severity.CRITICAL.name().equals(point.severity())
                || point.riskScore() != null && point.riskScore() >= 60.0);
    }

    private static String severityFor(boolean isAbnormal, Severity severity) {
        String normalized = severity == null ? Severity.NORMAL.name() : severity.name();
        if (!isAbnormal) {
            return normalized;
        }
        if (Severity.CRITICAL.name().equals(normalized)) {
            return Severity.CRITICAL.name();
        }
        return Severity.WARNING.name();
    }

    private static RiskScorePoint toRiskScorePoint(ChartPoint point) {
        return new RiskScorePoint(
                point.eventId(),
                point.analysisId(),
                point.timestamp(),
                point.riskScore(),
                point.countIncreaseYn(),
                point.isAbnormal(),
                point.severity()
        );
    }

    private static CycleTimePoint toCycleTimePoint(ChartPoint point) {
        return new CycleTimePoint(
                point.eventId(),
                point.analysisId(),
                point.timestamp(),
                point.targetCycleTimeSec(),
                point.actualCycleTimeSec(),
                point.countIncreaseYn(),
                point.isAbnormal(),
                point.severity()
        );
    }

    private static DelayPoint toDelayPoint(ChartPoint point) {
        return new DelayPoint(
                point.eventId(),
                point.analysisId(),
                point.timestamp(),
                point.cycleTimeGapSec(),
                point.timestampDelaySec(),
                point.countIncreaseYn(),
                point.isAbnormal(),
                point.severity()
        );
    }
}

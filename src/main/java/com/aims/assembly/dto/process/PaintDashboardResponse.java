package com.aims.assembly.dto.process;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PaintDashboardResponse(
        LocalDate selectedDate,
        LocalDateTime from,
        LocalDateTime to,
        LocalDateTime chartStartAt,
        LocalDateTime chartEndAt,
        Summary summary,
        Thresholds thresholds,
        Charts charts,
        Alert alert
) {
    public record Summary(
            long analysisCount,
            double averageThicknessValue,
            double averageSurfaceQualityScore,
            double defectRate,
            long alertCount,
            double averageThermalStdTemp
    ) {
        public Summary(
                long analysisCount,
                double defectRate,
                double averageSurfaceQualityScore,
                long alertCount
        ) {
            this(analysisCount, 0.0, averageSurfaceQualityScore, defectRate, alertCount, 0.0);
        }
    }

    public record Thresholds(
            HigherIsBetterThreshold surfaceQualityScore,
            InRangeThreshold thicknessValue,
            LowerIsBetterThreshold defectScore,
            LowerIsBetterThreshold thermalStdTemp
    ) {
    }

    public record HigherIsBetterThreshold(
            String label,
            String unit,
            String direction,
            double warningBelow,
            double dangerBelow
    ) {
    }

    public record InRangeThreshold(
            String label,
            String unit,
            String direction,
            double target,
            double normalMin,
            double normalMax,
            double warningMin,
            double warningMax
    ) {
    }

    public record LowerIsBetterThreshold(
            String label,
            String unit,
            String direction,
            double warningAbove,
            double dangerAbove
    ) {
    }

    public record Charts(
            MetricChart surfaceQuality,
            MetricChart thickness,
            MetricChart defectScore,
            MetricChart thermalStdTemp
    ) {
    }

    public record MetricChart(
            String title,
            String metricKey,
            String unit,
            List<MetricPoint> points,
            List<MetricMarker> markers
    ) {
    }

    public record MetricPoint(
            LocalDateTime time,
            Double value,
            String status,
            String visionLabel,
            String imagePosition,
            Double riskScore,
            Long analysisResultId
    ) {
    }

    public record MetricMarker(
            LocalDateTime time,
            Double value,
            String label,
            String imagePosition,
            String status,
            Long analysisResultId
    ) {
    }

    public record Alert(
            String title,
            List<String> messages,
            Detail detail
    ) {
        public Alert(String title, List<String> messages) {
            this(title, messages, null);
        }

        public record Detail(
                LocalDateTime time,
                String visionLabel,
                String imagePosition,
                Double thicknessValue,
                Double surfaceQualityScore,
                Double defectScore,
                Double thermalStdTemp,
                Double riskScore,
                String status
        ) {
        }
    }

    public static PaintDashboardResponse empty() {
        return empty(null);
    }

    public static PaintDashboardResponse empty(LocalDate selectedDate) {
        return empty(selectedDate, null, null);
    }

    public static PaintDashboardResponse empty(LocalDate selectedDate, LocalDateTime from, LocalDateTime to) {
        return new PaintDashboardResponse(
                selectedDate,
                from,
                to,
                null,
                null,
                new Summary(0, 0.0, 0.0, 0.0, 0, 0.0),
                defaultThresholds(),
                new Charts(
                        emptyChart("표면 품질 점수 추이", "surfaceQualityScore", "점"),
                        emptyChart("도막 두께 추이", "thicknessValue", "μm"),
                        emptyChart("불량 점수 추이", "defectScore", ""),
                        emptyChart("온도 편차 추이", "thermalStdTemp", "℃")
                ),
                new Alert("최근 도장 상태 정상", List.of("선택한 시간 범위 내 신규 위험 알람 없음"))
        );
    }

    private static MetricChart emptyChart(String title, String metricKey, String unit) {
        return new MetricChart(title, metricKey, unit, List.of(), List.of());
    }

    private static Thresholds defaultThresholds() {
        return new Thresholds(
                new HigherIsBetterThreshold("표면 품질 점수", "점", "HIGHER_IS_BETTER", 80.0, 60.0),
                new InRangeThreshold("도막 두께", "μm", "IN_RANGE_IS_BETTER", 115.0, 90.0, 120.0, 80.0, 130.0),
                new LowerIsBetterThreshold("불량 점수", "", "LOWER_IS_BETTER", 0.4, 0.6),
                new LowerIsBetterThreshold("온도 편차", "℃", "LOWER_IS_BETTER", 2.0, 5.0)
        );
    }
}

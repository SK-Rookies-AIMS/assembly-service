package com.aims.assembly.dto.process;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PaintDashboardResponse(
        LocalDate selectedDate,
        Summary summary,
        List<ChartPoint> chart,
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

    public record ChartPoint(
            LocalDateTime time,
            Double defectScore,
            Double surfaceQualityScore,
            Double thicknessValue,
            Double riskScore,
            String imagePosition,
            String visionLabel,
            Double thermalStdTemp,
            String severity
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
                String visionLabel,
                String imagePosition,
                Double thicknessValue,
                Double surfaceQualityScore,
                Double thermalStdTemp,
                Double riskScore,
                String severity
        ) {
        }
    }

    public static PaintDashboardResponse empty() {
        return empty(null);
    }

    public static PaintDashboardResponse empty(LocalDate selectedDate) {
        return new PaintDashboardResponse(
                selectedDate,
                new Summary(0, 0.0, 0.0, 0.0, 0, 0.0),
                List.of(),
                null
        );
    }
}

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
            double defectRate,
            double averageSurfaceQualityScore,
            long alertCount
    ) {
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
            List<String> messages
    ) {
    }

    public static PaintDashboardResponse empty() {
        return empty(null);
    }

    public static PaintDashboardResponse empty(LocalDate selectedDate) {
        return new PaintDashboardResponse(
                selectedDate,
                new Summary(0, 0.0, 0.0, 0),
                List.of(),
                null
        );
    }
}

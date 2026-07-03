package com.aims.assembly.dto.process;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AssemblyDashboardResponse(
        LocalDate selectedDate,
        Summary summary,
        List<VehicleRow> vehicles,
        Alert alert
) {
    public record Summary(
            long vehicleCount,
            long sequenceErrorCount,
            long missingPartCount,
            long fasteningErrorCount,
            double averageRiskScore
    ) {
    }

    public record VehicleRow(
            Long carMasterId,
            String carDisplayId,
            String expectedSequence,
            String actualSequence,
            Integer sequenceErrorCount,
            Integer missingPartCount,
            Integer fasteningErrorCount,
            Double riskScore,
            String severity,
            String status,
            LocalDateTime time
    ) {
    }

    public record Alert(
            String title,
            List<String> messages
    ) {
    }

    public static AssemblyDashboardResponse empty() {
        return empty(null);
    }

    public static AssemblyDashboardResponse empty(LocalDate selectedDate) {
        return new AssemblyDashboardResponse(
                selectedDate,
                new Summary(0, 0, 0, 0, 0.0),
                List.of(),
                null
        );
    }
}

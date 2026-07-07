package com.aims.assembly.dto.process;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AssemblyDashboardResponse(
        LocalDate selectedDate,
        LocalDateTime from,
        LocalDateTime to,
        LocalDateTime dataStartAt,
        LocalDateTime dataEndAt,
        Summary summary,
        List<VehicleRow> vehicles,
        Alert alert
) {
    public AssemblyDashboardResponse(
            LocalDate selectedDate,
            Summary summary,
            List<VehicleRow> vehicles,
            Alert alert
    ) {
        this(selectedDate, null, null, null, null, summary, vehicles, alert);
    }

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
        return empty(selectedDate, null, null);
    }

    public static AssemblyDashboardResponse empty(LocalDate selectedDate, LocalDateTime from, LocalDateTime to) {
        return new AssemblyDashboardResponse(
                selectedDate,
                from,
                to,
                null,
                null,
                new Summary(0, 0, 0, 0, 0.0),
                List.of(),
                null
        );
    }
}

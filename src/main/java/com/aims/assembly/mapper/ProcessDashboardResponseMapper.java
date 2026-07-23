package com.aims.assembly.mapper;

import com.aims.assembly.dto.process.AssemblyDashboardResponse;
import com.aims.assembly.dto.process.EquipmentOperationRateResponse;
import com.aims.assembly.dto.process.PaintDashboardResponse;
import com.aims.assembly.dto.process.ProcessAvailableDatesResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ProcessDashboardResponseMapper {

    private ProcessDashboardResponseMapper() {
    }

    public static EquipmentOperationRateResponse toEquipmentOperationRateResponse(
            List<EquipmentOperationRateResponse.Item> items
    ) {
        return new EquipmentOperationRateResponse(items);
    }

    public static EquipmentOperationRateResponse.Item toEquipmentOperationRateItem(
            String processCode,
            String processName,
            long runningCount,
            long warningCount,
            long operatingCount,
            long stoppedCount,
            long faultCount,
            long totalCount,
            double operationRate,
            Map<String, Long> statusCounts
    ) {
        return new EquipmentOperationRateResponse.Item(
                processCode,
                processName,
                runningCount,
                warningCount,
                operatingCount,
                stoppedCount,
                faultCount,
                totalCount,
                operationRate,
                statusCounts
        );
    }

    public static PaintDashboardResponse toPaintDashboardResponse(
            LocalDate selectedDate,
            LocalDateTime from,
            LocalDateTime to,
            LocalDateTime chartStartAt,
            LocalDateTime chartEndAt,
            PaintDashboardResponse.Summary summary,
            PaintDashboardResponse.Thresholds thresholds,
            PaintDashboardResponse.Charts charts,
            PaintDashboardResponse.Alert alert
    ) {
        return new PaintDashboardResponse(
                selectedDate,
                from,
                to,
                chartStartAt,
                chartEndAt,
                summary,
                thresholds,
                charts,
                alert
        );
    }

    public static PaintDashboardResponse.Summary toPaintSummary(
            long analysisCount,
            double defectRate,
            double averageSurfaceQualityScore,
            long alertCount
    ) {
        return new PaintDashboardResponse.Summary(analysisCount, defectRate, averageSurfaceQualityScore, alertCount);
    }

    public static PaintDashboardResponse.Summary toPaintSummary(
            long analysisCount,
            double averageThicknessValue,
            double averageSurfaceQualityScore,
            double defectRate,
            long alertCount,
            double averageThermalStdTemp
    ) {
        return new PaintDashboardResponse.Summary(
                analysisCount,
                averageThicknessValue,
                averageSurfaceQualityScore,
                defectRate,
                alertCount,
                averageThermalStdTemp
        );
    }

    public static PaintDashboardResponse.Alert toPaintAlert(String title, List<String> messages) {
        return new PaintDashboardResponse.Alert(title, messages);
    }

    public static PaintDashboardResponse.Alert toPaintAlert(
            String title,
            List<String> messages,
            PaintDashboardResponse.Alert.Detail detail
    ) {
        return new PaintDashboardResponse.Alert(title, messages, detail);
    }

    public static AssemblyDashboardResponse toAssemblyDashboardResponse(
            LocalDate selectedDate,
            LocalDateTime from,
            LocalDateTime to,
            LocalDateTime dataStartAt,
            LocalDateTime dataEndAt,
            AssemblyDashboardResponse.Summary summary,
            List<AssemblyDashboardResponse.VehicleRow> vehicles,
            AssemblyDashboardResponse.Alert alert
    ) {
        return new AssemblyDashboardResponse(selectedDate, from, to, dataStartAt, dataEndAt, summary, vehicles, alert);
    }

    public static AssemblyDashboardResponse.Summary toAssemblySummary(
            long vehicleCount,
            long sequenceErrorCount,
            long missingPartCount,
            long fasteningErrorCount,
            double averageRiskScore
    ) {
        return new AssemblyDashboardResponse.Summary(
                vehicleCount,
                sequenceErrorCount,
                missingPartCount,
                fasteningErrorCount,
                averageRiskScore
        );
    }

    public static AssemblyDashboardResponse.VehicleRow toAssemblyVehicleRow(
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
        return new AssemblyDashboardResponse.VehicleRow(
                carMasterId,
                carDisplayId,
                expectedSequence,
                actualSequence,
                sequenceErrorCount,
                missingPartCount,
                fasteningErrorCount,
                riskScore,
                severity,
                status,
                time
        );
    }

    public static AssemblyDashboardResponse.Alert toAssemblyAlert(String title, List<String> messages) {
        return new AssemblyDashboardResponse.Alert(title, messages);
    }

    public static ProcessAvailableDatesResponse toProcessAvailableDatesResponse(List<LocalDate> dates) {
        return ProcessAvailableDatesResponse.of(dates);
    }
}

package com.aims.assembly.controller.process;

import com.aims.assembly.domain.enums.EquipmentOperationStatus;
import com.aims.assembly.dto.process.AssemblyDashboardResponse;
import com.aims.assembly.dto.process.EquipmentOperationRateResponse;
import com.aims.assembly.dto.process.PaintDashboardResponse;
import com.aims.assembly.dto.process.ProcessAvailableDatesResponse;
import com.aims.assembly.service.process.ProcessDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProcessDashboardControllerTest {
    private final ProcessDashboardService service = mock(ProcessDashboardService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProcessDashboardController(service))
                .build();
    }

    @Test
    void paintEndpointReturnsSummaryChartAndAlertFields() throws Exception {
        when(service.getPaintDashboard(
                eq(LocalDate.of(2026, 6, 18)),
                eq(LocalDateTime.of(2026, 6, 18, 13, 55)),
                eq(LocalDateTime.of(2026, 6, 18, 14, 25)),
                eq(30)
        )).thenReturn(new PaintDashboardResponse(
                LocalDate.of(2026, 6, 18),
                new PaintDashboardResponse.Summary(6, 116.9, 81.2, 50.0, 3, 2.5),
                List.of(new PaintDashboardResponse.ChartPoint(
                        LocalDateTime.of(2026, 6, 18, 13, 55),
                        0.87,
                        72.3,
                        116.5,
                        88.5,
                        "LEFT",
                        "DEFECT",
                        4.2,
                        "CRITICAL"
                )),
                new PaintDashboardResponse.Alert(
                        "도장 품질 이상 감지",
                        List.of(
                                "비전 판정: DEFECT",
                                "이상 위치: LEFT",
                                "도막 두께: 116.5 μm",
                                "표면 품질 점수: 72.3점",
                                "열 편차: 4.2℃",
                                "위험도: 88.5"
                        ),
                        new PaintDashboardResponse.Alert.Detail(
                                "DEFECT",
                                "LEFT",
                                116.5,
                                72.3,
                                4.2,
                                88.5,
                                "CRITICAL"
                        )
                )
        ));

        mockMvc.perform(get("/api/process/paint")
                        .param("date", "2026-06-18")
                        .param("from", "2026-06-18T13:55:00")
                        .param("to", "2026-06-18T14:25:00")
                        .param("limit", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selectedDate").value("2026-06-18"))
                .andExpect(jsonPath("$.data.summary.analysisCount").value(6))
                .andExpect(jsonPath("$.data.summary.averageThicknessValue").value(116.9))
                .andExpect(jsonPath("$.data.summary.averageSurfaceQualityScore").value(81.2))
                .andExpect(jsonPath("$.data.summary.defectRate").value(50.0))
                .andExpect(jsonPath("$.data.summary.alertCount").value(3))
                .andExpect(jsonPath("$.data.summary.averageThermalStdTemp").value(2.5))
                .andExpect(jsonPath("$.data.chart[0].defectScore").value(0.87))
                .andExpect(jsonPath("$.data.chart[0].surfaceQualityScore").value(72.3))
                .andExpect(jsonPath("$.data.chart[0].imagePosition").value("LEFT"))
                .andExpect(jsonPath("$.data.chart[0].thicknessValue").value(116.5))
                .andExpect(jsonPath("$.data.chart[0].riskScore").value(88.5))
                .andExpect(jsonPath("$.data.alert.title").value("도장 품질 이상 감지"))
                .andExpect(jsonPath("$.data.alert.messages[0]").value("비전 판정: DEFECT"))
                .andExpect(jsonPath("$.data.alert.detail.visionLabel").value("DEFECT"))
                .andExpect(jsonPath("$.data.alert.detail.imagePosition").value("LEFT"))
                .andExpect(jsonPath("$.data.alert.detail.thicknessValue").value(116.5))
                .andExpect(jsonPath("$.data.alert.detail.surfaceQualityScore").value(72.3))
                .andExpect(jsonPath("$.data.alert.detail.thermalStdTemp").value(4.2))
                .andExpect(jsonPath("$.data.alert.detail.riskScore").value(88.5))
                .andExpect(jsonPath("$.data.alert.detail.severity").value("CRITICAL"));
    }

    @Test
    void assemblyEndpointReturnsSummaryVehiclesAndAlertFields() throws Exception {
        when(service.getAssemblyDashboard(
                eq(null),
                eq(null),
                eq(null),
                eq(30)
        )).thenReturn(new AssemblyDashboardResponse(
                LocalDate.of(2026, 6, 1),
                new AssemblyDashboardResponse.Summary(5, 1, 1, 2, 48.6),
                List.of(new AssemblyDashboardResponse.VehicleRow(
                        1L,
                        "CAR-000001",
                        "A01 > A02 > A03 > A04",
                        "A01 > A03 > A02 > A04",
                        1,
                        0,
                        1,
                        86.5,
                        "CRITICAL",
                        "위험",
                        LocalDateTime.of(2026, 6, 18, 13, 55)
                )),
                new AssemblyDashboardResponse.Alert(
                        "조립 순서 오류 감지",
                        List.of("조립 순서 오류와 체결 오류 동시 감지")
                )
        ));

        mockMvc.perform(get("/api/process/assembly")
                        .param("limit", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selectedDate").value("2026-06-01"))
                .andExpect(jsonPath("$.data.summary.vehicleCount").value(5))
                .andExpect(jsonPath("$.data.vehicles[0].expectedSequence")
                        .value("A01 > A02 > A03 > A04"))
                .andExpect(jsonPath("$.data.vehicles[0].actualSequence")
                        .value("A01 > A03 > A02 > A04"))
                .andExpect(jsonPath("$.data.vehicles[0].sequenceErrorCount").value(1))
                .andExpect(jsonPath("$.data.vehicles[0].missingPartCount").value(0))
                .andExpect(jsonPath("$.data.vehicles[0].fasteningErrorCount").value(1))
                .andExpect(jsonPath("$.data.alert.title").value("조립 순서 오류 감지"));
    }

    @Test
    void dateEndpointsReturnDatesAndLatestDate() throws Exception {
        when(service.getPaintDates()).thenReturn(new ProcessAvailableDatesResponse(
                List.of(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 18)),
                LocalDate.of(2026, 6, 18)
        ));
        when(service.getAssemblyDates()).thenReturn(new ProcessAvailableDatesResponse(
                List.of(LocalDate.of(2026, 6, 1)),
                LocalDate.of(2026, 6, 1)
        ));

        mockMvc.perform(get("/api/process/paint/dates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dates[0]").value("2026-06-01"))
                .andExpect(jsonPath("$.data.dates[1]").value("2026-06-18"))
                .andExpect(jsonPath("$.data.latestDate").value("2026-06-18"));

        mockMvc.perform(get("/api/process/assembly/dates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dates[0]").value("2026-06-01"))
                .andExpect(jsonPath("$.data.latestDate").value("2026-06-01"));
    }

    @Test
    void endpointsReturnEmptyResponses() throws Exception {
        when(service.getPaintDashboard(eq(null), eq(null), eq(null), eq(null)))
                .thenReturn(PaintDashboardResponse.empty());
        when(service.getAssemblyDashboard(eq(null), eq(null), eq(null), eq(null)))
                .thenReturn(AssemblyDashboardResponse.empty());
        when(service.getPaintDates()).thenReturn(ProcessAvailableDatesResponse.of(List.of()));
        when(service.getAssemblyDates()).thenReturn(ProcessAvailableDatesResponse.of(List.of()));

        mockMvc.perform(get("/api/process/paint"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selectedDate").doesNotExist())
                .andExpect(jsonPath("$.data.summary.analysisCount").value(0))
                .andExpect(jsonPath("$.data.summary.averageThicknessValue").value(0.0))
                .andExpect(jsonPath("$.data.summary.averageThermalStdTemp").value(0.0))
                .andExpect(jsonPath("$.data.chart").isEmpty())
                .andExpect(jsonPath("$.data.alert").doesNotExist());

        mockMvc.perform(get("/api/process/assembly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selectedDate").doesNotExist())
                .andExpect(jsonPath("$.data.summary.vehicleCount").value(0))
                .andExpect(jsonPath("$.data.vehicles").isEmpty())
                .andExpect(jsonPath("$.data.alert").doesNotExist());

        mockMvc.perform(get("/api/process/paint/dates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dates").isEmpty())
                .andExpect(jsonPath("$.data.latestDate").doesNotExist());

        mockMvc.perform(get("/api/process/assembly/dates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dates").isEmpty())
                .andExpect(jsonPath("$.data.latestDate").doesNotExist());
    }

    @Test
    void equipmentOperationRateEndpointReturnsFourProcessItemsInFixedOrder() throws Exception {
        when(service.getEquipmentOperationRate()).thenReturn(new EquipmentOperationRateResponse(List.of(
                item("PRESS", "프레스", 3, 1, 1, 0),
                item("BODY", "차체", 2, 1, 0, 1),
                item("PAINT", "도장", 0, 0, 0, 0),
                item("ASSEMBLY", "의장", 2, 2, 0, 1)
        )));

        mockMvc.perform(get("/api/process/equipment/operation-rate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.items[0].processCode").value("PRESS"))
                .andExpect(jsonPath("$.data.items[1].processCode").value("BODY"))
                .andExpect(jsonPath("$.data.items[2].processCode").value("PAINT"))
                .andExpect(jsonPath("$.data.items[3].processCode").value("ASSEMBLY"))
                .andExpect(jsonPath("$.data.items[0].runningCount").value(3))
                .andExpect(jsonPath("$.data.items[0].warningCount").value(1))
                .andExpect(jsonPath("$.data.items[0].operatingCount").value(4))
                .andExpect(jsonPath("$.data.items[0].stoppedCount").value(1))
                .andExpect(jsonPath("$.data.items[0].faultCount").value(0))
                .andExpect(jsonPath("$.data.items[0].totalCount").value(5))
                .andExpect(jsonPath("$.data.items[0].operationRate").value(80.0))
                .andExpect(jsonPath("$.data.items[0].statusCounts.RUNNING").value(3))
                .andExpect(jsonPath("$.data.items[0].statusCounts.WARNING").value(1))
                .andExpect(jsonPath("$.data.items[0].statusCounts.STOPPED").value(1))
                .andExpect(jsonPath("$.data.items[0].statusCounts.FAULT").value(0))
                .andExpect(jsonPath("$.data.items[2].totalCount").value(0))
                .andExpect(jsonPath("$.data.items[2].operationRate").value(0.0));
    }

    private EquipmentOperationRateResponse.Item item(
            String processCode,
            String processName,
            long runningCount,
            long warningCount,
            long stoppedCount,
            long faultCount
    ) {
        Map<EquipmentOperationStatus, Long> statusCounts =
                new EnumMap<>(EquipmentOperationStatus.class);
        statusCounts.put(EquipmentOperationStatus.RUNNING, runningCount);
        statusCounts.put(EquipmentOperationStatus.WARNING, warningCount);
        statusCounts.put(EquipmentOperationStatus.STOPPED, stoppedCount);
        statusCounts.put(EquipmentOperationStatus.FAULT, faultCount);
        long operatingCount = runningCount + warningCount;
        long totalCount = operatingCount + stoppedCount + faultCount;
        double operationRate = totalCount == 0
                ? 0.0
                : Math.round(operatingCount * 1000.0 / totalCount) / 10.0;
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
}

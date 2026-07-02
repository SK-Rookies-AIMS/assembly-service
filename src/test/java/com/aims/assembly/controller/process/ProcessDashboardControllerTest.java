package com.aims.assembly.controller.process;

import com.aims.assembly.dto.process.AssemblyDashboardResponse;
import com.aims.assembly.dto.process.PaintDashboardResponse;
import com.aims.assembly.dto.process.ProcessAvailableDatesResponse;
import com.aims.assembly.service.process.ProcessDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
                new PaintDashboardResponse.Summary(6, 50.0, 81.2, 3),
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
                        List.of("비전 불량 라벨 감지: DEFECT")
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
                .andExpect(jsonPath("$.data.chart[0].defectScore").value(0.87))
                .andExpect(jsonPath("$.data.chart[0].surfaceQualityScore").value(72.3))
                .andExpect(jsonPath("$.data.chart[0].imagePosition").value("LEFT"))
                .andExpect(jsonPath("$.data.chart[0].thicknessValue").value(116.5))
                .andExpect(jsonPath("$.data.alert.title").value("도장 품질 이상 감지"));
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
}

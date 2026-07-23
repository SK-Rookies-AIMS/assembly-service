package com.aims.assembly.controller.process;

import com.aims.assembly.domain.enums.EquipmentOperationStatus;
import com.aims.assembly.dto.process.AssemblyDashboardResponse;
import com.aims.assembly.dto.process.EquipmentOperationRateResponse;
import com.aims.assembly.dto.process.PaintDashboardResponse;
import com.aims.assembly.dto.process.ProcessAvailableDatesResponse;
import com.aims.assembly.service.process.ProcessDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
                LocalDateTime.of(2026, 6, 18, 13, 55),
                LocalDateTime.of(2026, 6, 18, 14, 25),
                LocalDateTime.of(2026, 6, 18, 13, 55),
                LocalDateTime.of(2026, 6, 18, 13, 55),
                new PaintDashboardResponse.Summary(6, 116.9, 81.2, 50.0, 3, 2.5),
                new PaintDashboardResponse.Thresholds(
                        new PaintDashboardResponse.HigherIsBetterThreshold(
                                "표면 품질 점수", "점", "HIGHER_IS_BETTER", 80.0, 60.0),
                        new PaintDashboardResponse.InRangeThreshold(
                                "도막 두께", "μm", "IN_RANGE_IS_BETTER", 115.0, 90.0, 120.0, 80.0, 130.0),
                        new PaintDashboardResponse.LowerIsBetterThreshold(
                                "불량 점수", "", "LOWER_IS_BETTER", 0.4, 0.6),
                        new PaintDashboardResponse.LowerIsBetterThreshold(
                                "온도 편차", "℃", "LOWER_IS_BETTER", 2.0, 5.0)
                ),
                new PaintDashboardResponse.Charts(
                        new PaintDashboardResponse.MetricChart(
                                "표면 품질 점수 추이",
                                "surfaceQualityScore",
                                "점",
                                List.of(new PaintDashboardResponse.MetricPoint(
                                        LocalDateTime.of(2026, 6, 18, 13, 55),
                                        72.3,
                                        "WARNING",
                                        "DEFECT",
                                        "LEFT",
                                        88.5,
                                        123L
                                )),
                                List.of()
                        ),
                        new PaintDashboardResponse.MetricChart(
                                "도막 두께 추이",
                                "thicknessValue",
                                "μm",
                                List.of(new PaintDashboardResponse.MetricPoint(
                                        LocalDateTime.of(2026, 6, 18, 13, 55),
                                        116.5,
                                        "NORMAL",
                                        "DEFECT",
                                        "LEFT",
                                        88.5,
                                        123L
                                )),
                                List.of()
                        ),
                        new PaintDashboardResponse.MetricChart(
                                "불량 점수 추이",
                                "defectScore",
                                "",
                                List.of(new PaintDashboardResponse.MetricPoint(
                                        LocalDateTime.of(2026, 6, 18, 13, 55),
                                        0.87,
                                        "DANGER",
                                        "DEFECT",
                                        "LEFT",
                                        88.5,
                                        123L
                                )),
                                List.of(new PaintDashboardResponse.MetricMarker(
                                        LocalDateTime.of(2026, 6, 18, 13, 55),
                                        0.87,
                                        "DEFECT",
                                        "LEFT",
                                        "DANGER",
                                        123L
                                ))
                        ),
                        new PaintDashboardResponse.MetricChart(
                                "온도 편차 추이",
                                "thermalStdTemp",
                                "℃",
                                List.of(new PaintDashboardResponse.MetricPoint(
                                        LocalDateTime.of(2026, 6, 18, 13, 55),
                                        4.2,
                                        "DANGER",
                                        "DEFECT",
                                        "LEFT",
                                        88.5,
                                        123L
                                )),
                                List.of()
                        )
                ),
                new PaintDashboardResponse.Alert(
                        "도장 품질 이상 감지",
                        List.of(
                                "비전 판정: DEFECT",
                                "상태: DANGER"
                        ),
                        new PaintDashboardResponse.Alert.Detail(
                                LocalDateTime.of(2026, 6, 18, 13, 55),
                                "DEFECT",
                                "LEFT",
                                116.5,
                                72.3,
                                0.87,
                                4.2,
                                88.5,
                                "DANGER"
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
                .andExpect(jsonPath("$.data.thresholds.surfaceQualityScore.warningBelow").value(80.0))
                .andExpect(jsonPath("$.data.charts.surfaceQuality.points[0].value").value(72.3))
                .andExpect(jsonPath("$.data.charts.surfaceQuality.points[0].status").value("WARNING"))
                .andExpect(jsonPath("$.data.charts.thickness.points[0].value").value(116.5))
                .andExpect(jsonPath("$.data.charts.defectScore.points[0].value").value(0.87))
                .andExpect(jsonPath("$.data.charts.defectScore.markers[0].label").value("DEFECT"))
                .andExpect(jsonPath("$.data.charts.thermalStdTemp.points[0].value").value(4.2))
                .andExpect(jsonPath("$.data.alert.title").value("도장 품질 이상 감지"))
                .andExpect(jsonPath("$.data.alert.messages[0]").value("비전 판정: DEFECT"))
                .andExpect(jsonPath("$.data.alert.detail.time").value("2026-06-18T13:55:00"))
                .andExpect(jsonPath("$.data.alert.detail.visionLabel").value("DEFECT"))
                .andExpect(jsonPath("$.data.alert.detail.imagePosition").value("LEFT"))
                .andExpect(jsonPath("$.data.alert.detail.thicknessValue").value(116.5))
                .andExpect(jsonPath("$.data.alert.detail.surfaceQualityScore").value(72.3))
                .andExpect(jsonPath("$.data.alert.detail.defectScore").value(0.87))
                .andExpect(jsonPath("$.data.alert.detail.thermalStdTemp").value(4.2))
                .andExpect(jsonPath("$.data.alert.detail.riskScore").value(88.5))
                .andExpect(jsonPath("$.data.alert.detail.status").value("DANGER"));
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
                .andExpect(jsonPath("$.data.charts.surfaceQuality.points").isEmpty())
                .andExpect(jsonPath("$.data.charts.defectScore.markers").isEmpty())
                .andExpect(jsonPath("$.data.alert.title").value("최근 도장 상태 정상"));

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

    @Test
    void equipmentOperationRateEndpointReturnsOkWithRedisRestoredResponse() throws Exception {
        EquipmentOperationRateResponse cacheMissResponse = new EquipmentOperationRateResponse(List.of(
                item("PRESS", "프레스", 1, 1, 2, 1),
                item("BODY", "차체", 5, 0, 0, 0),
                item("PAINT", "도장", 5, 0, 0, 0),
                item("ASSEMBLY", "의장", 5, 0, 0, 0)
        ));
        GenericJacksonJsonRedisSerializer serializer = redisJsonSerializer();
        EquipmentOperationRateResponse cacheHitResponse =
                (EquipmentOperationRateResponse) serializer.deserialize(serializer.serialize(cacheMissResponse));
        when(service.getEquipmentOperationRate()).thenReturn(cacheHitResponse);

        mockMvc.perform(get("/api/process/equipment/operation-rate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].processCode").value("PRESS"))
                .andExpect(jsonPath("$.data.items[0].operationRate").value(40.0))
                .andExpect(jsonPath("$.data.items[0].statusCounts.RUNNING").value(1))
                .andExpect(jsonPath("$.data.items[0].statusCounts.WARNING").value(1))
                .andExpect(jsonPath("$.data.items[0].statusCounts.STOPPED").value(2))
                .andExpect(jsonPath("$.data.items[0].statusCounts.FAULT").value(1));
    }

    @Test
    void equipmentOperationRateEndpointReturnsOkForRepeatedRedisRestoredResponses() throws Exception {
        EquipmentOperationRateResponse cacheMissResponse = new EquipmentOperationRateResponse(List.of(
                item("PRESS", "프레스", 1, 1, 2, 1),
                item("BODY", "차체", 5, 0, 0, 0),
                item("PAINT", "도장", 5, 0, 0, 0),
                item("ASSEMBLY", "의장", 5, 0, 0, 0)
        ));
        GenericJacksonJsonRedisSerializer serializer = redisJsonSerializer();
        EquipmentOperationRateResponse cacheHitResponse =
                (EquipmentOperationRateResponse) serializer.deserialize(serializer.serialize(cacheMissResponse));
        when(service.getEquipmentOperationRate()).thenReturn(cacheHitResponse);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/process/equipment/operation-rate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].operationRate").value(40.0))
                    .andExpect(jsonPath("$.data.items[0].statusCounts.STOPPED").value(2));
        }
    }

    private EquipmentOperationRateResponse.Item item(
            String processCode,
            String processName,
            long runningCount,
            long warningCount,
            long stoppedCount,
            long faultCount
    ) {
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        statusCounts.put(EquipmentOperationStatus.RUNNING.name(), runningCount);
        statusCounts.put(EquipmentOperationStatus.WARNING.name(), warningCount);
        statusCounts.put(EquipmentOperationStatus.STOPPED.name(), stoppedCount);
        statusCounts.put(EquipmentOperationStatus.FAULT.name(), faultCount);
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

    private GenericJacksonJsonRedisSerializer redisJsonSerializer() {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.aims.assembly.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.util.")
                .build();

        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(typeValidator)
                .build();
    }
}

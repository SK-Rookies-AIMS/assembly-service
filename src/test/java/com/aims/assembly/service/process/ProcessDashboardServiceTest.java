package com.aims.assembly.service.process;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import com.aims.assembly.domain.assembly.AssemblyAnalysisResult;
import com.aims.assembly.domain.commons.BaseEntity;
import com.aims.assembly.domain.enums.EquipmentOperationStatus;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.domain.enums.Severity;
import com.aims.assembly.domain.paint.PaintAnalysisResult;
import com.aims.assembly.dto.process.AssemblyDashboardResponse;
import com.aims.assembly.dto.process.EquipmentOperationRateResponse;
import com.aims.assembly.dto.process.PaintDashboardResponse;
import com.aims.assembly.repository.analysis.AssemblyAnalysisResultRepository;
import com.aims.assembly.repository.analysis.PaintAnalysisResultRepository;
import com.aims.assembly.repository.equipment.EquipmentOperationRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessDashboardServiceTest {
    private final PaintAnalysisResultRepository paintRepository =
            mock(PaintAnalysisResultRepository.class);
    private final AssemblyAnalysisResultRepository assemblyRepository =
            mock(AssemblyAnalysisResultRepository.class);
    private final EquipmentOperationRateRepository equipmentOperationRateRepository =
            mock(EquipmentOperationRateRepository.class);
    private ProcessDashboardService service;

    @BeforeEach
    void setUp() {
        service = new ProcessDashboardService(
                paintRepository,
                assemblyRepository,
                equipmentOperationRateRepository
        );
    }

    @Test
    void paintAndAssemblyDetailsDoNotUseJpaAuditBaseEntity() {
        assertThat(BaseEntity.class.isAssignableFrom(PaintAnalysisResult.class)).isFalse();
        assertThat(BaseEntity.class.isAssignableFrom(AssemblyAnalysisResult.class)).isFalse();
    }

    @Test
    void paintDashboardBuildsSummaryChartAndAlert() {
        LocalDateTime eventTime = LocalDateTime.of(2026, 6, 18, 13, 55);
        LocalDateTime analyzedAt = LocalDateTime.of(2026, 6, 19, 13, 56);
        PaintAnalysisResult row = PaintAnalysisResult.builder()
                .analysisResult(result(ProcessCode.PAINT, 1L, Severity.CRITICAL, true, 88.5, eventTime, analyzedAt))
                .defectScore(0.87)
                .thermalStdTemp(4.2)
                .surfaceQualityScore(72.3)
                .visionLabel("DEFECT")
                .imagePosition("LEFT")
                .thicknessValue(126.5)
                .build();
        when(paintRepository.findDashboardRows(
                eq(LocalDateTime.of(2026, 6, 18, 0, 0)),
                eq(LocalDateTime.of(2026, 6, 19, 0, 0)),
                any(Pageable.class)
        )).thenReturn(List.of(row));
        when(paintRepository.findDashboardSummaryRows(
                eq(LocalDateTime.of(2026, 6, 18, 0, 0)),
                eq(LocalDateTime.of(2026, 6, 19, 0, 0))
        )).thenReturn(List.of(row));

        PaintDashboardResponse response =
                service.getPaintDashboard(LocalDate.of(2026, 6, 18), null, null, 30);

        assertThat(response.selectedDate()).isEqualTo(LocalDate.of(2026, 6, 18));
        assertThat(response.from()).isEqualTo(LocalDateTime.of(2026, 6, 18, 0, 0));
        assertThat(response.to()).isEqualTo(LocalDateTime.of(2026, 6, 18, 23, 59, 59, 999999999));
        assertThat(response.chartStartAt()).isEqualTo(eventTime);
        assertThat(response.chartEndAt()).isEqualTo(eventTime);
        assertThat(response.summary().analysisCount()).isEqualTo(1);
        assertThat(response.summary().averageThicknessValue()).isEqualTo(126.5);
        assertThat(response.summary().averageThermalStdTemp()).isEqualTo(4.2);
        assertThat(response.summary().defectRate()).isEqualTo(100.0);
        assertThat(response.summary().averageSurfaceQualityScore()).isEqualTo(72.3);
        assertThat(response.summary().alertCount()).isEqualTo(1);
        assertThat(response.thresholds().surfaceQualityScore().warningBelow()).isEqualTo(80.0);
        assertThat(response.thresholds().surfaceQualityScore().dangerBelow()).isEqualTo(60.0);
        assertThat(response.thresholds().thicknessValue().target()).isEqualTo(115.0);
        assertThat(response.thresholds().thicknessValue().normalMin()).isEqualTo(90.0);
        assertThat(response.thresholds().thicknessValue().normalMax()).isEqualTo(120.0);
        assertThat(response.thresholds().thicknessValue().warningMin()).isEqualTo(80.0);
        assertThat(response.thresholds().thicknessValue().warningMax()).isEqualTo(130.0);
        assertThat(response.thresholds().defectScore().warningAbove()).isEqualTo(0.4);
        assertThat(response.thresholds().defectScore().dangerAbove()).isEqualTo(0.6);
        assertThat(response.thresholds().thermalStdTemp().warningAbove()).isEqualTo(2.0);
        assertThat(response.thresholds().thermalStdTemp().dangerAbove()).isEqualTo(5.0);
        assertThat(response.charts().surfaceQuality().points()).singleElement().satisfies(point -> {
            assertThat(point.time()).isEqualTo(eventTime);
            assertThat(point.value()).isEqualTo(72.3);
            assertThat(point.status()).isEqualTo("WARNING");
            assertThat(point.riskScore()).isEqualTo(88.5);
            assertThat(point.imagePosition()).isEqualTo("LEFT");
        });
        assertThat(response.charts().thickness().points()).singleElement()
                .satisfies(point -> {
                    assertThat(point.value()).isEqualTo(126.5);
                    assertThat(point.status()).isEqualTo("WARNING");
                });
        assertThat(response.charts().defectScore().points()).singleElement()
                .satisfies(point -> assertThat(point.value()).isEqualTo(0.87));
        assertThat(response.charts().defectScore().markers()).singleElement().satisfies(marker -> {
            assertThat(marker.value()).isEqualTo(0.87);
            assertThat(marker.label()).isEqualTo("DEFECT");
            assertThat(marker.status()).isEqualTo("DANGER");
        });
        assertThat(response.charts().thermalStdTemp().points()).singleElement()
                .satisfies(point -> {
                    assertThat(point.value()).isEqualTo(4.2);
                    assertThat(point.status()).isEqualTo("WARNING");
                });
        assertThat(response.alert().messages())
                .contains("비전 판정: DEFECT", "상태: DANGER");
        assertThat(response.alert().detail()).satisfies(detail -> {
            assertThat(detail.time()).isEqualTo(eventTime);
            assertThat(detail.defectScore()).isEqualTo(0.87);
            assertThat(detail.status()).isEqualTo("DANGER");
        });
    }

    @Test
    void assemblyDashboardBuildsSummaryVehiclesAndAlert() {
        LocalDateTime eventTime = LocalDateTime.of(2026, 6, 18, 13, 55);
        LocalDateTime analyzedAt = LocalDateTime.of(2026, 6, 19, 13, 56);
        AssemblyAnalysisResult row = AssemblyAnalysisResult.builder()
                .analysisResult(result(ProcessCode.ASSEMBLY, 1L, Severity.CRITICAL, true, 86.5, eventTime, analyzedAt))
                .expectedSequence("A01 > A02 > A03 > A04")
                .actualSequence("A01 > A03 > A02 > A04")
                .sequenceErrorCount(1)
                .missingPartCount(0)
                .fasteningErrorCount(1)
                .build();
        when(assemblyRepository.findDashboardEventTimeValues()).thenReturn(List.of(
                LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 18, 10, 0)
        ));
        when(assemblyRepository.findDashboardRows(
                eq(LocalDateTime.of(2026, 6, 18, 0, 0)),
                eq(LocalDateTime.of(2026, 6, 19, 0, 0)),
                any(Pageable.class)
        ))
                .thenReturn(List.of(row));
        when(assemblyRepository.findDashboardSummaryRows(
                eq(LocalDateTime.of(2026, 6, 18, 0, 0)),
                eq(LocalDateTime.of(2026, 6, 19, 0, 0))
        )).thenReturn(List.of(row));

        AssemblyDashboardResponse response = service.getAssemblyDashboard(null, null, null, 30);

        assertThat(response.selectedDate()).isEqualTo(LocalDate.of(2026, 6, 18));
        assertThat(response.from()).isEqualTo(LocalDateTime.of(2026, 6, 18, 0, 0));
        assertThat(response.to()).isEqualTo(LocalDateTime.of(2026, 6, 18, 23, 59, 59, 999999999));
        assertThat(response.dataStartAt()).isEqualTo(eventTime);
        assertThat(response.dataEndAt()).isEqualTo(eventTime);
        assertThat(response.summary().vehicleCount()).isEqualTo(1);
        assertThat(response.summary().sequenceErrorCount()).isEqualTo(1);
        assertThat(response.summary().missingPartCount()).isZero();
        assertThat(response.summary().fasteningErrorCount()).isEqualTo(1);
        assertThat(response.summary().averageRiskScore()).isEqualTo(86.5);
        assertThat(response.vehicles()).singleElement().satisfies(vehicle -> {
            assertThat(vehicle.carDisplayId()).isEqualTo("CAR-000001");
            assertThat(vehicle.expectedSequence()).isEqualTo("A01 > A02 > A03 > A04");
            assertThat(vehicle.actualSequence()).isEqualTo("A01 > A03 > A02 > A04");
            assertThat(vehicle.sequenceErrorCount()).isEqualTo(1);
            assertThat(vehicle.missingPartCount()).isZero();
            assertThat(vehicle.fasteningErrorCount()).isEqualTo(1);
            assertThat(vehicle.time()).isEqualTo(eventTime);
            assertThat(vehicle.status()).isEqualTo("위험");
        });
        assertThat(response.alert().messages())
                .contains("조립 순서 오류와 체결 오류 동시 감지");
    }

    @Test
    void returnsEmptyResponsesWhenNoDataExists() {
        when(paintRepository.findDashboardEventTimeValues()).thenReturn(List.of());
        when(assemblyRepository.findDashboardEventTimeValues()).thenReturn(List.of());
        when(paintRepository.findDashboardRows(any(), any(), any(Pageable.class)))
                .thenReturn(List.of());
        when(assemblyRepository.findDashboardRows(any(), any(), any(Pageable.class)))
                .thenReturn(List.of());
        when(paintRepository.findDashboardSummaryRows(any(), any()))
                .thenReturn(List.of());
        when(assemblyRepository.findDashboardSummaryRows(any(), any()))
                .thenReturn(List.of());

        PaintDashboardResponse paint = service.getPaintDashboard(null, null, null, null);
        AssemblyDashboardResponse assembly = service.getAssemblyDashboard(null, null, null, null);

        assertThat(paint.selectedDate()).isNull();
        assertThat(paint.summary().analysisCount()).isZero();
        assertThat(paint.charts().surfaceQuality().points()).isEmpty();
        assertThat(paint.charts().defectScore().markers()).isEmpty();
        assertThat(paint.alert().title()).isEqualTo("최근 도장 상태 정상");
        assertThat(assembly.selectedDate()).isNull();
        assertThat(assembly.summary().vehicleCount()).isZero();
        assertThat(assembly.vehicles()).isEmpty();
        assertThat(assembly.alert()).isNull();
    }

    @Test
    void paintDashboardWithoutDateUsesLatestAvailableDate() {
        PaintAnalysisResult row = PaintAnalysisResult.builder()
                .analysisResult(result(ProcessCode.PAINT, 1L, Severity.NORMAL, false, 10.0))
                .surfaceQualityScore(90.0)
                .build();
        when(paintRepository.findDashboardEventTimeValues()).thenReturn(List.of(
                LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 18, 10, 0)
        ));
        when(paintRepository.findDashboardRows(
                eq(LocalDateTime.of(2026, 6, 18, 0, 0)),
                eq(LocalDateTime.of(2026, 6, 19, 0, 0)),
                any(Pageable.class)
        )).thenReturn(List.of(row));
        when(paintRepository.findDashboardSummaryRows(
                eq(LocalDateTime.of(2026, 6, 18, 0, 0)),
                eq(LocalDateTime.of(2026, 6, 19, 0, 0))
        )).thenReturn(List.of(row));

        PaintDashboardResponse response = service.getPaintDashboard(null, null, null, 30);

        assertThat(response.selectedDate()).isEqualTo(LocalDate.of(2026, 6, 18));
        assertThat(response.summary().analysisCount()).isEqualTo(1);
    }

    @Test
    void paintDashboardUsesLatestLimitRowsAndFullRangeSummary() {
        List<PaintAnalysisResult> allRows = paintRows(35);
        List<PaintAnalysisResult> latestRowsDesc = new ArrayList<>(allRows.subList(5, 35));
        Collections.reverse(latestRowsDesc);
        when(paintRepository.findDashboardSummaryRows(
                eq(LocalDateTime.of(2026, 7, 7, 0, 0)),
                eq(LocalDateTime.of(2026, 7, 8, 0, 0))
        )).thenReturn(allRows);
        when(paintRepository.findDashboardRows(
                eq(LocalDateTime.of(2026, 7, 7, 0, 0)),
                eq(LocalDateTime.of(2026, 7, 8, 0, 0)),
                any(Pageable.class)
        )).thenReturn(latestRowsDesc);

        PaintDashboardResponse response =
                service.getPaintDashboard(LocalDate.of(2026, 7, 7), null, null, 30);

        assertThat(response.summary().analysisCount()).isEqualTo(35);
        assertThat(response.charts().surfaceQuality().points()).hasSize(30);
        assertThat(response.charts().surfaceQuality().points()).extracting(PaintDashboardResponse.MetricPoint::time)
                .containsExactlyElementsOf(allRows.subList(5, 35).stream()
                        .map(row -> row.getAnalysisResult().getEventTime())
                        .toList());
        assertThat(response.charts().surfaceQuality().points()).extracting(PaintDashboardResponse.MetricPoint::time)
                .doesNotContain(allRows.get(0).getAnalysisResult().getEventTime())
                .contains(LocalDateTime.of(2026, 7, 7, 13, 0));
        assertThat(response.chartStartAt()).isEqualTo(allRows.get(5).getAnalysisResult().getEventTime());
        assertThat(response.chartEndAt()).isEqualTo(allRows.get(34).getAnalysisResult().getEventTime());
    }

    @Test
    void paintDashboardKeepsRowsAtSameTimeAndBuildsDefectMarkers() {
        LocalDateTime eventTime = LocalDateTime.of(2026, 7, 7, 12, 19, 5);
        PaintAnalysisResult first = PaintAnalysisResult.builder()
                .analysisResult(result(ProcessCode.PAINT, 1L, Severity.NORMAL, false, 3.6, eventTime, eventTime))
                .defectScore(0.044)
                .surfaceQualityScore(97.9)
                .thicknessValue(113.9)
                .thermalStdTemp(0.67)
                .visionLabel("NORMAL")
                .imagePosition("LEFT")
                .build();
        PaintAnalysisResult second = PaintAnalysisResult.builder()
                .analysisResult(result(ProcessCode.PAINT, 2L, Severity.NORMAL, false, 5.3, eventTime, eventTime))
                .defectScore(0.072)
                .surfaceQualityScore(96.7)
                .thicknessValue(115.7)
                .thermalStdTemp(0.71)
                .visionLabel("NORMAL")
                .imagePosition("LEFT")
                .build();
        PaintAnalysisResult third = PaintAnalysisResult.builder()
                .analysisResult(result(ProcessCode.PAINT, 3L, Severity.NORMAL, false, 3.6, eventTime, eventTime))
                .defectScore(0.063)
                .surfaceQualityScore(97.257)
                .thicknessValue(110.212)
                .thermalStdTemp(0.879)
                .visionLabel("DUST_CONTAMINATION")
                .imagePosition("LEFT")
                .build();
        List<PaintAnalysisResult> rows = List.of(first, second, third);
        when(paintRepository.findDashboardSummaryRows(any(), any())).thenReturn(rows);
        when(paintRepository.findDashboardRows(any(), any(), any(Pageable.class))).thenReturn(rows);

        PaintDashboardResponse response =
                service.getPaintDashboard(LocalDate.of(2026, 7, 7), null, null, 30);

        assertThat(response.charts().surfaceQuality().points()).hasSize(3);
        assertThat(response.charts().thickness().points()).hasSize(3);
        assertThat(response.charts().defectScore().points()).hasSize(3);
        assertThat(response.charts().thermalStdTemp().points()).hasSize(3);
        assertThat(response.charts().defectScore().markers()).singleElement().satisfies(marker -> {
            assertThat(marker.time()).isEqualTo(eventTime);
            assertThat(marker.label()).isEqualTo("DUST_CONTAMINATION");
            assertThat(marker.status()).isEqualTo("WARNING");
        });
    }

    @Test
    void paintDashboardTreatsNormalZeroSurfaceQualityAsMissingValue() {
        LocalDateTime eventTime = LocalDateTime.of(2026, 7, 7, 14, 11, 59);
        PaintAnalysisResult missingSurfaceQuality = PaintAnalysisResult.builder()
                .analysisResult(result(ProcessCode.PAINT, 1L, Severity.NORMAL, false, 0.0, eventTime, eventTime))
                .defectScore(0.052)
                .surfaceQualityScore(0.0)
                .thicknessValue(116.0)
                .thermalStdTemp(0.0)
                .visionLabel("NORMAL")
                .imagePosition("LEFT")
                .build();
        PaintAnalysisResult measuredSurfaceQuality = PaintAnalysisResult.builder()
                .analysisResult(result(ProcessCode.PAINT, 2L, Severity.NORMAL, false, 3.0, eventTime.plusMinutes(1), eventTime))
                .defectScore(0.050)
                .surfaceQualityScore(96.0)
                .thicknessValue(116.0)
                .thermalStdTemp(0.5)
                .visionLabel("NORMAL")
                .imagePosition("LEFT")
                .build();
        List<PaintAnalysisResult> rows = List.of(missingSurfaceQuality, measuredSurfaceQuality);
        when(paintRepository.findDashboardSummaryRows(any(), any())).thenReturn(rows);
        when(paintRepository.findDashboardRows(any(), any(), any(Pageable.class))).thenReturn(rows);

        PaintDashboardResponse response =
                service.getPaintDashboard(LocalDate.of(2026, 7, 7), null, null, 30);

        assertThat(response.charts().surfaceQuality().points()).extracting(PaintDashboardResponse.MetricPoint::value)
                .containsExactly(null, 96.0);
        assertThat(response.summary().averageSurfaceQualityScore()).isEqualTo(96.0);
        assertThat(response.alert().title()).isEqualTo("최근 도장 상태 정상");
    }

    @Test
    void assemblyDashboardUsesLatestLimitRowsAndFullRangeSummary() {
        List<AssemblyAnalysisResult> allRows = assemblyRows(35);
        List<AssemblyAnalysisResult> latestRowsDesc = new ArrayList<>(allRows.subList(5, 35));
        Collections.reverse(latestRowsDesc);
        when(assemblyRepository.findDashboardSummaryRows(
                eq(LocalDateTime.of(2026, 7, 7, 0, 0)),
                eq(LocalDateTime.of(2026, 7, 8, 0, 0))
        )).thenReturn(allRows);
        when(assemblyRepository.findDashboardRows(
                eq(LocalDateTime.of(2026, 7, 7, 0, 0)),
                eq(LocalDateTime.of(2026, 7, 8, 0, 0)),
                any(Pageable.class)
        )).thenReturn(latestRowsDesc);

        AssemblyDashboardResponse response =
                service.getAssemblyDashboard(LocalDate.of(2026, 7, 7), null, null, 30);

        assertThat(response.summary().vehicleCount()).isEqualTo(35);
        assertThat(response.vehicles()).hasSize(30);
        assertThat(response.vehicles()).extracting(AssemblyDashboardResponse.VehicleRow::time)
                .containsExactlyElementsOf(allRows.subList(5, 35).stream()
                        .map(row -> row.getAnalysisResult().getEventTime())
                        .toList());
        assertThat(response.vehicles()).extracting(AssemblyDashboardResponse.VehicleRow::time)
                .doesNotContain(allRows.get(0).getAnalysisResult().getEventTime())
                .contains(LocalDateTime.of(2026, 7, 7, 13, 0));
        assertThat(response.dataStartAt()).isEqualTo(allRows.get(5).getAnalysisResult().getEventTime());
        assertThat(response.dataEndAt()).isEqualTo(allRows.get(34).getAnalysisResult().getEventTime());
    }

    @Test
    void dateApisReturnDatesAndLatestDate() {
        when(paintRepository.findDashboardEventTimeValues()).thenReturn(List.of(
                LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 1, 11, 0),
                LocalDateTime.of(2026, 6, 18, 10, 0)
        ));
        when(assemblyRepository.findDashboardEventTimeValues()).thenReturn(List.of(
                LocalDateTime.of(2026, 6, 1, 10, 0)
        ));

        assertThat(service.getPaintDates().dates())
                .containsExactly(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 18));
        assertThat(service.getPaintDates().latestDate()).isEqualTo(LocalDate.of(2026, 6, 18));
        assertThat(service.getAssemblyDates().dates()).containsExactly(LocalDate.of(2026, 6, 1));
        assertThat(service.getAssemblyDates().latestDate()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void equipmentOperationRateUsesCurrentStatusAndIncludesWarningAsOperating() {
        when(equipmentOperationRateRepository.countByProcessAndStatus()).thenReturn(List.of(
                new EquipmentOperationRateRepository.StatusCount(
                        ProcessCode.PRESS, EquipmentOperationStatus.RUNNING, 3),
                new EquipmentOperationRateRepository.StatusCount(
                        ProcessCode.PRESS, EquipmentOperationStatus.WARNING, 1),
                new EquipmentOperationRateRepository.StatusCount(
                        ProcessCode.PRESS, EquipmentOperationStatus.STOPPED, 1),
                new EquipmentOperationRateRepository.StatusCount(
                        ProcessCode.BODY, EquipmentOperationStatus.RUNNING, 2),
                new EquipmentOperationRateRepository.StatusCount(
                        ProcessCode.BODY, EquipmentOperationStatus.FAULT, 1),
                new EquipmentOperationRateRepository.StatusCount(
                        ProcessCode.ASSEMBLY, EquipmentOperationStatus.WARNING, 2),
                new EquipmentOperationRateRepository.StatusCount(
                        ProcessCode.ASSEMBLY, EquipmentOperationStatus.FAULT, 1)
        ));

        EquipmentOperationRateResponse response = service.getEquipmentOperationRate();

        assertThat(response.items()).hasSize(4);
        assertThat(response.items()).extracting(EquipmentOperationRateResponse.Item::processCode)
                .containsExactly("PRESS", "BODY", "PAINT", "ASSEMBLY");
        assertThat(response.items().get(0)).satisfies(item -> {
            assertThat(item.runningCount()).isEqualTo(3);
            assertThat(item.warningCount()).isEqualTo(1);
            assertThat(item.operatingCount()).isEqualTo(4);
            assertThat(item.stoppedCount()).isEqualTo(1);
            assertThat(item.faultCount()).isZero();
            assertThat(item.totalCount()).isEqualTo(5);
            assertThat(item.operationRate()).isEqualTo(80.0);
            assertThat(item.statusCounts()).containsEntry(EquipmentOperationStatus.WARNING, 1L);
        });
        assertThat(response.items().get(2)).satisfies(item -> {
            assertThat(item.processCode()).isEqualTo("PAINT");
            assertThat(item.totalCount()).isZero();
            assertThat(item.operationRate()).isEqualTo(0.0);
            assertThat(item.statusCounts()).containsEntry(EquipmentOperationStatus.RUNNING, 0L)
                    .containsEntry(EquipmentOperationStatus.WARNING, 0L)
                    .containsEntry(EquipmentOperationStatus.STOPPED, 0L)
                    .containsEntry(EquipmentOperationStatus.FAULT, 0L);
        });
        assertThat(response.items().get(3)).satisfies(item -> {
            assertThat(item.warningCount()).isEqualTo(2);
            assertThat(item.faultCount()).isEqualTo(1);
            assertThat(item.operationRate()).isEqualTo(66.7);
        });
    }

    private ManufacturingAnalysisResult result(
            ProcessCode processCode,
            Long carMasterId,
            Severity severity,
            boolean abnormal,
            double riskScore
    ) {
        return result(
                processCode,
                carMasterId,
                severity,
                abnormal,
                riskScore,
                LocalDateTime.of(2026, 6, 18, 13, 55),
                LocalDateTime.of(2026, 6, 18, 13, 56)
        );
    }

    private ManufacturingAnalysisResult result(
            ProcessCode processCode,
            Long carMasterId,
            Severity severity,
            boolean abnormal,
            double riskScore,
            LocalDateTime eventTime,
            LocalDateTime analyzedAt
    ) {
        return ManufacturingAnalysisResult.builder()
                .analysisId("ANL-" + processCode)
                .eventId("EVT-" + processCode)
                .carMasterId(carMasterId)
                .equipmentId(10L)
                .processCode(processCode)
                .eventTime(eventTime)
                .analyzedAt(analyzedAt)
                .isAbnormal(abnormal)
                .severity(severity)
                .riskScore(riskScore)
                .build();
    }

    private List<PaintAnalysisResult> paintRows(int count) {
        List<PaintAnalysisResult> rows = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            LocalDateTime eventTime = LocalDateTime.of(2026, 7, 7, 10, 0).plusMinutes(index * 6L);
            rows.add(PaintAnalysisResult.builder()
                    .analysisResult(result(
                            ProcessCode.PAINT,
                            (long) index,
                            Severity.NORMAL,
                            false,
                            index,
                            eventTime,
                            eventTime.plusSeconds(5)
                    ))
                    .surfaceQualityScore(90.0)
                    .thicknessValue(120.0)
                    .thermalStdTemp(1.0)
                    .build());
        }
        return rows;
    }

    private List<AssemblyAnalysisResult> assemblyRows(int count) {
        List<AssemblyAnalysisResult> rows = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            LocalDateTime eventTime = LocalDateTime.of(2026, 7, 7, 10, 0).plusMinutes(index * 6L);
            rows.add(AssemblyAnalysisResult.builder()
                    .analysisResult(result(
                            ProcessCode.ASSEMBLY,
                            (long) index,
                            Severity.NORMAL,
                            false,
                            index,
                            eventTime,
                            eventTime.plusSeconds(5)
                    ))
                    .expectedSequence("A01 > A02")
                    .actualSequence("A01 > A02")
                    .sequenceErrorCount(0)
                    .missingPartCount(0)
                    .fasteningErrorCount(0)
                    .build());
        }
        return rows;
    }
}

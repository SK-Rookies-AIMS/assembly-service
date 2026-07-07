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

        PaintDashboardResponse response =
                service.getPaintDashboard(LocalDate.of(2026, 6, 18), null, null, 30);

        assertThat(response.selectedDate()).isEqualTo(LocalDate.of(2026, 6, 18));
        assertThat(response.summary().analysisCount()).isEqualTo(1);
        assertThat(response.summary().averageThicknessValue()).isEqualTo(126.5);
        assertThat(response.summary().averageThermalStdTemp()).isEqualTo(4.2);
        assertThat(response.summary().defectRate()).isEqualTo(100.0);
        assertThat(response.summary().averageSurfaceQualityScore()).isEqualTo(72.3);
        assertThat(response.summary().alertCount()).isEqualTo(1);
        assertThat(response.chart()).singleElement().satisfies(point -> {
            assertThat(point.time()).isEqualTo(eventTime);
            assertThat(point.defectScore()).isEqualTo(0.87);
            assertThat(point.surfaceQualityScore()).isEqualTo(72.3);
            assertThat(point.thicknessValue()).isEqualTo(126.5);
            assertThat(point.riskScore()).isEqualTo(88.5);
            assertThat(point.imagePosition()).isEqualTo("LEFT");
        });
        assertThat(response.alert().messages())
                .contains("비전 불량 라벨 감지: DEFECT")
                .anyMatch(message -> message.contains("도장 두께 이상 의심"));
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

        AssemblyDashboardResponse response = service.getAssemblyDashboard(null, null, null, 30);

        assertThat(response.selectedDate()).isEqualTo(LocalDate.of(2026, 6, 18));
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

        PaintDashboardResponse paint = service.getPaintDashboard(null, null, null, null);
        AssemblyDashboardResponse assembly = service.getAssemblyDashboard(null, null, null, null);

        assertThat(paint.selectedDate()).isNull();
        assertThat(paint.summary().analysisCount()).isZero();
        assertThat(paint.chart()).isEmpty();
        assertThat(paint.alert()).isNull();
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

        PaintDashboardResponse response = service.getPaintDashboard(null, null, null, 30);

        assertThat(response.selectedDate()).isEqualTo(LocalDate.of(2026, 6, 18));
        assertThat(response.summary().analysisCount()).isEqualTo(1);
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
}

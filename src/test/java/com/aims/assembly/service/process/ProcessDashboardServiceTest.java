package com.aims.assembly.service.process;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import com.aims.assembly.domain.assembly.AssemblyAnalysisResult;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.domain.enums.Severity;
import com.aims.assembly.domain.paint.PaintAnalysisResult;
import com.aims.assembly.dto.process.AssemblyDashboardResponse;
import com.aims.assembly.dto.process.PaintDashboardResponse;
import com.aims.assembly.repository.analysis.AssemblyAnalysisResultRepository;
import com.aims.assembly.repository.analysis.PaintAnalysisResultRepository;
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
    private ProcessDashboardService service;

    @BeforeEach
    void setUp() {
        service = new ProcessDashboardService(paintRepository, assemblyRepository);
    }

    @Test
    void paintDashboardBuildsSummaryChartAndAlert() {
        PaintAnalysisResult row = PaintAnalysisResult.builder()
                .analysisResult(result(ProcessCode.PAINT, 1L, Severity.CRITICAL, true, 88.5))
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
        assertThat(response.summary().defectRate()).isEqualTo(100.0);
        assertThat(response.summary().averageSurfaceQualityScore()).isEqualTo(72.3);
        assertThat(response.summary().alertCount()).isEqualTo(1);
        assertThat(response.chart()).singleElement().satisfies(point -> {
            assertThat(point.defectScore()).isEqualTo(0.87);
            assertThat(point.surfaceQualityScore()).isEqualTo(72.3);
            assertThat(point.thicknessValue()).isEqualTo(126.5);
            assertThat(point.imagePosition()).isEqualTo("LEFT");
        });
        assertThat(response.alert().messages())
                .contains("비전 불량 라벨 감지: DEFECT")
                .anyMatch(message -> message.contains("도장 두께 이상 의심"));
    }

    @Test
    void assemblyDashboardBuildsSummaryVehiclesAndAlert() {
        AssemblyAnalysisResult row = AssemblyAnalysisResult.builder()
                .analysisResult(result(ProcessCode.ASSEMBLY, 1L, Severity.CRITICAL, true, 86.5))
                .expectedSequence("A01 > A02 > A03 > A04")
                .actualSequence("A01 > A03 > A02 > A04")
                .sequenceErrorCount(1)
                .missingPartCount(0)
                .fasteningErrorCount(1)
                .build();
        when(assemblyRepository.findDashboardAnalyzedAtValues()).thenReturn(List.of(
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
            assertThat(vehicle.status()).isEqualTo("위험");
        });
        assertThat(response.alert().messages())
                .contains("조립 순서 오류와 체결 오류 동시 감지");
    }

    @Test
    void returnsEmptyResponsesWhenNoDataExists() {
        when(paintRepository.findDashboardAnalyzedAtValues()).thenReturn(List.of());
        when(assemblyRepository.findDashboardAnalyzedAtValues()).thenReturn(List.of());
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
        when(paintRepository.findDashboardAnalyzedAtValues()).thenReturn(List.of(
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
        when(paintRepository.findDashboardAnalyzedAtValues()).thenReturn(List.of(
                LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 1, 11, 0),
                LocalDateTime.of(2026, 6, 18, 10, 0)
        ));
        when(assemblyRepository.findDashboardAnalyzedAtValues()).thenReturn(List.of(
                LocalDateTime.of(2026, 6, 1, 10, 0)
        ));

        assertThat(service.getPaintDates().dates())
                .containsExactly(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 18));
        assertThat(service.getPaintDates().latestDate()).isEqualTo(LocalDate.of(2026, 6, 18));
        assertThat(service.getAssemblyDates().dates()).containsExactly(LocalDate.of(2026, 6, 1));
        assertThat(service.getAssemblyDates().latestDate()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    private ManufacturingAnalysisResult result(
            ProcessCode processCode,
            Long carMasterId,
            Severity severity,
            boolean abnormal,
            double riskScore
    ) {
        return ManufacturingAnalysisResult.builder()
                .analysisId("ANL-" + processCode)
                .eventId("EVT-" + processCode)
                .carMasterId(carMasterId)
                .equipmentId(10L)
                .processCode(processCode)
                .eventTime(LocalDateTime.of(2026, 6, 18, 13, 55))
                .analyzedAt(LocalDateTime.of(2026, 6, 18, 13, 56))
                .isAbnormal(abnormal)
                .severity(severity)
                .riskScore(riskScore)
                .build();
    }
}

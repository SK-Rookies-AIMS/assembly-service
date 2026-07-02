package com.aims.assembly.service.body;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import com.aims.assembly.domain.body.BodyAnalysisResult;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.domain.enums.Severity;
import com.aims.assembly.repository.analysis.BodyAnalysisResultRepository;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BodyAnomalyDetectionServiceTest {

    private final BodyAnalysisResultRepository repository = mock(BodyAnalysisResultRepository.class);
    private final ManufacturingEventJsonRepository eventJsonRepository = mock(ManufacturingEventJsonRepository.class);
    private final BodyAnomalyDetectionService service =
            new BodyAnomalyDetectionService(repository, eventJsonRepository, new ObjectMapper());

    @Test
    void dashboardReturnsRobotMetricsChartAndAlertReasons() {
        BodyAnalysisResult result = bodyResult(
                "EVT-20260601-000046",
                LocalDateTime.of(2026, 6, 1, 10, 15),
                72.0
        );

        BodyAnalysisResultRepository.BodyDateOptionProjection projection =
                mock(BodyAnalysisResultRepository.BodyDateOptionProjection.class);
        when(projection.getDate()).thenReturn(LocalDate.of(2026, 6, 1));
        when(projection.getSampleEventId()).thenReturn("EVT-20260601-000046");
        when(repository.findBodyAnalysisDateOptions()).thenReturn(List.of(projection));
        when(repository.findDashboardByEventTimeBetween(
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 6, 1, 23, 59, 59, 999_999_999),
                PageRequest.of(0, 10_000)
        )).thenReturn(List.of(result));
        when(repository.findByAnalysisResult_AnalysisId("ANL-EVT-20260601-000046"))
                .thenReturn(Optional.of(result));

        var response = service.findDashboard(null, null, null, null, 30);

        assertThat(response.metrics().robotMotionStatus()).isEqualTo("COLLISION_RISK");
        assertThat(response.metrics().robotOperationMode()).isEqualTo("AUTO_MANUAL_STOPPED");
        assertThat(response.metrics().robotVibrationScore()).isEqualTo(0.27);
        assertThat(response.metrics().frequencyPeakValue()).isEqualTo(2.907113);
        assertThat(response.metrics().frequencyPeakBand()).isEqualTo("501_600_HZ");
        assertThat(response.metrics().riskScore()).isEqualTo(72.0);
        assertThat(response.metrics().frequencyBands())
                .containsEntry("LOW", 1.193284)
                .containsEntry("MEDIUM", 1.987782)
                .containsEntry("HIGH", 2.907113);
        assertThat(response.chart()).hasSize(1);
        assertThat(response.chart().get(0).robotVibrationScore()).isEqualTo(0.27);
        assertThat(response.chart().get(0).frequencyPeakValue()).isEqualTo(2.907113);
        assertThat(response.chart().get(0).riskScore()).isEqualTo(72.0);
        assertThat(response.alert().detected()).isTrue();
        assertThat(response.alert().reasons()).contains(
                "robot_motion_status = COLLISION_RISK",
                "robot_operation_mode = AUTO_MANUAL_STOPPED",
                "피크 진동값 급증 (2.9 mm/s)",
                "고주파 대역 이상 감지 (501-600Hz)"
        );
    }

    private BodyAnalysisResult bodyResult(String eventId, LocalDateTime eventTime, double riskScore) {
        ManufacturingAnalysisResult analysis = ManufacturingAnalysisResult.builder()
                .id(1L)
                .analysisId("ANL-" + eventId)
                .eventId(eventId)
                .carMasterId(1L)
                .equipmentId(1L)
                .processCode(ProcessCode.BODY)
                .eventTime(eventTime)
                .isAbnormal(true)
                .severity(Severity.WARNING)
                .riskScore(riskScore)
                .build();
        return BodyAnalysisResult.builder()
                .analysisResult(analysis)
                .robotMotionStatus("COLLISION_RISK")
                .robotOperationMode("AUTO_MANUAL_STOPPED")
                .robotVibrationScore(0.27)
                .frequencyPeakBand("501_600_HZ")
                .frequencyPeakValue(0.002907113)
                .frequencyBandsJson("""
                        {
                          "freq_0_100_hz": 0.001193284,
                          "freq_101_200_hz": 0.001987782,
                          "freq_501_600_hz": 0.002907113
                        }
                        """)
                .build();
    }
}

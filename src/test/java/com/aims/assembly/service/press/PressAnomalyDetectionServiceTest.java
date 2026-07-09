package com.aims.assembly.service.press;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.domain.enums.Severity;
import com.aims.assembly.domain.press.PressAnalysisResult;
import com.aims.assembly.repository.analysis.PressAnalysisResultRepository;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PressAnomalyDetectionServiceTest {

    private final PressAnalysisResultRepository repository = mock(PressAnalysisResultRepository.class);
    private final ManufacturingEventJsonRepository eventRepository = mock(ManufacturingEventJsonRepository.class);
    private final PressAnomalyDetectionService service = new PressAnomalyDetectionService(repository, eventRepository);

    @Test
    void dashboardUsesAnalysisResultEventTimeAsPrimarySource() {
        PressAnalysisResult result = pressResult(
                "EVT-20260601-000397",
                LocalDateTime.of(2026, 6, 1, 9, 30),
                78.0
        );

        PressAnalysisResultRepository.PressDateOptionProjection projection =
                mock(PressAnalysisResultRepository.PressDateOptionProjection.class);
        when(projection.getDate()).thenReturn(LocalDate.of(2026, 6, 1));
        when(projection.getSampleEventId()).thenReturn("EVT-20260601-000397");
        when(repository.findPressAnalysisDateOptions()).thenReturn(List.of(projection));

        when(repository.findDashboardByEventTimeBetween(
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 6, 1, 23, 59, 59, 999_999_999),
                PageRequest.of(0, 10_000)
        )).thenReturn(List.of(result));
        when(repository.findMaxRiskScoreByEventTimeBetween(
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 6, 1, 23, 59, 59, 999_999_999)
        )).thenReturn(78.0);

        var response = service.findDashboard(null, null, null, null, 30);

        assertThat(response.date()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(response.from()).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0));
        assertThat(response.to()).isEqualTo(LocalDateTime.of(2026, 6, 1, 23, 59, 59, 999_999_999));
        assertThat(response.dateOptions()).extracting("date").containsExactly(LocalDate.of(2026, 6, 1));
        assertThat(response.chart()).hasSize(1);
        assertThat(response.chart().get(0).eventId()).isEqualTo("EVT-20260601-000397");
        assertThat(response.chart().get(0).timestamp()).isEqualTo(LocalDateTime.of(2026, 6, 1, 9, 30));
        assertThat(response.chart().get(0).targetCycleTimeSec()).isEqualTo(40.0);
        assertThat(response.chart().get(0).actualCycleTimeSec()).isEqualTo(43.0);
        assertThat(response.chart().get(0).cycleTimeGapSec()).isEqualTo(3.0);
        assertThat(response.charts().cycleTime().title()).isEqualTo("press cycle time");
        assertThat(response.charts().cycleTime().points()).hasSize(1);
        assertThat(response.charts().cycleTime().points().get(0).targetCycleTimeSec()).isEqualTo(40.0);
        assertThat(response.charts().cycleTime().points().get(0).actualCycleTimeSec()).isEqualTo(43.0);
        assertThat(response.charts().delay().title()).isEqualTo("press delay/gap");
        assertThat(response.charts().delay().points()).hasSize(1);
        assertThat(response.charts().delay().points().get(0).cycleTimeGapSec()).isEqualTo(3.0);
        assertThat(response.metrics().targetCycleTimeSec()).isEqualTo(40.0);
        assertThat(response.metrics().actualCycleTimeSec()).isEqualTo(43.0);
        assertThat(response.metrics().cycleTimeGapSec()).isEqualTo(3.0);
        assertThat(response.metrics().riskScore()).isEqualTo(78.0);
        assertThat(response.alert().detected()).isTrue();
        assertThat(response.alert().title()).isNotBlank();
    }

    private PressAnalysisResult pressResult(String eventId, LocalDateTime eventTime, double riskScore) {
        ManufacturingAnalysisResult analysis = ManufacturingAnalysisResult.builder()
                .id(1L)
                .analysisId("ANL-" + eventId)
                .eventId(eventId)
                .carMasterId(1L)
                .equipmentId(1L)
                .processCode(ProcessCode.PRESS)
                .eventTime(eventTime)
                .isAbnormal(true)
                .severity(Severity.WARNING)
                .riskScore(riskScore)
                .build();
        return PressAnalysisResult.builder()
                .analysisResult(analysis)
                .targetCycleTimeSec(40.0)
                .actualCycleTimeSec(43.0)
                .cycleTimeGapSec(3.0)
                .countIncreaseYn(false)
                .build();
    }
}

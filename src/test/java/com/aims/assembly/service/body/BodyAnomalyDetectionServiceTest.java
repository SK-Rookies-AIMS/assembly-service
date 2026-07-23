package com.aims.assembly.service.body;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import com.aims.assembly.domain.body.BodyAnalysisResult;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.domain.enums.Severity;
import com.aims.assembly.kafka.model.ManufacturingRawEvent;
import com.aims.assembly.repository.analysis.BodyAnalysisResultRepository;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository.StoredManufacturingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    void dashboardReturnsSeparatedChartsAndThresholds() {
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
        when(eventJsonRepository.findByEventId("EVT-20260601-000046"))
                .thenReturn(Optional.of(storedEvent(
                        "EVT-20260601-000046",
                        """
                                {
                                  "processData": {
                                    "body": {
                                      "robotMotionStatus": "COLLISION_RISK",
                                      "robotOperationMode": "AUTO_MANUAL_STOPPED",
                                      "frequencyPeakBand": "501_600_HZ",
                                      "frequencyBands": {
                                        "freq_0_100_hz": 1.193284,
                                        "freq_101_200_hz": 1.987782,
                                        "freq_501_600_hz": 2.907113
                                      }
                                    }
                                  },
                                  "sensor": {
                                    "robotArmVibration": {
                                      "vibrationScore": 0.27,
                                      "vibrationPeak": 0.0067,
                                      "vibrationRms": 0.003611111
                                    }
                                  }
                                }
                                """
                )));

        var response = service.findDashboard(null, null, null, null, 30);

        assertThat(response.metrics().robotMotionStatus()).isEqualTo("COLLISION_RISK");
        assertThat(response.metrics().robotOperationMode()).isEqualTo("AUTO_MANUAL_STOPPED");
        assertThat(response.metrics().avgRobotVibrationScore()).isEqualTo(0.27);
        assertThat(response.metrics().avgFrequencyPeakValue()).isEqualTo(2.907113);
        assertThat(response.metrics().frequencyPeakBand()).isEqualTo("501~600Hz");
        assertThat(response.metrics().vibrationWarningLine()).isEqualTo(0.75);
        assertThat(response.metrics().vibrationDangerLine()).isEqualTo(1.25);
        assertThat(response.metrics().peakWarningLine()).isEqualTo(0.005);
        assertThat(response.metrics().peakDangerLine()).isEqualTo(0.0055);
        assertThat(response.metrics().riskScore()).isEqualTo(72.0);
        assertThat(response.metrics().severity()).isEqualTo("CRITICAL");
        assertThat(response.metrics().frequencyBands())
                .containsEntry("LOW", 1.193284)
                .containsEntry("MEDIUM", 1.987782)
                .containsEntry("HIGH", 2.907113);

        assertThat(response.charts().robotVibration().points()).hasSize(1);
        assertThat(response.charts().robotVibration().points().get(0).value()).isEqualTo(0.27);
        assertThat(response.charts().robotVibration().points().get(0).warningLine()).isEqualTo(0.75);
        assertThat(response.charts().robotVibration().points().get(0).dangerLine()).isEqualTo(1.25);

        assertThat(response.charts().frequencyPeak().points()).hasSize(1);
        assertThat(response.charts().frequencyPeak().points().get(0).value()).isEqualTo(2.907113);
        assertThat(response.charts().frequencyPeak().points().get(0).secondaryValue()).isEqualTo(0.003611111);
        assertThat(response.charts().frequencyPeak().points().get(0).warningLine()).isEqualTo(0.005);
        assertThat(response.charts().frequencyPeak().points().get(0).dangerLine()).isEqualTo(0.0055);

        assertThat(response.frequencyChart()).hasSize(3);
        assertThat(response.frequencyChart().get(0).targetValue()).isEqualTo(0.006);
        assertThat(response.frequencyChart().get(0).warningValue()).isEqualTo(0.008);
        assertThat(response.frequencyChart().get(0).dangerValue()).isEqualTo(0.009);

        assertThat(response.alert().detected()).isTrue();
        assertThat(response.alert().reasons()).isNotEmpty();
        assertThat(response.alert().reasons()).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void abnormalBodyPointIsPromotedToWarningSeverity() {
        ManufacturingAnalysisResult analysis = ManufacturingAnalysisResult.builder()
                .id(2L)
                .analysisId("ANL-EVT-20260601-000777")
                .eventId("EVT-20260601-000777")
                .carMasterId(1L)
                .equipmentId(1L)
                .processCode(ProcessCode.BODY)
                .eventTime(LocalDateTime.of(2026, 6, 1, 11, 0))
                .isAbnormal(true)
                .severity(Severity.NORMAL)
                .riskScore(15.0)
                .build();
        BodyAnalysisResult result = BodyAnalysisResult.builder()
                .analysisResult(analysis)
                .robotMotionStatus("NORMAL")
                .robotOperationMode("AUTO")
                .robotVibrationScore(0.2)
                .frequencyPeakBand("MID")
                .frequencyPeakValue(0.002)
                .frequencyBandsJson("{}")
                .build();

        BodyAnalysisResultRepository.BodyDateOptionProjection projection =
                mock(BodyAnalysisResultRepository.BodyDateOptionProjection.class);
        when(projection.getDate()).thenReturn(LocalDate.of(2026, 6, 1));
        when(projection.getSampleEventId()).thenReturn("EVT-20260601-000777");
        when(repository.findBodyAnalysisDateOptions()).thenReturn(List.of(projection));
        when(repository.findDashboardByEventTimeBetween(
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 6, 1, 23, 59, 59, 999_999_999),
                PageRequest.of(0, 10_000)
        )).thenReturn(List.of(result));
        when(repository.findByAnalysisResult_AnalysisId("ANL-EVT-20260601-000777"))
                .thenReturn(Optional.of(result));
        when(eventJsonRepository.findByEventId("EVT-20260601-000777"))
                .thenReturn(Optional.of(storedEvent(
                        "EVT-20260601-000777",
                        """
                                {
                                  "processData": {
                                    "body": {
                                      "robotMotionStatus": "NORMAL",
                                      "robotOperationMode": "AUTO",
                                      "frequencyPeakBand": "MID",
                                      "frequencyBands": {
                                        "LOW": 0.001,
                                        "MEDIUM": 0.002,
                                        "HIGH": 0.003
                                      }
                                    }
                                  },
                                  "sensor": {
                                    "robotArmVibration": {
                                      "vibrationScore": 0.2,
                                      "vibrationPeak": 0.002,
                                      "vibrationRms": 0.001
                                    }
                                  }
                                }
                                """
                )));

        var response = service.findDashboard(null, null, null, null, 30);

        assertThat(response.charts().robotVibration().points()).hasSize(1);
        assertThat(response.charts().robotVibration().points().get(0).severity()).isEqualTo("WARNING");
        assertThat(response.charts().frequencyPeak().points()).hasSize(1);
        assertThat(response.charts().frequencyPeak().points().get(0).severity()).isEqualTo("WARNING");
        assertThat(response.metrics().severity()).isEqualTo("WARNING");
    }

    private StoredManufacturingEvent storedEvent(String eventId, String rawJson) {
        ManufacturingRawEvent payload = new ManufacturingRawEvent(
                1L,
                eventId,
                LocalDateTime.of(2026, 6, 1, 10, 15),
                1L,
                1L,
                ProcessCode.BODY,
                "EQ-1",
                "BODY",
                null,
                null,
                Map.of()
        );
        return new StoredManufacturingEvent(
                payload,
                rawJson,
                "CAR-1",
                null,
                null,
                false,
                0L,
                null
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
                .frequencyPeakValue(2.907113)
                .frequencyBandsJson("""
                        {
                          "freq_0_100_hz": 1.193284,
                          "freq_101_200_hz": 1.987782,
                          "freq_501_600_hz": 2.907113
                        }
                        """)
                .build();
    }
}

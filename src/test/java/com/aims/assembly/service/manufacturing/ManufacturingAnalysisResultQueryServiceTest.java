package com.aims.assembly.service.manufacturing;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.domain.enums.Severity;
import com.aims.assembly.kafka.ManufacturingEventAnalyzer;
import com.aims.assembly.properties.KafkaCustomProperties;
import com.aims.assembly.repository.analysis.ManufacturingAnalysisQueryRepository;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ManufacturingAnalysisResultQueryServiceTest {

    private final ManufacturingAnalysisQueryRepository queryRepository =
            mock(ManufacturingAnalysisQueryRepository.class);
    private final ManufacturingEventJsonRepository eventRepository =
            mock(ManufacturingEventJsonRepository.class);
    private final ManufacturingAnalysisResultQueryService service =
            new ManufacturingAnalysisResultQueryService(
                    queryRepository,
                    eventRepository,
                    mock(ManufacturingEventAnalyzer.class),
                    new KafkaCustomProperties()
            );

    @Test
    void identifiesFinalCompletedCarWhenAllProcessesAreNormal() {
        when(queryRepository.findByCarMasterId(700L))
                .thenReturn(List.of(
                        result("A1", "E1", ProcessCode.PRESS),
                        result("A2", "E2", ProcessCode.BODY),
                        result("A3", "E3", ProcessCode.PAINT),
                        result("A4", "E4", ProcessCode.ASSEMBLY)
                ));
        when(eventRepository.hasBlockedEventsByCarMasterId(700L)).thenReturn(false);

        var response = service.findCarCompletion(700L);

        assertThat(response.status()).isEqualTo("FINAL_COMPLETED");
        assertThat(response.finalCompleted()).isTrue();
        assertThat(response.completedNormalProcessCount()).isEqualTo(4);
    }

    @Test
    void marksCarAbnormalWhenAnyProcessIsAbnormal() {
        when(queryRepository.findByCarMasterId(700L))
                .thenReturn(List.of(
                        result("A1", "E1", ProcessCode.PRESS),
                        ManufacturingAnalysisResult.builder()
                                .analysisId("A2")
                                .eventId("E2")
                                .carMasterId(700L)
                                .equipmentId(6L)
                                .processCode(ProcessCode.BODY)
                                .eventTime(LocalDateTime.of(2026, 6, 26, 10, 1))
                                .isAbnormal(true)
                                .severity(Severity.CRITICAL)
                                .riskScore(100.0)
                                .analyzedAt(LocalDateTime.of(2026, 6, 26, 10, 1))
                                .build()
                ));

        var response = service.findCarCompletion(700L);

        assertThat(response.status()).isEqualTo("ABNORMAL");
        assertThat(response.finalCompleted()).isFalse();
    }

    private ManufacturingAnalysisResult result(
            String analysisId,
            String eventId,
            ProcessCode processCode
    ) {
        return ManufacturingAnalysisResult.builder()
                .analysisId(analysisId)
                .eventId(eventId)
                .carMasterId(700L)
                .equipmentId(6L)
                .processCode(processCode)
                .eventTime(LocalDateTime.of(2026, 6, 26, 10, processCode.ordinal()))
                .isAbnormal(false)
                .severity(Severity.NORMAL)
                .riskScore(0.0)
                .analyzedAt(LocalDateTime.of(2026, 6, 26, 10, processCode.ordinal()))
                .build();
    }
}

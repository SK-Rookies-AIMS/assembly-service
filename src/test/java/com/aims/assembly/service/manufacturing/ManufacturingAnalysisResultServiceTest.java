package com.aims.assembly.service.manufacturing;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import com.aims.assembly.domain.assembly.AssemblyAnalysisResult;
import com.aims.assembly.domain.body.BodyAnalysisResult;
import com.aims.assembly.domain.enums.AnalysisStatus;
import com.aims.assembly.domain.enums.DispatchStatus;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.domain.paint.PaintAnalysisResult;
import com.aims.assembly.domain.press.PressAnalysisResult;
import com.aims.assembly.kafka.ManufacturingEventAnalyzer;
import com.aims.assembly.kafka.model.ManufacturingAnalysisEvent;
import com.aims.assembly.kafka.model.ManufacturingRawEvent;
import com.aims.assembly.repository.analysis.AssemblyAnalysisResultRepository;
import com.aims.assembly.repository.analysis.BodyAnalysisResultRepository;
import com.aims.assembly.repository.analysis.ManufacturingAnalysisResultRepository;
import com.aims.assembly.repository.analysis.PaintAnalysisResultRepository;
import com.aims.assembly.repository.analysis.PressAnalysisResultRepository;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository.StoredManufacturingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManufacturingAnalysisResultServiceTest {
    private final ManufacturingAnalysisResultRepository resultRepository =
            mock(ManufacturingAnalysisResultRepository.class);
    private final ManufacturingEventJsonRepository eventRepository =
            mock(ManufacturingEventJsonRepository.class);
    private final PressAnalysisResultRepository pressRepository =
            mock(PressAnalysisResultRepository.class);
    private final BodyAnalysisResultRepository bodyRepository =
            mock(BodyAnalysisResultRepository.class);
    private final PaintAnalysisResultRepository paintRepository =
            mock(PaintAnalysisResultRepository.class);
    private final AssemblyAnalysisResultRepository assemblyRepository =
            mock(AssemblyAnalysisResultRepository.class);
    private ManufacturingAnalysisResultService service;

    @BeforeEach
    void setUp() {
        service = new ManufacturingAnalysisResultService(
                resultRepository,
                eventRepository,
                pressRepository,
                bodyRepository,
                paintRepository,
                assemblyRepository,
                mock(ManufacturingEventAnalyzer.class),
                new ObjectMapper()
        );
        when(resultRepository.saveAndFlush(any(ManufacturingAnalysisResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.findByEventId(any())).thenReturn(Optional.empty());
    }

    @Test
    void assemblyDetailUsesStoredKafkaRawEventJsonShape() {
        ManufacturingRawEvent raw = rawEvent(ProcessCode.ASSEMBLY, "EVT-20260601-000400", Map.of(
                "processData", Map.of(
                        "assembly", Map.of(
                                "actualSequence", "P03>PA01>B02>A02",
                                "expectedSequence", "P03>B02>PA01>A02",
                                "missingPartCount", 1,
                                "sequenceErrorCount", 1,
                                "fasteningErrorCount", 1
                        )
                )
        ));

        service.save(raw, analysisEvent(raw));

        ArgumentCaptor<AssemblyAnalysisResult> captor =
                ArgumentCaptor.forClass(AssemblyAnalysisResult.class);
        verify(assemblyRepository).save(captor.capture());
        AssemblyAnalysisResult detail = captor.getValue();
        assertThat(detail.getExpectedSequence()).isEqualTo("P03>B02>PA01>A02");
        assertThat(detail.getActualSequence()).isEqualTo("P03>PA01>B02>A02");
        assertThat(detail.getSequenceErrorCount()).isEqualTo(1);
        assertThat(detail.getMissingPartCount()).isEqualTo(1);
        assertThat(detail.getFasteningErrorCount()).isEqualTo(1);
    }

    @Test
    void assemblyDetailUnwrapsKafkaEnvelopeEventJsonBeforeExtractingProcessData() {
        ManufacturingRawEvent raw = rawEvent(ProcessCode.ASSEMBLY, "EVT-WRAPPED", Map.of(
                "eventJson", Map.of(
                        "processData", Map.of(
                                "assembly", Map.of(
                                        "actualSequence", "P03>PA01>B02>A02",
                                        "expectedSequence", "P03>B02>PA01>A02",
                                        "missingPartCount", 1,
                                        "sequenceErrorCount", 1,
                                        "fasteningErrorCount", 1
                                )
                        )
                )
        ));

        service.save(raw, analysisEvent(raw));

        ArgumentCaptor<AssemblyAnalysisResult> captor =
                ArgumentCaptor.forClass(AssemblyAnalysisResult.class);
        verify(assemblyRepository).save(captor.capture());
        assertThat(captor.getValue().getExpectedSequence()).isEqualTo("P03>B02>PA01>A02");
        assertThat(captor.getValue().getActualSequence()).isEqualTo("P03>PA01>B02>A02");
        assertThat(captor.getValue().getSequenceErrorCount()).isEqualTo(1);
        assertThat(captor.getValue().getMissingPartCount()).isEqualTo(1);
        assertThat(captor.getValue().getFasteningErrorCount()).isEqualTo(1);
    }

    @Test
    void processSpecificDetailsReadRequestedFields() {
        ManufacturingRawEvent press = rawEvent(ProcessCode.PRESS, "EVT-PRESS", Map.of(
                "processData", Map.of("press", Map.of("timestampDelaySec", 3.0))
        ));
        service.save(press, analysisEvent(press));
        ArgumentCaptor<PressAnalysisResult> pressCaptor =
                ArgumentCaptor.forClass(PressAnalysisResult.class);
        verify(pressRepository).save(pressCaptor.capture());
        assertThat(pressCaptor.getValue().getTimestampDelaySec()).isEqualTo(3.0);

        ManufacturingRawEvent body = rawEvent(ProcessCode.BODY, "EVT-BODY", Map.of(
                "processData", Map.of("body", Map.of(
                        "robotOperationMode", "AUTO",
                        "frequencyBands", Map.of("100Hz", 0.2, "200Hz", 0.4)
                ))
        ));
        service.save(body, analysisEvent(body));
        ArgumentCaptor<BodyAnalysisResult> bodyCaptor =
                ArgumentCaptor.forClass(BodyAnalysisResult.class);
        verify(bodyRepository).save(bodyCaptor.capture());
        assertThat(bodyCaptor.getValue().getRobotOperationMode()).isEqualTo("AUTO");
        assertThat(bodyCaptor.getValue().getFrequencyBandsJson()).contains("100Hz").contains("200Hz");

        ManufacturingRawEvent paint = rawEvent(ProcessCode.PAINT, "EVT-PAINT", Map.of(
                "processData", Map.of("paint", Map.of(
                        "imagePosition", "LEFT",
                        "thicknessValue", 116.0
                ))
        ));
        service.save(paint, analysisEvent(paint));
        ArgumentCaptor<PaintAnalysisResult> paintCaptor =
                ArgumentCaptor.forClass(PaintAnalysisResult.class);
        verify(paintRepository).save(paintCaptor.capture());
        assertThat(paintCaptor.getValue().getImagePosition()).isEqualTo("LEFT");
        assertThat(paintCaptor.getValue().getThicknessValue()).isEqualTo(116.0);
    }

    @Test
    void processSpecificDetailsPreferStoredEventJsonWhenRawEventJsonIsPartial() {
        ManufacturingRawEvent pressRaw = rawEvent(ProcessCode.PRESS, "EVT-STORED-PRESS", Map.of());
        when(eventRepository.findByEventId("EVT-STORED-PRESS")).thenReturn(Optional.of(storedEvent(
                rawEvent(ProcessCode.PRESS, "EVT-STORED-PRESS", Map.of(
                        "processData", Map.of("press", Map.of(
                                "targetCycleTimeSec", 40.0,
                                "timestampDelaySec", 0.0
                        )),
                        "processMetrics", Map.of("cycleTimeSec", 40.0)
                ))
        )));
        service.save(pressRaw, analysisEvent(pressRaw));
        ArgumentCaptor<PressAnalysisResult> pressCaptor =
                ArgumentCaptor.forClass(PressAnalysisResult.class);
        verify(pressRepository).save(pressCaptor.capture());
        assertThat(pressCaptor.getValue().getTimestampDelaySec()).isEqualTo(0.0);
        assertThat(pressCaptor.getValue().getCycleTimeGapSec()).isEqualTo(0.0);

        ManufacturingRawEvent bodyRaw = rawEvent(ProcessCode.BODY, "EVT-STORED-BODY", Map.of());
        when(eventRepository.findByEventId("EVT-STORED-BODY")).thenReturn(Optional.of(storedEvent(
                rawEvent(ProcessCode.BODY, "EVT-STORED-BODY", Map.of(
                        "processData", Map.of("body", Map.of(
                                "robotOperationMode", "AUTO",
                                "frequencyBands", Map.of("freq_0_100_hz", 0.001, "freq_101_200_hz", 0.002)
                        ))
                ))
        )));
        service.save(bodyRaw, analysisEvent(bodyRaw));
        ArgumentCaptor<BodyAnalysisResult> bodyCaptor =
                ArgumentCaptor.forClass(BodyAnalysisResult.class);
        verify(bodyRepository).save(bodyCaptor.capture());
        assertThat(bodyCaptor.getValue().getRobotOperationMode()).isEqualTo("AUTO");
        assertThat(bodyCaptor.getValue().getFrequencyBandsJson())
                .contains("freq_0_100_hz")
                .contains("freq_101_200_hz");

        ManufacturingRawEvent paintRaw = rawEvent(ProcessCode.PAINT, "EVT-STORED-PAINT", Map.of());
        when(eventRepository.findByEventId("EVT-STORED-PAINT")).thenReturn(Optional.of(storedEvent(
                rawEvent(ProcessCode.PAINT, "EVT-STORED-PAINT", Map.of(
                        "processData", Map.of("paint", Map.of(
                                "imagePosition", "LEFT",
                                "thicknessValue", 116.0
                        ))
                ))
        )));
        service.save(paintRaw, analysisEvent(paintRaw));
        ArgumentCaptor<PaintAnalysisResult> paintCaptor =
                ArgumentCaptor.forClass(PaintAnalysisResult.class);
        verify(paintRepository).save(paintCaptor.capture());
        assertThat(paintCaptor.getValue().getImagePosition()).isEqualTo("LEFT");
        assertThat(paintCaptor.getValue().getThicknessValue()).isEqualTo(116.0);

        ManufacturingRawEvent assemblyRaw = rawEvent(ProcessCode.ASSEMBLY, "EVT-STORED-ASSEMBLY", Map.of());
        when(eventRepository.findByEventId("EVT-STORED-ASSEMBLY")).thenReturn(Optional.of(storedEvent(
                rawEvent(ProcessCode.ASSEMBLY, "EVT-STORED-ASSEMBLY", Map.of(
                        "processData", Map.of("assembly", Map.of(
                                "expectedSequence", "P03>B05>PA04>A04",
                                "actualSequence", "P03>B05>PA04>A04",
                                "sequenceErrorCount", 0,
                                "missingPartCount", 0,
                                "fasteningErrorCount", 0
                        ))
                ))
        )));
        service.save(assemblyRaw, analysisEvent(assemblyRaw));
        ArgumentCaptor<AssemblyAnalysisResult> assemblyCaptor =
                ArgumentCaptor.forClass(AssemblyAnalysisResult.class);
        verify(assemblyRepository).save(assemblyCaptor.capture());
        assertThat(assemblyCaptor.getValue().getExpectedSequence()).isEqualTo("P03>B05>PA04>A04");
        assertThat(assemblyCaptor.getValue().getActualSequence()).isEqualTo("P03>B05>PA04>A04");
        assertThat(assemblyCaptor.getValue().getSequenceErrorCount()).isZero();
        assertThat(assemblyCaptor.getValue().getMissingPartCount()).isZero();
        assertThat(assemblyCaptor.getValue().getFasteningErrorCount()).isZero();
    }

    private ManufacturingRawEvent rawEvent(ProcessCode processCode, String eventId, Map<String, Object> json) {
        return new ManufacturingRawEvent(
                0L,
                eventId,
                LocalDateTime.of(2026, 6, 1, 23, 50, 51),
                100L,
                200L,
                processCode,
                "EQ-100",
                "ROBOT",
                "RUNNING",
                "PROCESS_STATUS",
                json
        );
    }

    private StoredManufacturingEvent storedEvent(ManufacturingRawEvent raw) {
        return new StoredManufacturingEvent(
                raw,
                "{}",
                "CAR",
                DispatchStatus.SENT,
                AnalysisStatus.NORMAL,
                true,
                0L,
                null
        );
    }

    private ManufacturingAnalysisEvent analysisEvent(ManufacturingRawEvent raw) {
        return new ManufacturingAnalysisEvent(
                "ANL-" + raw.eventId(),
                raw.eventId(),
                raw.eventTime(),
                raw.eventTime(),
                "FAC",
                "LINE",
                raw.processCode(),
                raw.equipmentCode(),
                "EQ-NAME",
                "ROBOT",
                "PROD",
                "CAR",
                raw.carMasterId(),
                "PROCESS_RISK_ANALYSIS",
                new ManufacturingAnalysisEvent.RiskScores(
                        0.0, 0.0, 0.0, 0.0,
                        new ManufacturingAnalysisEvent.ProcessRisk(0.0, null, null, null)
                ),
                100.0,
                "LOW",
                new ManufacturingAnalysisEvent.AnalysisResult(false, false, false, false, false),
                new ManufacturingAnalysisEvent.Reason("Normal", Collections.emptyList()),
                new ManufacturingAnalysisEvent.Recommendation("ACTION", "Message")
        );
    }
}

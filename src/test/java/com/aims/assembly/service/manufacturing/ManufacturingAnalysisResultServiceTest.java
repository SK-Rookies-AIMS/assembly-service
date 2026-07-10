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
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
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
                new ManufacturingEventAnalyzer(),
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
        service.save(press, analysisEvent(press, press.eventTime().plusSeconds(3)));
        ArgumentCaptor<PressAnalysisResult> pressCaptor =
                ArgumentCaptor.forClass(PressAnalysisResult.class);
        verify(pressRepository).save(pressCaptor.capture());
        assertThat(pressCaptor.getValue().getTargetCycleTimeSec()).isEqualTo(40.0);
        assertThat(pressCaptor.getValue().getActualCycleTimeSec()).isNull();
        assertThat(pressCaptor.getValue().getCycleTimeGapSec()).isNull();
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

    @Test
    void pressDetailUnwrapsStringEventJsonAndStoresNonNullNumbers() {
        ManufacturingRawEvent press = rawEvent(ProcessCode.PRESS, "EVT-PRESS-STRING", Map.of(
                "eventJson",
                """
                {
                  "processData": {
                    "press": {
                      "countIncreaseYn": true,
                      "targetCycleTimeSec": 12.0,
                      "timestampDelaySec": 4.2
                    }
                  },
                  "processMetrics": {
                    "cycleTimeSec": 14.6
                  }
                }
                """
        ));

        service.save(press, analysisEvent(press, press.eventTime().plusSeconds(4)));

        ArgumentCaptor<PressAnalysisResult> captor =
                ArgumentCaptor.forClass(PressAnalysisResult.class);
        verify(pressRepository).save(captor.capture());
        PressAnalysisResult detail = captor.getValue();
        assertThat(detail.getCountIncreaseYn()).isTrue();
        assertThat(detail.getTargetCycleTimeSec()).isEqualTo(12.0);
        assertThat(detail.getActualCycleTimeSec()).isEqualTo(14.6);
        assertThat(detail.getCycleTimeGapSec()).isCloseTo(2.6, within(0.0001));
        assertThat(detail.getTimestampDelaySec()).isEqualTo(4.0);
    }

    @Test
    void bodyDetailUsesFrequencyBandsForPeakValue() {
        ManufacturingRawEvent body = rawEvent(ProcessCode.BODY, "EVT-BODY-PEAK", Map.of(
                "sensor", Map.of(
                        "robotArmVibration", Map.of(
                                "vibrationScore", 0.27,
                                "vibrationPeak", 0.001193284
                        )
                ),
                "processData", Map.of(
                        "body", Map.of(
                                "robotMotionStatus", "NORMAL",
                                "robotOperationMode", "AUTO",
                                "frequencyPeakBand", "501_600_HZ",
                                "frequencyBands", Map.of(
                                        "freq_0_100_hz", 0.001193284,
                                        "freq_501_600_hz", 0.002907113
                                )
                        )
                )
        ));

        service.save(body, analysisEvent(body));

        ArgumentCaptor<BodyAnalysisResult> captor = ArgumentCaptor.forClass(BodyAnalysisResult.class);
        verify(bodyRepository).save(captor.capture());
        BodyAnalysisResult detail = captor.getValue();
        assertThat(detail.getRobotVibrationScore()).isEqualTo(0.27);
        assertThat(detail.getFrequencyPeakBand()).isEqualTo("501_600_HZ");
        assertThat(detail.getFrequencyPeakValue()).isEqualTo(0.002907113);
        assertThat(detail.getFrequencyBandsJson()).contains("freq_501_600_hz");
    }

    @Test
    void pressAndBodyDetailsUseAnalyzerFallbackWhenDetailPayloadIsMissing() {
        ManufacturingRawEvent press = rawEvent(ProcessCode.PRESS, "EVT-PRESS-FALLBACK", Map.of(
                "processMetrics", Map.of(
                        "cycleTimeSec", 47.5,
                        "stationDelaySec", 7.5
                ),
                "sensor", Map.of(
                        "current", Map.of("rmsAmpere", 2.0)
                )
        ));

        service.save(press, analysisEvent(press));

        ArgumentCaptor<PressAnalysisResult> pressCaptor =
                ArgumentCaptor.forClass(PressAnalysisResult.class);
        verify(pressRepository).save(pressCaptor.capture());
        PressAnalysisResult pressDetail = pressCaptor.getValue();
        assertThat(pressDetail.getCountIncreaseYn()).isFalse();
        assertThat(pressDetail.getTargetCycleTimeSec()).isEqualTo(40.0);
        assertThat(pressDetail.getActualCycleTimeSec()).isEqualTo(47.5);
        assertThat(pressDetail.getCycleTimeGapSec()).isEqualTo(7.5);
        assertThat(pressDetail.getTimestampDelaySec()).isEqualTo(0.0);

        ManufacturingRawEvent body = rawEvent(ProcessCode.BODY, "EVT-BODY-FALLBACK", Map.of(
                "sensor", Map.of(
                        "robotArmVibration", Map.of(
                                "vibrationScore", 0.31,
                                "vibrationPeak", 0.004
                        )
                )
        ));

        service.save(body, analysisEvent(body));

        ArgumentCaptor<BodyAnalysisResult> bodyCaptor =
                ArgumentCaptor.forClass(BodyAnalysisResult.class);
        verify(bodyRepository).save(bodyCaptor.capture());
        BodyAnalysisResult bodyDetail = bodyCaptor.getValue();
        assertThat(bodyDetail.getRobotMotionStatus()).isEqualTo("NORMAL");
        assertThat(bodyDetail.getRobotOperationMode()).isEqualTo("NORMAL");
        assertThat(bodyDetail.getRobotVibrationScore()).isEqualTo(0.31);
        assertThat(bodyDetail.getFrequencyPeakBand()).isEqualTo("UNKNOWN");
        assertThat(bodyDetail.getFrequencyPeakValue()).isEqualTo(0.004);
        assertThat(bodyDetail.getFrequencyBandsJson()).isEqualTo("{}");
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
        return analysisEvent(raw, raw.eventTime());
    }

    private ManufacturingAnalysisEvent analysisEvent(ManufacturingRawEvent raw, LocalDateTime analyzedAt) {
        return new ManufacturingAnalysisEvent(
                "ANL-" + raw.eventId(),
                raw.eventId(),
                offset(raw.eventTime()),
                offset(analyzedAt),
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

    @Test
    void test1_rawActualSequenceEqualsExpectedSequenceAndAbnormal() {
        ManufacturingRawEvent raw = rawEvent(ProcessCode.ASSEMBLY, "EVT-TEST-1", Map.of(
                "processData", Map.of(
                        "assembly", Map.of(
                                "expectedSequence", "P01>B03>PA02>A03",
                                "actualSequence", "P01>B03>PA02>A03"
                        )
                )
        ));
        ManufacturingAnalysisEvent analysis = new ManufacturingAnalysisEvent(
                "ANL-1", "EVT-TEST-1", offset(raw.eventTime()), offset(raw.eventTime()),
                "FAC", "LINE", ProcessCode.ASSEMBLY, "EQ-100", "EQ-NAME", "ROBOT", "PROD", "CAR", raw.carMasterId(),
                "PROCESS_RISK_ANALYSIS",
                new ManufacturingAnalysisEvent.RiskScores(0.0, 0.0, 0.0, 0.0, new ManufacturingAnalysisEvent.ProcessRisk(0.0, null, null, null)),
                90.0, "HIGH",
                new ManufacturingAnalysisEvent.AnalysisResult(true, false, false, false, false),
                new ManufacturingAnalysisEvent.Reason("Abnormal", Collections.emptyList()),
                new ManufacturingAnalysisEvent.Recommendation("ACTION", "Message")
        );

        service.save(raw, analysis);

        ArgumentCaptor<AssemblyAnalysisResult> captor = ArgumentCaptor.forClass(AssemblyAnalysisResult.class);
        verify(assemblyRepository).save(captor.capture());
        AssemblyAnalysisResult result = captor.getValue();
        assertThat(result.getExpectedSequence()).isEqualTo("P01>B03>PA02>A03");
        assertThat(result.getActualSequence()).isEqualTo("P01>B03>PA02>A03");
        // 수정 후: sequence가 동일하면 abnormal 여부와 무관하게 sequenceErrorCount = 0
        assertThat(result.getSequenceErrorCount()).isZero();
    }

    private OffsetDateTime offset(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    @Test
    void test2_rawActualSequenceDiffersFromExpectedSequence() {
        ManufacturingRawEvent raw = rawEvent(ProcessCode.ASSEMBLY, "EVT-TEST-2", Map.of(
                "processData", Map.of(
                        "assembly", Map.of(
                                "expectedSequence", "P01>B03>PA02>A03",
                                "actualSequence", "P01>B04>PA02>A03"
                        )
                )
        ));

        service.save(raw, analysisEvent(raw));

        ArgumentCaptor<AssemblyAnalysisResult> captor = ArgumentCaptor.forClass(AssemblyAnalysisResult.class);
        verify(assemblyRepository).save(captor.capture());
        AssemblyAnalysisResult result = captor.getValue();
        assertThat(result.getExpectedSequence()).isEqualTo("P01>B03>PA02>A03");
        assertThat(result.getActualSequence()).isEqualTo("P01>B04>PA02>A03");
    }

    @Test
    void test3_rawActualSequenceMissing() {
        ManufacturingRawEvent raw = rawEvent(ProcessCode.ASSEMBLY, "EVT-TEST-3", Map.of(
                "processData", Map.of(
                        "assembly", Map.of(
                                "expectedSequence", "P01>B03>PA02>A03"
                        )
                )
        ));

        service.save(raw, analysisEvent(raw));

        ArgumentCaptor<AssemblyAnalysisResult> captor = ArgumentCaptor.forClass(AssemblyAnalysisResult.class);
        verify(assemblyRepository).save(captor.capture());
        AssemblyAnalysisResult result = captor.getValue();
        assertThat(result.getExpectedSequence()).isEqualTo("P01>B03>PA02>A03");
        assertThat(result.getActualSequence()).isNull();
    }

    @Test
    void test4_rawExpectedSequenceMissing() {
        ManufacturingRawEvent raw = rawEvent(ProcessCode.ASSEMBLY, "EVT-TEST-4", Map.of(
                "processData", Map.of(
                        "assembly", Map.of(
                                "actualSequence", "P01>B04>PA02>A03"
                        )
                )
        ));

        service.save(raw, analysisEvent(raw));

        ArgumentCaptor<AssemblyAnalysisResult> captor = ArgumentCaptor.forClass(AssemblyAnalysisResult.class);
        verify(assemblyRepository).save(captor.capture());
        AssemblyAnalysisResult result = captor.getValue();
        assertThat(result.getExpectedSequence()).isNull();
        assertThat(result.getActualSequence()).isEqualTo("P01>B04>PA02>A03");
    }

    /**
     * [핵심 버그 재현]
     * raw: expectedSequence == actualSequence, 모든 count=0
     * equipmentStatus=WARNING → analysis.isAbnormal=true
     * 수정 전: sequenceErrorCount가 합성값(2 등)으로 저장되어 화면에 불일치 발생
     * 수정 후: sequence가 동일 → sequenceErrorCount=0 으로 저장
     */
    @Test
    void sequenceErrorCount_mustBeZero_whenSequencesMatch_evenIfEquipmentFaultMakesAbnormal() {
        ManufacturingRawEvent raw = rawEvent(ProcessCode.ASSEMBLY, "EVT-20260602-000092", Map.of(
                "processData", Map.of(
                        "assembly", Map.of(
                                "expectedSequence", "P05>B05>PA02>A03",
                                "actualSequence", "P05>B05>PA02>A03",
                                "sequenceErrorCount", 0,
                                "missingPartCount", 0,
                                "fasteningErrorCount", 0
                        )
                ),
                "equipmentStatus", Map.of("operationStatus", "WARNING")
        ));
        // equipmentFault=true로 인해 isAbnormal=true
        ManufacturingAnalysisEvent analysis = new ManufacturingAnalysisEvent(
                "ANL-92", "EVT-20260602-000092", offset(raw.eventTime()), offset(raw.eventTime()),
                "FAC", "LINE", ProcessCode.ASSEMBLY, "EQ-200", "EQ-NAME", "ROBOT", "PROD", "CAR", 123L,
                "PROCESS_RISK_ANALYSIS",
                new ManufacturingAnalysisEvent.RiskScores(67.0, 0.0, 0.0, 0.0,
                        new ManufacturingAnalysisEvent.ProcessRisk(null, null, null, 67.0)),
                67.0, "WARNING",
                new ManufacturingAnalysisEvent.AnalysisResult(true, false, false, true, false),
                new ManufacturingAnalysisEvent.Reason("설비 이상", Collections.emptyList()),
                new ManufacturingAnalysisEvent.Recommendation("ACTION", "Message")
        );

        service.save(raw, analysis);

        ArgumentCaptor<AssemblyAnalysisResult> captor = ArgumentCaptor.forClass(AssemblyAnalysisResult.class);
        verify(assemblyRepository).save(captor.capture());
        AssemblyAnalysisResult result = captor.getValue();
        assertThat(result.getExpectedSequence()).isEqualTo("P05>B05>PA02>A03");
        assertThat(result.getActualSequence()).isEqualTo("P05>B05>PA02>A03");
        assertThat(result.getSequenceErrorCount())
                .as("expected == actual 이면 sequenceErrorCount=0 이어야 한다 (equipmentFault여도)")
                .isEqualTo(0);
    }

    /**
     * sequence가 다를 때 count=0이면 보정값(>0)을 사용해야 한다.
     */
    @Test
    void sequenceErrorCount_mustBePositive_whenSequencesDiffer_andRawCountIsZero() {
        ManufacturingRawEvent raw = rawEvent(ProcessCode.ASSEMBLY, "EVT-DIFF-SEQ", Map.of(
                "processData", Map.of(
                        "assembly", Map.of(
                                "expectedSequence", "P01>B04>PA04>A03",
                                "actualSequence", "P01>PA04>B04>A03",
                                "sequenceErrorCount", 0,
                                "missingPartCount", 0,
                                "fasteningErrorCount", 0
                        )
                )
        ));
        ManufacturingAnalysisEvent analysis = new ManufacturingAnalysisEvent(
                "ANL-DIFF", "EVT-DIFF-SEQ", offset(raw.eventTime()), offset(raw.eventTime()),
                "FAC", "LINE", ProcessCode.ASSEMBLY, "EQ-300", "EQ-NAME", "ROBOT", "PROD", "CAR", 999L,
                "PROCESS_RISK_ANALYSIS",
                new ManufacturingAnalysisEvent.RiskScores(65.0, 0.0, 0.0, 0.0,
                        new ManufacturingAnalysisEvent.ProcessRisk(null, null, null, 65.0)),
                65.0, "WARNING",
                new ManufacturingAnalysisEvent.AnalysisResult(true, false, false, false, true),
                new ManufacturingAnalysisEvent.Reason("순서 오류", Collections.emptyList()),
                new ManufacturingAnalysisEvent.Recommendation("ACTION", "Message")
        );

        service.save(raw, analysis);

        ArgumentCaptor<AssemblyAnalysisResult> captor = ArgumentCaptor.forClass(AssemblyAnalysisResult.class);
        verify(assemblyRepository).save(captor.capture());
        AssemblyAnalysisResult result = captor.getValue();
        assertThat(result.getExpectedSequence()).isEqualTo("P01>B04>PA04>A03");
        assertThat(result.getActualSequence()).isEqualTo("P01>PA04>B04>A03");
        assertThat(result.getSequenceErrorCount())
                .as("expected != actual 이면 sequenceErrorCount > 0 이어야 한다")
                .isGreaterThan(0);
    }

    /**
     * raw count가 양수이고 sequence도 다를 때: raw count를 그대로 사용
     */
    @Test
    void sequenceErrorCount_mustUseRawValue_whenSequencesDifferAndRawCountIsPositive() {
        ManufacturingRawEvent raw = rawEvent(ProcessCode.ASSEMBLY, "EVT-RAW-CNT", Map.of(
                "processData", Map.of(
                        "assembly", Map.of(
                                "expectedSequence", "P01>B04>PA04>A03",
                                "actualSequence", "P01>PA04>B04>A03",
                                "sequenceErrorCount", 1,
                                "missingPartCount", 0,
                                "fasteningErrorCount", 1
                        )
                )
        ));

        service.save(raw, analysisEvent(raw));

        ArgumentCaptor<AssemblyAnalysisResult> captor = ArgumentCaptor.forClass(AssemblyAnalysisResult.class);
        verify(assemblyRepository).save(captor.capture());
        AssemblyAnalysisResult result = captor.getValue();
        assertThat(result.getSequenceErrorCount())
                .as("raw sequenceErrorCount=1, sequence 다름 → 원본값 1 유지")
                .isEqualTo(1);
        assertThat(result.getFasteningErrorCount())
                .as("raw fasteningErrorCount=1 그대로")
                .isEqualTo(1);
    }
}

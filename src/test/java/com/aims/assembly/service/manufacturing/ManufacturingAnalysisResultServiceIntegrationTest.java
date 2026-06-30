package com.aims.assembly.service.manufacturing;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import com.aims.assembly.domain.assembly.AssemblyAnalysisResult;
import com.aims.assembly.domain.body.BodyAnalysisResult;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.domain.paint.PaintAnalysisResult;
import com.aims.assembly.domain.press.PressAnalysisResult;
import com.aims.assembly.kafka.model.ManufacturingAnalysisEvent;
import com.aims.assembly.kafka.model.ManufacturingRawEvent;
import com.aims.assembly.repository.analysis.AssemblyAnalysisResultRepository;
import com.aims.assembly.repository.analysis.BodyAnalysisResultRepository;
import com.aims.assembly.repository.analysis.ManufacturingAnalysisResultRepository;
import com.aims.assembly.repository.analysis.PaintAnalysisResultRepository;
import com.aims.assembly.repository.analysis.PressAnalysisResultRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.kafka.bootstrap-servers=localhost:9092",
        "app.kafka.security-protocol=PLAINTEXT",
        "app.kafka.listeners-enabled=false"
})
@Transactional
class ManufacturingAnalysisResultServiceIntegrationTest {

    @Autowired
    private ManufacturingAnalysisResultService service;

    @Autowired
    private ManufacturingAnalysisResultRepository resultRepository;

    @Autowired
    private PressAnalysisResultRepository pressRepository;

    @Autowired
    private BodyAnalysisResultRepository bodyRepository;

    @Autowired
    private PaintAnalysisResultRepository paintRepository;

    @Autowired
    private AssemblyAnalysisResultRepository assemblyRepository;

    @Test
    void pressDetailMappingTest() {
        // Given
        String eventId = "EVT-TEST-PRESS-INTEG";
        ManufacturingRawEvent raw = rawEvent(ProcessCode.PRESS, eventId, Map.of(
                "processData", Map.of(
                        "press", Map.of(
                                "countIncreaseYn", true,
                                "targetCycleTimeSec", 40.0,
                                "timestampDelaySec", 3.0
                        )
                ),
                "processMetrics", Map.of(
                        "cycleTimeSec", 45.0
                )
        ));
        ManufacturingAnalysisEvent analysis = analysisEvent(raw);

        // When
        service.save(raw, analysis);

        // Then
        ManufacturingAnalysisResult savedResult = resultRepository.findAll().stream()
                .filter(r -> eventId.equals(r.getEventId()))
                .findFirst()
                .orElseThrow();

        PressAnalysisResult detail = pressRepository.findById(savedResult.getId()).orElseThrow();
        assertThat(detail.getCountIncreaseYn()).isTrue();
        assertThat(detail.getTargetCycleTimeSec()).isEqualTo(40.0);
        assertThat(detail.getActualCycleTimeSec()).isEqualTo(45.0);
        assertThat(detail.getCycleTimeGapSec()).isEqualTo(5.0);
        assertThat(detail.getTimestampDelaySec()).isEqualTo(3.0);
    }

    @Test
    void bodyDetailMappingTest() {
        // Given
        String eventId = "EVT-TEST-BODY-INTEG";
        ManufacturingRawEvent raw = rawEvent(ProcessCode.BODY, eventId, Map.of(
                "sensor", Map.of(
                        "robotArmVibration", Map.of(
                                "vibrationScore", 0.5,
                                "frequencyHz", 150.0
                        )
                ),
                "processData", Map.of(
                        "body", Map.of(
                                "frequencyPeakBand", "MID",
                                "robotOperationMode", "AUTO",
                                "robotMotionStatus", "NORMAL",
                                "frequencyBands", Map.of("100Hz", 0.2, "200Hz", 0.4)
                        )
                )
        ));
        ManufacturingAnalysisEvent analysis = analysisEvent(raw);

        // When
        service.save(raw, analysis);

        // Then
        ManufacturingAnalysisResult savedResult = resultRepository.findAll().stream()
                .filter(r -> eventId.equals(r.getEventId()))
                .findFirst()
                .orElseThrow();

        BodyAnalysisResult detail = bodyRepository.findById(savedResult.getId()).orElseThrow();
        assertThat(detail.getRobotOperationMode()).isEqualTo("AUTO");
        assertThat(detail.getRobotMotionStatus()).isEqualTo("NORMAL");
        assertThat(detail.getRobotVibrationScore()).isEqualTo(0.5);
        assertThat(detail.getFrequencyPeakBand()).isEqualTo("MID");
        assertThat(detail.getFrequencyPeakValue()).isEqualTo(150.0);
        assertThat(detail.getFrequencyBandsJson()).contains("100Hz").contains("200Hz");
    }

    @Test
    void paintDetailMappingTest() {
        // Given
        String eventId = "EVT-TEST-PAINT-INTEG";
        ManufacturingRawEvent raw = rawEvent(ProcessCode.PAINT, eventId, Map.of(
                "processData", Map.of(
                        "paint", Map.of(
                                "defectScore", 0.15,
                                "thermalStdTemp", 1.2,
                                "surfaceQualityScore", 95.0,
                                "visionLabel", "NORMAL",
                                "imagePosition", "LEFT",
                                "thicknessValue", 116.0
                        )
                )
        ));
        ManufacturingAnalysisEvent analysis = analysisEvent(raw);

        // When
        service.save(raw, analysis);

        // Then
        ManufacturingAnalysisResult savedResult = resultRepository.findAll().stream()
                .filter(r -> eventId.equals(r.getEventId()))
                .findFirst()
                .orElseThrow();

        PaintAnalysisResult detail = paintRepository.findById(savedResult.getId()).orElseThrow();
        assertThat(detail.getImagePosition()).isEqualTo("LEFT");
        assertThat(detail.getThicknessValue()).isEqualTo(116.0);
        assertThat(detail.getDefectScore()).isEqualTo(0.15);
        assertThat(detail.getThermalStdTemp()).isEqualTo(1.2);
        assertThat(detail.getSurfaceQualityScore()).isEqualTo(95.0);
        assertThat(detail.getVisionLabel()).isEqualTo("NORMAL");
    }

    @Test
    void assemblyDetailMappingTest() {
        // Given
        String eventId = "EVT-TEST-ASSEMBLY-INTEG";
        ManufacturingRawEvent raw = rawEvent(ProcessCode.ASSEMBLY, eventId, Map.of(
                "processData", Map.of(
                        "assembly", Map.of(
                                "expectedSequence", "P02>B01>PA05>A05",
                                "actualSequence", "P02>B01>PA05>A05",
                                "sequenceErrorCount", 1,
                                "missingPartCount", 2,
                                "fasteningErrorCount", 3
                        )
                )
        ));
        ManufacturingAnalysisEvent analysis = analysisEvent(raw);

        // When
        service.save(raw, analysis);

        // Then
        ManufacturingAnalysisResult savedResult = resultRepository.findAll().stream()
                .filter(r -> eventId.equals(r.getEventId()))
                .findFirst()
                .orElseThrow();

        AssemblyAnalysisResult detail = assemblyRepository.findById(savedResult.getId()).orElseThrow();
        assertThat(detail.getExpectedSequence()).isEqualTo("P02>B01>PA05>A05");
        assertThat(detail.getActualSequence()).isEqualTo("P02>B01>PA05>A05");
        assertThat(detail.getSequenceErrorCount()).isEqualTo(1);
        assertThat(detail.getMissingPartCount()).isEqualTo(2);
        assertThat(detail.getFasteningErrorCount()).isEqualTo(3);
    }

    @Test
    void assemblySnakeCaseDetailMappingTest() {
        // Given
        String eventId = "EVT-TEST-ASSEMBLY-SNAKE-INTEG";
        // Simulate event_json payload containing pure snake_case keys
        ManufacturingRawEvent raw = rawEvent(ProcessCode.ASSEMBLY, eventId, Map.of(
                "process_data", Map.of(
                        "assembly", Map.of(
                                "expected_sequence", "P02>B01>PA05>A05",
                                "actual_sequence", "P02>B01>PA05>A06",
                                "sequence_error_count", 1,
                                "missing_part_count", 2,
                                "fastening_error_count", 3
                        )
                )
        ));
        ManufacturingAnalysisEvent analysis = analysisEvent(raw);

        // When
        service.save(raw, analysis);

        // Then
        ManufacturingAnalysisResult savedResult = resultRepository.findAll().stream()
                .filter(r -> eventId.equals(r.getEventId()))
                .findFirst()
                .orElseThrow();

        AssemblyAnalysisResult detail = assemblyRepository.findById(savedResult.getId()).orElseThrow();
        assertThat(detail.getExpectedSequence()).isEqualTo("P02>B01>PA05>A05");
        assertThat(detail.getActualSequence()).isEqualTo("P02>B01>PA05>A06");
        assertThat(detail.getSequenceErrorCount()).isEqualTo(1);
        assertThat(detail.getMissingPartCount()).isEqualTo(2);
        assertThat(detail.getFasteningErrorCount()).isEqualTo(3);
    }

    @Test
    void assemblyCamelCaseDetailMappingTest() {
        // Given
        String eventId = "EVT-20260601-000400";
        // Simulate exact JSON payload supplied by the user
        ManufacturingRawEvent raw = rawEvent(ProcessCode.ASSEMBLY, eventId, Map.of(
                "processData", Map.of(
                        "assembly", Map.of(
                                "expectedSequence", "P03>B02>PA01>A02",
                                "actualSequence", "P03>PA01>B02>A02",
                                "sequenceErrorCount", 1,
                                "missingPartCount", 1,
                                "fasteningErrorCount", 1
                        )
                )
        ));
        ManufacturingAnalysisEvent analysis = analysisEvent(raw);

        // When
        service.save(raw, analysis);

        // Then
        ManufacturingAnalysisResult savedResult = resultRepository.findAll().stream()
                .filter(r -> eventId.equals(r.getEventId()))
                .findFirst()
                .orElseThrow();

        AssemblyAnalysisResult detail = assemblyRepository.findById(savedResult.getId()).orElseThrow();
        assertThat(detail.getExpectedSequence()).isEqualTo("P03>B02>PA01>A02");
        assertThat(detail.getActualSequence()).isEqualTo("P03>PA01>B02>A02");
        assertThat(detail.getSequenceErrorCount()).isEqualTo(1);
        assertThat(detail.getMissingPartCount()).isEqualTo(1);
        assertThat(detail.getFasteningErrorCount()).isEqualTo(1);
    }

    private ManufacturingRawEvent rawEvent(ProcessCode processCode, String eventId, Map<String, Object> json) {
        return new ManufacturingRawEvent(
                0L,
                eventId,
                LocalDateTime.now(),
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

    private ManufacturingAnalysisEvent analysisEvent(ManufacturingRawEvent raw) {
        return new ManufacturingAnalysisEvent(
                "ANL-100",
                raw.eventId(),
                raw.eventTime(),
                LocalDateTime.now(),
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

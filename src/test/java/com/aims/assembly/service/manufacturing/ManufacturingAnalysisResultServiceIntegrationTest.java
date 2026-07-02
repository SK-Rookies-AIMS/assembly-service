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
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.kafka.bootstrap-servers=localhost:9092",
        "app.kafka.security-protocol=PLAINTEXT",
        "app.kafka.listeners-enabled=false",
        "app.kafka.scheduler.enabled=false"
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

    @Autowired
    private ManufacturingEventJsonRepository eventRepository;

    @Autowired
    @Qualifier("mainJdbcTemplate")
    private JdbcTemplate mainJdbcTemplate;

    @Autowired
    private EntityManager entityManager;

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

    @Test
    void assemblyWrappedEventJsonDetailMappingTest() {
        String eventId = "EVT-TEST-ASSEMBLY-WRAPPED-INTEG";
        ManufacturingRawEvent raw = rawEvent(ProcessCode.ASSEMBLY, eventId, Map.of(
                "eventJson", Map.of(
                        "processData", Map.of(
                                "assembly", Map.of(
                                        "expectedSequence", "P03>B02>PA01>A02",
                                        "actualSequence", "P03>PA01>B02>A02",
                                        "sequenceErrorCount", 1,
                                        "missingPartCount", 1,
                                        "fasteningErrorCount", 1
                                )
                        )
                )
        ));
        ManufacturingAnalysisEvent analysis = analysisEvent(raw);

        service.save(raw, analysis);

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

    @Test
    void assemblyDetailMappingFromStoredKafkaRawEventJsonTest() {
        String eventId = "EVT-20260601-000400";
        ManufacturingRawEvent raw = eventRepository.findByEventId(eventId)
                .orElseThrow()
                .payload();
        ManufacturingRawEvent publishedRaw = withEventTime(raw, LocalDateTime.of(2026, 6, 1, 23, 50, 51));
        ManufacturingAnalysisEvent analysis = analysisEvent(publishedRaw);

        Object processData = publishedRaw.eventJson().get("processData");
        assertThat(processData).isInstanceOf(Map.class);
        Object assembly = ((Map<?, ?>) processData).get("assembly");
        assertThat(assembly).isInstanceOf(Map.class);
        Map<?, ?> assemblyMap = (Map<?, ?>) assembly;
        assertThat(assemblyMap.get("expectedSequence")).isEqualTo("P03>B02>PA01>A02");
        assertThat(assemblyMap.get("actualSequence")).isEqualTo("P03>PA01>B02>A02");
        assertThat(assemblyMap.get("sequenceErrorCount")).isEqualTo(1);
        assertThat(assemblyMap.get("missingPartCount")).isEqualTo(1);
        assertThat(assemblyMap.get("fasteningErrorCount")).isEqualTo(1);

        service.save(publishedRaw, analysis);

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

    @Test
    @Commit
    void persistsRequestedColumnsToActualMainDb() {
        cleanupVerificationRows();

        service.save(rawEvent(ProcessCode.ASSEMBLY, "EVT-INTEG-VERIFY-ASSEMBLY", Map.of(
                "processData", Map.of("assembly", Map.of(
                        "expectedSequence", "P02>B01>PA05>A05",
                        "actualSequence", "P02>B01>PA05>A06",
                        "sequenceErrorCount", 1,
                        "missingPartCount", 2,
                        "fasteningErrorCount", 3
                ))
        )), analysisEvent(rawEvent(ProcessCode.ASSEMBLY, "EVT-INTEG-VERIFY-ASSEMBLY", Map.of())));
        service.save(rawEvent(ProcessCode.PAINT, "EVT-INTEG-VERIFY-PAINT", Map.of(
                "processData", Map.of("paint", Map.of(
                        "imagePosition", "LEFT",
                        "thicknessValue", 116.0
                ))
        )), analysisEvent(rawEvent(ProcessCode.PAINT, "EVT-INTEG-VERIFY-PAINT", Map.of())));
        service.save(rawEvent(ProcessCode.BODY, "EVT-INTEG-VERIFY-BODY", Map.of(
                "processData", Map.of("body", Map.of(
                        "robotOperationMode", "AUTO",
                        "frequencyBands", Map.of("100Hz", 0.2, "200Hz", 0.4)
                ))
        )), analysisEvent(rawEvent(ProcessCode.BODY, "EVT-INTEG-VERIFY-BODY", Map.of())));
        service.save(rawEvent(ProcessCode.PRESS, "EVT-INTEG-VERIFY-PRESS", Map.of(
                "processData", Map.of("press", Map.of("timestampDelaySec", 3.0))
        )), analysisEvent(rawEvent(ProcessCode.PRESS, "EVT-INTEG-VERIFY-PRESS", Map.of())));

        entityManager.flush();

        Map<String, Object> assembly = queryDetail("assembly_analysis_result", "EVT-INTEG-VERIFY-ASSEMBLY");
        assertThat(assembly.get("expected_sequence")).isEqualTo("P02>B01>PA05>A05");
        assertThat(assembly.get("actual_sequence")).isEqualTo("P02>B01>PA05>A06");
        assertThat(((Number) assembly.get("sequence_error_count")).intValue()).isEqualTo(1);
        assertThat(((Number) assembly.get("missing_part_count")).intValue()).isEqualTo(2);
        assertThat(((Number) assembly.get("fastening_error_count")).intValue()).isEqualTo(3);

        Map<String, Object> paint = queryDetail("paint_analysis_result", "EVT-INTEG-VERIFY-PAINT");
        assertThat(paint.get("image_position")).isEqualTo("LEFT");
        assertThat(((Number) paint.get("thickness_value")).doubleValue()).isEqualTo(116.0);

        Map<String, Object> body = queryDetail("body_analysis_result", "EVT-INTEG-VERIFY-BODY");
        assertThat(body.get("robot_operation_mode")).isEqualTo("AUTO");
        assertThat(body.get("frequency_bands_json").toString()).contains("100Hz").contains("200Hz");

        Map<String, Object> press = queryDetail("press_analysis_result", "EVT-INTEG-VERIFY-PRESS");
        assertThat(((Number) press.get("target_cycle_time_sec")).doubleValue()).isEqualTo(40.0);
        assertThat(((Number) press.get("actual_cycle_time_sec")).doubleValue()).isEqualTo(43.0);
        assertThat(((Number) press.get("cycle_time_gap_sec")).doubleValue()).isEqualTo(3.0);
        assertThat(((Number) press.get("timestamp_delay_sec")).doubleValue()).isEqualTo(3.0);
    }

    private void cleanupVerificationRows() {
        String eventIdPredicate = "SELECT id FROM manufacturing_analysis_result WHERE event_id LIKE 'EVT-INTEG-VERIFY-%'";
        mainJdbcTemplate.update("DELETE FROM assembly_analysis_result WHERE analysis_result_id IN (" + eventIdPredicate + ")");
        mainJdbcTemplate.update("DELETE FROM paint_analysis_result WHERE analysis_result_id IN (" + eventIdPredicate + ")");
        mainJdbcTemplate.update("DELETE FROM body_analysis_result WHERE analysis_result_id IN (" + eventIdPredicate + ")");
        mainJdbcTemplate.update("DELETE FROM press_analysis_result WHERE analysis_result_id IN (" + eventIdPredicate + ")");
        mainJdbcTemplate.update("DELETE FROM manufacturing_analysis_result WHERE event_id LIKE 'EVT-INTEG-VERIFY-%'");
    }

    private Map<String, Object> queryDetail(String tableName, String eventId) {
        return mainJdbcTemplate.queryForMap("""
                SELECT detail.*
                FROM %s detail
                JOIN manufacturing_analysis_result result
                  ON result.id = detail.analysis_result_id
                WHERE result.event_id = ?
                """.formatted(tableName), eventId);
    }

    private ManufacturingRawEvent withEventTime(ManufacturingRawEvent raw, LocalDateTime eventTime) {
        return new ManufacturingRawEvent(
                raw.id(),
                raw.eventId(),
                eventTime,
                raw.carMasterId(),
                raw.equipmentId(),
                raw.processCode(),
                raw.equipmentCode(),
                raw.equipmentType(),
                raw.equipmentStatus(),
                raw.eventType(),
                raw.eventJson()
        );
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

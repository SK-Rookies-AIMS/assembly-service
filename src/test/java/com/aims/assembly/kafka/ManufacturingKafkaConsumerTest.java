package com.aims.assembly.kafka;

import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.domain.enums.DispatchStatus;
import com.aims.assembly.kafka.model.EquipmentStatusEvent;
import com.aims.assembly.kafka.model.KafkaPublishResult;
import com.aims.assembly.kafka.model.ManufacturingAlertEvent;
import com.aims.assembly.kafka.model.ManufacturingAnalysisEvent;
import com.aims.assembly.kafka.model.ManufacturingRawEvent;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository;
import com.aims.assembly.service.equipment.EquipmentStateService;
import com.aims.assembly.service.manufacturing.ManufacturingAnalysisResultService;
import com.aims.assembly.service.manufacturing.ManufacturingProcessRouter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ManufacturingKafkaConsumerTest {

    @Test
    void rawEquipmentAbnormalPublishesEquipmentStatusAndAlertTopics() {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ManufacturingEventAnalyzer analyzer = mock(ManufacturingEventAnalyzer.class);
        ManufacturingProcessRouter processRouter = mock(ManufacturingProcessRouter.class);
        ManufacturingKafkaProducer producer = mock(ManufacturingKafkaProducer.class);
        KafkaMessageTraceStore traceStore = mock(KafkaMessageTraceStore.class);
        ManufacturingRawEventParser parser = mock(ManufacturingRawEventParser.class);
        EquipmentStateService equipmentStateService = mock(EquipmentStateService.class);
        ManufacturingEventJsonRepository eventRepository = mock(ManufacturingEventJsonRepository.class);
        ManufacturingAnalysisResultService analysisResultService =
                mock(ManufacturingAnalysisResultService.class);
        ManufacturingKafkaConsumer consumer = new ManufacturingKafkaConsumer(
                objectMapper,
                analyzer,
                processRouter,
                producer,
                traceStore,
                parser,
                equipmentStateService,
                eventRepository,
                analysisResultService
        );
        ManufacturingRawEvent raw = new ManufacturingRawEvent(
                1L, "EVT-1", LocalDateTime.of(2026, 6, 26, 16, 28, 11),
                700L, 6L, ProcessCode.BODY, "EQ-BODY-1", "ROBOT",
                "ERROR", "PROCESS_STATUS", Map.of()
        );
        ManufacturingAnalysisEvent analysis = normalAnalysis(raw);
        EquipmentStatusEvent equipmentEvent = new EquipmentStatusEvent(
                "EQEVT-1", raw.eventId(), raw.eventTime(), null, null, raw.processCode(),
                raw.equipmentCode(), null, raw.equipmentType(), "FAULT",
                "CRITICAL", 100.0, 0.0, raw.equipmentId(), "FAULT", "error"
        );
        ManufacturingAlertEvent alert = new ManufacturingAlertEvent(
                "ALT-1", raw.eventId(), null, raw.eventTime(), null, null, raw.processCode(),
                raw.equipmentCode(), null, raw.carMasterId(), raw.equipmentId(),
                "EQUIPMENT_ABNORMAL", "title", "message", "CRITICAL", 100.0,
                "OPEN", true, List.of("error"), "check"
        );
        when(parser.parse("raw-json")).thenReturn(raw);
        when(analyzer.isEquipmentAbnormal(raw)).thenReturn(true);
        when(analyzer.toEquipmentStatusEvent(raw)).thenReturn(equipmentEvent);
        when(analyzer.toEquipmentStatusAlert(raw)).thenReturn(alert);
        when(processRouter.route(raw)).thenReturn(analysis);
        when(producer.sendEquipment(equipmentEvent))
                .thenReturn(CompletableFuture.completedFuture(publishResult()));
        when(producer.sendAlert(alert)).thenReturn(CompletableFuture.completedFuture(publishResult()));
        when(producer.sendAnalysis(analysis)).thenReturn(CompletableFuture.completedFuture(publishResult()));

        consumer.consumeRaw(
                new ConsumerRecord<>("factory.manufacturing.raw", 0, 0L, "700", "raw-json")
        );

        verify(producer).sendEquipment(equipmentEvent);
        verify(producer).sendAlert(alert);
        verify(analysisResultService).save(raw, analysis);
        verify(eventRepository).markAnalysisCompleted(raw.eventId(), false);
        verify(eventRepository).releaseNextProcessByCurrentRowId(null);
        verify(eventRepository, never()).blockFollowingProcesses(any(), anyList());
    }

    @Test
    void pressNormalCompletionReleasesBodyInDatabaseUsingStoredCarMasterId() {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource("jdbc:h2:mem:consumerflow;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createTables(jdbc);
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 9, 0);
        insert(jdbc, 37, "EVT-37", now.minusMinutes(3), 33L, "PRESS",
                "SENT", "NOT_ANALYZED", true);
        insert(jdbc, 38, "EVT-38", now.minusMinutes(2), 33L, "BODY",
                "PENDING", "NOT_ANALYZED", false);
        insert(jdbc, 39, "EVT-39", now.minusMinutes(1), 33L, "PAINT",
                "PENDING", "NOT_ANALYZED", false);
        insert(jdbc, 40, "EVT-40", now, 33L, "ASSEMBLY",
                "PENDING", "NOT_ANALYZED", false);

        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ManufacturingEventAnalyzer analyzer = mock(ManufacturingEventAnalyzer.class);
        ManufacturingProcessRouter processRouter = mock(ManufacturingProcessRouter.class);
        ManufacturingKafkaProducer producer = mock(ManufacturingKafkaProducer.class);
        KafkaMessageTraceStore traceStore = mock(KafkaMessageTraceStore.class);
        ManufacturingRawEventParser parser = mock(ManufacturingRawEventParser.class);
        EquipmentStateService equipmentStateService = mock(EquipmentStateService.class);
        ManufacturingEventJsonRepository eventRepository = new ManufacturingEventJsonRepository(jdbc);
        ManufacturingAnalysisResultService analysisResultService =
                mock(ManufacturingAnalysisResultService.class);
        ManufacturingKafkaConsumer consumer = new ManufacturingKafkaConsumer(
                objectMapper,
                analyzer,
                processRouter,
                producer,
                traceStore,
                parser,
                equipmentStateService,
                eventRepository,
                analysisResultService
        );
        ManufacturingRawEvent rawWithoutCarMasterId = new ManufacturingRawEvent(
                37L, "EVT-37", now.minusMinutes(3),
                null, 10L, ProcessCode.PRESS, "EQ-1", "HYDRAULIC_PRESS",
                "RUNNING", "PROCESS_STATUS", Map.of("event", Map.of("carId", "CAR-33"))
        );
        ManufacturingAnalysisEvent analysis = normalAnalysis(rawWithoutCarMasterId, 33L);
        when(parser.parse("raw-json")).thenReturn(rawWithoutCarMasterId);
        when(analyzer.isEquipmentAbnormal(rawWithoutCarMasterId)).thenReturn(false);
        when(processRouter.route(rawWithoutCarMasterId)).thenReturn(analysis);
        when(producer.sendAnalysis(analysis)).thenReturn(CompletableFuture.completedFuture(publishResult()));

        consumer.consumeRaw(
                new ConsumerRecord<>("factory.manufacturing.raw", 0, 0L, "33", "raw-json")
        );

        assertThat(analysisStatus(jdbc, 37)).isEqualTo("NORMAL");
        assertThat(status(jdbc, 37)).isEqualTo(DispatchStatus.SENT.name());
        assertThat(status(jdbc, 38)).isEqualTo(DispatchStatus.READY.name());
        assertThat(analysisStatus(jdbc, 38)).isEqualTo("NOT_ANALYZED");
        assertThat(status(jdbc, 39)).isEqualTo(DispatchStatus.PENDING.name());
        assertThat(status(jdbc, 40)).isEqualTo(DispatchStatus.PENDING.name());
    }

    @Test
    void pressAbnormalCompletionUpdatesAnalysisStatusAndBlocksFollowingProcesses() {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource("jdbc:h2:mem:consumerabnormal;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createTables(jdbc);
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 9, 30);
        insert(jdbc, 45, "EVT-20260601-000045", now.minusMinutes(3), 11L, "PRESS",
                "SENT", "NOT_ANALYZED", true);
        insert(jdbc, 46, "EVT-20260601-000046", now.minusMinutes(2), 11L, "BODY",
                "PENDING", "NOT_ANALYZED", false);
        insert(jdbc, 47, "EVT-20260601-000047", now.minusMinutes(1), 11L, "PAINT",
                "PENDING", "NOT_ANALYZED", false);
        insert(jdbc, 48, "EVT-20260601-000048", now, 11L, "ASSEMBLY",
                "PENDING", "NOT_ANALYZED", false);

        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ManufacturingEventAnalyzer analyzer = mock(ManufacturingEventAnalyzer.class);
        ManufacturingProcessRouter processRouter = mock(ManufacturingProcessRouter.class);
        ManufacturingKafkaProducer producer = mock(ManufacturingKafkaProducer.class);
        KafkaMessageTraceStore traceStore = mock(KafkaMessageTraceStore.class);
        ManufacturingRawEventParser parser = mock(ManufacturingRawEventParser.class);
        EquipmentStateService equipmentStateService = mock(EquipmentStateService.class);
        ManufacturingEventJsonRepository eventRepository = new ManufacturingEventJsonRepository(jdbc);
        ManufacturingAnalysisResultService analysisResultService =
                mock(ManufacturingAnalysisResultService.class);
        ManufacturingKafkaConsumer consumer = new ManufacturingKafkaConsumer(
                objectMapper,
                analyzer,
                processRouter,
                producer,
                traceStore,
                parser,
                equipmentStateService,
                eventRepository,
                analysisResultService
        );
        ManufacturingRawEvent raw = new ManufacturingRawEvent(
                45L, "EVT-20260601-000045", now.minusMinutes(3),
                11L, 10L, ProcessCode.PRESS, "EQ-1", "HYDRAULIC_PRESS",
                "RUNNING", "PROCESS_STATUS", Map.of("event", Map.of("carId", "CAR-11"))
        );
        ManufacturingAnalysisEvent analysis = abnormalAnalysis(raw);
        when(parser.parse("raw-json")).thenReturn(raw);
        when(analyzer.isEquipmentAbnormal(raw)).thenReturn(false);
        when(processRouter.route(raw)).thenReturn(analysis);
        when(producer.sendAnalysis(analysis)).thenReturn(CompletableFuture.completedFuture(publishResult()));

        consumer.consumeRaw(
                new ConsumerRecord<>("factory.manufacturing.raw", 0, 0L, "11", "raw-json")
        );

        assertThat(analysisStatus(jdbc, 45)).isEqualTo("ABNORMAL");
        assertThat(status(jdbc, 45)).isEqualTo(DispatchStatus.SENT.name());
        assertThat(status(jdbc, 46)).isEqualTo(DispatchStatus.BLOCKED.name());
        assertThat(status(jdbc, 47)).isEqualTo(DispatchStatus.BLOCKED.name());
        assertThat(status(jdbc, 48)).isEqualTo(DispatchStatus.BLOCKED.name());
    }

    private ManufacturingAnalysisEvent normalAnalysis(ManufacturingRawEvent raw) {
        return normalAnalysis(raw, raw.carMasterId());
    }

    private ManufacturingAnalysisEvent normalAnalysis(ManufacturingRawEvent raw, Long carMasterId) {
        return new ManufacturingAnalysisEvent(
                "ANL-1",
                raw.eventId(),
                raw.eventTime(),
                raw.eventTime(),
                null,
                null,
                raw.processCode(),
                raw.equipmentCode(),
                null,
                raw.equipmentType(),
                null,
                null,
                carMasterId,
                "PROCESS_RISK_ANALYSIS",
                new ManufacturingAnalysisEvent.RiskScores(
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        new ManufacturingAnalysisEvent.ProcessRisk(null, 0.0, null, null)
                ),
                100.0,
                "LOW",
                new ManufacturingAnalysisEvent.AnalysisResult(false, false, false, false, false),
                new ManufacturingAnalysisEvent.Reason("normal", List.of()),
                new ManufacturingAnalysisEvent.Recommendation("NONE", "none")
        );
    }

    private ManufacturingAnalysisEvent abnormalAnalysis(ManufacturingRawEvent raw) {
        return new ManufacturingAnalysisEvent(
                "ANL-CRITICAL",
                raw.eventId(),
                raw.eventTime(),
                raw.eventTime(),
                null,
                null,
                raw.processCode(),
                raw.equipmentCode(),
                null,
                raw.equipmentType(),
                null,
                null,
                raw.carMasterId(),
                "PROCESS_RISK_ANALYSIS",
                new ManufacturingAnalysisEvent.RiskScores(
                        95.0,
                        0.0,
                        0.0,
                        95.0,
                        new ManufacturingAnalysisEvent.ProcessRisk(95.0, null, null, null)
                ),
                20.0,
                "CRITICAL",
                new ManufacturingAnalysisEvent.AnalysisResult(true, false, false, false, false),
                new ManufacturingAnalysisEvent.Reason("critical", List.of("critical")),
                new ManufacturingAnalysisEvent.Recommendation("STOP", "stop")
        );
    }

    private KafkaPublishResult publishResult() {
        return new KafkaPublishResult("topic", 0, 0L, "key", "EVT-1");
    }

    private void createTables(JdbcTemplate jdbc) {
        jdbc.execute("DROP TABLE IF EXISTS manufacturing_event_json");
        jdbc.execute("DROP TABLE IF EXISTS equipment");
        jdbc.execute("""
                CREATE TABLE equipment (
                  id BIGINT PRIMARY KEY, equipment_code VARCHAR(50), equipment_type VARCHAR(50),
                  health_status VARCHAR(20), current_status VARCHAR(20))
                """);
        jdbc.update("INSERT INTO equipment VALUES (10, 'EQ-1', 'HYDRAULIC_PRESS', 'NORMAL', 'RUNNING')");
        jdbc.execute("""
                CREATE TABLE manufacturing_event_json (
                  id BIGINT PRIMARY KEY, event_id VARCHAR(100), event_time TIMESTAMP,
                  car_master_id BIGINT, equipment_id BIGINT, process_code VARCHAR(20),
                  event_json VARCHAR(2000), dispatch_status VARCHAR(20), analysis_status VARCHAR(20),
                  is_sent BOOLEAN, retry_count BIGINT, error_message VARCHAR(2000),
                  updated_at TIMESTAMP)
                """);
    }

    private void insert(
            JdbcTemplate jdbc,
            long id,
            String eventId,
            LocalDateTime time,
            long carMasterId,
            String processCode,
            String dispatchStatus,
            String analysisStatus,
            boolean sent
    ) {
        jdbc.update("""
                INSERT INTO manufacturing_event_json VALUES
                (?, ?, ?, ?, 10, ?, '{"event":{"carId":"CAR-33"}}',
                 ?, ?, ?, 0, NULL, CURRENT_TIMESTAMP)
                """, id, eventId, time, carMasterId, processCode,
                dispatchStatus, analysisStatus, sent);
    }

    private String status(JdbcTemplate jdbc, long id) {
        return jdbc.queryForObject("SELECT dispatch_status FROM manufacturing_event_json WHERE id=?",
                String.class, id);
    }

    private String analysisStatus(JdbcTemplate jdbc, long id) {
        return jdbc.queryForObject("SELECT analysis_status FROM manufacturing_event_json WHERE id=?",
                String.class, id);
    }
}

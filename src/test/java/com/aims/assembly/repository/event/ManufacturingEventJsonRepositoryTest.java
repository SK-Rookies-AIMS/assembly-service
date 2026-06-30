package com.aims.assembly.repository.event;

import com.aims.assembly.domain.enums.DispatchStatus;
import com.aims.assembly.domain.enums.ProcessCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ManufacturingEventJsonRepositoryTest {
    private JdbcTemplate jdbc;
    private ManufacturingEventJsonRepository repository;
    private DriverManagerDataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource("jdbc:h2:mem:eventrepo;MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
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
        repository = new ManufacturingEventJsonRepository(jdbc);
    }

    @Test
    void selectsOnlyDueReadyUnsentRowsInEventTimeAndIdOrder() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 22, 10, 0);
        insert(3, now.minusMinutes(1), "READY", false);
        insert(2, now.minusMinutes(1), "READY", false);
        insert(1, now.plusMinutes(1), "READY", false);
        insert(4, now.minusMinutes(2), "PENDING", false);
        insert(5, now.minusMinutes(2), "READY", true);

        var rows = repository.findReadyForUpdate(now, 1_000, 3);

        assertThat(rows).extracting(ManufacturingEventJsonRepository.StoredManufacturingEvent::id)
                .containsExactly(2L, 3L);
    }

    @Test
    void faultBlocksReadyAndRecoveryRestoresOnlyBlocked() {
        LocalDateTime now = LocalDateTime.now();
        insert(1, now, "READY", false);
        insert(2, now, "PENDING", false);

        assertThat(repository.blockReadyEvents(10L, "EQ-1")).isEqualTo(1);
        assertThat(status(1)).isEqualTo(DispatchStatus.BLOCKED.name());
        assertThat(status(2)).isEqualTo(DispatchStatus.PENDING.name());

        assertThat(repository.restoreBlockedEvents(10L, "EQ-1")).isEqualTo(1);
        assertThat(status(1)).isEqualTo(DispatchStatus.READY.name());
        assertThat(status(2)).isEqualTo(DispatchStatus.PENDING.name());
    }

    @Test
    void concurrentClaimsSkipRowsLockedByAnotherScheduler() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        insert(1, now.minusSeconds(1), "READY", false);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> tx.execute(status -> {
                var rows = repository.findReadyForUpdate(now, 1, 3);
                locked.countDown();
                try { release.await(2, TimeUnit.SECONDS); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                return rows;
            }));
            assertThat(locked.await(1, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> tx.execute(status ->
                    repository.findReadyForUpdate(now, 1, 3)));

            assertThat(second.get(1, TimeUnit.SECONDS)).isEmpty();
            release.countDown();
            assertThat(first.get(1, TimeUnit.SECONDS)).hasSize(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void capsSchedulerBatchAtOneThousand() {
        LocalDateTime now = LocalDateTime.now();
        for (int id = 1; id <= 1_001; id++) {
            insert(id, now.minusSeconds(1), "READY", false);
        }
        assertThat(repository.findReadyForUpdate(now, 5_000, 3)).hasSize(1_000);
    }

    @Test
    void activatesFirstPendingThenWaitsForPreviousAnalysisBeforeNextEvent() {
        LocalDateTime now = LocalDateTime.now();
        insert(1, now.minusMinutes(2), "PENDING", false);
        insert(2, now.minusMinutes(1), "PENDING", false);

        assertThat(repository.prepareDispatchablePendingEvents(now, 1_000)).isEqualTo(1);
        assertThat(status(1)).isEqualTo(DispatchStatus.READY.name());
        assertThat(status(2)).isEqualTo(DispatchStatus.PENDING.name());

        repository.markAnalysisCompleted("EVT-1", false);
        assertThat(repository.prepareDispatchablePendingEvents(now, 1_000)).isEqualTo(1);
        assertThat(status(2)).isEqualTo(DispatchStatus.READY.name());
    }

    @Test
    void blocksNextPendingEventWhenPreviousAnalysisWasAbnormal() {
        LocalDateTime now = LocalDateTime.now();
        insert(1, now.minusMinutes(2), "PENDING", false);
        insert(2, now.minusMinutes(1), "PENDING", false);

        assertThat(repository.prepareDispatchablePendingEvents(now, 1_000)).isEqualTo(1);
        repository.markAnalysisCompleted("EVT-1", true);

        assertThat(repository.prepareDispatchablePendingEvents(now, 1_000)).isEqualTo(1);
        assertThat(status(2)).isEqualTo(DispatchStatus.BLOCKED.name());
    }

    @Test
    void pressNormalCompletionReleasesBodyOnly() {
        insertManufacturingFlow();

        assertThat(repository.releaseNextProcess(1L, ProcessCode.BODY.name())).isEqualTo(1);

        assertThat(status(1)).isEqualTo(DispatchStatus.SENT.name());
        assertThat(status(2)).isEqualTo(DispatchStatus.READY.name());
        assertThat(status(3)).isEqualTo(DispatchStatus.PENDING.name());
        assertThat(status(4)).isEqualTo(DispatchStatus.PENDING.name());
    }

    @Test
    void releaseNextProcessUsesCarMasterIdNextProcessPendingAndUnsentOnly() {
        LocalDateTime now = LocalDateTime.now();
        insert(37, now.minusMinutes(3), 33L, "PRESS", "SENT", "NORMAL", true);
        insert(38, now.minusMinutes(2), 33L, "BODY", "PENDING", "NOT_ANALYZED", false);
        insert(39, now.minusMinutes(1), 33L, "PAINT", "PENDING", "NOT_ANALYZED", false);
        insert(40, now, 33L, "ASSEMBLY", "PENDING", "NOT_ANALYZED", false);

        int updatedRows = repository.releaseNextProcess(33L, ProcessCode.BODY.name());

        assertThat(updatedRows).isEqualTo(1);
        assertThat(status(37)).isEqualTo(DispatchStatus.SENT.name());
        assertThat(analysisStatus(37)).isEqualTo("NORMAL");
        assertThat(status(38)).isEqualTo(DispatchStatus.READY.name());
        assertThat(analysisStatus(38)).isEqualTo("NOT_ANALYZED");
        assertThat(status(39)).isEqualTo(DispatchStatus.PENDING.name());
        assertThat(status(40)).isEqualTo(DispatchStatus.PENDING.name());
    }

    @Test
    void releaseNextProcessMatchesManualSqlForCarMasterElevenBody() {
        LocalDateTime now = LocalDateTime.now();
        insert(41, now.minusMinutes(1), 11L, "PRESS", "SENT", "NORMAL", true);
        insert(42, now, 11L, "BODY", "PENDING", "NOT_ANALYZED", false);

        int updatedRows = repository.releaseNextProcess(11L, "BODY");

        assertThat(updatedRows).isEqualTo(1);
        assertThat(status(41)).isEqualTo(DispatchStatus.SENT.name());
        assertThat(analysisStatus(41)).isEqualTo("NORMAL");
        assertThat(status(42)).isEqualTo(DispatchStatus.READY.name());
        assertThat(analysisStatus(42)).isEqualTo("NOT_ANALYZED");
    }

    @Test
    void bodyNormalCompletionReleasesPaintOnly() {
        insertManufacturingFlow();
        jdbc.update("UPDATE manufacturing_event_json SET dispatch_status='SENT' WHERE id=2");

        assertThat(repository.releaseNextProcess(1L, ProcessCode.PAINT.name())).isEqualTo(1);

        assertThat(status(2)).isEqualTo(DispatchStatus.SENT.name());
        assertThat(status(3)).isEqualTo(DispatchStatus.READY.name());
        assertThat(status(4)).isEqualTo(DispatchStatus.PENDING.name());
    }

    @Test
    void paintNormalCompletionReleasesAssembly() {
        insertManufacturingFlow();
        jdbc.update("UPDATE manufacturing_event_json SET dispatch_status='SENT' WHERE id=3");

        assertThat(repository.releaseNextProcess(1L, ProcessCode.ASSEMBLY.name())).isEqualTo(1);

        assertThat(status(3)).isEqualTo(DispatchStatus.SENT.name());
        assertThat(status(4)).isEqualTo(DispatchStatus.READY.name());
    }

    @Test
    void pressAbnormalCompletionBlocksFollowingProcesses() {
        insertManufacturingFlow();

        assertThat(repository.blockFollowingProcesses(
                1L,
                java.util.List.of(ProcessCode.BODY, ProcessCode.PAINT, ProcessCode.ASSEMBLY)
        )).isEqualTo(3);

        assertThat(status(1)).isEqualTo(DispatchStatus.SENT.name());
        assertThat(status(2)).isEqualTo(DispatchStatus.BLOCKED.name());
        assertThat(status(3)).isEqualTo(DispatchStatus.BLOCKED.name());
        assertThat(status(4)).isEqualTo(DispatchStatus.BLOCKED.name());
    }

    @Test
    void bodyAbnormalCompletionBlocksPaintAndAssembly() {
        insertManufacturingFlow();
        jdbc.update("UPDATE manufacturing_event_json SET dispatch_status='SENT' WHERE id=2");

        assertThat(repository.blockFollowingProcesses(
                1L,
                java.util.List.of(ProcessCode.PAINT, ProcessCode.ASSEMBLY)
        )).isEqualTo(2);

        assertThat(status(2)).isEqualTo(DispatchStatus.SENT.name());
        assertThat(status(3)).isEqualTo(DispatchStatus.BLOCKED.name());
        assertThat(status(4)).isEqualTo(DispatchStatus.BLOCKED.name());
    }

    @Test
    void paintAbnormalCompletionBlocksAssembly() {
        insertManufacturingFlow();
        jdbc.update("UPDATE manufacturing_event_json SET dispatch_status='SENT' WHERE id=3");

        assertThat(repository.blockFollowingProcesses(
                1L,
                java.util.List.of(ProcessCode.ASSEMBLY)
        )).isEqualTo(1);

        assertThat(status(3)).isEqualTo(DispatchStatus.SENT.name());
        assertThat(status(4)).isEqualTo(DispatchStatus.BLOCKED.name());
    }

    @Test
    void bodyPendingEventRequiresPreviousPressNormalCompletion() {
        LocalDateTime now = LocalDateTime.now();
        insert(1, now.minusMinutes(2), "PENDING", false, "PRESS");
        insert(2, now.minusMinutes(1), "PENDING", false, "BODY");

        assertThat(repository.prepareDispatchablePendingEvents(now, 1_000)).isEqualTo(1);
        assertThat(status(1)).isEqualTo(DispatchStatus.READY.name());
        assertThat(status(2)).isEqualTo(DispatchStatus.PENDING.name());

        repository.markAnalysisCompleted("EVT-1", false);
        assertThat(repository.prepareDispatchablePendingEvents(now, 1_000)).isEqualTo(1);
        assertThat(status(2)).isEqualTo(DispatchStatus.READY.name());
    }

    @Test
    void mapsSuccessfulNormalAndAbnormalAnalysisAndKeepsFailureNotAnalyzed() {
        LocalDateTime now = LocalDateTime.now();
        insert(1, now, "READY", false);

        repository.markAnalysisCompleted("EVT-1", false);
        assertThat(analysisStatus(1)).isEqualTo("NORMAL");
        repository.markAnalysisCompleted("EVT-1", true);
        assertThat(analysisStatus(1)).isEqualTo("ABNORMAL");
        repository.markAnalysisFailed("EVT-1", "parse failure");
        assertThat(analysisStatus(1)).isEqualTo("NOT_ANALYZED");
    }

    @Test
    void normalAnalysisUpdatesSourceEventAnalysisStatusByEventId() {
        LocalDateTime now = LocalDateTime.now();
        insert(45, now, 11L, "PRESS", "SENT", "NOT_ANALYZED", true);

        int updatedRows = repository.markAnalysisCompleted("EVT-45", false);

        assertThat(updatedRows).isEqualTo(1);
        assertThat(analysisStatus(45)).isEqualTo("NORMAL");
        assertThat(status(45)).isEqualTo(DispatchStatus.SENT.name());
    }

    @Test
    void activatesPendingEventAsBlockedWhenTargetEquipmentIsFaulted() {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("UPDATE equipment SET health_status='CRITICAL', current_status='FAULT' WHERE id=10");
        insert(1, now.minusSeconds(1), "PENDING", false);

        assertThat(repository.prepareDispatchablePendingEvents(now, 1_000)).isEqualTo(1);
        assertThat(status(1)).isEqualTo(DispatchStatus.BLOCKED.name());
    }

    @Test
    void activatesPendingEventAsReadyWhenTargetEquipmentHealthIsWarning() {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("UPDATE equipment SET health_status='WARNING', current_status='WARNING' WHERE id=10");
        insert(1, now.minusSeconds(1), "PENDING", false);

        assertThat(repository.prepareDispatchablePendingEvents(now, 1_000)).isEqualTo(1);
        assertThat(status(1)).isEqualTo(DispatchStatus.READY.name());
    }

    @Test
    void readsNullEventTimeAndExcludesItFromSchedulerQueries() {
        insert(1, null, "READY", false);

        assertThat(repository.findById(1).orElseThrow().payload().eventTime()).isNull();
        assertThat(repository.findReadyForUpdate(LocalDateTime.now(), 1_000, 3)).isEmpty();
    }

    @Test
    void mapsProcessCodeFromColumnWithoutAddingItToEventJson() {
        insert(1, LocalDateTime.now(), "READY", false);

        var payload = repository.findById(1).orElseThrow().payload();

        assertThat(payload.processCode()).isEqualTo(ProcessCode.PRESS);
        assertThat(payload.eventJson()).doesNotContainKeys("processCode", "process_code");
    }

    private void insert(long id, LocalDateTime time, String status, boolean sent) {
        insert(id, time, status, sent, "PRESS");
    }

    private void insert(long id, LocalDateTime time, String status, boolean sent, String processCode) {
        insert(id, time, 1L, processCode, status, "NOT_ANALYZED", sent);
    }

    private void insert(
            long id,
            LocalDateTime time,
            long carMasterId,
            String processCode,
            String dispatchStatus,
            String analysisStatus,
            boolean sent
    ) {
        jdbc.update("""
                INSERT INTO manufacturing_event_json VALUES
                (?, ?, ?, ?, 10, ?, '{"event":{"carId":"CAR-1"}}',
                 ?, ?, ?, 0, NULL, CURRENT_TIMESTAMP)
                """, id, "EVT-" + id, time, carMasterId, processCode,
                dispatchStatus, analysisStatus, sent);
    }

    private void insertManufacturingFlow() {
        LocalDateTime now = LocalDateTime.now();
        insert(1, now.minusMinutes(3), "SENT", true, "PRESS");
        insert(2, now.minusMinutes(2), "PENDING", false, "BODY");
        insert(3, now.minusMinutes(1), "PENDING", false, "PAINT");
        insert(4, now, "PENDING", false, "ASSEMBLY");
    }

    private String status(long id) {
        return jdbc.queryForObject("SELECT dispatch_status FROM manufacturing_event_json WHERE id=?",
                String.class, id);
    }

    private String analysisStatus(long id) {
        return jdbc.queryForObject("SELECT analysis_status FROM manufacturing_event_json WHERE id=?",
                String.class, id);
    }
}

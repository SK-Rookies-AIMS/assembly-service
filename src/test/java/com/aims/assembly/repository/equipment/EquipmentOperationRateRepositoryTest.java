package com.aims.assembly.repository.equipment;

import com.aims.assembly.domain.enums.EquipmentOperationStatus;
import com.aims.assembly.domain.enums.ProcessCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentOperationRateRepositoryTest {

    private JdbcTemplate jdbc;
    private EquipmentOperationRateRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource("jdbc:h2:mem:equipmentrate;MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS equipment");
        jdbc.execute("""
                CREATE TABLE equipment (
                  id BIGINT PRIMARY KEY,
                  process_code VARCHAR(20) NOT NULL,
                  equipment_code VARCHAR(50) NOT NULL,
                  current_status VARCHAR(20) NOT NULL DEFAULT 'RUNNING'
                )
                """);
        repository = new EquipmentOperationRateRepository(jdbc);
    }

    @Test
    void countsEquipmentByProcessCodeAndCurrentStatusWithoutHealthStatusColumn() {
        insert(1, "PRESS", "EQ-PRESS-1", "RUNNING");
        insert(2, "PRESS", "EQ-PRESS-2", "WARNING");
        insert(3, "PRESS", "EQ-PRESS-3", "STOPPED");
        insert(4, "BODY", "EQ-BODY-1", "FAULT");
        insert(5, "PAINT", "EQ-PAINT-1", "RUNNING");

        assertThat(repository.countByProcessAndStatus())
                .contains(
                        new EquipmentOperationRateRepository.StatusCount(
                                ProcessCode.PRESS, EquipmentOperationStatus.RUNNING, 1),
                        new EquipmentOperationRateRepository.StatusCount(
                                ProcessCode.PRESS, EquipmentOperationStatus.WARNING, 1),
                        new EquipmentOperationRateRepository.StatusCount(
                                ProcessCode.PRESS, EquipmentOperationStatus.STOPPED, 1),
                        new EquipmentOperationRateRepository.StatusCount(
                                ProcessCode.BODY, EquipmentOperationStatus.FAULT, 1),
                        new EquipmentOperationRateRepository.StatusCount(
                                ProcessCode.PAINT, EquipmentOperationStatus.RUNNING, 1)
                );
    }

    private void insert(long id, String processCode, String equipmentCode, String currentStatus) {
        jdbc.update(
                "INSERT INTO equipment (id, process_code, equipment_code, current_status) VALUES (?, ?, ?, ?)",
                id,
                processCode,
                equipmentCode,
                currentStatus
        );
    }
}

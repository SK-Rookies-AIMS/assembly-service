package com.aims.assembly.repository.process;

import com.aims.assembly.dto.process.ProcessEventResponse;
import com.aims.assembly.mapper.process.ProcessEventMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProcessEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProcessEventRepository(@Qualifier("sampleJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ProcessEventResponse.EquipmentDTO> findEquipments() {
        String sql = """
                SELECT id, process_code, equipment_code, equipment_name, equipment_type, created_at
                FROM equipment
                ORDER BY id ASC
                """;

        return jdbcTemplate.query(sql, ProcessEventMapper.equipmentMapper());
    }

    public List<ProcessEventResponse.ManufacturingEventDTO> findManufacturingEvents() {
        String sql = """
                SELECT *
                FROM manufacturing_event
                ORDER BY id ASC
                """;

        return jdbcTemplate.query(sql, ProcessEventMapper.manufacturingEventMapper());
    }

    public List<ProcessEventResponse.ThermalVisionDTO> findThermalVisions() {
        String sql = """
                SELECT *
                FROM thermal_vision
                ORDER BY id ASC
                """;

        return jdbcTemplate.query(sql, ProcessEventMapper.thermalVisionMapper());
    }

    public List<ProcessEventResponse.RobotArmVibrationDTO> findRobotArmVibrations() {
        String sql = """
                SELECT *
                FROM robot_arm_vibration
                ORDER BY id ASC
                """;

        return jdbcTemplate.query(sql, ProcessEventMapper.robotArmVibrationMapper());
    }
}

package com.aims.assembly.mapper.process;

import com.aims.assembly.dto.process.ProcessEventResponse;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ProcessEventMapper {

    public static RowMapper<ProcessEventResponse.EquipmentDTO> equipmentMapper() {
        return (rs, rowNum) -> ProcessEventResponse.EquipmentDTO.builder()
                .id(rs.getLong("id"))
                .processCode(rs.getString("process_code"))
                .equipmentCode(rs.getString("equipment_code"))
                .equipmentName(rs.getString("equipment_name"))
                .equipmentType(rs.getString("equipment_type"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .build();
    }

    public static RowMapper<ProcessEventResponse.ManufacturingEventDTO> manufacturingEventMapper() {
        return (rs, rowNum) -> ProcessEventResponse.ManufacturingEventDTO.builder()
                .id(rs.getLong("id"))
                .carMasterId(rs.getLong("car_master_id"))
                .equipmentId(rs.getLong("equipment_id"))
                .sampleManufacturingEventId(rs.getString("sample_manufacturing_event_id"))
                .equipmentCode(rs.getString("equipment_code"))
                .sourceDataset(rs.getString("source_dataset"))
                .sourceType(rs.getString("source_type"))
                .dataType(rs.getString("data_type"))
                .processCode(rs.getString("process_code"))
                .equipmentType(rs.getString("equipment_type"))
                .stationCode(rs.getString("station_code"))
                .eventTime(rs.getTimestamp("event_time").toLocalDateTime())
                .metricCode(rs.getString("metric_code"))
                .metricValue(rs.getDouble("metric_value"))
                .unit(rs.getString("unit"))
                .processTime(rs.getDouble("process_time"))
                .waitingTime(rs.getDouble("waiting_time"))
                .qualityResult(rs.getString("quality_result"))
                .defectType(rs.getString("defect_type"))
                .expectedIsAbnormal(rs.getBoolean("expected_is_abnormal"))
                .expectedAbnormalType(rs.getString("expected_abnormal_type"))
                .expectedSeverity(rs.getString("expected_severity"))
                .expectedLabel(rs.getString("expected_label"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .build();
    }

    public static RowMapper<ProcessEventResponse.ThermalVisionDTO> thermalVisionMapper() {
        return (rs, rowNum) -> ProcessEventResponse.ThermalVisionDTO.builder()
                .id(rs.getLong("id"))
                .manufacturingEventId(rs.getLong("manufacturing_event_id"))
                .carMasterId(rs.getLong("car_master_id"))
                .imagePosition(rs.getString("image_position"))
                .thermalAvgTemp(rs.getDouble("thermal_avg_temp"))
                .thermalMaxTemp(rs.getDouble("thermal_max_temp"))
                .thermalMinTemp(rs.getDouble("thermal_min_temp"))
                .thermalStdTemp(rs.getDouble("thermal_std_temp"))
                .thicknessValue(rs.getDouble("thickness_value"))
                .defectScore(rs.getDouble("defect_score"))
                .expectedVisionLabel(rs.getString("excepted_vision_label"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .build();
    }

    public static RowMapper<ProcessEventResponse.RobotArmVibrationDTO> robotArmVibrationMapper() {
        return (rs, rowNum) -> ProcessEventResponse.RobotArmVibrationDTO.builder()
                .id(rs.getLong("id"))
                .manufacturingEventId(rs.getLong("manufacturing_event_id"))
                .equipmentId(rs.getLong("equipment_id"))
                .measuredAt(rs.getTimestamp("measured_at").toLocalDateTime())
                .freq0100Hz(rs.getFloat("freq_0_100_hz"))
                .freq101200Hz(rs.getFloat("freq_101_200_hz"))
                .freq201300Hz(rs.getFloat("freq_201_300_hz"))
                .freq301400Hz(rs.getFloat("freq_301_400_hz"))
                .freq401500Hz(rs.getFloat("freq_401_500_hz"))
                .freq501600Hz(rs.getFloat("freq_501_600_hz"))
                .freq601700Hz(rs.getFloat("freq_601_700_hz"))
                .freq701800Hz(rs.getFloat("freq_701_800_hz"))
                .freq801900Hz(rs.getFloat("freq_801_900_hz"))
                .freq9011000Hz(rs.getFloat("freq_901_1000_hz"))
                .freq10011100Hz(rs.getFloat("freq_1001_1100_hz"))
                .freq11011200Hz(rs.getFloat("freq_1101_1200_hz"))
                .freq12011300Hz(rs.getFloat("freq_1201_1300_hz"))
                .freq13011400Hz(rs.getFloat("freq_1301_1400_hz"))
                .freq14011500Hz(rs.getFloat("freq_1401_1500_hz"))
                .freq15011600Hz(rs.getFloat("freq_1501_1600_hz"))
                .build();
    }
}

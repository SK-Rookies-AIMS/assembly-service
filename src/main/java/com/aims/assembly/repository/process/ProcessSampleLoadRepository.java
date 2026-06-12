package com.aims.assembly.repository.process;

import lombok.Builder;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class ProcessSampleLoadRepository {

    private static final List<String> ASSEMBLY_WORK_TIME_COLUMNS = List.of(
            "checked_at", "worked_at", "created_at", "updated_at"
    );

    private final JdbcTemplate sampleJdbcTemplate;
    private final JdbcTemplate mainJdbcTemplate;

    public ProcessSampleLoadRepository(
            @Qualifier("sampleJdbcTemplate") JdbcTemplate sampleJdbcTemplate,
            @Qualifier("mainJdbcTemplate") JdbcTemplate mainJdbcTemplate
    ) {
        this.sampleJdbcTemplate = sampleJdbcTemplate;
        this.mainJdbcTemplate = mainJdbcTemplate;
    }

    public List<ManufacturingEventRow> findManufacturingEvents(LocalDateTime fromDate) {
        String sql = """
                SELECT *
                FROM manufacturing_event
                WHERE (? IS NULL OR event_time >= ?)
                ORDER BY event_time ASC, id ASC
                """;
        Timestamp timestamp = toTimestamp(fromDate);
        return sampleJdbcTemplate.query(sql, manufacturingEventMapper(), timestamp, timestamp);
    }

    public List<ThermalVisionRow> findThermalVisions(LocalDateTime fromDate) {
        String sql = """
                SELECT tv.*
                FROM thermal_vision tv
                JOIN manufacturing_event me ON me.id = tv.manufacturing_event_id
                WHERE (? IS NULL OR me.event_time >= ?)
                ORDER BY tv.created_at ASC, tv.id ASC
                """;
        Timestamp timestamp = toTimestamp(fromDate);
        return sampleJdbcTemplate.query(sql, thermalVisionMapper(), timestamp, timestamp);
    }

    public List<RobotArmVibrationRow> findRobotArmVibrations(LocalDateTime fromDate) {
        String sql = """
                SELECT rv.*
                FROM robot_arm_vibration rv
                JOIN manufacturing_event me ON me.id = rv.manufacturing_event_id
                WHERE (? IS NULL OR me.event_time >= ?)
                ORDER BY rv.measured_at ASC, rv.id ASC
                """;
        Timestamp timestamp = toTimestamp(fromDate);
        return sampleJdbcTemplate.query(sql, robotArmVibrationMapper(), timestamp, timestamp);
    }

    public List<Map<String, Object>> findAssemblyWorkChecks(LocalDateTime fromDate) {
        String orderTimeColumn = findFirstExistingColumn("assembly_work_check", ASSEMBLY_WORK_TIME_COLUMNS);
        String orderBy = orderTimeColumn == null ? "id ASC" : orderTimeColumn + " ASC, id ASC";
        String sql = """
                SELECT awc.*
                FROM assembly_work_check awc
                JOIN manufacturing_event me ON me.id = awc.manufacturing_event_id
                WHERE (? IS NULL OR me.event_time >= ?)
                ORDER BY %s
                """.formatted(orderBy);
        Timestamp timestamp = toTimestamp(fromDate);
        return sampleJdbcTemplate.queryForList(sql, timestamp, timestamp);
    }

    public List<EquipmentRow> findEquipments() {
        String sql = """
                SELECT id, process_code, equipment_code, equipment_name, equipment_type, created_at
                FROM equipment
                ORDER BY id ASC
                """;
        return sampleJdbcTemplate.query(sql, equipmentMapper());
    }

    public void deleteTargetTables() {
        // Development/test/demo reset only. This intentionally limits reset scope to generated analysis tables.
        // MySQL ALTER TABLE causes an implicit commit, so AUTO_INCREMENT reset is not rollback-safe like normal DML.
        // Keep this path behind reset=true and do not use it for production data recovery flows.
        mainJdbcTemplate.update("DELETE FROM equipment_status");
        mainJdbcTemplate.update("DELETE FROM product_process_history");
        mainJdbcTemplate.update("DELETE FROM assembly_analysis_result");
        mainJdbcTemplate.update("DELETE FROM paint_analysis_result");
        mainJdbcTemplate.update("DELETE FROM body_analysis_result");
        mainJdbcTemplate.update("DELETE FROM press_analysis_result");

        mainJdbcTemplate.execute("ALTER TABLE equipment_status AUTO_INCREMENT = 1");
        mainJdbcTemplate.execute("ALTER TABLE product_process_history AUTO_INCREMENT = 1");
        mainJdbcTemplate.execute("ALTER TABLE assembly_analysis_result AUTO_INCREMENT = 1");
        mainJdbcTemplate.execute("ALTER TABLE paint_analysis_result AUTO_INCREMENT = 1");
        mainJdbcTemplate.execute("ALTER TABLE body_analysis_result AUTO_INCREMENT = 1");
        mainJdbcTemplate.execute("ALTER TABLE press_analysis_result AUTO_INCREMENT = 1");
    }

    public int insertPressResults(List<PressAnalysisInsert> rows) {
        String sql = """
                INSERT INTO press_analysis_result (
                    manufacturing_event_id, car_master_id, process_code, equipment_code,
                    operation_rate, cnt_value, current_rms, vibration_value, cycle_time,
                    timestamp_delay, threshold_value, is_abnormal, abnormal_type, risk_score, analyzed_at
                )
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM press_analysis_result WHERE manufacturing_event_id = ?
                )
                """;
        return sum(mainJdbcTemplate.batchUpdate(sql, rows, 500, (ps, row) -> {
            ps.setLong(1, row.manufacturingEventId());
            ps.setObject(2, row.carMasterId());
            ps.setString(3, row.processCode());
            ps.setString(4, row.equipmentCode());
            ps.setObject(5, row.operationRate());
            ps.setObject(6, row.cntValue());
            ps.setObject(7, row.currentRms());
            ps.setObject(8, row.vibrationValue());
            ps.setObject(9, row.cycleTime());
            ps.setObject(10, row.timestampDelay());
            ps.setObject(11, row.thresholdValue());
            ps.setBoolean(12, row.isAbnormal());
            ps.setString(13, row.abnormalType());
            ps.setObject(14, row.riskScore());
            ps.setTimestamp(15, Timestamp.valueOf(row.analyzedAt()));
            ps.setLong(16, row.manufacturingEventId());
        }));
    }

    public int insertBodyResults(List<BodyAnalysisInsert> rows) {
        String sql = """
                INSERT INTO body_analysis_result (
                    manufacturing_event_id, car_master_id, process_code, equipment_code,
                    robot_current_rms, vibration_value, robot_motion_status, operation_rate,
                    threshold_value, is_abnormal, abnormal_type, risk_score, analyzed_at
                )
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM body_analysis_result WHERE manufacturing_event_id = ?
                )
                """;
        return sum(mainJdbcTemplate.batchUpdate(sql, rows, 500, (ps, row) -> {
            ps.setLong(1, row.manufacturingEventId());
            ps.setObject(2, row.carMasterId());
            ps.setString(3, row.processCode());
            ps.setString(4, row.equipmentCode());
            ps.setObject(5, row.robotCurrentRms());
            ps.setObject(6, row.vibrationValue());
            ps.setString(7, row.robotMotionStatus());
            ps.setObject(8, row.operationRate());
            ps.setObject(9, row.thresholdValue());
            ps.setBoolean(10, row.isAbnormal());
            ps.setString(11, row.abnormalType());
            ps.setObject(12, row.riskScore());
            ps.setTimestamp(13, Timestamp.valueOf(row.analyzedAt()));
            ps.setLong(14, row.manufacturingEventId());
        }));
    }

    public int insertPaintResults(List<PaintAnalysisInsert> rows) {
        String sql = """
                INSERT INTO paint_analysis_result (
                    manufacturing_event_id, thermal_vision_id, car_master_id, process_code, equipment_code,
                    operation_rate, defect_count, defect_rate, thermal_avg_temp, thermal_max_temp,
                    surface_quality_score, threshold_value, is_abnormal, abnormal_type, risk_score, analyzed_at
                )
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM paint_analysis_result WHERE manufacturing_event_id = ?
                )
                """;
        return sum(mainJdbcTemplate.batchUpdate(sql, rows, 500, (ps, row) -> {
            ps.setLong(1, row.manufacturingEventId());
            ps.setObject(2, row.thermalVisionId());
            ps.setObject(3, row.carMasterId());
            ps.setString(4, row.processCode());
            ps.setString(5, row.equipmentCode());
            ps.setObject(6, row.operationRate());
            ps.setObject(7, row.defectCount());
            ps.setObject(8, row.defectRate());
            ps.setObject(9, row.thermalAvgTemp());
            ps.setObject(10, row.thermalMaxTemp());
            ps.setObject(11, row.surfaceQualityScore());
            ps.setObject(12, row.thresholdValue());
            ps.setBoolean(13, row.isAbnormal());
            ps.setString(14, row.abnormalType());
            ps.setObject(15, row.riskScore());
            ps.setTimestamp(16, Timestamp.valueOf(row.analyzedAt()));
            ps.setLong(17, row.manufacturingEventId());
        }));
    }

    public int insertAssemblyResults(List<AssemblyAnalysisInsert> rows) {
        String sql = """
                INSERT INTO assembly_analysis_result (
                    manufacturing_event_id, car_master_id, process_code, equipment_code, operation_rate,
                    assembly_sequence_status, missing_part_count, fastening_error_count, sequence_error_count,
                    threshold_value, is_abnormal, abnormal_type, risk_score, analyzed_at
                )
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM assembly_analysis_result WHERE manufacturing_event_id = ?
                )
                """;
        return sum(mainJdbcTemplate.batchUpdate(sql, rows, 500, (ps, row) -> {
            ps.setLong(1, row.manufacturingEventId());
            ps.setObject(2, row.carMasterId());
            ps.setString(3, row.processCode());
            ps.setString(4, row.equipmentCode());
            ps.setObject(5, row.operationRate());
            ps.setString(6, row.assemblySequenceStatus());
            ps.setObject(7, row.missingPartCount());
            ps.setObject(8, row.fasteningErrorCount());
            ps.setObject(9, row.sequenceErrorCount());
            ps.setObject(10, row.thresholdValue());
            ps.setBoolean(11, row.isAbnormal());
            ps.setString(12, row.abnormalType());
            ps.setObject(13, row.riskScore());
            ps.setTimestamp(14, Timestamp.valueOf(row.analyzedAt()));
            ps.setLong(15, row.manufacturingEventId());
        }));
    }

    public int insertProcessHistories(List<ProcessHistoryInsert> rows) {
        String sql = """
                INSERT INTO product_process_history (
                    manufacturing_event_id, car_master_id, process_code, equipment_code, station_code,
                    lot_code, started_at, ended_at, process_time, waiting_time, result_status,
                    sequence_no, previous_process_code, created_at
                )
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM product_process_history WHERE manufacturing_event_id = ?
                )
                """;
        return sum(mainJdbcTemplate.batchUpdate(sql, rows, 500, (ps, row) -> {
            ps.setLong(1, row.manufacturingEventId());
            ps.setObject(2, row.carMasterId());
            ps.setString(3, row.processCode());
            ps.setString(4, row.equipmentCode());
            ps.setString(5, row.stationCode());
            ps.setString(6, row.lotCode());
            ps.setTimestamp(7, Timestamp.valueOf(row.startedAt()));
            ps.setTimestamp(8, Timestamp.valueOf(row.endedAt()));
            ps.setObject(9, row.processTime());
            ps.setObject(10, row.waitingTime());
            ps.setString(11, row.resultStatus());
            ps.setObject(12, row.sequenceNo());
            ps.setString(13, row.previousProcessCode());
            ps.setTimestamp(14, Timestamp.valueOf(row.createdAt()));
            ps.setLong(15, row.manufacturingEventId());
        }));
    }

    public int insertEquipmentStatuses(List<EquipmentStatusInsert> rows) {
        String sql = """
                INSERT INTO equipment_status (equipment_id, status, created_at, updated_at)
                SELECT ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM equipment_status WHERE equipment_id = ?
                )
                """;
        return sum(mainJdbcTemplate.batchUpdate(sql, rows, 500, (ps, row) -> {
            ps.setLong(1, row.equipmentId());
            ps.setString(2, row.status());
            ps.setTimestamp(3, Timestamp.valueOf(row.createdAt()));
            ps.setTimestamp(4, Timestamp.valueOf(row.updatedAt()));
            ps.setLong(5, row.equipmentId());
        }));
    }

    private String findFirstExistingColumn(String tableName, List<String> candidates) {
        Set<String> columns = new HashSet<>(sampleJdbcTemplate.queryForList(
                """
                        SELECT column_name
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = ?
                        """,
                String.class,
                tableName
        ));
        return candidates.stream().filter(columns::contains).findFirst().orElse(null);
    }

    private RowMapper<ManufacturingEventRow> manufacturingEventMapper() {
        return (rs, rowNum) -> ManufacturingEventRow.builder()
                .id(rs.getLong("id"))
                .carMasterId(nullableLong(rs, "car_master_id"))
                .equipmentId(nullableLong(rs, "equipment_id"))
                .equipmentCode(rs.getString("equipment_code"))
                .processCode(rs.getString("process_code"))
                .stationCode(rs.getString("station_code"))
                .eventTime(nullableDateTime(rs, "event_time"))
                .metricCode(rs.getString("metric_code"))
                .metricValue(nullableDouble(rs, "metric_value"))
                .processTime(nullableDouble(rs, "process_time"))
                .waitingTime(nullableDouble(rs, "waiting_time"))
                .qualityResult(rs.getString("quality_result"))
                .defectType(rs.getString("defect_type"))
                .expectedIsAbnormal(nullableBoolean(rs, "expected_is_abnormal"))
                .expectedAbnormalType(rs.getString("expected_abnormal_type"))
                .expectedSeverity(rs.getString("expected_severity"))
                .createdAt(nullableDateTime(rs, "created_at"))
                .build();
    }

    private RowMapper<ThermalVisionRow> thermalVisionMapper() {
        return (rs, rowNum) -> ThermalVisionRow.builder()
                .id(rs.getLong("id"))
                .manufacturingEventId(nullableLong(rs, "manufacturing_event_id"))
                .carMasterId(nullableLong(rs, "car_master_id"))
                .thermalAvgTemp(nullableDouble(rs, "thermal_avg_temp"))
                .thermalMaxTemp(nullableDouble(rs, "thermal_max_temp"))
                .defectScore(nullableDouble(rs, "defect_score"))
                .createdAt(nullableDateTime(rs, "created_at"))
                .build();
    }

    private RowMapper<RobotArmVibrationRow> robotArmVibrationMapper() {
        return (rs, rowNum) -> RobotArmVibrationRow.builder()
                .id(rs.getLong("id"))
                .manufacturingEventId(nullableLong(rs, "manufacturing_event_id"))
                .equipmentId(nullableLong(rs, "equipment_id"))
                .measuredAt(nullableDateTime(rs, "measured_at"))
                .freq0100Hz(nullableDouble(rs, "freq_0_100_hz"))
                .freq101200Hz(nullableDouble(rs, "freq_101_200_hz"))
                .freq201300Hz(nullableDouble(rs, "freq_201_300_hz"))
                .freq301400Hz(nullableDouble(rs, "freq_301_400_hz"))
                .freq401500Hz(nullableDouble(rs, "freq_401_500_hz"))
                .freq501600Hz(nullableDouble(rs, "freq_501_600_hz"))
                .freq601700Hz(nullableDouble(rs, "freq_601_700_hz"))
                .freq701800Hz(nullableDouble(rs, "freq_701_800_hz"))
                .freq801900Hz(nullableDouble(rs, "freq_801_900_hz"))
                .freq9011000Hz(nullableDouble(rs, "freq_901_1000_hz"))
                .freq10011100Hz(nullableDouble(rs, "freq_1001_1100_hz"))
                .freq11011200Hz(nullableDouble(rs, "freq_1101_1200_hz"))
                .freq12011300Hz(nullableDouble(rs, "freq_1201_1300_hz"))
                .freq13011400Hz(nullableDouble(rs, "freq_1301_1400_hz"))
                .freq14011500Hz(nullableDouble(rs, "freq_1401_1500_hz"))
                .freq15011600Hz(nullableDouble(rs, "freq_1501_1600_hz"))
                .build();
    }

    private RowMapper<EquipmentRow> equipmentMapper() {
        return (rs, rowNum) -> EquipmentRow.builder()
                .id(rs.getLong("id"))
                .processCode(rs.getString("process_code"))
                .equipmentCode(rs.getString("equipment_code"))
                .equipmentName(rs.getString("equipment_name"))
                .equipmentType(rs.getString("equipment_type"))
                .createdAt(nullableDateTime(rs, "created_at"))
                .build();
    }

    private static Long nullableLong(ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Double nullableDouble(ResultSet rs, String column) throws java.sql.SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static Boolean nullableBoolean(ResultSet rs, String column) throws java.sql.SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime nullableDateTime(ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static int sum(int[][] batchResults) {
        int total = 0;
        for (int[] batch : batchResults) {
            for (int result : batch) {
                if (result > 0) {
                    total += result;
                }
            }
        }
        return total;
    }

    @Getter
    @Builder
    public static class ManufacturingEventRow {
        private Long id;
        private Long carMasterId;
        private Long equipmentId;
        private String equipmentCode;
        private String processCode;
        private String stationCode;
        private LocalDateTime eventTime;
        private String metricCode;
        private Double metricValue;
        private Double processTime;
        private Double waitingTime;
        private String qualityResult;
        private String defectType;
        private Boolean expectedIsAbnormal;
        private String expectedAbnormalType;
        private String expectedSeverity;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    public static class ThermalVisionRow {
        private Long id;
        private Long manufacturingEventId;
        private Long carMasterId;
        private Double thermalAvgTemp;
        private Double thermalMaxTemp;
        private Double defectScore;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    public static class RobotArmVibrationRow {
        private Long id;
        private Long manufacturingEventId;
        private Long equipmentId;
        private LocalDateTime measuredAt;
        private Double freq0100Hz;
        private Double freq101200Hz;
        private Double freq201300Hz;
        private Double freq301400Hz;
        private Double freq401500Hz;
        private Double freq501600Hz;
        private Double freq601700Hz;
        private Double freq701800Hz;
        private Double freq801900Hz;
        private Double freq9011000Hz;
        private Double freq10011100Hz;
        private Double freq11011200Hz;
        private Double freq12011300Hz;
        private Double freq13011400Hz;
        private Double freq14011500Hz;
        private Double freq15011600Hz;
    }

    @Getter
    @Builder
    public static class EquipmentRow {
        private Long id;
        private String processCode;
        private String equipmentCode;
        private String equipmentName;
        private String equipmentType;
        private LocalDateTime createdAt;
    }

    public record PressAnalysisInsert(
            long id, long manufacturingEventId, Long carMasterId, String processCode, String equipmentCode,
            Double operationRate, Integer cntValue, Double currentRms, Double vibrationValue, Double cycleTime,
            Double timestampDelay, Double thresholdValue, boolean isAbnormal, String abnormalType,
            Double riskScore, LocalDateTime analyzedAt
    ) {
    }

    public record BodyAnalysisInsert(
            long id, long manufacturingEventId, Long carMasterId, String processCode, String equipmentCode,
            Double robotCurrentRms, Double vibrationValue, String robotMotionStatus, Double operationRate,
            Double thresholdValue, boolean isAbnormal, String abnormalType, Double riskScore,
            LocalDateTime analyzedAt
    ) {
    }

    public record PaintAnalysisInsert(
            long id, long manufacturingEventId, Long thermalVisionId, Long carMasterId, String processCode,
            String equipmentCode, Double operationRate, Integer defectCount, Double defectRate,
            Double thermalAvgTemp, Double thermalMaxTemp, Double surfaceQualityScore, Double thresholdValue,
            boolean isAbnormal, String abnormalType, Double riskScore, LocalDateTime analyzedAt
    ) {
    }

    public record AssemblyAnalysisInsert(
            long id, long manufacturingEventId, Long carMasterId, String processCode, String equipmentCode,
            Double operationRate, String assemblySequenceStatus, Integer missingPartCount,
            Integer fasteningErrorCount, Integer sequenceErrorCount, Double thresholdValue,
            boolean isAbnormal, String abnormalType, Double riskScore, LocalDateTime analyzedAt
    ) {
    }

    public record ProcessHistoryInsert(
            long id, long manufacturingEventId, Long carMasterId, String processCode, String equipmentCode,
            String stationCode, String lotCode, LocalDateTime startedAt, LocalDateTime endedAt,
            Double processTime, Double waitingTime, String resultStatus, Integer sequenceNo,
            String previousProcessCode, LocalDateTime createdAt
    ) {
    }

    public record EquipmentStatusInsert(
            long equipmentId, String status, LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
    }
}

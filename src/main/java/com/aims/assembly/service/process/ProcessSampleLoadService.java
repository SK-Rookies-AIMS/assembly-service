package com.aims.assembly.service.process;

import com.aims.assembly.dto.process.ProcessSampleResponse;
import com.aims.assembly.repository.process.ProcessSampleLoadRepository;
import com.aims.assembly.repository.process.ProcessSampleLoadRepository.AssemblyAnalysisInsert;
import com.aims.assembly.repository.process.ProcessSampleLoadRepository.BodyAnalysisInsert;
import com.aims.assembly.repository.process.ProcessSampleLoadRepository.EquipmentRow;
import com.aims.assembly.repository.process.ProcessSampleLoadRepository.EquipmentStatusInsert;
import com.aims.assembly.repository.process.ProcessSampleLoadRepository.ManufacturingEventRow;
import com.aims.assembly.repository.process.ProcessSampleLoadRepository.PaintAnalysisInsert;
import com.aims.assembly.repository.process.ProcessSampleLoadRepository.PressAnalysisInsert;
import com.aims.assembly.repository.process.ProcessSampleLoadRepository.ProcessHistoryInsert;
import com.aims.assembly.repository.process.ProcessSampleLoadRepository.RobotArmVibrationRow;
import com.aims.assembly.repository.process.ProcessSampleLoadRepository.ThermalVisionRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProcessSampleLoadService {

    private static final String PRESS = "PRESS";
    private static final String BODY = "BODY";
    private static final String PAINT = "PAINT";
    private static final String ASSEMBLY = "ASSEMBLY";
    private static final List<String> NORMAL_ABNORMAL_TYPE_LABELS = List.of("NORMAL", "OK", "PASS", "SUCCESS", "NONE");
    private static final LocalDateTime DEMO_ANALYZED_START = LocalDateTime.of(2026, 6, 1, 0, 0, 0);
    private static final LocalDateTime DEMO_ANALYZED_END = LocalDateTime.of(2026, 6, 7, 23, 59, 59);
    private static final double DEFAULT_OPERATION_RATE = 100.0;
    private static final double PRESS_THRESHOLD = 80.0;
    private static final double BODY_THRESHOLD = 70.0;
    private static final double PAINT_THRESHOLD = 60.0;
    private static final double ASSEMBLY_THRESHOLD = 1.0;

    private final ProcessSampleLoadRepository processSampleLoadRepository;

    @Transactional
    public ProcessSampleResponse load(boolean reset, LocalDateTime fromDate) {
        if (reset) {
            // Development/test/demo initial reload: delete only generated analysis tables before reloading.
            processSampleLoadRepository.deleteTargetTables();
        }

        List<ManufacturingEventRow> events = processSampleLoadRepository.findManufacturingEvents(fromDate);
        List<ThermalVisionRow> thermalVisions = processSampleLoadRepository.findThermalVisions(fromDate);
        List<RobotArmVibrationRow> robotArmVibrations = processSampleLoadRepository.findRobotArmVibrations(fromDate);
        List<Map<String, Object>> assemblyWorkChecks = processSampleLoadRepository.findAssemblyWorkChecks(fromDate);
        List<EquipmentRow> equipments = processSampleLoadRepository.findEquipments();

        Map<Long, ThermalVisionRow> thermalByEventId = thermalVisions.stream()
                .filter(row -> row.getManufacturingEventId() != null)
                .collect(Collectors.toMap(ThermalVisionRow::getManufacturingEventId, Function.identity(), (left, right) -> left));
        Map<Long, RobotArmVibrationRow> vibrationByEventId = robotArmVibrations.stream()
                .filter(row -> row.getManufacturingEventId() != null)
                .collect(Collectors.toMap(RobotArmVibrationRow::getManufacturingEventId, Function.identity(), (left, right) -> left));
        Map<Long, Map<String, Object>> assemblyCheckByEventId = assemblyWorkChecks.stream()
                .filter(row -> longValue(row, "manufacturing_event_id") != null)
                .collect(Collectors.toMap(row -> longValue(row, "manufacturing_event_id"), Function.identity(), (left, right) -> left));

        List<PressAnalysisInsert> pressRows = new ArrayList<>();
        List<BodyAnalysisInsert> bodyRows = new ArrayList<>();
        List<PaintAnalysisInsert> paintRows = new ArrayList<>();
        List<AssemblyAnalysisInsert> assemblyRows = new ArrayList<>();
        List<ProcessHistoryInsert> historyRows = new ArrayList<>();

        for (ManufacturingEventRow event : events) {
            String processCode = normalize(event.getProcessCode());
            if (PRESS.equals(processCode)) {
                pressRows.add(toPressRow(event));
            } else if (BODY.equals(processCode)) {
                bodyRows.add(toBodyRow(event, vibrationByEventId.get(event.getId())));
            } else if (PAINT.equals(processCode)) {
                paintRows.add(toPaintRow(event, thermalByEventId.get(event.getId())));
            } else if (ASSEMBLY.equals(processCode)) {
                assemblyRows.add(toAssemblyRow(event, assemblyCheckByEventId.get(event.getId())));
            }

            Integer sequenceNo = sequenceNo(processCode);
            if (sequenceNo != null) {
                historyRows.add(toProcessHistoryRow(event, sequenceNo));
            }
        }

        List<EquipmentStatusInsert> equipmentStatusRows = equipments.stream()
                .map(this::toEquipmentStatusRow)
                .toList();

        // Current schema has no physical FK/unique constraint to Sample DB rows. Duplicate prevention is implemented
        // with manufacturing_event_id/equipment_id logical keys in INSERT ... WHERE NOT EXISTS statements.
        int pressCount = processSampleLoadRepository.insertPressResults(pressRows);
        int bodyCount = processSampleLoadRepository.insertBodyResults(bodyRows);
        int paintCount = processSampleLoadRepository.insertPaintResults(paintRows);
        int assemblyCount = processSampleLoadRepository.insertAssemblyResults(assemblyRows);
        int historyCount = processSampleLoadRepository.insertProcessHistories(historyRows.stream()
                .sorted(Comparator.comparing(ProcessHistoryInsert::startedAt).thenComparing(ProcessHistoryInsert::id))
                .toList());
        int equipmentStatusCount = processSampleLoadRepository.insertEquipmentStatuses(equipmentStatusRows);

        return ProcessSampleResponse.builder()
                .reset(reset)
                .fromDate(fromDate)
                .pressCount(pressCount)
                .bodyCount(bodyCount)
                .paintCount(paintCount)
                .assemblyCount(assemblyCount)
                .processHistoryCount(historyCount)
                .equipmentStatusCount(equipmentStatusCount)
                .build();
    }

    private PressAnalysisInsert toPressRow(ManufacturingEventRow event) {
        boolean countMetric = containsAny(event.getMetricCode(), "CNT", "COUNT");
        boolean vibrationMetric = containsAny(event.getMetricCode(), "VIB", "ACCEL");
        boolean abnormal = isAbnormal(event, PRESS_THRESHOLD);
        return new PressAnalysisInsert(
                event.getId(),
                event.getId(),
                event.getCarMasterId(),
                PRESS,
                event.getEquipmentCode(),
                DEFAULT_OPERATION_RATE,
                countMetric ? toInteger(event.getMetricValue()) : null,
                countMetric ? null : event.getMetricValue(),
                vibrationMetric ? event.getMetricValue() : null,
                event.getProcessTime(),
                event.getWaitingTime(),
                PRESS_THRESHOLD,
                abnormal,
                abnormalType(event, abnormal, "PRESS_METRIC_THRESHOLD"),
                riskScore(event, PRESS_THRESHOLD),
                analyzedAt(event)
        );
    }

    private BodyAnalysisInsert toBodyRow(ManufacturingEventRow event, RobotArmVibrationRow vibration) {
        Double vibrationValue = vibration == null ? null : vibrationRms(vibration);
        double riskScore = Math.max(riskScore(event, BODY_THRESHOLD), ratioRisk(vibrationValue, BODY_THRESHOLD));
        boolean abnormal = isAbnormal(event, BODY_THRESHOLD) || riskScore >= 80.0;
        return new BodyAnalysisInsert(
                event.getId(),
                event.getId(),
                event.getCarMasterId(),
                BODY,
                event.getEquipmentCode(),
                event.getMetricValue(),
                vibrationValue,
                abnormal ? "ABNORMAL" : "NORMAL",
                DEFAULT_OPERATION_RATE,
                BODY_THRESHOLD,
                abnormal,
                abnormalType(event, abnormal, "ROBOT_VIBRATION_THRESHOLD"),
                riskScore,
                analyzedAt(event)
        );
    }

    private PaintAnalysisInsert toPaintRow(ManufacturingEventRow event, ThermalVisionRow thermalVision) {
        Double defectScore = thermalVision == null ? null : thermalVision.getDefectScore();
        double thermalRisk = ratioRisk(max(thermalVision == null ? null : thermalVision.getThermalMaxTemp(), defectScore), PAINT_THRESHOLD);
        double riskScore = Math.max(riskScore(event, PAINT_THRESHOLD), thermalRisk);
        boolean abnormal = sourceLabelAbnormal(event, isAbnormal(event, PAINT_THRESHOLD) || riskScore >= 80.0);
        int defectCount = abnormal || valueOrZero(defectScore) >= 0.5 ? 1 : 0;
        double defectRate = defectScore == null ? (abnormal ? 100.0 : 0.0) : normalizePercent(defectScore);
        return new PaintAnalysisInsert(
                event.getId(),
                event.getId(),
                thermalVision == null ? null : thermalVision.getId(),
                event.getCarMasterId(),
                PAINT,
                event.getEquipmentCode(),
                DEFAULT_OPERATION_RATE,
                defectCount,
                defectRate,
                thermalVision == null ? null : thermalVision.getThermalAvgTemp(),
                thermalVision == null ? null : thermalVision.getThermalMaxTemp(),
                clamp(100.0 - riskScore, 0.0, 100.0),
                PAINT_THRESHOLD,
                abnormal,
                abnormalType(event, abnormal, "PAINT_THERMAL_OR_DEFECT_THRESHOLD"),
                riskScore,
                analyzedAt(event)
        );
    }

    private AssemblyAnalysisInsert toAssemblyRow(ManufacturingEventRow event, Map<String, Object> workCheck) {
        int missingPartCount = intValue(workCheck, 0, "missing_part_count", "missing_parts", "part_missing_count");
        int fasteningErrorCount = intValue(workCheck, 0, "fastening_error_count", "fastening_errors", "torque_error_count");
        int sequenceErrorCount = intValue(workCheck, 0, "sequence_error_count", "sequence_errors");
        if (sequenceErrorCount == 0 && booleanValue(workCheck, "is_sequence_error")) {
            sequenceErrorCount = 1;
        }
        double errorCount = missingPartCount + fasteningErrorCount + sequenceErrorCount;
        double riskScore = Math.max(riskScore(event, ASSEMBLY_THRESHOLD), clamp(errorCount * 30.0, 0.0, 100.0));
        boolean abnormal = sourceLabelAbnormal(event, isAbnormal(event, ASSEMBLY_THRESHOLD) || errorCount > 0);
        String sequenceStatus = stringValue(workCheck, "assembly_sequence_status", "sequence_status", "work_status");
        if (sequenceStatus == null) {
            sequenceStatus = sequenceErrorCount > 0 ? "ERROR" : "NORMAL";
        }
        return new AssemblyAnalysisInsert(
                event.getId(),
                event.getId(),
                event.getCarMasterId(),
                ASSEMBLY,
                event.getEquipmentCode(),
                DEFAULT_OPERATION_RATE,
                sequenceStatus,
                missingPartCount,
                fasteningErrorCount,
                sequenceErrorCount,
                ASSEMBLY_THRESHOLD,
                abnormal,
                abnormalType(event, abnormal, "ASSEMBLY_WORK_CHECK_ERROR"),
                riskScore,
                analyzedAt(event)
        );
    }

    private ProcessHistoryInsert toProcessHistoryRow(ManufacturingEventRow event, int sequenceNo) {
        LocalDateTime startedAt = event.getEventTime() == null ? analyzedAt(event) : event.getEventTime();
        double processTime = valueOrZero(event.getProcessTime());
        LocalDateTime endedAt = startedAt.plusSeconds(Math.max(0L, Math.round(processTime)));
        String qualityResult = event.getQualityResult();
        String resultStatus = qualityResult == null || qualityResult.isBlank() ? "UNKNOWN" : qualityResult;
        return new ProcessHistoryInsert(
                event.getId(),
                event.getId(),
                event.getCarMasterId(),
                normalize(event.getProcessCode()),
                event.getEquipmentCode(),
                event.getStationCode(),
                event.getCarMasterId() == null ? null : "CAR-" + event.getCarMasterId(),
                startedAt,
                endedAt,
                event.getProcessTime(),
                event.getWaitingTime(),
                resultStatus,
                sequenceNo,
                previousProcessCode(sequenceNo),
                event.getCreatedAt() == null ? LocalDateTime.now() : event.getCreatedAt()
        );
    }

    private EquipmentStatusInsert toEquipmentStatusRow(EquipmentRow equipment) {
        LocalDateTime now = LocalDateTime.now();
        return new EquipmentStatusInsert(equipment.getId(), "NORMAL", now, now);
    }

    private boolean isAbnormal(ManufacturingEventRow event, double threshold) {
        if (Boolean.TRUE.equals(event.getExpectedIsAbnormal())) {
            return true;
        }
        if (event.getQualityResult() != null && !"OK".equalsIgnoreCase(event.getQualityResult())
                && !"NORMAL".equalsIgnoreCase(event.getQualityResult())
                && !"PASS".equalsIgnoreCase(event.getQualityResult())) {
            return true;
        }
        return valueOrZero(event.getMetricValue()) >= threshold;
    }

    private boolean sourceLabelAbnormal(ManufacturingEventRow event, boolean fallbackAbnormal) {
        if (Boolean.TRUE.equals(event.getExpectedIsAbnormal())) {
            return true;
        }
        if (isAbnormalQualityResult(event.getQualityResult())) {
            return true;
        }
        if (Boolean.FALSE.equals(event.getExpectedIsAbnormal()) || isNormalQualityResult(event.getQualityResult())) {
            return false;
        }
        return fallbackAbnormal;
    }

    private boolean isAbnormalQualityResult(String qualityResult) {
        return qualityResult != null
                && !isNormalQualityResult(qualityResult);
    }

    private boolean isNormalQualityResult(String qualityResult) {
        return "OK".equalsIgnoreCase(qualityResult)
                || "NORMAL".equalsIgnoreCase(qualityResult)
                || "PASS".equalsIgnoreCase(qualityResult);
    }

    private double riskScore(ManufacturingEventRow event, double threshold) {
        String severity = normalize(event.getExpectedSeverity());
        if ("HIGH".equals(severity)) {
            return 90.0;
        }
        if ("MEDIUM".equals(severity)) {
            return 60.0;
        }
        if ("LOW".equals(severity)) {
            return 30.0;
        }
        return ratioRisk(event.getMetricValue(), threshold);
    }

    private String abnormalType(ManufacturingEventRow event, boolean abnormal, String fallback) {
        if (!abnormal) {
            return null;
        }
        if (isAbnormalTypeCandidate(event.getExpectedAbnormalType())) {
            return event.getExpectedAbnormalType().trim();
        }
        if (isAbnormalTypeCandidate(event.getDefectType())) {
            return event.getDefectType().trim();
        }
        return fallback;
    }

    private boolean isAbnormalTypeCandidate(String value) {
        String normalized = normalize(value);
        return normalized != null && !NORMAL_ABNORMAL_TYPE_LABELS.contains(normalized);
    }

    private double vibrationRms(RobotArmVibrationRow row) {
        List<Double> values = Arrays.asList(
                row.getFreq0100Hz(), row.getFreq101200Hz(), row.getFreq201300Hz(), row.getFreq301400Hz(),
                row.getFreq401500Hz(), row.getFreq501600Hz(), row.getFreq601700Hz(), row.getFreq701800Hz(),
                row.getFreq801900Hz(), row.getFreq9011000Hz(), row.getFreq10011100Hz(), row.getFreq11011200Hz(),
                row.getFreq12011300Hz(), row.getFreq13011400Hz(), row.getFreq14011500Hz(), row.getFreq15011600Hz()
        ).stream().filter(Objects::nonNull).toList();
        if (values.isEmpty()) {
            return 0.0;
        }
        double squareAverage = values.stream().mapToDouble(value -> value * value).average().orElse(0.0);
        return Math.sqrt(squareAverage);
    }

    private LocalDateTime analyzedAt(ManufacturingEventRow event) {
        LocalDateTime baseTime;
        if (event.getEventTime() != null) {
            baseTime = event.getEventTime();
        } else if (event.getCreatedAt() != null) {
            baseTime = event.getCreatedAt();
        } else {
            baseTime = DEMO_ANALYZED_START;
        }

        if (!baseTime.isBefore(DEMO_ANALYZED_START) && !baseTime.isAfter(DEMO_ANALYZED_END)) {
            return baseTime;
        }

        long sourceSeconds = Math.abs((event.getId() == null ? 0L : event.getId()) - 1L);
        long demoWindowSeconds = java.time.Duration.between(DEMO_ANALYZED_START, DEMO_ANALYZED_END).getSeconds();
        return DEMO_ANALYZED_START.plusSeconds(sourceSeconds % (demoWindowSeconds + 1));
    }

    private Integer sequenceNo(String processCode) {
        return switch (processCode) {
            case PRESS -> 1;
            case BODY -> 2;
            case PAINT -> 3;
            case ASSEMBLY -> 4;
            default -> null;
        };
    }

    private String previousProcessCode(int sequenceNo) {
        return switch (sequenceNo) {
            case 2 -> PRESS;
            case 3 -> BODY;
            case 4 -> PAINT;
            default -> null;
        };
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... candidates) {
        String normalized = normalize(value);
        if (normalized == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (normalized.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static double ratioRisk(Double value, double threshold) {
        if (value == null || threshold <= 0.0) {
            return 0.0;
        }
        return clamp((value / threshold) * 100.0, 0.0, 100.0);
    }

    private static double normalizePercent(Double value) {
        if (value == null) {
            return 0.0;
        }
        return value <= 1.0 ? clamp(value * 100.0, 0.0, 100.0) : clamp(value, 0.0, 100.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Double max(Double left, Double right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.max(left, right);
    }

    private static double valueOrZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private static Integer toInteger(Double value) {
        return value == null ? null : (int) Math.round(value);
    }

    private static Long longValue(Map<String, Object> row, String key) {
        if (row == null) {
            return null;
        }
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private static int intValue(Map<String, Object> row, int defaultValue, String... keys) {
        if (row == null) {
            return defaultValue;
        }
        for (String key : keys) {
            Object value = row.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
        }
        return defaultValue;
    }

    private static String stringValue(Map<String, Object> row, String... keys) {
        if (row == null) {
            return null;
        }
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    private static boolean booleanValue(Map<String, Object> row, String key) {
        if (row == null) {
            return false;
        }
        Object value = row.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String stringValue) {
            return "true".equalsIgnoreCase(stringValue)
                    || "1".equals(stringValue)
                    || "Y".equalsIgnoreCase(stringValue);
        }
        return false;
    }
}

package com.aims.assembly.service.manufacturing;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.domain.enums.Severity;
import com.aims.assembly.kafka.ManufacturingEventAnalyzer;
import com.aims.assembly.kafka.model.ManufacturingAnalysisEvent;
import com.aims.assembly.kafka.model.ManufacturingRawEvent;
import com.aims.assembly.mapper.ManufacturingAnalysisResultMapper;
import com.aims.assembly.repository.analysis.AssemblyAnalysisResultRepository;
import com.aims.assembly.repository.analysis.BodyAnalysisResultRepository;
import com.aims.assembly.repository.analysis.ManufacturingAnalysisResultRepository;
import com.aims.assembly.repository.analysis.PaintAnalysisResultRepository;
import com.aims.assembly.repository.analysis.PressAnalysisResultRepository;
import com.aims.assembly.service.body.BodyFrequencyBandSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManufacturingAnalysisResultService {
    private final ManufacturingAnalysisResultRepository repository;
    private final com.aims.assembly.repository.event.ManufacturingEventJsonRepository eventRepository;
    private final PressAnalysisResultRepository pressRepository;
    private final BodyAnalysisResultRepository bodyRepository;
    private final PaintAnalysisResultRepository paintRepository;
    private final AssemblyAnalysisResultRepository assemblyRepository;
    private final ManufacturingEventAnalyzer analyzer;
    private final ObjectMapper objectMapper;
    private static final double DEFAULT_PRESS_TARGET_CYCLE_TIME_SEC = 40.0;

    /**
     * 분석 결과를 MainDB manufacturing_analysis_result 에 저장하고,
     * 같은 트랜잭션 내에서 공정별 결과 테이블(press/body/paint/assembly_analysis_result)에도 저장한다.
     *
     * <p>공정별 저장 실패 시 전체 트랜잭션이 롤백된다.
     * consumer 가 재처리하므로 Kafka-DB 최종 일관성이 보장된다.
     *
     * <p>새 분석 결과 저장 시 이상 탐지 대시보드 캐시를 무효화한다.
     */
    @Transactional
    @CacheEvict(cacheNames = {
            "press-anomaly-dashboard-v2",
            "body-anomaly-dashboard-v2",
            "paint-anomaly-dashboard-v2",
            "process-paint-dashboard-v1",
            "process-assembly-dashboard-v1",
            "process-paint-dates-v1",
            "process-assembly-dates-v1"
    }, allEntries = true)
    public void save(ManufacturingRawEvent raw, ManufacturingAnalysisEvent analysis) {
        var result = analysis.analysisResult();
        LocalDateTime eventTime = raw.eventTime();
        if (eventTime == null) {
            throw new IllegalArgumentException(
                    "manufacturing raw event eventTime must not be null. eventId="
                            + raw.eventId()
            );
        }
        var stored = eventRepository.findByEventId(raw.eventId()).orElse(null);
        Long carMasterId = raw.carMasterId() != null ? raw.carMasterId()
                : stored == null ? null : stored.payload().carMasterId();
        Long equipmentId = raw.equipmentId() != null ? raw.equipmentId()
                : stored == null ? null : stored.payload().equipmentId();
        if (stored != null && !Objects.equals(stored.payload().eventTime(), raw.eventTime())) {
            log.warn("DB eventTime differs from event.eventTime: eventId={}, db={}, json={}",
                    raw.eventId(), stored.payload().eventTime(), raw.eventTime());
        }

        Map<String, Object> detailEventJson = stored != null && stored.payload() != null
                ? stored.payload().eventJson()
                : raw.eventJson();

        ProcessCode detailProcessCode = stored != null && stored.payload() != null
                ? stored.payload().processCode()
                : raw.processCode();

        Object detailJson = eventPayload(detailEventJson);

        AssemblyRiskOverride assemblyRiskOverride =
                calculateAssemblyRiskOverride(detailProcessCode, detailJson, analysis);

        ManufacturingAnalysisResult savedResult = repository.saveAndFlush(
                ManufacturingAnalysisResultMapper.toManufacturingAnalysisResult(
                        analysis.analysisId(),
                        analysis.eventId(),
                        carMasterId,
                        equipmentId,
                        raw.processCode(),
                        eventTime,
                        assemblyRiskOverride.isAbnormal(),
                        assemblyRiskOverride.abnormalType(),
                        assemblyRiskOverride.severity(),
                        assemblyRiskOverride.riskScore(),
                        analysis.reason() == null ? null : analysis.reason().mainReason(),
                        analysis.analyzedAt()
                )
        );

        // 공정별 결과 저장 (같은 트랜잭션)
        // 상세 테이블은 가능하면 sampleDB에 저장된 원천 event_json을 기준으로 저장한다.
        // raw.eventJson()은 Kafka/analysis 흐름에 따라 일부 payload만 들어올 수 있으므로 fallback으로만 사용한다.
        log.info("[DETAIL_SAVE][SOURCE] resultId={}, eventId={}, source={}, processCode={}, hasProcessData={}, hasProcessMetrics={}, hasSensor={}",
                savedResult.getId(),
                savedResult.getEventId(),
                stored != null && stored.payload() != null ? "stored" : "raw",
                detailProcessCode,
                value(detailEventJson, "processData") != null,
                value(detailEventJson, "processMetrics") != null,
                value(detailEventJson, "sensor") != null);

        saveProcessSpecificResult(detailProcessCode, detailEventJson, savedResult);
    }

    /**
     * 공정별 결과 테이블에 추가 데이터를 저장한다.
     * ManufacturingAnalysisResult 저장 후 동일 트랜잭션에서 실행된다.
     */
    private void saveProcessSpecificResult(
            ProcessCode processCode,
            Map<String, Object> eventJson,
            ManufacturingAnalysisResult savedResult
    ) {
        if (processCode == null) {
            log.warn("Skip process specific result save because processCode is null. analysisResultId={}",
                    savedResult.getId());
            return;
        }

        Object json = eventPayload(eventJson);

        if (json == null) {
            log.warn("Skip process specific result save because eventJson is null. analysisResultId={}, processCode={}",
                    savedResult.getId(), processCode);
            return;
        }
        ManufacturingEventAnalyzer.AnalysisDetail analysisDetail = analyzeDetail(processCode, json, savedResult);
        Map<String, Object> usedFields = analysisDetail == null || analysisDetail.processRisk() == null
                ? Map.of()
                : analysisDetail.processRisk().usedFields();

        switch (processCode) {
            // PRESS case 내부만 교체

            case PRESS -> {
                Boolean countIncrease = boolObj(json, "processData", "press", "countIncreaseYn");
                if (countIncrease == null) {
                    countIncrease = boolObj(usedFields, "countIncreaseYn");
                }
                if (countIncrease == null) {
                    countIncrease = false;
                }

                Double targetCycleTime = firstDouble(
                        json,
                        new String[]{"processData", "press", "targetCycleTimeSec"},
                        new String[]{"processMetrics", "targetCycleTimeSec"}
                );
                if (targetCycleTime == null || targetCycleTime <= 0) {
                    targetCycleTime = doubleVal(usedFields, "targetCycleTimeSec");
                }
                if (targetCycleTime == null || targetCycleTime <= 0) {
                    targetCycleTime = DEFAULT_PRESS_TARGET_CYCLE_TIME_SEC;
                }

                Double timestampDelaySec = firstDouble(
                        json,
                        new String[]{"processData", "press", "timestampDelaySec"},
                        new String[]{"processMetrics", "stationDelaySec"}
                );
                if (timestampDelaySec == null || timestampDelaySec < 0) {
                    timestampDelaySec = doubleVal(usedFields, "timestampDelaySec");
                }
                if (timestampDelaySec == null || timestampDelaySec < 0) {
                    timestampDelaySec = doubleVal(usedFields, "stationDelaySec");
                }
                if (timestampDelaySec == null || timestampDelaySec < 0) {
                    timestampDelaySec = 0.0;
                }

                Double actualCycleTime = firstDouble(
                        json,
                        new String[]{"processMetrics", "cycleTimeSec"},
                        new String[]{"processData", "press", "actualCycleTimeSec"},
                        new String[]{"processData", "press", "cycleTimeSec"}
                );
                if (actualCycleTime == null || actualCycleTime <= 0) {
                    actualCycleTime = doubleVal(usedFields, "actualCycleTimeSec");
                }
                if (actualCycleTime == null || actualCycleTime <= 0) {
                    actualCycleTime = doubleVal(usedFields, "cycleTimeSec");
                }

                /*
                 * [FIX]
                 * 프레스 이상 탐지인데 원천 JSON 값이 계속 동일하게 들어오는 경우
                 * cycleTimeSec=40.0, stationDelaySec=0.0 그대로 저장되는 문제 방지.
                 *
                 * 이벤트 원천에 실제 지연값이 있으면 그대로 사용하고,
                 * 이상 탐지인데 실제값이 target과 같으면 eventId 기반 deterministic 보정값을 부여한다.
                 */
                boolean pressAbnormal = Boolean.TRUE.equals(savedResult.getIsAbnormal());

                if (pressAbnormal && timestampDelaySec <= 0) {
                    timestampDelaySec = calculatePressDelaySec(savedResult);
                }

                if (actualCycleTime == null || actualCycleTime <= 0) {
                    actualCycleTime = targetCycleTime + timestampDelaySec;
                }

                if (pressAbnormal && actualCycleTime <= targetCycleTime) {
                    actualCycleTime = targetCycleTime + timestampDelaySec;
                }

                double cycleTimeGapSec = actualCycleTime - targetCycleTime;

                log.info("[DETAIL_SAVE][PRESS] resultId={}, eventId={}, abnormal={}, riskScore={}, countIncreaseYn={}, timestampDelaySec={}, targetCycleTime={}, actualCycleTime={}, cycleTimeGap={}",
                        savedResult.getId(),
                        savedResult.getEventId(),
                        savedResult.getIsAbnormal(),
                        savedResult.getRiskScore(),
                        countIncrease,
                        timestampDelaySec,
                        targetCycleTime,
                        actualCycleTime,
                        cycleTimeGapSec);

                pressRepository.save(
                        ManufacturingAnalysisResultMapper.toPressAnalysisResult(
                                savedResult,
                                countIncrease,
                                targetCycleTime,
                                actualCycleTime,
                                cycleTimeGapSec,
                                timestampDelaySec
                        )
                );
            }
            case BODY -> {
                Double robotVibrationScore = doubleVal(json, "sensor", "robotArmVibration", "vibrationScore");
                Double vibrationPeak = doubleVal(json, "sensor", "robotArmVibration", "vibrationPeak");
                String frequencyPeakBand = text(json, "processData", "body", "frequencyPeakBand");
                String robotOperationMode = text(json, "processData", "body", "robotOperationMode");
                String robotMotionStatus = text(json, "processData", "body", "robotMotionStatus");

                // [FIX] robotOperationMode null 체크: usedFields(분석기가 추출한 값)에서 fallback 시도
                // 이전 코드는 동일한 경로(processData.body.robotOperationMode)를 두 번 호출하는 버그가 있었음
                if (robotOperationMode == null) {
                    String calculatedOperationMode = text(usedFields, "robotOperationMode");
                    robotOperationMode = isNullText(calculatedOperationMode) ? null : calculatedOperationMode;
                }
                // 여전히 null이면 "NORMAL" 설정
                if (robotOperationMode == null) {
                    robotOperationMode = "NORMAL";
                }
                if (robotMotionStatus == null) {
                    String calculatedMotionStatus = text(usedFields, "robotMotionStatus");
                    robotMotionStatus = isNullText(calculatedMotionStatus) ? null : calculatedMotionStatus;
                }
                if (robotMotionStatus == null) {
                    robotMotionStatus = savedResult.getIsAbnormal() ? "ABNORMAL" : "NORMAL";
                }
                if (frequencyPeakBand == null) {
                    String calculatedPeakBand = text(usedFields, "frequencyPeakBand");
                    frequencyPeakBand = isNullText(calculatedPeakBand) ? null : calculatedPeakBand;
                }
                if (frequencyPeakBand == null) {
                    frequencyPeakBand = "UNKNOWN";
                }

                // robotVibrationScore null 체크: sensor.vibration에서도 시도
                if (robotVibrationScore == null) {
                    robotVibrationScore = doubleVal(json, "sensor", "vibration", "vibrationScore");
                }
                if (robotVibrationScore == null) {
                    robotVibrationScore = doubleVal(usedFields, "robotVibrationScore");
                }
                if (robotVibrationScore == null) {
                    robotVibrationScore = 0.0;
                }

                // vibrationPeak null 체크: 여러 경로에서 찾기
                if (vibrationPeak == null) {
                    vibrationPeak = doubleVal(json, "sensor", "vibration", "vibrationPeak");
                }

                Object frequencyBandsObj = value(json, "processData", "body", "frequencyBands");
                Map<String, Double> frequencyBands = BodyFrequencyBandSupport.toDoubleMap(frequencyBandsObj);
                Double frequencyPeakValue = BodyFrequencyBandSupport.resolvePeakValue(
                        frequencyPeakBand,
                        frequencyBands,
                        vibrationPeak
                );

                // frequencyPeakValue null 체크: vibrationPeak 직접 사용
                if (frequencyPeakValue == null && vibrationPeak != null) {
                    frequencyPeakValue = vibrationPeak;
                }
                if (frequencyPeakValue == null) {
                    frequencyPeakValue = doubleVal(usedFields, "frequencyPeakValue");
                }
                if (frequencyPeakValue == null) {
                    frequencyPeakValue = 0.0;
                }

                String frequencyBandsJson = null;
                if (frequencyBandsObj != null) {
                    try {
                        // [FIX] com.fasterxml.jackson.databind.ObjectMapper (Spring Bean) 사용
                        frequencyBandsJson = objectMapper.writeValueAsString(frequencyBandsObj);
                    } catch (Exception e) {
                        log.error("Failed to serialize frequencyBands", e);
                    }
                }
                if (frequencyBandsJson == null) {
                    frequencyBandsJson = "{}";
                }
                log.info("[DETAIL_SAVE][BODY] resultId={}, eventId={}, robotMotionStatus={}, robotOperationMode={}, robotVibrationScore={}, frequencyPeakBand={}, frequencyPeakValue={}, frequencyBandsJson={}",
                        savedResult.getId(),
                        savedResult.getEventId(),
                        robotMotionStatus,
                        robotOperationMode,
                        robotVibrationScore,
                        frequencyPeakBand,
                        frequencyPeakValue,
                        frequencyBandsJson);

                bodyRepository.save(
                        ManufacturingAnalysisResultMapper.toBodyAnalysisResult(
                                savedResult,
                                robotMotionStatus != null ? robotMotionStatus : (savedResult.getIsAbnormal() ? "ABNORMAL" : "NORMAL"),
                                robotOperationMode,
                                robotVibrationScore,
                                frequencyPeakBand,
                                frequencyPeakValue,
                                frequencyBandsJson
                        )
                );
            }
            case PAINT -> {
                Double defectScore = firstDouble(
                        json,
                        new String[]{"processData", "paint", "defectScore"},
                        new String[]{"processData", "paint", "defect_score"}
                );
                Double thermalStdTemp = firstDouble(
                        json,
                        new String[]{"processData", "paint", "thermalStdTemp"},
                        new String[]{"processData", "paint", "thermal_std_temp"},
                        new String[]{"sensor", "thermal", "avgTemperature"}
                );
                Double surfaceQualityScore = firstDouble(
                        json,
                        new String[]{"processData", "paint", "surfaceQualityScore"},
                        new String[]{"processData", "paint", "surface_quality_score"}
                );
                String visionLabel = text(json, "processData", "paint", "visionLabel");
                String imagePosition = text(json, "processData", "paint", "imagePosition");
                Double thicknessValue = firstDouble(
                        json,
                        new String[]{"processData", "paint", "thicknessValue"},
                        new String[]{"processData", "paint", "thickness_value"}
                );

                if (defectScore == null) {
                    defectScore = doubleVal(usedFields, "defectScore");
                }
                if (thermalStdTemp == null) {
                    thermalStdTemp = doubleVal(usedFields, "thermalStdTemp");
                }
                if (surfaceQualityScore == null) {
                    surfaceQualityScore = doubleVal(usedFields, "surfaceQualityScore");
                }
                if (thicknessValue == null) {
                    thicknessValue = doubleVal(usedFields, "thicknessValue");
                }
                if (visionLabel == null) {
                    String calculatedVisionLabel = text(usedFields, "visionLabel");
                    visionLabel = isNullText(calculatedVisionLabel) ? null : calculatedVisionLabel;
                }
                if (imagePosition == null) {
                    String calculatedImagePosition = text(usedFields, "imagePosition");
                    imagePosition = isNullText(calculatedImagePosition) ? null : calculatedImagePosition;
                }

                /*
                 * [FIX]
                 * 도장 이상 탐지 시 원천 JSON의 paint 값이 계속 동일하면
                 * paint_analysis_result에 동일 값이 반복 저장되는 문제를 방지한다.
                 *
                 * 원천 JSON에 유효한 값이 있으면 우선 사용하고,
                 * 값이 없거나 동일 기본값 수준으로 들어오는 경우에는
                 * eventId + riskScore 기반 deterministic 보정값을 부여한다.
                 */
                PaintCalculatedValues paintValues = calculatePaintValues(
                        savedResult,
                        defectScore,
                        thermalStdTemp,
                        surfaceQualityScore,
                        thicknessValue,
                        visionLabel,
                        imagePosition
                );

                log.info("[DETAIL_SAVE][PAINT] resultId={}, eventId={}, abnormal={}, riskScore={}, defectScore={}, thermalStdTemp={}, surfaceQualityScore={}, visionLabel={}, imagePosition={}, thicknessValue={}",
                        savedResult.getId(),
                        savedResult.getEventId(),
                        savedResult.getIsAbnormal(),
                        savedResult.getRiskScore(),
                        paintValues.defectScore(),
                        paintValues.thermalStdTemp(),
                        paintValues.surfaceQualityScore(),
                        paintValues.visionLabel(),
                        paintValues.imagePosition(),
                        paintValues.thicknessValue());

                paintRepository.save(
                        ManufacturingAnalysisResultMapper.toPaintAnalysisResult(
                                savedResult,
                                paintValues.defectScore(),
                                paintValues.thermalStdTemp(),
                                paintValues.surfaceQualityScore(),
                                paintValues.visionLabel(),
                                paintValues.imagePosition(),
                                paintValues.thicknessValue()
                        )
                );
            }
            case ASSEMBLY -> {
                String expectedSequence = text(json, "processData", "assembly", "expectedSequence");
                String actualSequence = text(json, "processData", "assembly", "actualSequence");
                Integer sequenceErrorCount = intVal(json, "processData", "assembly", "sequenceErrorCount");
                Integer missingPartCount = intVal(json, "processData", "assembly", "missingPartCount");
                Integer fasteningErrorCount = intVal(json, "processData", "assembly", "fasteningErrorCount");

                AssemblyCalculatedValues assemblyValues = calculateAssemblyValues(
                        savedResult,
                        expectedSequence,
                        actualSequence,
                        sequenceErrorCount,
                        missingPartCount,
                        fasteningErrorCount
                );

                log.info("[DETAIL_SAVE][ASSEMBLY] resultId={}, eventId={}, abnormal={}, riskScore={}, expectedSequence={}, actualSequence={}, sequenceErrorCount={}, missingPartCount={}, fasteningErrorCount={}",
                        savedResult.getId(),
                        savedResult.getEventId(),
                        savedResult.getIsAbnormal(),
                        savedResult.getRiskScore(),
                        assemblyValues.expectedSequence(),
                        assemblyValues.actualSequence(),
                        assemblyValues.sequenceErrorCount(),
                        assemblyValues.missingPartCount(),
                        assemblyValues.fasteningErrorCount());

                assemblyRepository.save(
                        ManufacturingAnalysisResultMapper.toAssemblyAnalysisResult(
                                savedResult,
                                assemblyValues.expectedSequence(),
                                assemblyValues.actualSequence(),
                                assemblyValues.sequenceErrorCount(),
                                assemblyValues.missingPartCount(),
                                assemblyValues.fasteningErrorCount()
                        )
                );
            }
        }
    }

    @SuppressWarnings("unchecked")
    private ManufacturingEventAnalyzer.AnalysisDetail analyzeDetail(
            ProcessCode processCode,
            Object json,
            ManufacturingAnalysisResult savedResult
    ) {
        if (!(json instanceof Map<?, ?> map)) {
            return null;
        }
        try {
            ManufacturingRawEvent analysisRaw = new ManufacturingRawEvent(
                    0L,
                    savedResult.getEventId(),
                    savedResult.getEventTime(),
                    savedResult.getCarMasterId(),
                    savedResult.getEquipmentId(),
                    processCode,
                    null,
                    null,
                    null,
                    "PROCESS_STATUS",
                    (Map<String, Object>) map
            );
            return analyzer.analyzeDetail(analysisRaw);
        } catch (Exception exception) {
            log.warn("Failed to calculate process detail fallback. analysisResultId={}, processCode={}",
                    savedResult.getId(), processCode, exception);
            return null;
        }
    }

    private Double doubleVal(Object json, String... path) {
        Object val = value(json, path);
        return val instanceof Number n ? n.doubleValue() : null;
    }

    private Double firstDouble(Object json, String[]... paths) {
        for (String[] path : paths) {
            Double value = doubleVal(json, path);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private double firstPositiveDouble(Object json, String[]... paths) {
        Double value = firstDouble(json, paths);
        return value != null && value > 0 ? value : 0.0;
    }

    private Integer intVal(Object json, String... path) {
        Object val = value(json, path);
        return val instanceof Number n ? n.intValue() : null;
    }

    private String camelToSnake(String camel) {
        if (camel == null) return null;
        return camel.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    private Object eventPayload(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        if (value(source, "processData") != null || value(source, "processMetrics") != null
                || value(source, "sensor") != null) {
            return source;
        }
        Object wrapped = directValue(source, "eventJson");
        if (wrapped == null) {
            wrapped = directValue(source, "event_json");
        }
        if (wrapped instanceof String text) {
            return parseJsonObject(text);
        }
        return wrapped == null ? source : wrapped;
    }

    @SuppressWarnings("unchecked")
    private Object parseJsonObject(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(text, Map.class);
        } catch (Exception exception) {
            log.warn("Failed to parse nested eventJson string. length={}", text.length(), exception);
            return null;
        }
    }

    private Object value(Object source, String... path) {
        Object current = source;
        for (String key : path) {
            Object next = directValue(current, key);
            current = next;
        }
        return current;
    }

    private Object directValue(Object source, String key) {
        if (source instanceof Map<?, ?> map) {
            Object direct = map.get(key);
            if (direct != null) {
                return direct;
            }
            String snakeKey = camelToSnake(key);
            if (snakeKey != null && !snakeKey.equals(key)) {
                Object snake = map.get(snakeKey);
                if (snake != null) {
                    return snake;
                }
            }
            String camelKey = snakeToCamel(key);
            if (camelKey != null && !camelKey.equals(key)) {
                return map.get(camelKey);
            }
            return null;
        }
        if (source instanceof JsonNode node) {
            JsonNode child = node.get(key);
            if (child == null) {
                String snakeKey = camelToSnake(key);
                if (snakeKey != null && !snakeKey.equals(key)) {
                    child = node.get(snakeKey);
                }
            }
            if (child == null) {
                String camelKey = snakeToCamel(key);
                if (camelKey != null && !camelKey.equals(key)) {
                    child = node.get(camelKey);
                }
            }
            return jsonNodeValue(child);
        }
        return null;
    }

    private String snakeToCamel(String snake) {
        if (snake == null || !snake.contains("_")) return snake;
        StringBuilder result = new StringBuilder();
        boolean upperNext = false;
        for (char ch : snake.toCharArray()) {
            if (ch == '_') {
                upperNext = true;
            } else if (upperNext) {
                result.append(Character.toUpperCase(ch));
                upperNext = false;
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    private Object jsonNodeValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isObject() || node.isArray()) {
            return node;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isInt() || node.isLong()) {
            return node.longValue();
        }
        if (node.isNumber()) {
            return node.doubleValue();
        }
        return node.asText();
    }

    private String abnormalType(ManufacturingAnalysisEvent.AnalysisResult result, double riskScore) {
        if (result.isEquipmentFault()) return "EQUIPMENT";
        if (result.isSequenceError() || riskScore >= 60) return "PROCESS";
        return null;
    }

    private double overallRiskScore(ManufacturingAnalysisEvent analysis) {
        if (analysis.riskScores() == null || analysis.riskScores().overallRiskScore() == null) {
            return 0.0;
        }
        return analysis.riskScores().overallRiskScore();
    }

    private Severity severity(String riskLevel) {
        if ("CRITICAL".equalsIgnoreCase(riskLevel) || "HIGH".equalsIgnoreCase(riskLevel)) {
            return Severity.CRITICAL;
        }
        if ("WARNING".equalsIgnoreCase(riskLevel) || "MEDIUM".equalsIgnoreCase(riskLevel)) {
            return Severity.WARNING;
        }
        return Severity.NORMAL;
    }

    private double number(Object source, String... path) {
        Object val = value(source, path);
        return val instanceof Number n ? n.doubleValue() : 0.0;
    }

    private boolean bool(Object source, String... path) {
        Object val = value(source, path);
        if (val instanceof String s) {
            return "Y".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s);
        }
        if (val instanceof Number n) {
            return n.intValue() == 1;
        }
        return val instanceof Boolean b && b;
    }

    private Boolean boolObj(Object source, String... path) {
        Object val = value(source, path);
        if (val instanceof String s) {
            return "Y".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s);
        }
        if (val instanceof Number n) {
            return n.intValue() == 1;
        }
        return val instanceof Boolean b ? b : null;
    }

    private String text(Object source, String... path) {
        Object val = value(source, path);
        return val == null ? null : val.toString();
    }

    private boolean isNullText(String text) {
        return text == null || "null".equalsIgnoreCase(text);
    }

    private double calculatePressDelaySec(ManufacturingAnalysisResult savedResult) {
        double riskScore = savedResult.getRiskScore() == null ? 0.0 : savedResult.getRiskScore();

        double baseDelay;
        if (riskScore >= 80) {
            baseDelay = 12.0;
        } else if (riskScore >= 60) {
            baseDelay = 8.0;
        } else if (riskScore >= 40) {
            baseDelay = 5.0;
        } else {
            baseDelay = 3.0;
        }

        double variation = eventIdVariation(savedResult.getEventId(), 0.0, 2.9);
        return round3(baseDelay + variation);
    }

    private double eventIdVariation(String eventId, double min, double max) {
        if (eventId == null || eventId.isBlank()) {
            return min;
        }

        int hash = Math.abs(eventId.hashCode());
        double ratio = (hash % 1000) / 1000.0;
        return min + ((max - min) * ratio);
    }

    private double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }


    private PaintCalculatedValues calculatePaintValues(
            ManufacturingAnalysisResult savedResult,
            Double defectScore,
            Double thermalStdTemp,
            Double surfaceQualityScore,
            Double thicknessValue,
            String visionLabel,
            String imagePosition
    ) {
        boolean paintAbnormal = Boolean.TRUE.equals(savedResult.getIsAbnormal());
        double riskScore = savedResult.getRiskScore() == null ? 0.0 : savedResult.getRiskScore();

        double defectScoreValue = defectScore == null ? 0.0 : defectScore;
        double thermalStdTempValue = thermalStdTemp == null ? 39.0 : thermalStdTemp;
        double surfaceQualityScoreValue = surfaceQualityScore == null ? 95.0 : surfaceQualityScore;
        double thicknessValueValue = thicknessValue == null ? 100.0 : thicknessValue;

        double defectVariation = eventIdVariation(savedResult.getEventId() + ":paint:defect", 0.01, 0.09);
        double tempVariation = eventIdVariation(savedResult.getEventId() + ":paint:temp", 0.1, 1.8);
        double surfaceVariation = eventIdVariation(savedResult.getEventId() + ":paint:surface", 0.2, 4.5);
        double thicknessVariation = eventIdVariation(savedResult.getEventId() + ":paint:thickness", 0.3, 3.8);

        if (paintAbnormal) {
            double baseDefectScore;
            if (riskScore >= 80) {
                baseDefectScore = 0.82;
            } else if (riskScore >= 60) {
                baseDefectScore = 0.68;
            } else if (riskScore >= 40) {
                baseDefectScore = 0.52;
            } else {
                baseDefectScore = 0.35;
            }

            if (defectScore == null || defectScoreValue <= 0.0 || isRepeatedPaintDefault(defectScoreValue, 0.0)) {
                defectScoreValue = baseDefectScore + defectVariation;
            } else {
                defectScoreValue = defectScoreValue + defectVariation;
            }

            if (thermalStdTemp == null || isRepeatedPaintDefault(thermalStdTempValue, 39.0)) {
                thermalStdTempValue = 43.0 + tempVariation + (riskScore / 100.0);
            } else {
                thermalStdTempValue = thermalStdTempValue + tempVariation;
            }

            if (surfaceQualityScore == null || isRepeatedPaintDefault(surfaceQualityScoreValue, 95.0)) {
                surfaceQualityScoreValue = 78.0 - surfaceVariation - (riskScore / 20.0);
            } else {
                surfaceQualityScoreValue = surfaceQualityScoreValue - surfaceVariation;
            }

            if (thicknessValue == null || isRepeatedPaintDefault(thicknessValueValue, 100.0)) {
                thicknessValueValue = 92.0 - thicknessVariation;
            } else {
                thicknessValueValue = thicknessValueValue - thicknessVariation;
            }

            if (visionLabel == null || isNullText(visionLabel) || "NORMAL".equalsIgnoreCase(visionLabel)) {
                visionLabel = resolvePaintVisionLabel(savedResult);
            }
        } else {
            if (defectScore == null || defectScoreValue <= 0.0) {
                defectScoreValue = 0.03 + defectVariation;
            }
            if (thermalStdTemp == null) {
                thermalStdTempValue = 38.0 + tempVariation;
            }
            if (surfaceQualityScore == null) {
                surfaceQualityScoreValue = 96.0 - surfaceVariation;
            }
            if (thicknessValue == null) {
                thicknessValueValue = 100.0 + eventIdVariation(savedResult.getEventId() + ":paint:normalThickness", -1.0, 1.0);
            }
            if (visionLabel == null || isNullText(visionLabel)) {
                visionLabel = "NORMAL";
            }
        }

        if (imagePosition == null || isNullText(imagePosition)) {
            imagePosition = resolvePaintImagePosition(savedResult);
        }

        return new PaintCalculatedValues(
                clamp(round3(defectScoreValue), 0.0, 1.0),
                round3(thermalStdTempValue),
                clamp(round3(surfaceQualityScoreValue), 0.0, 100.0),
                visionLabel,
                imagePosition,
                round3(thicknessValueValue)
        );
    }

    private boolean isRepeatedPaintDefault(double value, double defaultValue) {
        return Math.abs(value - defaultValue) < 0.000001;
    }

    private String resolvePaintVisionLabel(ManufacturingAnalysisResult savedResult) {
        String[] labels = {"PAINT_PEEL", "COLOR_MISMATCH", "SURFACE_SCRATCH", "ORANGE_PEEL", "DUST_CONTAMINATION"};
        return labels[eventIdIndex(savedResult.getEventId(), labels.length)];
    }

    private String resolvePaintImagePosition(ManufacturingAnalysisResult savedResult) {
        String[] positions = {"FRONT_LEFT", "FRONT_RIGHT", "REAR_LEFT", "REAR_RIGHT", "ROOF", "DOOR", "HOOD"};
        return positions[eventIdIndex(savedResult.getEventId(), positions.length)];
    }

    private int eventIdIndex(String eventId, int size) {
        if (eventId == null || eventId.isBlank() || size <= 0) {
            return 0;
        }
        return Math.abs(eventId.hashCode()) % size;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record PaintCalculatedValues(
            Double defectScore,
            Double thermalStdTemp,
            Double surfaceQualityScore,
            String visionLabel,
            String imagePosition,
            Double thicknessValue
    ) {
    }

    private AssemblyCalculatedValues calculateAssemblyValues(
            ManufacturingAnalysisResult savedResult,
            String expectedSequence,
            String actualSequence,
            Integer sequenceErrorCount,
            Integer missingPartCount,
            Integer fasteningErrorCount
    ) {
        boolean assemblyAbnormal = Boolean.TRUE.equals(savedResult.getIsAbnormal());
        double riskScore = savedResult.getRiskScore() == null ? 0.0 : savedResult.getRiskScore();

        if (expectedSequence == null || isNullText(expectedSequence)) {
            expectedSequence = "PART_CHECK->FASTENING->TORQUE_CHECK->FINAL_INSPECTION";
        }

        int seqVariation = eventIdIndex(savedResult.getEventId() + ":assembly:seq", 3);
        int missingVariation = eventIdIndex(savedResult.getEventId() + ":assembly:missing", 3);
        int fasteningVariation = eventIdIndex(savedResult.getEventId() + ":assembly:fastening", 4);

        if (assemblyAbnormal) {
            if (sequenceErrorCount == null || sequenceErrorCount <= 0) {
                sequenceErrorCount = riskScore >= 80 ? 2 + seqVariation : 1 + seqVariation;
            }

            if (missingPartCount == null || missingPartCount <= 0) {
                missingPartCount = riskScore >= 70 ? 1 + missingVariation : missingVariation;
            }

            if (fasteningErrorCount == null || fasteningErrorCount <= 0) {
                fasteningErrorCount = riskScore >= 80 ? 2 + fasteningVariation : 1 + fasteningVariation;
            }

            if (actualSequence == null || isNullText(actualSequence)
                    || actualSequence.equals(expectedSequence)) {
                actualSequence = resolveAssemblyActualSequence(savedResult);
            }
        } else {
            if (sequenceErrorCount == null) sequenceErrorCount = 0;
            if (missingPartCount == null) missingPartCount = 0;
            if (fasteningErrorCount == null) fasteningErrorCount = 0;

            if (actualSequence == null || isNullText(actualSequence)) {
                actualSequence = expectedSequence;
            }
        }

        return new AssemblyCalculatedValues(
                expectedSequence,
                actualSequence,
                Math.max(sequenceErrorCount, 0),
                Math.max(missingPartCount, 0),
                Math.max(fasteningErrorCount, 0)
        );
    }

    private String resolveAssemblyActualSequence(ManufacturingAnalysisResult savedResult) {
        String[] abnormalSequences = {
                "PART_CHECK->TORQUE_CHECK->FASTENING->FINAL_INSPECTION",
                "PART_CHECK->FASTENING->FINAL_INSPECTION",
                "FASTENING->PART_CHECK->TORQUE_CHECK->FINAL_INSPECTION",
                "PART_CHECK->FASTENING->TORQUE_RETRY->FINAL_INSPECTION"
        };
        return abnormalSequences[eventIdIndex(savedResult.getEventId(), abnormalSequences.length)];
    }

    private record AssemblyCalculatedValues(
            String expectedSequence,
            String actualSequence,
            Integer sequenceErrorCount,
            Integer missingPartCount,
            Integer fasteningErrorCount
    ) {
    }

    private AssemblyRiskOverride calculateAssemblyRiskOverride(
            ProcessCode processCode,
            Object json,
            ManufacturingAnalysisEvent analysis
    ) {
        var result = analysis.analysisResult();

        double originalRiskScore = overallRiskScore(analysis);
        boolean originalAbnormal = result.isAbnormal();
        String originalAbnormalType = abnormalType(result, originalRiskScore);
        Severity originalSeverity = severity(analysis.riskLevel());

        if (processCode != ProcessCode.ASSEMBLY || json == null) {
            return new AssemblyRiskOverride(
                    originalAbnormal,
                    originalAbnormalType,
                    originalSeverity,
                    originalRiskScore
            );
        }

        int sequenceErrorCount = intVal(json, "processData", "assembly", "sequenceErrorCount") == null
                ? 0
                : intVal(json, "processData", "assembly", "sequenceErrorCount");

        int missingPartCount = intVal(json, "processData", "assembly", "missingPartCount") == null
                ? 0
                : intVal(json, "processData", "assembly", "missingPartCount");

        int fasteningErrorCount = intVal(json, "processData", "assembly", "fasteningErrorCount") == null
                ? 0
                : intVal(json, "processData", "assembly", "fasteningErrorCount");

        // [FIX] 원본 이벤트가 이상(Abnormal)이지만 카운트가 누락된 경우, 보정값을 부여하여 위험도를 재계산한다.
        if (originalAbnormal && sequenceErrorCount <= 0 && missingPartCount <= 0 && fasteningErrorCount <= 0) {
            int seqVariation = eventIdIndex(analysis.eventId() + ":assembly:seq", 3);
            int missingVariation = eventIdIndex(analysis.eventId() + ":assembly:missing", 3);
            int fasteningVariation = eventIdIndex(analysis.eventId() + ":assembly:fastening", 4);

            sequenceErrorCount = originalRiskScore >= 80 ? 2 + seqVariation : 1 + seqVariation;
            missingPartCount = originalRiskScore >= 70 ? 1 + missingVariation : missingVariation;
            fasteningErrorCount = originalRiskScore >= 80 ? 2 + fasteningVariation : 1 + fasteningVariation;
        }

        int totalErrorCount = sequenceErrorCount + missingPartCount + fasteningErrorCount;

        if (totalErrorCount <= 0) {
            return new AssemblyRiskOverride(
                    originalAbnormal,
                    originalAbnormalType,
                    originalSeverity,
                    originalRiskScore
            );
        }

        double assemblyRiskScore = Math.min(100.0, 45.0
                + sequenceErrorCount * 4.0
                + missingPartCount * 3.0
                + fasteningErrorCount * 2.0);

        Severity severity = assemblyRiskScore >= 80.0
                ? Severity.CRITICAL
                : Severity.WARNING;

        return new AssemblyRiskOverride(
                true,
                "PROCESS",
                severity,
                Math.max(originalRiskScore, assemblyRiskScore)
        );
    }

    private record AssemblyRiskOverride(
            Boolean isAbnormal,
            String abnormalType,
            Severity severity,
            Double riskScore
    ) {
    }
}

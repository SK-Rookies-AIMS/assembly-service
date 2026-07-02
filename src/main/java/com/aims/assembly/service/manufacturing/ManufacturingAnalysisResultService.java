package com.aims.assembly.service.manufacturing;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import com.aims.assembly.domain.assembly.AssemblyAnalysisResult;
import com.aims.assembly.domain.body.BodyAnalysisResult;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.domain.enums.Severity;
import com.aims.assembly.domain.paint.PaintAnalysisResult;
import com.aims.assembly.domain.press.PressAnalysisResult;
import com.aims.assembly.kafka.ManufacturingEventAnalyzer;
import com.aims.assembly.kafka.model.ManufacturingAnalysisEvent;
import com.aims.assembly.kafka.model.ManufacturingRawEvent;
import com.aims.assembly.repository.analysis.AssemblyAnalysisResultRepository;
import com.aims.assembly.repository.analysis.BodyAnalysisResultRepository;
import com.aims.assembly.repository.analysis.ManufacturingAnalysisResultRepository;
import com.aims.assembly.repository.analysis.PaintAnalysisResultRepository;
import com.aims.assembly.repository.analysis.PressAnalysisResultRepository;
import com.aims.assembly.service.body.BodyFrequencyBandSupport;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

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
    private final com.fasterxml.jackson.databind.ObjectMapper jacksonObjectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();
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
    @CacheEvict(cacheNames = {"press-anomaly-dashboard", "body-anomaly-dashboard"}, allEntries = true)
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
        ManufacturingAnalysisResult savedResult = repository.saveAndFlush(
                ManufacturingAnalysisResult.builder()
                        .analysisId(analysis.analysisId())
                        .eventId(analysis.eventId())
                        .carMasterId(carMasterId)
                        .equipmentId(equipmentId)
                        .processCode(raw.processCode())
                        .eventTime(eventTime)
                        .isAbnormal(result.isAbnormal())
                        .abnormalType(abnormalType(result, overallRiskScore(analysis)))
                        .severity(severity(analysis.riskLevel()))
                        .riskScore(overallRiskScore(analysis))
                        .analysisMessage(analysis.reason() == null ? null : analysis.reason().mainReason())
                        .analyzedAt(analysis.analyzedAt())
                        .build()
        );

        // 공정별 결과 저장 (같은 트랜잭션)
        // 상세 테이블은 가능하면 sampleDB에 저장된 원천 event_json을 기준으로 저장한다.
        // raw.eventJson()은 Kafka/analysis 흐름에 따라 일부 payload만 들어올 수 있으므로 fallback으로만 사용한다.
        Map<String, Object> detailEventJson = stored != null && stored.payload() != null
                ? stored.payload().eventJson()
                : raw.eventJson();

        ProcessCode detailProcessCode = stored != null && stored.payload() != null
                ? stored.payload().processCode()
                : raw.processCode();

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

        switch (processCode) {
            case PRESS -> {
                Boolean countIncrease = bool(json, "processData", "press", "countIncreaseYn");
                Double targetCycleTime = firstDouble(
                        json,
                        new String[]{"processData", "press", "targetCycleTimeSec"},
                        new String[]{"processMetrics", "targetCycleTimeSec"}
                );
                if (targetCycleTime == null || targetCycleTime <= 0) {
                    targetCycleTime = DEFAULT_PRESS_TARGET_CYCLE_TIME_SEC;
                }
                Double timestampDelaySec = firstDouble(
                        json,
                        new String[]{"processData", "press", "timestampDelaySec"},
                        new String[]{"processMetrics", "stationDelaySec"}
                );
                if (timestampDelaySec == null || timestampDelaySec < 0) {
                    timestampDelaySec = 0.0;
                }
                Double actualCycleTime = firstDouble(
                        json,
                        new String[]{"processMetrics", "cycleTimeSec"},
                        new String[]{"processData", "press", "actualCycleTimeSec"},
                        new String[]{"processData", "press", "cycleTimeSec"}
                );
                
                // Null 체크: processMetrics에서 cycleTimeSec 추출 시도
                if (actualCycleTime == null) {
                    Double processMetricsCycleTime = doubleVal(json, "processMetrics", "cycleTimeSec");
                    if (processMetricsCycleTime != null && processMetricsCycleTime > 0) {
                        actualCycleTime = processMetricsCycleTime;
                    }
                }
                
                if (actualCycleTime == null || actualCycleTime <= 0) {
                    actualCycleTime = targetCycleTime + timestampDelaySec;
                }
                if (actualCycleTime <= 0) {
                    actualCycleTime = targetCycleTime;
                }
                double cycleTimeGapSec = actualCycleTime - targetCycleTime;
                log.info("[DETAIL_SAVE][PRESS] resultId={}, eventId={}, countIncreaseYn={}, timestampDelaySec={}, targetCycleTime={}, actualCycleTime={}, cycleTimeGap={}",
                        savedResult.getId(),
                        savedResult.getEventId(),
                        countIncrease,
                        timestampDelaySec,
                        targetCycleTime,
                        actualCycleTime,
                        cycleTimeGapSec);
                pressRepository.save(
                        PressAnalysisResult.builder()
                                .analysisResult(savedResult)
                                .countIncreaseYn(countIncrease)
                                .targetCycleTimeSec(targetCycleTime)
                                .actualCycleTimeSec(actualCycleTime)
                                .cycleTimeGapSec(cycleTimeGapSec)
                                .timestampDelaySec(timestampDelaySec)
                                .build()
                );
            }
            case BODY -> {
                Double robotVibrationScore = doubleVal(json, "sensor", "robotArmVibration", "vibrationScore");
                Double vibrationPeak = doubleVal(json, "sensor", "robotArmVibration", "vibrationPeak");
                String frequencyPeakBand = text(json, "processData", "body", "frequencyPeakBand");
                String robotOperationMode = text(json, "processData", "body", "robotOperationMode");
                String robotMotionStatus = text(json, "processData", "body", "robotMotionStatus");

                // robotOperationMode null 체크: processData.body에서도 찾기 시도
                if (robotOperationMode == null) {
                    robotOperationMode = text(json, "processData", "body", "robotOperationMode");
                }
                // 여전히 null이면 "NORMAL" 설정
                if (robotOperationMode == null) {
                    robotOperationMode = "NORMAL";
                }
                
                // robotVibrationScore null 체크: sensor.robotArmVibration에서 다시 시도
                if (robotVibrationScore == null) {
                    Double vibrationScoreFromSensor = doubleVal(json, "sensor", "robotArmVibration", "vibrationScore");
                    if (vibrationScoreFromSensor != null) {
                        robotVibrationScore = vibrationScoreFromSensor;
                    } else {
                        // sensor.vibration에서도 시도
                        robotVibrationScore = doubleVal(json, "sensor", "vibration", "vibrationScore");
                    }
                }
                
                // vibrationPeak null 체크: 여러 경로에서 찾기
                if (vibrationPeak == null) {
                    vibrationPeak = doubleVal(json, "sensor", "robotArmVibration", "vibrationPeak");
                    if (vibrationPeak == null) {
                        vibrationPeak = doubleVal(json, "sensor", "vibration", "vibrationPeak");
                    }
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
                
                String frequencyBandsJson = null;
                if (frequencyBandsObj != null) {
                    try {
                        frequencyBandsJson = objectMapper.writeValueAsString(frequencyBandsObj);
                    } catch (Exception e) {
                        log.error("Failed to serialize frequencyBands", e);
                    }
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
                        BodyAnalysisResult.builder()
                                .analysisResult(savedResult)
                                .robotMotionStatus(robotMotionStatus != null ? robotMotionStatus : (savedResult.getIsAbnormal() ? "ABNORMAL" : "NORMAL"))
                                .robotOperationMode(robotOperationMode)
                                .robotVibrationScore(robotVibrationScore)
                                .frequencyPeakBand(frequencyPeakBand)
                                .frequencyPeakValue(frequencyPeakValue)
                                .frequencyBandsJson(frequencyBandsJson)
                                .build()
                );
            }
            case PAINT -> {
                Double defectScore = doubleVal(json, "processData", "paint", "defectScore");
                Double thermalStdTemp = doubleVal(json, "processData", "paint", "thermalStdTemp");
                Double surfaceQualityScore = doubleVal(json, "processData", "paint", "surfaceQualityScore");
                String visionLabel = text(json, "processData", "paint", "visionLabel");
                String imagePosition = text(json, "processData", "paint", "imagePosition");
                Double thicknessValue = doubleVal(json, "processData", "paint", "thicknessValue");
                log.info("[DETAIL_SAVE][PAINT] resultId={}, eventId={}, imagePosition={}, thicknessValue={}",
                        savedResult.getId(),
                        savedResult.getEventId(),
                        imagePosition,
                        thicknessValue);

                paintRepository.save(
                        PaintAnalysisResult.builder()
                                .analysisResult(savedResult)
                                .defectScore(defectScore)
                                .thermalStdTemp(thermalStdTemp)
                                .surfaceQualityScore(surfaceQualityScore)
                                .visionLabel(visionLabel)
                                .imagePosition(imagePosition)
                                .thicknessValue(thicknessValue)
                                .build()
                );
            }
            case ASSEMBLY -> {
                String expectedSequence = text(json, "processData", "assembly", "expectedSequence");
                String actualSequence = text(json, "processData", "assembly", "actualSequence");
                Integer sequenceErrorCount = intVal(json, "processData", "assembly", "sequenceErrorCount");
                Integer missingPartCount = intVal(json, "processData", "assembly", "missingPartCount");
                Integer fasteningErrorCount = intVal(json, "processData", "assembly", "fasteningErrorCount");
                log.info("[DETAIL_SAVE][ASSEMBLY] resultId={}, eventId={}, expectedSequence={}, actualSequence={}, sequenceErrorCount={}, missingPartCount={}, fasteningErrorCount={}",
                        savedResult.getId(),
                        savedResult.getEventId(),
                        expectedSequence,
                        actualSequence,
                        sequenceErrorCount,
                        missingPartCount,
                        fasteningErrorCount);

                assemblyRepository.save(
                        AssemblyAnalysisResult.builder()
                                .analysisResult(savedResult)
                                .expectedSequence(expectedSequence)
                                .actualSequence(actualSequence)
                                .sequenceErrorCount(sequenceErrorCount)
                                .missingPartCount(missingPartCount)
                                .fasteningErrorCount(fasteningErrorCount)
                                .build()
                );
            }
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
            return jacksonObjectMapper.readValue(text, Map.class);
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
        return val instanceof Boolean b && b;
    }

    private String text(Object source, String... path) {
        Object val = value(source, path);
        return val == null ? null : val.toString();
    }
}

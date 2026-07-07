import re

with open('src/main/java/com/aims/assembly/service/body/BodyAnomalyDetectionService.java', 'r', encoding='utf-8') as f:
    content = f.read()

# I will just write a completely clean version based on the code I know
clean_code = """package com.aims.assembly.service.body;

import com.aims.assembly.domain.body.BodyAnalysisResult;
import com.aims.assembly.dto.body.BodyAnomalyDetectionResponse;
import com.aims.assembly.mapper.BodyAnomalyDetectionResponseMapper;
import com.aims.assembly.repository.analysis.BodyAnalysisResultRepository;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BodyAnomalyDetectionService {
    private static final int MAX_EVENT_LOOKUP_SIZE = 10_000;
    private static final Double TARGET_VIBRATION_SCORE = 0.75;
    private static final Double TARGET_FREQUENCY_PEAK = 3.0;

    private final BodyAnalysisResultRepository repository;
    private final ManufacturingEventJsonRepository eventJsonRepository;
    private final ObjectMapper objectMapper;

    @Cacheable(
            cacheNames = "body-anomaly-dashboard-v2",
            key = "T(java.time.LocalDate).parse(#date?.toString() ?: #from?.toLocalDate()?.toString() ?: #to?.toLocalDate()?.toString() ?: T(java.time.LocalDate).now().toString()) + ':' + (#limit ?: 30)"
    )
    public BodyAnomalyDetectionResponse findDashboard(
            LocalDate date,
            LocalDateTime from,
            LocalDateTime to,
            LocalDateTime endAt,
            int limit
    ) {
        List<BodyAnomalyDetectionResponse.DateOption> dateOptions = dateOptions();
        LocalDate targetDate = resolveDate(date, from, to, endAt, dateOptions);
        LocalDateTime rangeFrom = from != null ? from : targetDate.atStartOfDay();
        LocalDateTime rangeTo = to != null ? to : LocalDateTime.of(targetDate, LocalTime.MAX);
        if (endAt != null && endAt.isBefore(rangeTo)) {
            rangeTo = endAt;
        }

        int size = Math.max(1, Math.min(limit, 200));
        List<BodyAnomalyDetectionResponse.ChartPoint> allPoints = repository.findDashboardByEventTimeBetween(
                        rangeFrom,
                        rangeTo,
                        PageRequest.of(0, MAX_EVENT_LOOKUP_SIZE)
                ).stream()
                .collect(Collectors.toMap(
                        result -> result.getAnalysisResult().getEventId(),
                        result -> result,
                        (left, right) -> isLater(left, right) ? left : right
                ))
                .values()
                .stream()
                .map(this::toChartPoint)
                .filter(point -> point.timestamp() != null)
                .sorted(Comparator.comparing(BodyAnomalyDetectionResponse.ChartPoint::timestamp))
                .toList();

        List<BodyAnomalyDetectionResponse.ChartPoint> points = allPoints.size() <= size
                ? allPoints
                : allPoints.subList(allPoints.size() - size, allPoints.size());

        boolean detected = points.stream().anyMatch(this::isBodyAnomaly);
        LocalDateTime previousEndAt = points.isEmpty() ? null : points.get(0).timestamp().minusNanos(1);

        double maxRiskScore = points.stream()
                .filter(this::isBodyAnomaly)
                .mapToDouble(p -> p.riskScore() != null ? p.riskScore() : 0.0)
                .max()
                .orElse(points.isEmpty() ? 0.0 : safeNumber(points.get(points.size() - 1).riskScore()));

        BodyAnomalyDetectionResponse.ChartPoint latest = points.isEmpty() ? null : points.get(points.size() - 1);
        BodyAnomalyDetectionResponse.ChartPoint summaryPoint = points.stream()
                .filter(this::isBodyAnomaly)
                .max(Comparator.comparingDouble(this::anomalyWeight)
                        .thenComparing(BodyAnomalyDetectionResponse.ChartPoint::timestamp,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(latest);

        BodyAnalysisResult summaryResult = summaryPoint == null ? null
                : repository.findByAnalysisResult_AnalysisId(summaryPoint.analysisId()).orElse(null);

        BodyAnomalyDetectionResponse.Metrics metrics = toMetrics(summaryResult, summaryPoint, detected, maxRiskScore);
        
        BodyAnomalyDetectionResponse.FrequencyZoneAnalysis freqAnalysis = null;
        if (metrics != null && metrics.frequencyBands() != null) {
            freqAnalysis = analyzeFrequencyZones(metrics.frequencyBands());
        }

        BodyAnomalyDetectionResponse response = BodyAnomalyDetectionResponseMapper.toResponse(
                targetDate,
                rangeFrom,
                rangeTo,
                previousEndAt,
                dateOptions,
                metrics,
                points,
                freqAnalysis,
                toAlert(points, detected, summaryResult)
        );
        log.info(
                "차체 이상 탐지 대시보드 조회 완료: date={}, from={}, to={}, 건수={}, 탐지여부={}",
                targetDate,
                rangeFrom,
                rangeTo,
                points.size(),
                detected
        );
        return response;
    }

    private List<BodyAnomalyDetectionResponse.DateOption> dateOptions() {
        return repository.findBodyAnalysisDateOptions().stream()
                .map(option -> BodyAnomalyDetectionResponseMapper.toDateOption(
                        option.getDate(),
                        option.getSampleEventId()
                ))
                .toList();
    }

    private LocalDate resolveDate(
            LocalDate date,
            LocalDateTime from,
            LocalDateTime to,
            LocalDateTime endAt,
            List<BodyAnomalyDetectionResponse.DateOption> dateOptions
    ) {
        if (date != null) return date;
        if (from != null) return from.toLocalDate();
        if (endAt != null) return endAt.toLocalDate();
        if (to != null) return to.toLocalDate();
        return dateOptions.isEmpty() ? LocalDate.now() : dateOptions.get(0).date();
    }

    private BodyAnomalyDetectionResponse.ChartPoint toChartPoint(BodyAnalysisResult result) {
        var analysis = result.getAnalysisResult();
        Double vibrationScore = result.getRobotVibrationScore();
        Double vibrationPeak = null;
        Double vibrationRms = null;
        
        SensorData sensorData = extractSensorDataFromEventJson(analysis.getEventId());
        if (sensorData != null) {
            if (vibrationScore == null) {
                vibrationScore = sensorData.vibrationScore;
            }
            vibrationPeak = sensorData.peakValue;
            vibrationRms = sensorData.vibrationRms;
        }
        
        boolean isAbnormal = Boolean.TRUE.equals(analysis.getIsAbnormal()) || (analysis.getRiskScore() != null && analysis.getRiskScore() >= 30.0);
        String severity = analysis.getSeverity() == null ? "NORMAL" : analysis.getSeverity().name();
        if (isAbnormal && "NORMAL".equals(severity)) {
            severity = "WARNING";
        }

        return BodyAnomalyDetectionResponseMapper.toChartPoint(
                analysis.getEventId(),
                analysis.getAnalysisId(),
                analysis.getEventTime(),
                TARGET_VIBRATION_SCORE,
                vibrationScore,
                TARGET_FREQUENCY_PEAK,
                toDisplayPeakValue(vibrationPeak),
                vibrationRms,
                analysis.getRiskScore(),
                isAbnormal,
                severity
        );
    }

    private BodyAnomalyDetectionResponse.Metrics toMetrics(
            BodyAnalysisResult result,
            BodyAnomalyDetectionResponse.ChartPoint point,
            boolean detected,
            double maxRiskScore
    ) {
        String severity = detected ? "WARNING" : "NORMAL";
        if (point != null && point.severity() != null && !detected) {
            severity = point.severity();
        }
        
        Double pointVibrationScore = point != null ? point.vibrationScore() : null;
        Double pointVibrationPeak = point != null ? point.vibrationPeak() : null;
        Double pointVibrationRms = point != null ? point.vibrationRms() : null;
        
        if (result == null) {
            BodyProcessData bodyData = null;
            if (point != null && point.eventId() != null) {
                bodyData = extractBodyDataFromEventJson(point.eventId());
            }
            
            if (bodyData != null) {
                return BodyAnomalyDetectionResponseMapper.toMetrics(
                        bodyData.robotMotionStatus,
                        bodyData.robotOperationMode != null ? bodyData.robotOperationMode : "NORMAL",
                        TARGET_VIBRATION_SCORE,
                        pointVibrationScore,
                        TARGET_FREQUENCY_PEAK,
                        pointVibrationPeak,
                        pointVibrationRms,
                        bodyData.frequencyPeakBand,
                        null,
                        maxRiskScore,
                        "0-100",
                        severity,
                        bodyData.frequencyBands != null ? bodyData.frequencyBands : new HashMap<>()
                );
            }
            return BodyAnomalyDetectionResponseMapper.toMetrics(
                    null, "NORMAL", TARGET_VIBRATION_SCORE, pointVibrationScore, TARGET_FREQUENCY_PEAK, pointVibrationPeak, pointVibrationRms, null, null, maxRiskScore, "0-100", severity, new HashMap<>()
            );
        }
        return BodyAnomalyDetectionResponseMapper.toMetrics(
                result.getRobotMotionStatus(),
                result.getRobotOperationMode() != null ? result.getRobotOperationMode() : "NORMAL",
                TARGET_VIBRATION_SCORE,
                pointVibrationScore != null ? pointVibrationScore : result.getRobotVibrationScore(),
                TARGET_FREQUENCY_PEAK,
                pointVibrationPeak,
                pointVibrationRms,
                result.getFrequencyPeakBand(),
                toDisplayPeakValue(result.getFrequencyPeakValue()),
                maxRiskScore,
                "0-100",
                severity,
                parseFrequencyBands(result.getFrequencyBandsJson())
        );
    }
    
    private Map<String, Double> parseFrequencyBands(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Double>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse frequencyBands json", e);
            return new HashMap<>();
        }
    }

    private BodyAnomalyDetectionResponse.FrequencyZoneAnalysis analyzeFrequencyZones(Map<String, Double> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        
        List<Double> lowVals = new ArrayList<>();
        List<Double> mainVals = new ArrayList<>();
        List<Double> highVals = new ArrayList<>();
        List<Double> ultraVals = new ArrayList<>();
        
        for (Map.Entry<String, Double> entry : raw.entrySet()) {
            String key = entry.getKey().toUpperCase();
            Double val = entry.getValue();
            if (val == null) continue;
            
            int upperHz = -1;
            String[] parts = key.replace("FREQ_", "").replace("_HZ", "").split("_");
            try {
                if (parts.length >= 2) {
                    upperHz = Integer.parseInt(parts[1]);
                } else if (parts.length == 1) {
                    upperHz = Integer.parseInt(parts[0]);
                }
            } catch (Exception e) {
                // ignore
            }
            
            if (upperHz <= 0) continue;
            
            if (upperHz <= 300) {
                lowVals.add(val);
            } else if (upperHz <= 600) {
                mainVals.add(val);
            } else if (upperHz <= 1000) {
                highVals.add(val);
            } else {
                ultraVals.add(val);
            }
        }
        
        return new BodyAnomalyDetectionResponse.FrequencyZoneAnalysis(
                computeZoneStats(lowVals),
                computeZoneStats(mainVals),
                computeZoneStats(highVals),
                computeZoneStats(ultraVals)
        );
    }
    
    private BodyAnomalyDetectionResponse.FrequencyZoneAnalysis.ZoneStats computeZoneStats(List<Double> vals) {
        if (vals.isEmpty()) return new BodyAnomalyDetectionResponse.FrequencyZoneAnalysis.ZoneStats(0.0, 0.0);
        double max = vals.stream().mapToDouble(v -> v).max().orElse(0.0);
        double avg = vals.stream().mapToDouble(v -> v).average().orElse(0.0);
        return new BodyAnomalyDetectionResponse.FrequencyZoneAnalysis.ZoneStats(avg, max);
    }

    private BodyAnomalyDetectionResponse.AlertPanel toAlert(
            List<BodyAnomalyDetectionResponse.ChartPoint> points,
            boolean detected,
            BodyAnalysisResult summaryResult
    ) {
        if (!detected || points == null || points.isEmpty()) {
            return BodyAnomalyDetectionResponseMapper.toAlert(false, "차체 이상 미탐지", List.of());
        }

        List<String> reasons = new ArrayList<>();
        int abnormalCount = 0;
        double maxVibrationScore = 0.0;

        for (BodyAnomalyDetectionResponse.ChartPoint point : points) {
            if (!isBodyAnomaly(point)) continue;
            if (Boolean.TRUE.equals(point.isAbnormal())) abnormalCount++;
            if (point.robotVibrationScore() != null) {
                maxVibrationScore = Math.max(maxVibrationScore, point.robotVibrationScore());
            }
        }

        if (summaryResult != null) {
            if ("COLLISION_RISK".equals(summaryResult.getRobotMotionStatus())) {
                reasons.add("robot_motion_status = COLLISION_RISK");
            } else if (summaryResult.getRobotMotionStatus() != null
                    && !"NORMAL".equals(summaryResult.getRobotMotionStatus())) {
                reasons.add("robot_motion_status = " + summaryResult.getRobotMotionStatus());
            }
            if ("AUTO_MANUAL_STOPPED".equals(summaryResult.getRobotOperationMode())
                    || "STOPPED".equals(summaryResult.getRobotOperationMode())
                    || "MANUAL".equals(summaryResult.getRobotOperationMode())) {
                reasons.add("robot_operation_mode = " + summaryResult.getRobotOperationMode());
            }
            if (summaryResult.getFrequencyPeakValue() != null && summaryResult.getFrequencyPeakValue() > 0) {
                reasons.add(String.format(
                        "피크 진동값 급증 (%.1f mm/s)",
                        toDisplayPeakValue(summaryResult.getFrequencyPeakValue())
                ));
            }
            if (summaryResult.getFrequencyPeakBand() != null) {
                reasons.add("고주파 대역 이상 감지 (" + formatFrequencyBand(summaryResult.getFrequencyPeakBand()) + ")");
            }
        }

        if (abnormalCount > 0) {
            reasons.add("이상 징후 감지: " + abnormalCount + "건");
        }
        if (maxVibrationScore > 0) {
            reasons.add(String.format("최대 진동 점수: %.2f", maxVibrationScore));
        }

        return BodyAnomalyDetectionResponseMapper.toAlert(true, "차체 이상 탐지", List.copyOf(reasons));
    }

    private Double toDisplayPeakValue(Double rawValue) {
        if (rawValue == null) {
            return null;
        }
        return rawValue < 1.0 ? rawValue * 1000.0 : rawValue;
    }

    private String formatFrequencyBand(String raw) {
        if (raw == null) return "";
        return switch (raw.toUpperCase()) {
            case "LOW" -> "LOW (0–100Hz)";
            case "MID", "MEDIUM" -> "MID (30–80Hz)";
            case "HIGH" -> "HIGH (80–300Hz)";
            default -> raw.replace("_HZ", "Hz").replace("_", "-");
        };
    }

    private boolean isBodyAnomaly(BodyAnomalyDetectionResponse.ChartPoint point) {
        return point != null
                && (Boolean.TRUE.equals(point.isAbnormal())
                || (point.riskScore() != null && point.riskScore() >= 30.0));
    }

    private double anomalyWeight(BodyAnomalyDetectionResponse.ChartPoint point) {
        if (point == null) return -1.0;
        double weight = 0.0;
        if (Boolean.TRUE.equals(point.isAbnormal())) weight += 50.0;
        if (point.riskScore() != null) weight += point.riskScore();
        if (point.robotVibrationScore() != null) weight += point.robotVibrationScore() * 10.0;
        if (point.frequencyPeakValue() != null) weight += point.frequencyPeakValue() * 5.0;
        return weight;
    }

    private Double safeNumber(Double value) {
        return value == null ? 0.0 : value;
    }

    private boolean isLater(BodyAnalysisResult left, BodyAnalysisResult right) {
        LocalDateTime leftAt = left.getAnalysisResult().getAnalyzedAt();
        LocalDateTime rightAt = right.getAnalysisResult().getAnalyzedAt();
        if (leftAt != null && rightAt != null) {
            int compared = leftAt.compareTo(rightAt);
            if (compared != 0) return compared > 0;
        } else if (leftAt != null) return true;
        else if (rightAt != null) return false;
        Long leftId = left.getAnalysisResult().getId();
        Long rightId = right.getAnalysisResult().getId();
        if (leftId == null) return false;
        if (rightId == null) return true;
        return leftId >= rightId;
    }

    private BodyProcessData extractBodyDataFromEventJson(String eventId) {
        try {
            var eventOpt = eventJsonRepository.findByEventId(eventId);
            if (eventOpt.isEmpty()) {
                return null;
            }
            
            var storedEvent = eventOpt.get();
            Map<String, Object> eventMap = objectMapper.readValue(
                    storedEvent.rawJson(), 
                    new TypeReference<Map<String, Object>>() {}
            );
            
            if (eventMap == null) {
                return null;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> processData = (Map<String, Object>) eventMap.get("processData");
            if (processData == null) {
                return null;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> bodyData = (Map<String, Object>) processData.get("body");
            if (bodyData == null) {
                return null;
            }
            
            String robotMotionStatus = (String) bodyData.get("robotMotionStatus");
            String robotOperationMode = (String) bodyData.get("robotOperationMode");
            String frequencyPeakBand = (String) bodyData.get("frequencyPeakBand");
            
            @SuppressWarnings("unchecked")
            Map<String, Double> frequencyBands = (Map<String, Double>) bodyData.get("frequencyBands");
            
            return new BodyProcessData(robotMotionStatus, robotOperationMode, frequencyPeakBand, frequencyBands);
        } catch (Exception e) {
            log.warn("Failed to extract body data from eventJson for eventId: {}", eventId, e);
            return null;
        }
    }

    private static class BodyProcessData {
        String robotMotionStatus;
        String robotOperationMode;
        String frequencyPeakBand;
        Map<String, Double> frequencyBands;
        
        BodyProcessData(String robotMotionStatus, String robotOperationMode, String frequencyPeakBand, Map<String, Double> frequencyBands) {
            this.robotMotionStatus = robotMotionStatus;
            this.robotOperationMode = robotOperationMode;
            this.frequencyPeakBand = frequencyPeakBand;
            this.frequencyBands = frequencyBands;
        }
    }

    private SensorData extractSensorDataFromEventJson(String eventId) {
        try {
            var eventOpt = eventJsonRepository.findByEventId(eventId);
            if (eventOpt.isEmpty()) {
                return null;
            }
            
            var storedEvent = eventOpt.get();
            Map<String, Object> eventMap = objectMapper.readValue(
                    storedEvent.rawJson(),
                    new TypeReference<Map<String, Object>>() {}
            );
            
            if (eventMap == null) {
                return null;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> sensor = (Map<String, Object>) eventMap.get("sensor");
            if (sensor == null) {
                return null;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> robotArmVibration = (Map<String, Object>) sensor.get("robotArmVibration");
            Double vibrationScore = null;
            Double peakValue = null;
            Double vibrationRms = null;
            
            if (robotArmVibration != null) {
                Object vibrationScoreObj = robotArmVibration.get("vibrationScore");
                if (vibrationScoreObj != null) {
                    vibrationScore = ((Number) vibrationScoreObj).doubleValue();
                }
                Object vibrationPeakObj = robotArmVibration.get("vibrationPeak");
                if (vibrationPeakObj != null) {
                    peakValue = ((Number) vibrationPeakObj).doubleValue();
                }
                Object vibrationRmsObj = robotArmVibration.get("vibrationRms");
                if (vibrationRmsObj != null) {
                    vibrationRms = ((Number) vibrationRmsObj).doubleValue();
                }
            }
            
            return new SensorData(vibrationScore, peakValue, vibrationRms);
        } catch (Exception e) {
            log.warn("Failed to extract sensor data from eventJson for eventId: {}", eventId, e);
            return null;
        }
    }

    private static class SensorData {
        Double vibrationScore;
        Double peakValue;
        Double vibrationRms;
        
        SensorData(Double vibrationScore, Double peakValue, Double vibrationRms) {
            this.vibrationScore = vibrationScore;
            this.peakValue = peakValue;
            this.vibrationRms = vibrationRms;
        }
    }
}
"""

with open('src/main/java/com/aims/assembly/service/body/BodyAnomalyDetectionService.java', 'w', encoding='utf-8') as f:
    f.write(clean_code)

print("Rewrite done")

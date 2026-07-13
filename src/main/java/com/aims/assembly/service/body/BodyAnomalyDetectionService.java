package com.aims.assembly.service.body;

import com.aims.assembly.domain.body.BodyAnalysisResult;
import com.aims.assembly.domain.enums.Severity;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BodyAnomalyDetectionService {
    private static final int MAX_EVENT_LOOKUP_SIZE = 10_000;
    // ISO 13373/20816의 밴드별 진동 관리 원칙을 참고해 LOW/MID/HIGH 임계값을 분리한다.
    private static final Pattern DETAILED_BAND_PATTERN = Pattern.compile("freq_(\\d+)_(\\d+)_hz", Pattern.CASE_INSENSITIVE);
    private static final Double ROBOT_VIBRATION_WARNING_LINE = 0.75;
    private static final Double ROBOT_VIBRATION_DANGER_LINE = 1.25;
    private static final BandThreshold LOW_BAND_THRESHOLD = new BandThreshold(0.006, 0.008, 0.009);
    private static final BandThreshold MID_BAND_THRESHOLD = new BandThreshold(0.015, 0.021, 0.024);
    private static final BandThreshold HIGH_BAND_THRESHOLD = new BandThreshold(0.004, 0.005, 0.0055);
    private static final Double PEAK_WARNING_LINE = MID_BAND_THRESHOLD.warning();
    private static final Double PEAK_DANGER_LINE = MID_BAND_THRESHOLD.danger();

    private final BodyAnalysisResultRepository repository;
    private final ManufacturingEventJsonRepository eventJsonRepository;
    private final ObjectMapper objectMapper;

    @Cacheable(
            cacheNames = "body-anomaly-dashboard",
            key = "T(java.time.LocalDate).parse(#date?.toString() ?: #from?.toLocalDate()?.toString() ?: #to?.toLocalDate()?.toString() ?: T(java.time.LocalDate).now().toString())"
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

        List<BodyAnomalyDetectionResponse.ChartPoint> points = allPoints;

        boolean detected = points.stream().anyMatch(this::isBodyAnomaly);
        LocalDateTime previousEndAt = points.isEmpty() ? null : points.get(0).timestamp().minusNanos(1);

        Double averageRobotVibrationScore = average(points, BodyAnomalyDetectionResponse.ChartPoint::robotVibrationScore);
        Double averageVibrationPeak = average(points, BodyAnomalyDetectionResponse.ChartPoint::vibrationPeak);
        Double averageVibrationRms = average(points, BodyAnomalyDetectionResponse.ChartPoint::vibrationRms);
        Double averageFrequencyPeakValue = average(points, BodyAnomalyDetectionResponse.ChartPoint::frequencyPeakValue);
        Double maxRiskScore = points.stream()
                .map(BodyAnomalyDetectionResponse.ChartPoint::riskScore)
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);

        BodyAnomalyDetectionResponse.ChartPoint latest = points.isEmpty() ? null : points.get(points.size() - 1);
        BodyAnomalyDetectionResponse.ChartPoint summaryPoint = selectRepresentativePoint(
                points,
                averageRobotVibrationScore,
                averageFrequencyPeakValue
        ).orElse(latest);

        BodyAnalysisResult summaryResult = summaryPoint == null ? null
                : repository.findByAnalysisResult_AnalysisId(summaryPoint.analysisId()).orElse(null);

        BodyAnomalyDetectionResponse.Metrics metrics = toMetrics(
                summaryResult,
                summaryPoint,
                detected,
                averageRobotVibrationScore,
                averageVibrationPeak,
                averageVibrationRms,
                averageFrequencyPeakValue,
                maxRiskScore
        );

        List<BodyAnomalyDetectionResponse.RobotMetricPoint> robotVibrationPoints = points.stream()
                .map(point -> BodyAnomalyDetectionResponseMapper.toRobotMetricPoint(
                        point.eventId(),
                        point.analysisId(),
                        point.timestamp(),
                        point.robotVibrationScore(),
                        point.vibrationWarningLine(),
                        point.vibrationDangerLine(),
                        point.isAbnormal(),
                        point.severity()
                ))
                .toList();
        List<BodyAnomalyDetectionResponse.PeakMetricPoint> peakPoints = points.stream()
                .map(point -> BodyAnomalyDetectionResponseMapper.toPeakMetricPoint(
                        point.eventId(),
                        point.analysisId(),
                        point.timestamp(),
                        point.frequencyPeakValue(),
                        point.vibrationRms(),
                        point.peakWarningLine(),
                        point.peakDangerLine(),
                        point.isAbnormal(),
                        point.severity()
                ))
                .toList();

        Map<String, Double> rawFrequencyBands = resolveRawFrequencyBands(summaryResult, summaryPoint);
        Map<String, Double> frequencyBands = summarizeFrequencyBands(rawFrequencyBands);
        BodyAnomalyDetectionResponse.FrequencyZoneAnalysis freqAnalysis = analyzeFrequencyZones(rawFrequencyBands);
        List<BodyAnomalyDetectionResponse.FrequencyBandPoint> freqChart = buildFrequencyChart(
                frequencyBands,
                summaryPoint != null ? summaryPoint.timestamp() : null
        );
        List<BodyAnomalyDetectionResponse.FrequencyZonePoint> freqZoneChart = buildFrequencyZoneChart(rawFrequencyBands);

        return BodyAnomalyDetectionResponseMapper.toResponse(
                targetDate,
                rangeFrom,
                rangeTo,
                previousEndAt,
                dateOptions,
                metrics,
                BodyAnomalyDetectionResponseMapper.toCharts(
                        BodyAnomalyDetectionResponseMapper.toRobotMetricChart(
                                "robot vibration acceleration",
                                "robotVibrationScore",
                                "",
                                robotVibrationPoints
                        ),
                        BodyAnomalyDetectionResponseMapper.toPeakMetricChart(
                                "peak vibration value",
                                "frequencyPeakValue",
                                "",
                                peakPoints
                        )
                ),
                freqChart,
                freqZoneChart,
                freqAnalysis,
                toAlert(points, detected, summaryResult)
        );
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
        Double robotVibrationScore = result.getRobotVibrationScore();
        Double vibrationPeak = null;
        Double vibrationRms = null;

        SensorData sensorData = extractSensorDataFromEventJson(analysis.getEventId());
        if (sensorData != null) {
            if (robotVibrationScore == null) {
                robotVibrationScore = sensorData.vibrationScore();
            }
            vibrationPeak = sensorData.peakValue();
            vibrationRms = sensorData.vibrationRms();
        }

        Map<String, Double> summaryBands = summarizeFrequencyBands(parseFrequencyBands(result.getFrequencyBandsJson()));
        BandThreshold peakThreshold = thresholdForBand(
                resolveFrequencyPeakBand(summaryBands, result.getFrequencyPeakValue(), result.getFrequencyPeakBand())
        );
        String severity = bodySeverity(
                result.getRobotMotionStatus(),
                result.getRobotOperationMode(),
                result.getFrequencyPeakBand(),
                summaryBands,
                result.getFrequencyPeakValue(),
                analysis.getSeverity(),
                analysis.getRiskScore(),
                Boolean.TRUE.equals(analysis.getIsAbnormal())
        );
        boolean isAbnormal = !"NORMAL".equalsIgnoreCase(severity);

        return BodyAnomalyDetectionResponseMapper.toChartPoint(
                analysis.getEventId(),
                analysis.getAnalysisId(),
                analysis.getEventTime(),
                robotVibrationScore,
                result.getFrequencyPeakValue(),
                vibrationPeak,
                ROBOT_VIBRATION_WARNING_LINE,
                ROBOT_VIBRATION_DANGER_LINE,
                peakThreshold.warning(),
                peakThreshold.danger(),
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
            Double averageRobotVibrationScore,
            Double averageVibrationPeak,
            Double averageVibrationRms,
            Double averageFrequencyPeakValue,
            Double riskScore
    ) {
        String severity = point != null && point.severity() != null
                ? point.severity()
                : (detected ? "WARNING" : "NORMAL");

        Double pointRobotVibrationScore = point != null ? point.robotVibrationScore() : null;
        Double pointVibrationPeak = point != null ? point.vibrationPeak() : null;
        Double pointVibrationRms = point != null ? point.vibrationRms() : null;
        Double pointFrequencyPeakValue = point != null ? point.frequencyPeakValue() : null;
        BandThreshold peakThreshold = peakThresholdFor(result, point, averageFrequencyPeakValue);

        if (result == null) {
            BodyProcessData bodyData = null;
            if (point != null && point.eventId() != null) {
                bodyData = extractBodyDataFromEventJson(point.eventId());
            }

            if (bodyData != null) {
                return BodyAnomalyDetectionResponseMapper.toMetrics(
                        localizeRobotMotionStatus(bodyData.robotMotionStatus()),
                        localizeRobotOperationMode(bodyData.robotOperationMode() != null ? bodyData.robotOperationMode() : "NORMAL"),
                        averageRobotVibrationScore != null ? averageRobotVibrationScore : pointRobotVibrationScore,
                        averageVibrationPeak != null ? averageVibrationPeak : pointVibrationPeak,
                        averageVibrationRms != null ? averageVibrationRms : pointVibrationRms,
                        localizeFrequencyPeakBand(resolveFrequencyPeakBand(bodyData.frequencyBands(), averageFrequencyPeakValue, bodyData.frequencyPeakBand())),
                        averageFrequencyPeakValue != null ? averageFrequencyPeakValue : pointFrequencyPeakValue,
                        ROBOT_VIBRATION_WARNING_LINE,
                        ROBOT_VIBRATION_DANGER_LINE,
                        peakThreshold.warning(),
                        peakThreshold.danger(),
                        riskScore != null ? riskScore : 0.0,
                        "0-100",
                        severity,
                        summarizeFrequencyBands(bodyData.frequencyBands())
                );
            }

            return BodyAnomalyDetectionResponseMapper.toMetrics(
                    null,
                    "정상",
                    averageRobotVibrationScore != null ? averageRobotVibrationScore : pointRobotVibrationScore,
                    averageVibrationPeak != null ? averageVibrationPeak : pointVibrationPeak,
                    averageVibrationRms != null ? averageVibrationRms : pointVibrationRms,
                    null,
                    averageFrequencyPeakValue != null ? averageFrequencyPeakValue : pointFrequencyPeakValue,
                    ROBOT_VIBRATION_WARNING_LINE,
                    ROBOT_VIBRATION_DANGER_LINE,
                    peakThreshold.warning(),
                    peakThreshold.danger(),
                    riskScore != null ? riskScore : 0.0,
                    "0-100",
                    severity,
                    new HashMap<>()
            );
        }

        return BodyAnomalyDetectionResponseMapper.toMetrics(
                localizeRobotMotionStatus(result.getRobotMotionStatus()),
                localizeRobotOperationMode(result.getRobotOperationMode() != null ? result.getRobotOperationMode() : "NORMAL"),
                averageRobotVibrationScore != null ? averageRobotVibrationScore : (pointRobotVibrationScore != null ? pointRobotVibrationScore : result.getRobotVibrationScore()),
                averageVibrationPeak != null ? averageVibrationPeak : pointVibrationPeak,
                averageVibrationRms != null ? averageVibrationRms : pointVibrationRms,
                localizeFrequencyPeakBand(resolveFrequencyPeakBand(parseFrequencyBands(result.getFrequencyBandsJson()), averageFrequencyPeakValue, result.getFrequencyPeakBand())),
                averageFrequencyPeakValue != null ? averageFrequencyPeakValue : result.getFrequencyPeakValue(),
                ROBOT_VIBRATION_WARNING_LINE,
                ROBOT_VIBRATION_DANGER_LINE,
                peakThreshold.warning(),
                peakThreshold.danger(),
                riskScore != null ? riskScore : 0.0,
                "0-100",
                severity,
                summarizeFrequencyBands(parseFrequencyBands(result.getFrequencyBandsJson()))
        );
    }

    private Map<String, Double> parseFrequencyBands(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Double>>() {
            });
        } catch (Exception e) {
            log.warn("Failed to parse frequencyBands json", e);
            return new HashMap<>();
        }
    }

    private Map<String, Double> summarizeFrequencyBands(Map<String, Double> bands) {
        Map<String, Double> summary = BodyFrequencyBandSupport.toSummaryBands(bands);
        if (summary == null || summary.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Double> localized = new LinkedHashMap<>();
        summary.forEach((key, value) -> localized.put(localizeFrequencyBandKey(key), value));
        return localized;
    }

    private BandThreshold peakThresholdFor(
            BodyAnalysisResult result,
            BodyAnomalyDetectionResponse.ChartPoint point,
            Double fallbackFrequencyPeakValue
    ) {
        if (point != null) {
            return new BandThreshold(
                    0.0,
                    point.peakWarningLine() != null ? point.peakWarningLine() : PEAK_WARNING_LINE,
                    point.peakDangerLine() != null ? point.peakDangerLine() : PEAK_DANGER_LINE
            );
        }

        if (result != null) {
            Map<String, Double> summaryBands = summarizeFrequencyBands(parseFrequencyBands(result.getFrequencyBandsJson()));
            String band = resolveFrequencyPeakBand(
                    summaryBands,
                    fallbackFrequencyPeakValue != null ? fallbackFrequencyPeakValue : result.getFrequencyPeakValue(),
                    result.getFrequencyPeakBand()
            );
            BandThreshold threshold = thresholdForBand(band);
            return new BandThreshold(threshold.target(), threshold.warning(), threshold.danger());
        }

        return new BandThreshold(0.0, PEAK_WARNING_LINE, PEAK_DANGER_LINE);
    }

    private List<BodyAnomalyDetectionResponse.FrequencyBandPoint> buildFrequencyChart(
            Map<String, Double> bands,
            LocalDateTime chartTime
    ) {
        if (bands == null || bands.isEmpty()) {
            return List.of();
        }

        return bands.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .sorted(Comparator.comparingInt(entry -> frequencyBandOrder(entry.getKey())))
                .map(entry -> BodyAnomalyDetectionResponseMapper.toFrequencyBandPoint(
                        chartTime,
                        localizeFrequencyBandLabel(entry.getKey()),
                        entry.getValue(),
                        thresholdForBand(entry.getKey()).target(),
                        thresholdForBand(entry.getKey()).warning(),
                        thresholdForBand(entry.getKey()).danger()
                ))
                .toList();
    }

    private List<BodyAnomalyDetectionResponse.FrequencyZonePoint> buildFrequencyZoneChart(Map<String, Double> rawBands) {
        if (rawBands == null || rawBands.isEmpty()) {
            return List.of();
        }

        List<Double> zone1 = new ArrayList<>();
        List<Double> zone2 = new ArrayList<>();
        List<Double> zone3 = new ArrayList<>();
        List<Double> zone4 = new ArrayList<>();
        List<Double> zone5 = new ArrayList<>();

        for (Map.Entry<String, Double> entry : rawBands.entrySet()) {
            Double value = entry.getValue();
            if (value == null) {
                continue;
            }
            int upperHz = extractUpperHz(entry.getKey());
            if (upperHz <= 0) {
                continue;
            }

            if (upperHz <= 300) {
                zone1.add(value);
            } else if (upperHz <= 600) {
                zone2.add(value);
            } else if (upperHz <= 900) {
                zone3.add(value);
            } else if (upperHz <= 1200) {
                zone4.add(value);
            } else if (upperHz <= 1600) {
                zone5.add(value);
            }
        }

        return List.of(
                buildFrequencyZonePoint("Zone 1", "0~300Hz", "저주파 / 기본 구조 진동", zone1),
                buildFrequencyZonePoint("Zone 2", "301~600Hz", "로봇 본체 진동", zone2),
                buildFrequencyZonePoint("Zone 3", "601~900Hz", "관절·감속기 진동", zone3),
                buildFrequencyZonePoint("Zone 4", "901~1200Hz", "베어링·기계 이상 진동", zone4),
                buildFrequencyZonePoint("Zone 5", "1201~1600Hz", "고주파 충격·충돌 위험", zone5)
        );
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
            Double val = entry.getValue();
            if (val == null) continue;

            int upperHz = extractUpperHz(entry.getKey());
            if (upperHz <= 0) continue;

            if (upperHz <= 300) {
                lowVals.add(val);
            } else if (upperHz <= 600) {
                mainVals.add(val);
            } else if (upperHz <= 900) {
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

    private BodyAnomalyDetectionResponse.FrequencyZonePoint buildFrequencyZonePoint(
            String zone,
            String range,
            String description,
            List<Double> values
    ) {
        BodyAnomalyDetectionResponse.FrequencyZoneAnalysis.ZoneStats stats = computeZoneStats(values);
        return BodyAnomalyDetectionResponseMapper.toFrequencyZonePoint(
                zone,
                range,
                description,
                stats.avg(),
                stats.max(),
                MID_BAND_THRESHOLD.target(),
                MID_BAND_THRESHOLD.warning(),
                MID_BAND_THRESHOLD.danger()
        );
    }

    private BodyAnomalyDetectionResponse.FrequencyZoneAnalysis.ZoneStats computeZoneStats(List<Double> vals) {
        if (vals.isEmpty()) {
            return new BodyAnomalyDetectionResponse.FrequencyZoneAnalysis.ZoneStats(0.0, 0.0);
        }
        double max = vals.stream().mapToDouble(v -> v).max().orElse(0.0);
        double avg = vals.stream().mapToDouble(v -> v).average().orElse(0.0);
        return new BodyAnomalyDetectionResponse.FrequencyZoneAnalysis.ZoneStats(avg, max);
    }

    private Map<String, Double> resolveRawFrequencyBands(
            BodyAnalysisResult result,
            BodyAnomalyDetectionResponse.ChartPoint point
    ) {
        if (result != null) {
            return parseFrequencyBands(result.getFrequencyBandsJson());
        }
        if (point != null && point.eventId() != null) {
            BodyProcessData bodyData = extractBodyDataFromEventJson(point.eventId());
            if (bodyData != null && bodyData.frequencyBands() != null) {
                return bodyData.frequencyBands();
            }
        }
        return new HashMap<>();
    }

    private <T> Double average(List<T> points, java.util.function.Function<T, Double> extractor) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        return points.stream()
                .map(extractor)
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private java.util.Optional<BodyAnomalyDetectionResponse.ChartPoint> selectRepresentativePoint(
            List<BodyAnomalyDetectionResponse.ChartPoint> points,
            Double averageRobotVibrationScore,
            Double averageFrequencyPeakValue
    ) {
        if (points == null || points.isEmpty()) {
            return java.util.Optional.empty();
        }

        if (averageRobotVibrationScore == null && averageFrequencyPeakValue == null) {
            return points.stream()
                    .filter(this::isBodyAnomaly)
                    .max(Comparator.comparingDouble(this::anomalyWeight)
                            .thenComparing(BodyAnomalyDetectionResponse.ChartPoint::timestamp,
                                    Comparator.nullsLast(Comparator.naturalOrder())));
        }

        return points.stream()
                .filter(this::isBodyAnomaly)
                .min(Comparator.comparingDouble(point -> representativeDistance(
                        point,
                        averageRobotVibrationScore,
                        averageFrequencyPeakValue
                )));
    }

    private double representativeDistance(
            BodyAnomalyDetectionResponse.ChartPoint point,
            Double averageRobotVibrationScore,
            Double averageFrequencyPeakValue
    ) {
        double distance = 0.0;
        if (averageRobotVibrationScore != null && point.robotVibrationScore() != null) {
            distance += Math.abs(point.robotVibrationScore() - averageRobotVibrationScore);
        }
        if (averageFrequencyPeakValue != null && point.frequencyPeakValue() != null) {
            distance += Math.abs(point.frequencyPeakValue() - averageFrequencyPeakValue);
        }
        return distance;
    }

    private int frequencyBandOrder(String rawKey) {
        int[] range = extractBandRange(rawKey);
        if (range != null) {
            return range[0];
        }

        String normalized = rawKey == null ? "" : rawKey.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "LOW" -> 0;
            case "MID", "MEDIUM" -> 1;
            case "HIGH" -> 2;
            default -> Integer.MAX_VALUE;
        };
    }

    private int extractUpperHz(String rawKey) {
        int[] range = extractBandRange(rawKey);
        return range == null ? -1 : range[1];
    }

    private int[] extractBandRange(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }

        Matcher matcher = DETAILED_BAND_PATTERN.matcher(rawKey.toLowerCase(Locale.ROOT).replace("-", "_"));
        if (matcher.find()) {
            return new int[] {
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2))
            };
        }

        String normalized = rawKey.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "LOW" -> new int[] { 0, 100 };
            case "MID", "MEDIUM" -> new int[] { 101, 200 };
            case "HIGH" -> new int[] { 201, 500 };
            default -> null;
        };
    }

    private BandThreshold thresholdForBand(String rawKey) {
        // 상세 주파수 키가 아니라도, ISO 기준처럼 대역별 정상 범위를 먼저 적용한다.
        String normalized = rawKey == null ? "" : rawKey.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("LOW")) {
            return LOW_BAND_THRESHOLD;
        }
        if (normalized.contains("MID") || normalized.contains("MEDIUM")) {
            return MID_BAND_THRESHOLD;
        }
        if (normalized.contains("HIGH")) {
            return HIGH_BAND_THRESHOLD;
        }
        int[] range = extractBandRange(rawKey);
        if (range != null) {
            int upperHz = range[1];
            if (upperHz <= 10) {
                return LOW_BAND_THRESHOLD;
            }
            if (upperHz <= 50) {
                return MID_BAND_THRESHOLD;
            }
            return HIGH_BAND_THRESHOLD;
        }
        return LOW_BAND_THRESHOLD;
    }

    private String localizeFrequencyBandLabel(String raw) {
        int[] range = extractBandRange(raw);
        if (range != null) {
            return range[0] + "~" + range[1] + "Hz";
        }
        return formatFrequencyBand(raw);
    }

    private String resolveFrequencyPeakBand(
            Map<String, Double> bands,
            Double averageFrequencyPeakValue,
            String fallbackBand
    ) {
        if (bands == null || bands.isEmpty()) {
            return fallbackBand;
        }
        if (averageFrequencyPeakValue == null) {
            return fallbackBand;
        }

        String bestBand = fallbackBand;
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<String, Double> entry : bands.entrySet()) {
            Double value = entry.getValue();
            if (value == null) {
                continue;
            }
            double distance = Math.abs(value - averageFrequencyPeakValue);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestBand = entry.getKey();
            }
        }
        return bestBand;
    }

    private String bodySeverity(
            String motionStatus,
            String operationMode,
            String frequencyPeakBand,
            Map<String, Double> frequencyBands,
            Double frequencyPeakValue,
            Severity analysisSeverity,
            Double riskScore,
            boolean analysisAbnormal
    ) {
        // 로봇 상태 + 운전 모드 + 주파수 피크를 합산해 차체 이상 등급을 정한다.
        SeverityLevel severity = SeverityLevel.NORMAL;
        severity = SeverityLevel.max(severity, motionSeverity(motionStatus));
        severity = SeverityLevel.max(severity, operationSeverity(operationMode));
        severity = SeverityLevel.max(severity, frequencySeverity(frequencyPeakBand, frequencyBands, frequencyPeakValue));

        if (analysisSeverity == Severity.CRITICAL || (riskScore != null && riskScore >= 80.0)) {
            severity = SeverityLevel.max(severity, SeverityLevel.CRITICAL);
        } else if (analysisSeverity == Severity.WARNING || (riskScore != null && riskScore >= 60.0)) {
            severity = SeverityLevel.max(severity, SeverityLevel.WARNING);
        }

        if (analysisAbnormal && severity == SeverityLevel.NORMAL) {
            severity = SeverityLevel.WARNING;
        }
        return severity.name();
    }

    private String localizeFrequencyBandKey(String raw) {
        if (raw == null) {
            return "";
        }
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "LOW" -> "LOW";
            case "MID", "MEDIUM" -> "MEDIUM";
            case "HIGH" -> "HIGH";
            default -> raw;
        };
    }

    private String localizeFrequencyPeakBand(String raw) {
        int[] range = extractBandRange(raw);
        if (range != null) {
            return range[0] + "~" + range[1] + "Hz";
        }
        return formatFrequencyBand(raw);
    }

    private String localizeRobotMotionStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return "NORMAL";
        }
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "NORMAL" -> "NORMAL";
            case "WARNING" -> "WARNING";
            case "COLLISION_RISK" -> "COLLISION_RISK";
            case "ABNORMAL" -> "ABNORMAL";
            default -> raw;
        };
    }

    private String localizeRobotOperationMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return "NORMAL";
        }
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "NORMAL" -> "NORMAL";
            case "AUTO" -> "AUTO";
            case "MANUAL" -> "MANUAL";
            case "STOPPED" -> "STOPPED";
            case "AUTO_MANUAL_STOPPED" -> "AUTO_MANUAL_STOPPED";
            default -> raw;
        };
    }

    private BodyAnomalyDetectionResponse.AlertPanel toAlert(
            List<BodyAnomalyDetectionResponse.ChartPoint> points,
            boolean detected,
            BodyAnalysisResult summaryResult
    ) {
        if (!detected || points == null || points.isEmpty()) {
            return BodyAnomalyDetectionResponseMapper.toAlert(false, "차체 이상 탐지 미검출", List.of());
        }

        List<String> reasons = new ArrayList<>();
        int abnormalCount = 0;
        double maxRobotVibrationScore = 0.0;

        for (BodyAnomalyDetectionResponse.ChartPoint point : points) {
            if (!isBodyAnomaly(point)) continue;
            if (!"NORMAL".equalsIgnoreCase(point.severity()) || Boolean.TRUE.equals(point.isAbnormal())) abnormalCount++;
            if (point.robotVibrationScore() != null) {
                maxRobotVibrationScore = Math.max(maxRobotVibrationScore, point.robotVibrationScore());
            }
        }

        if (summaryResult != null) {
            if ("COLLISION_RISK".equals(summaryResult.getRobotMotionStatus())) {
                reasons.add("로봇 진동 상태 = 충돌 위험");
            } else if (summaryResult.getRobotMotionStatus() != null
                    && !"NORMAL".equals(summaryResult.getRobotMotionStatus())) {
                reasons.add("로봇 진동 상태 = " + localizeRobotMotionStatus(summaryResult.getRobotMotionStatus()));
            }
            if ("AUTO_MANUAL_STOPPED".equals(summaryResult.getRobotOperationMode())
                    || "STOPPED".equals(summaryResult.getRobotOperationMode())
                    || "MANUAL".equals(summaryResult.getRobotOperationMode())) {
                reasons.add("로봇 동작 모드 = " + localizeRobotOperationMode(summaryResult.getRobotOperationMode()));
            }
            if (summaryResult.getFrequencyPeakValue() != null && summaryResult.getFrequencyPeakValue() > 0) {
                reasons.add(String.format("피크 진동 증가 (%.6f mm/s)", summaryResult.getFrequencyPeakValue()));
            }
            if (summaryResult.getFrequencyPeakBand() != null) {
                reasons.add("주파수 피크 대역 = " + localizeFrequencyPeakBand(summaryResult.getFrequencyPeakBand()));
            }
        }

        if (abnormalCount > 0) {
            reasons.add("이상 탐지 건수 = " + abnormalCount);
        }
        if (maxRobotVibrationScore > 0) {
            reasons.add(String.format("최대 로봇 진동 점수 = %.2f", maxRobotVibrationScore));
        }

        return BodyAnomalyDetectionResponseMapper.toAlert(true, "차체 이상이 감지되었습니다", List.copyOf(reasons));
    }

    private String formatFrequencyBand(String raw) {
        if (raw == null) return "";
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "LOW" -> "LOW (0-300Hz)";
            case "MID", "MEDIUM" -> "MID (300-600Hz)";
            case "HIGH" -> "HIGH (600-1000Hz)";
            default -> raw.replace("_HZ", "Hz").replace("_", "-");
        };
    }

    private SeverityLevel motionSeverity(String motionStatus) {
        if (motionStatus == null || motionStatus.isBlank()) {
            return SeverityLevel.WARNING;
        }
        return switch (motionStatus.trim().toUpperCase(Locale.ROOT)) {
            case "NORMAL" -> SeverityLevel.NORMAL;
            case "WARNING", "UNKNOWN" -> SeverityLevel.WARNING;
            case "ABNORMAL", "COLLISION_RISK" -> SeverityLevel.CRITICAL;
            default -> SeverityLevel.WARNING;
        };
    }

    private SeverityLevel operationSeverity(String operationMode) {
        if (operationMode == null || operationMode.isBlank()) {
            return SeverityLevel.WARNING;
        }
        return switch (operationMode.trim().toUpperCase(Locale.ROOT)) {
            case "AUTO" -> SeverityLevel.NORMAL;
            case "MANUAL", "STOPPED", "AUTO_MANUAL_STOPPED" -> SeverityLevel.WARNING;
            default -> SeverityLevel.WARNING;
        };
    }

    private SeverityLevel frequencySeverity(
            String frequencyPeakBand,
            Map<String, Double> frequencyBands,
            Double frequencyPeakValue
    ) {
        // 주파수 피크는 밴드별 정상/경고/위험 임계값을 따로 둔다.
        if (frequencyPeakValue == null || frequencyPeakValue <= 0.0) {
            return SeverityLevel.NORMAL;
        }

        String thresholdBand = resolveFrequencyPeakBand(frequencyBands, frequencyPeakValue, frequencyPeakBand);
        BandThreshold threshold = thresholdForBand(thresholdBand);
        if (frequencyPeakValue >= threshold.danger()) {
            return SeverityLevel.CRITICAL;
        }
        if (frequencyPeakValue >= threshold.warning()) {
            return SeverityLevel.WARNING;
        }
        return SeverityLevel.NORMAL;
    }

    private boolean isBodyAnomaly(BodyAnomalyDetectionResponse.ChartPoint point) {
        // 차체는 severity, analysis abnormal, risk score 중 하나만 이상이어도 anomaly로 본다.
        return point != null
                && (!"NORMAL".equalsIgnoreCase(point.severity())
                || Boolean.TRUE.equals(point.isAbnormal())
                || (point.riskScore() != null && point.riskScore() >= 60.0));
    }

    private double anomalyWeight(BodyAnomalyDetectionResponse.ChartPoint point) {
        if (point == null) return -1.0;
        double weight = 0.0;
        if ("CRITICAL".equalsIgnoreCase(point.severity())) weight += 100.0;
        else if ("WARNING".equalsIgnoreCase(point.severity())) weight += 50.0;
        if (Boolean.TRUE.equals(point.isAbnormal())) weight += 25.0;
        if (point.riskScore() != null) weight += point.riskScore();
        if (point.robotVibrationScore() != null) weight += point.robotVibrationScore() * 10.0;
        if (point.vibrationPeak() != null) weight += point.vibrationPeak() * 5.0;
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
        } else if (leftAt != null) {
            return true;
        } else if (rightAt != null) {
            return false;
        }
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
                    new TypeReference<Map<String, Object>>() {
                    }
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

    private record BodyProcessData(
            String robotMotionStatus,
            String robotOperationMode,
            String frequencyPeakBand,
            Map<String, Double> frequencyBands
    ) {
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
                    new TypeReference<Map<String, Object>>() {
                    }
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

    private record SensorData(
            Double vibrationScore,
            Double peakValue,
            Double vibrationRms
    ) {
    }

    private enum SeverityLevel {
        NORMAL,
        WARNING,
        CRITICAL;

        private static SeverityLevel max(SeverityLevel left, SeverityLevel right) {
            return right.ordinal() > left.ordinal() ? right : left;
        }
    }

    private record BandThreshold(
            double target,
            double warning,
            double danger
    ) {
    }
}

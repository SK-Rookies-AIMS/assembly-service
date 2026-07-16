package com.aims.assembly.service.body;

import com.aims.assembly.domain.body.BodyAnalysisResult;
import com.aims.assembly.domain.enums.Severity;
import com.aims.assembly.dto.body.BodyAnomalyDetectionResponse;
import com.aims.assembly.mapper.BodyAnomalyDetectionResponseMapper;
import com.aims.assembly.repository.analysis.BodyAnalysisResultRepository;
import com.aims.assembly.repository.event.AlertEventRepository;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@Transactional(readOnly = true)
public class BodyAnomalyDetectionService {
    private static final int MAX_EVENT_LOOKUP_SIZE = 10_000;
    // ISO 13373/20816 기준에 맞춰 차체 주파수 대역을 LOW / MID / HIGH 범위로 분류한다.
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
    private final AlertEventRepository alertEventRepository;
    private final ObjectMapper objectMapper;

    BodyAnomalyDetectionService(
            BodyAnalysisResultRepository repository,
            ManufacturingEventJsonRepository eventJsonRepository,
            ObjectMapper objectMapper
    ) {
        this(repository, eventJsonRepository, null, objectMapper);
    }

    @Cacheable(
            cacheNames = "body-anomaly-dashboard",
            key = "T(java.time.LocalDate).parse(#date?.toString() ?: #from?.toLocalDate()?.toString() ?: #to?.toLocalDate()?.toString() ?: #endAt?.toLocalDate()?.toString() ?: T(java.time.LocalDate).now().toString())"
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
        Map<String, String> logNoByEventId = resolveAlertLogNos(points.stream()
                .map(BodyAnomalyDetectionResponse.ChartPoint::eventId)
                .filter(eventId -> eventId != null && !eventId.isBlank())
                .toList());
        points = points.stream()
                .map(point -> attachLogNo(point, logNoByEventId.get(point.eventId())))
                .toList();

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
                        point.logNo(),
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
                        point.logNo(),
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
                null,
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

    private Map<String, String> resolveAlertLogNos(List<String> eventIds) {
        if (alertEventRepository == null) {
            return Map.of();
        }
        if (eventIds == null || eventIds.isEmpty()) {
            return Map.of();
        }

        return alertEventRepository.findByEventIdIn(new LinkedHashSet<>(eventIds)).stream()
                .collect(Collectors.toMap(
                        event -> event.getEventId(),
                        event -> event.getLogNo(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private BodyAnomalyDetectionResponse.ChartPoint attachLogNo(
            BodyAnomalyDetectionResponse.ChartPoint point,
            String logNo
    ) {
        if (point == null) {
            return null;
        }
        return new BodyAnomalyDetectionResponse.ChartPoint(
                point.eventId(),
                point.analysisId(),
                logNo,
                point.timestamp(),
                point.robotVibrationScore(),
                point.frequencyPeakValue(),
                point.vibrationPeak(),
                point.vibrationWarningLine(),
                point.vibrationDangerLine(),
                point.peakWarningLine(),
                point.peakDangerLine(),
                point.vibrationRms(),
                point.riskScore(),
                point.isAbnormal(),
                point.severity()
        );
    }

    private BodyAnomalyDetectionResponse.RobotMetricPoint attachLogNo(
            BodyAnomalyDetectionResponse.RobotMetricPoint point,
            String logNo
    ) {
        if (point == null) {
            return null;
        }
        return new BodyAnomalyDetectionResponse.RobotMetricPoint(
                point.eventId(),
                point.analysisId(),
                logNo,
                point.timestamp(),
                point.value(),
                point.warningLine(),
                point.dangerLine(),
                point.isAbnormal(),
                point.severity()
        );
    }

    private BodyAnomalyDetectionResponse.PeakMetricPoint attachLogNo(
            BodyAnomalyDetectionResponse.PeakMetricPoint point,
            String logNo
    ) {
        if (point == null) {
            return null;
        }
        return new BodyAnomalyDetectionResponse.PeakMetricPoint(
                point.eventId(),
                point.analysisId(),
                logNo,
                point.timestamp(),
                point.value(),
                point.secondaryValue(),
                point.warningLine(),
                point.dangerLine(),
                point.isAbnormal(),
                point.severity()
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

        Map<String, Double> summaryBands = summarizeFrequencyBands(bands);
        List<BodyAnomalyDetectionResponse.FrequencyBandPoint> points = new ArrayList<>();
        summaryBands.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> frequencyBandOrder(entry.getKey())))
                .forEach(entry -> {
                    BandThreshold threshold = thresholdForBand(entry.getKey());
                    points.add(BodyAnomalyDetectionResponseMapper.toFrequencyBandPoint(
                            chartTime,
                            localizeFrequencyBandKey(entry.getKey()),
                            entry.getValue(),
                            threshold.target(),
                            threshold.warning(),
                            threshold.danger()
                    ));
                });
        return points;
    }

    private List<BodyAnomalyDetectionResponse.FrequencyZonePoint> buildFrequencyZoneChart(Map<String, Double> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }

        List<Double> lowVals = new ArrayList<>();
        List<Double> mainVals = new ArrayList<>();
        List<Double> highVals = new ArrayList<>();
        List<Double> zone4Vals = new ArrayList<>();
        List<Double> zone5Vals = new ArrayList<>();

        for (Map.Entry<String, Double> entry : raw.entrySet()) {
            Double val = entry.getValue();
            if (val == null) {
                continue;
            }

            int upperHz = extractUpperHz(entry.getKey());
            if (upperHz <= 0) {
                continue;
            }

            if (upperHz <= 300) {
                lowVals.add(val);
            } else if (upperHz <= 600) {
                mainVals.add(val);
            } else if (upperHz <= 900) {
                highVals.add(val);
            } else if (upperHz <= 1200) {
                zone4Vals.add(val);
            } else if (upperHz <= 1600) {
                zone5Vals.add(val);
            } else {
                zone5Vals.add(val);
            }
        }

        return List.of(
                buildFrequencyZonePoint("Zone 1", "0~300Hz", "로봇 진동 / 보호 대역", lowVals, LOW_BAND_THRESHOLD),
                buildFrequencyZonePoint("Zone 2", "301~600Hz", "정상 동작 / 중간 절차 대역", mainVals, MID_BAND_THRESHOLD),
                buildFrequencyZonePoint("Zone 3", "601~900Hz", "로봇 진동 감시 대역", highVals, HIGH_BAND_THRESHOLD),
                buildFrequencyZonePoint("Zone 4", "901~1200Hz", "고주파 진동 집중 감시 대역", zone4Vals, HIGH_BAND_THRESHOLD),
                buildFrequencyZonePoint("Zone 5", "1201~1600Hz", "초고주파 충격 / 충돌 위험 대역", zone5Vals, HIGH_BAND_THRESHOLD)
        );
    }

    private BodyAnomalyDetectionResponse.FrequencyZoneAnalysis analyzeFrequencyZones(Map<String, Double> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }

        List<Double> lowVals = new ArrayList<>();
        List<Double> mainVals = new ArrayList<>();
        List<Double> highVals = new ArrayList<>();
        List<Double> zone4Vals = new ArrayList<>();
        List<Double> zone5Vals = new ArrayList<>();

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
            } else if (upperHz <= 1200) {
                zone4Vals.add(val);
            } else if (upperHz <= 1600) {
                zone5Vals.add(val);
            } else {
                zone5Vals.add(val);
            }
        }

        return new BodyAnomalyDetectionResponse.FrequencyZoneAnalysis(
                computeZoneStats(lowVals),
                computeZoneStats(mainVals),
                computeZoneStats(highVals),
                computeZoneStats(zone4Vals),
                computeZoneStats(zone5Vals)
        );
    }

    private BodyAnomalyDetectionResponse.FrequencyZonePoint buildFrequencyZonePoint(
            String zone,
            String range,
            String description,
            List<Double> values,
            BandThreshold threshold
    ) {
        BodyAnomalyDetectionResponse.FrequencyZoneAnalysis.ZoneStats stats = computeZoneStats(values);
        return BodyAnomalyDetectionResponseMapper.toFrequencyZonePoint(
                zone,
                range,
                description,
                stats.avg(),
                stats.max(),
                threshold.target(),
                threshold.warning(),
                threshold.danger()
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

    // 주파수 대역 키를 LOW / MID / HIGH 임계값 세트로 변환한다.
    private BandThreshold thresholdForBand(String rawKey) {
        // 해석된 대역명을 ISO 기준 임계값 세트에 매핑한다.
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

    // 개별 신호를 합쳐 차체 최종 심각도 문자열을 만든다.
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
        // 동작 상태, 운전 모드, 주파수 대역, 분석 위험도를 합쳐 최종 심각도를 계산한다.
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
            return BodyAnomalyDetectionResponseMapper.toAlert(false, "차체 이상 탐지 미검출", null, List.of());
        }

        List<String> reasons = new ArrayList<>();
        int abnormalCount = 0;
        double maxRobotVibrationScore = 0.0;

        for (BodyAnomalyDetectionResponse.ChartPoint point : points) {
            if (!isBodyAnomaly(point)) {
                continue;
            }
            if (!"NORMAL".equalsIgnoreCase(point.severity()) || Boolean.TRUE.equals(point.isAbnormal())) {
                abnormalCount++;
            }
            if (point.robotVibrationScore() != null) {
                maxRobotVibrationScore = Math.max(maxRobotVibrationScore, point.robotVibrationScore());
            }
        }

        if (summaryResult != null) {
            if ("COLLISION_RISK".equals(summaryResult.getRobotMotionStatus())) {
                reasons.add("로봇 진동 상태: 충돌 위험");
            } else if (summaryResult.getRobotMotionStatus() != null && !"NORMAL".equals(summaryResult.getRobotMotionStatus())) {
                reasons.add("로봇 진동 상태: " + localizeRobotMotionStatus(summaryResult.getRobotMotionStatus()));
            }
            if ("AUTO_MANUAL_STOPPED".equals(summaryResult.getRobotOperationMode())
                    || "STOPPED".equals(summaryResult.getRobotOperationMode())
                    || "MANUAL".equals(summaryResult.getRobotOperationMode())) {
                reasons.add("로봇 동작 모드: " + localizeRobotOperationMode(summaryResult.getRobotOperationMode()));
            }
            if (summaryResult.getFrequencyPeakValue() != null && summaryResult.getFrequencyPeakValue() > 0) {
                reasons.add(String.format("주파수 피크 증가: %.6f mm/s", summaryResult.getFrequencyPeakValue()));
            }
            if (summaryResult.getFrequencyPeakBand() != null) {
                reasons.add("주파수 피크 대역: " + localizeFrequencyPeakBand(summaryResult.getFrequencyPeakBand()));
            }
        }

        if (abnormalCount > 0) {
            reasons.add("이상 탐지 건수: " + abnormalCount);
        }
        if (maxRobotVibrationScore > 0) {
            reasons.add(String.format("최대 로봇 진동 가속도: %.4f", maxRobotVibrationScore));
        }

        String logNo = summaryResult == null ? null : resolveAlertLogNo(summaryResult.getAnalysisResult().getEventId());
        return BodyAnomalyDetectionResponseMapper.toAlert(true, "차체 이상 탐지 경고", logNo, List.copyOf(reasons));
    }

    private String resolveAlertLogNo(String eventId) {
        if (alertEventRepository == null || eventId == null || eventId.isBlank()) {
            return null;
        }
        return alertEventRepository.findLogNoByEventId(eventId).orElse(null);
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

    // 로봇 이동 상태를 기준으로 경고/위험 심각도를 판정한다.
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

    // 동작 모드를 기준으로 정상/경고 심각도를 판정한다.
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

    // 주파수 피크 값과 대역 임계값을 비교해 이상 심각도를 계산한다.
    private SeverityLevel frequencySeverity(
            String frequencyPeakBand,
            Map<String, Double> frequencyBands,
            Double frequencyPeakValue
    ) {
        // 대역별 피크 임계값으로 경고 / 위험 심각도를 판정한다.
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

    // 차체 이상 판단에 쓰는 핵심 신호를 종합해 anomaly 여부를 결정한다.
    private boolean isBodyAnomaly(BodyAnomalyDetectionResponse.ChartPoint point) {
        // severity, abnormal 플래그, risk score 중 하나라도 기준을 넘으면 이상으로 본다.
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

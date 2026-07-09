package com.aims.assembly.service.press;

import com.aims.assembly.domain.press.PressAnalysisResult;
import com.aims.assembly.dto.press.PressAnomalyDetectionResponse;
import com.aims.assembly.mapper.PressAnomalyDetectionResponseMapper;
import com.aims.assembly.repository.analysis.PressAnalysisResultRepository;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository;
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
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PressAnomalyDetectionService {
    private static final int MAX_EVENT_LOOKUP_SIZE = 10_000;

    private final PressAnalysisResultRepository repository;
    private final ManufacturingEventJsonRepository eventRepository;

    @Cacheable(
            cacheNames = "press-anomaly-dashboard",
            key = "T(java.time.LocalDate).parse(#date?.toString() ?: #from?.toLocalDate()?.toString() ?: #to?.toLocalDate()?.toString() ?: T(java.time.LocalDate).now().toString())"
    )
    public PressAnomalyDetectionResponse findDashboard(
            LocalDate date,
            LocalDateTime from,
            LocalDateTime to,
            LocalDateTime endAt,
            int limit
    ) {
        List<PressAnomalyDetectionResponse.DateOption> dateOptions = dateOptions();
        LocalDate targetDate = resolveDate(date, from, to, endAt, dateOptions);
        LocalDateTime rangeFrom = from != null ? from : targetDate.atStartOfDay();
        LocalDateTime rangeTo = to != null ? to : LocalDateTime.of(targetDate, LocalTime.MAX);
        if (endAt != null && endAt.isBefore(rangeTo)) {
            rangeTo = endAt;
        }

        List<PressAnomalyDetectionResponse.ChartPoint> points = repository.findDashboardByEventTimeBetween(
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
                .sorted(Comparator.comparing(PressAnomalyDetectionResponse.ChartPoint::timestamp)
                        .thenComparing(PressAnomalyDetectionResponse.ChartPoint::eventId))
                .toList();

        PressAnomalyDetectionResponse.ChartPoint latest = points.isEmpty() ? null : points.get(points.size() - 1);
        PressAnomalyDetectionResponse.ChartPoint summaryPoint = points.stream()
                .filter(this::isPressAnomaly)
                .max(Comparator.comparingDouble(this::anomalyWeight)
                        .thenComparing(PressAnomalyDetectionResponse.ChartPoint::timestamp,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(latest);
        boolean detected = points.stream().anyMatch(this::isPressAnomaly);
        LocalDateTime previousEndAt = points.isEmpty() ? null : points.get(0).timestamp().minusNanos(1);

        Double dbMaxRiskScore = repository.findMaxRiskScoreByEventTimeBetween(rangeFrom, rangeTo);
        double maxRiskScore = dbMaxRiskScore != null ? dbMaxRiskScore : 0.0;
        PressAnomalyDetectionResponse.Charts charts = PressAnomalyDetectionResponseMapper.toCharts(points);

        PressAnomalyDetectionResponse response = PressAnomalyDetectionResponseMapper.toResponse(
                targetDate,
                rangeFrom,
                rangeTo,
                previousEndAt,
                dateOptions,
                toMetrics(summaryPoint, detected, maxRiskScore),
                charts,
                points,
                toAlert(points, detected, summaryPoint)
        );
        log.info("press anomaly dashboard loaded: date={}, from={}, to={}, count={}, detected={}",
                targetDate, rangeFrom, rangeTo, points.size(), detected);
        return response;
    }

    private List<PressAnomalyDetectionResponse.DateOption> dateOptions() {
        return repository.findPressAnalysisDateOptions().stream()
                .map(option -> PressAnomalyDetectionResponseMapper.toDateOption(
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
            List<PressAnomalyDetectionResponse.DateOption> dateOptions
    ) {
        if (date != null) return date;
        if (from != null) return from.toLocalDate();
        if (endAt != null) return endAt.toLocalDate();
        if (to != null) return to.toLocalDate();
        return dateOptions.isEmpty() ? LocalDate.now() : dateOptions.get(0).date();
    }

    private PressAnomalyDetectionResponse.ChartPoint toChartPoint(PressAnalysisResult result) {
        return PressAnomalyDetectionResponseMapper.toChartPoint(result);
    }

    private PressAnomalyDetectionResponse.Metrics toMetrics(
            PressAnomalyDetectionResponse.ChartPoint point,
            boolean detected,
            double maxRiskScore
    ) {
        if (point == null) {
            return PressAnomalyDetectionResponseMapper.toMetrics(
                    new PressAnomalyDetectionResponse.ChartPoint(
                            null, null, null,
                            0.0, 0.0, 0.0,
                            maxRiskScore,
                            null, null,
                            detected ? "WARNING" : "NORMAL"
                    )
            );
        }
        String computedSeverity = maxRiskScore >= 80.0
                ? "CRITICAL"
                : (maxRiskScore >= 60.0 ? "WARNING" : point.severity());
        PressAnomalyDetectionResponse.ChartPoint updatedPoint = new PressAnomalyDetectionResponse.ChartPoint(
                point.eventId(),
                point.analysisId(),
                point.timestamp(),
                point.targetCycleTimeSec(),
                point.actualCycleTimeSec(),
                point.cycleTimeGapSec(),
                maxRiskScore,
                point.countIncreaseYn(),
                point.isAbnormal(),
                computedSeverity
        );
        return PressAnomalyDetectionResponseMapper.toMetrics(updatedPoint);
    }

    private PressAnomalyDetectionResponse.AlertPanel toAlert(
            List<PressAnomalyDetectionResponse.ChartPoint> points,
            boolean detected,
            PressAnomalyDetectionResponse.ChartPoint summaryPoint
    ) {
        if (!detected || points == null || points.isEmpty()) {
            return PressAnomalyDetectionResponseMapper.toAlert(false, "press anomaly not detected", List.of());
        }

        int countIncreaseFail = 0;
        int cycleTimeExceeded = 0;
        int abnormalCount = 0;
        double maxCycleTimeGap = 0.0;

        for (PressAnomalyDetectionResponse.ChartPoint point : points) {
            if (!isPressAnomaly(point)) {
                continue;
            }
            if (Boolean.FALSE.equals(point.countIncreaseYn())) {
                countIncreaseFail++;
            }
            if (point.targetCycleTimeSec() != null && point.actualCycleTimeSec() != null
                    && point.actualCycleTimeSec() > point.targetCycleTimeSec()) {
                cycleTimeExceeded++;
                maxCycleTimeGap = Math.max(maxCycleTimeGap, point.actualCycleTimeSec() - point.targetCycleTimeSec());
            }
            if (Boolean.TRUE.equals(point.isAbnormal())) {
                abnormalCount++;
            }
        }

        List<String> reasons = new ArrayList<>();
        if (countIncreaseFail > 0) {
            reasons.add("countIncreaseYn failure: " + countIncreaseFail + " cases");
        }
        if (cycleTimeExceeded > 0) {
            reasons.add("cycle time exceeded: " + cycleTimeExceeded + " cases, max +" + formatSec(maxCycleTimeGap) + " sec");
        }
        if (abnormalCount > 0) {
            reasons.add("abnormal points: " + abnormalCount + " cases");
        }
        if (summaryPoint != null) {
            reasons.add("summary event: " + summaryPoint.eventId());
        }

        return PressAnomalyDetectionResponseMapper.toAlert(true, "press anomaly warning", List.copyOf(reasons));
    }

    private boolean isPressAnomaly(PressAnomalyDetectionResponse.ChartPoint point) {
        return point != null
                && (Boolean.FALSE.equals(point.countIncreaseYn())
                || (point.targetCycleTimeSec() != null && point.actualCycleTimeSec() != null
                && point.actualCycleTimeSec() > point.targetCycleTimeSec())
                || Boolean.TRUE.equals(point.isAbnormal()));
    }

    private double anomalyWeight(PressAnomalyDetectionResponse.ChartPoint point) {
        if (point == null) {
            return -1.0;
        }
        double weight = 0.0;
        if (Boolean.FALSE.equals(point.countIncreaseYn())) {
            weight += 40.0;
        }
        if (point.targetCycleTimeSec() != null && point.actualCycleTimeSec() != null
                && point.actualCycleTimeSec() > point.targetCycleTimeSec()) {
            weight += (point.actualCycleTimeSec() - point.targetCycleTimeSec()) * 10.0;
        }
        if (Boolean.TRUE.equals(point.isAbnormal())) {
            weight += 50.0;
        }
        return weight;
    }

    private String formatSec(Double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value == null ? 0.0 : value);
    }

    private boolean isLater(
            PressAnalysisResult left,
            PressAnalysisResult right
    ) {
        LocalDateTime leftAnalyzedAt = left.getAnalysisResult().getAnalyzedAt();
        LocalDateTime rightAnalyzedAt = right.getAnalysisResult().getAnalyzedAt();
        if (leftAnalyzedAt != null && rightAnalyzedAt != null) {
            int compared = leftAnalyzedAt.compareTo(rightAnalyzedAt);
            if (compared != 0) {
                return compared > 0;
            }
        } else if (leftAnalyzedAt != null) {
            return true;
        } else if (rightAnalyzedAt != null) {
            return false;
        }
        Long leftId = left.getAnalysisResult().getId();
        Long rightId = right.getAnalysisResult().getId();
        if (leftId == null) {
            return false;
        }
        if (rightId == null) {
            return true;
        }
        return leftId >= rightId;
    }
}

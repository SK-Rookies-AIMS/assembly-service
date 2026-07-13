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
    // ISO 7870 관리도 해석을 참고해, 프레스 사이클 편차를 정상/경고/위험으로 나누는 기준값이다.
    private static final double PRESS_NORMAL_CYCLE_DELTA_SEC = 2.0;
    private static final double PRESS_WARNING_CYCLE_DELTA_SEC = 3.0;

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
            String severity = detected ? "WARNING" : "NORMAL";
                return PressAnomalyDetectionResponseMapper.toMetrics(
                    new PressAnomalyDetectionResponse.ChartPoint(
                            null, null, null,
                            0.0, 0.0, 0.0,
                            maxRiskScore,
                            null, null,
                            severity,
                            PressAnomalyDetectionResponse.WARNING_CYCLE_GAP_SEC,
                            PressAnomalyDetectionResponse.DANGER_CYCLE_GAP_SEC
                    )
            );
        }
        String computedSeverity = pressSeverity(point);
        if (maxRiskScore >= 80.0) {
            computedSeverity = "CRITICAL";
        } else if (maxRiskScore >= 60.0 && "NORMAL".equalsIgnoreCase(computedSeverity)) {
            computedSeverity = "WARNING";
        }
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
                computedSeverity,
                PressAnomalyDetectionResponse.WARNING_CYCLE_GAP_SEC,
                PressAnomalyDetectionResponse.DANGER_CYCLE_GAP_SEC
        );
        return PressAnomalyDetectionResponseMapper.toMetrics(updatedPoint);
    }

    private PressAnomalyDetectionResponse.AlertPanel toAlert(
            List<PressAnomalyDetectionResponse.ChartPoint> points,
            boolean detected,
            PressAnomalyDetectionResponse.ChartPoint summaryPoint
    ) {
        if (!detected || points == null || points.isEmpty()) {
            return PressAnomalyDetectionResponseMapper.toAlert(false, "프레스 이상 탐지 미검출", List.of());
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
            double gap = cycleTimeGap(point);
            if (gap > PRESS_NORMAL_CYCLE_DELTA_SEC) {
                cycleTimeExceeded++;
                maxCycleTimeGap = Math.max(maxCycleTimeGap, gap);
            }
            if (!"NORMAL".equalsIgnoreCase(pressSeverity(point))) {
                abnormalCount++;
            }
        }

        List<String> reasons = new ArrayList<>();
        if (countIncreaseFail > 0) {
            reasons.add("생산 수 증가 미충족: " + countIncreaseFail + "건");
        }
        if (cycleTimeExceeded > 0) {
            reasons.add("사이클 지연 초과: " + cycleTimeExceeded + "건, 최대 +" + formatSec(maxCycleTimeGap) + " sec");
        }
        if (abnormalCount > 0) {
            reasons.add("이상 감지: " + abnormalCount + "건");
        }
        if (summaryPoint != null) {
            reasons.add("대표 이상 이벤트: " + summaryPoint.eventId());
        }

        return PressAnomalyDetectionResponseMapper.toAlert(true, "프레스 이상 탐지 경고", List.copyOf(reasons));
    }

    private boolean isPressAnomaly(PressAnomalyDetectionResponse.ChartPoint point) {
        // 카운트 증가 여부, 사이클 편차, 분석 결과 abnormal 플래그를 함께 반영한다.
        return point != null && !"NORMAL".equalsIgnoreCase(pressSeverity(point));
    }

    private double anomalyWeight(PressAnomalyDetectionResponse.ChartPoint point) {
        if (point == null) {
            return -1.0;
        }
        double weight = 0.0;
        String severity = pressSeverity(point);
        if ("CRITICAL".equalsIgnoreCase(severity)) {
            weight += 100.0;
        } else if ("WARNING".equalsIgnoreCase(severity)) {
            weight += 50.0;
        }
        if (Boolean.FALSE.equals(point.countIncreaseYn())) {
            weight += 40.0;
        }
        if (point.countIncreaseYn() == null) {
            weight += 20.0;
        }
        double gap = cycleTimeGap(point);
        if (gap > 0.0) {
            weight += gap * 10.0;
        }
        if (point.isAbnormal() != null && point.isAbnormal()) {
            weight += 15.0;
        }
        return weight;
    }

    private double cycleTimeGap(PressAnomalyDetectionResponse.ChartPoint point) {
        if (point == null || point.targetCycleTimeSec() == null || point.actualCycleTimeSec() == null) {
            return 0.0;
        }
        return Math.abs(point.actualCycleTimeSec() - point.targetCycleTimeSec());
    }

    private String pressSeverity(PressAnomalyDetectionResponse.ChartPoint point) {
        if (point == null) {
            return "NORMAL";
        }
        if ("CRITICAL".equalsIgnoreCase(point.severity())) {
            return "CRITICAL";
        }
        if ("WARNING".equalsIgnoreCase(point.severity())) {
            return "WARNING";
        }
        double gap = cycleTimeGap(point);
        if (Boolean.FALSE.equals(point.countIncreaseYn()) || gap > PRESS_WARNING_CYCLE_DELTA_SEC) {
            return "CRITICAL";
        }
        if (point.countIncreaseYn() == null || gap > PRESS_NORMAL_CYCLE_DELTA_SEC || Boolean.TRUE.equals(point.isAbnormal())) {
            return "WARNING";
        }
        return "NORMAL";
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

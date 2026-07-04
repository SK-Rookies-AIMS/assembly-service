package com.aims.assembly.service.process;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import com.aims.assembly.domain.assembly.AssemblyAnalysisResult;
import com.aims.assembly.domain.enums.EquipmentOperationStatus;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.domain.enums.Severity;
import com.aims.assembly.domain.paint.PaintAnalysisResult;
import com.aims.assembly.dto.process.AssemblyDashboardResponse;
import com.aims.assembly.dto.process.EquipmentOperationRateResponse;
import com.aims.assembly.dto.process.PaintDashboardResponse;
import com.aims.assembly.dto.process.ProcessAvailableDatesResponse;
import com.aims.assembly.repository.analysis.AssemblyAnalysisResultRepository;
import com.aims.assembly.repository.analysis.PaintAnalysisResultRepository;
import com.aims.assembly.repository.equipment.EquipmentOperationRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProcessDashboardService {
    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 200;
    private static final double DEFECT_SCORE_ALERT_THRESHOLD = 0.7;
    private static final double SURFACE_QUALITY_ALERT_THRESHOLD = 85.0;
    private static final double THICKNESS_MIN = 115.0;
    private static final double THICKNESS_MAX = 125.0;
    private static final double THERMAL_STD_TEMP_ALERT_THRESHOLD = 3.0;

    private final PaintAnalysisResultRepository paintRepository;
    private final AssemblyAnalysisResultRepository assemblyRepository;
    private final EquipmentOperationRateRepository equipmentOperationRateRepository;

    @Cacheable(cacheNames = "process-equipment-operation-rate-v1", key = "'all'")
    public EquipmentOperationRateResponse getEquipmentOperationRate() {
        Map<ProcessCode, EnumMap<EquipmentOperationStatus, Long>> countsByProcess =
                initialEquipmentStatusCounts();
        for (EquipmentOperationRateRepository.StatusCount row
                : equipmentOperationRateRepository.countByProcessAndStatus()) {
            countsByProcess.get(row.processCode()).put(row.status(), row.count());
        }

        return new EquipmentOperationRateResponse(List.of(
                equipmentOperationRateItem(ProcessCode.PRESS, countsByProcess.get(ProcessCode.PRESS)),
                equipmentOperationRateItem(ProcessCode.BODY, countsByProcess.get(ProcessCode.BODY)),
                equipmentOperationRateItem(ProcessCode.PAINT, countsByProcess.get(ProcessCode.PAINT)),
                equipmentOperationRateItem(ProcessCode.ASSEMBLY, countsByProcess.get(ProcessCode.ASSEMBLY))
        ));
    }

    @Cacheable(
            cacheNames = "process-paint-dashboard-v1",
            key = "'date:' + (#date == null ? 'null' : #date.toString())"
                    + " + ':from:' + (#from == null ? 'null' : #from.toString())"
                    + " + ':to:' + (#to == null ? 'null' : #to.toString())"
                    + " + ':limit:' + (#limit == null ? 30 : T(java.lang.Math).max(1, T(java.lang.Math).min(#limit, 200)))"
    )
    public PaintDashboardResponse getPaintDashboard(
            LocalDate date,
            LocalDateTime from,
            LocalDateTime to,
            Integer limit
    ) {
        LocalDate latestDate = date == null && from == null && to == null ? latestPaintDate() : null;
        QueryRange range = queryRange(date, from, to, latestDate);
        List<PaintAnalysisResult> rows = paintRepository.findDashboardRows(
                range.from(),
                range.to(),
                PageRequest.of(0, normalizeLimit(limit))
        );
        if (rows.isEmpty()) {
            return PaintDashboardResponse.empty(range.selectedDate());
        }

        long analysisCount = rows.size();
        long abnormalCount = rows.stream()
                .filter(row -> Boolean.TRUE.equals(row.getAnalysisResult().getIsAbnormal()))
                .count();
        long alertCount = rows.stream()
                .filter(row -> isAlert(row.getAnalysisResult()))
                .count();
        double averageSurfaceQualityScore = average(rows.stream()
                .map(PaintAnalysisResult::getSurfaceQualityScore)
                .filter(Objects::nonNull)
                .toList());

        PaintDashboardResponse.Summary summary = new PaintDashboardResponse.Summary(
                analysisCount,
                percentage(abnormalCount, analysisCount),
                averageSurfaceQualityScore,
                alertCount
        );

        List<PaintDashboardResponse.ChartPoint> chart = rows.stream()
                .map(row -> {
                    ManufacturingAnalysisResult result = row.getAnalysisResult();
                    return new PaintDashboardResponse.ChartPoint(
                            displayTime(result),
                            row.getDefectScore(),
                            row.getSurfaceQualityScore(),
                            row.getThicknessValue(),
                            result.getRiskScore(),
                            row.getImagePosition(),
                            row.getVisionLabel(),
                            row.getThermalStdTemp(),
                            severityName(result)
                    );
                })
                .toList();

        PaintAnalysisResult alertRow = rows.stream()
                .max(Comparator
                        .comparing((PaintAnalysisResult row) -> riskScore(row.getAnalysisResult()))
                        .thenComparing(row -> displayTime(row.getAnalysisResult()),
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);

        return new PaintDashboardResponse(range.selectedDate(), summary, chart, paintAlert(alertRow));
    }

    @Cacheable(
            cacheNames = "process-assembly-dashboard-v1",
            key = "'date:' + (#date == null ? 'null' : #date.toString())"
                    + " + ':from:' + (#from == null ? 'null' : #from.toString())"
                    + " + ':to:' + (#to == null ? 'null' : #to.toString())"
                    + " + ':limit:' + (#limit == null ? 30 : T(java.lang.Math).max(1, T(java.lang.Math).min(#limit, 200)))"
    )
    public AssemblyDashboardResponse getAssemblyDashboard(
            LocalDate date,
            LocalDateTime from,
            LocalDateTime to,
            Integer limit
    ) {
        LocalDate latestDate = date == null && from == null && to == null ? latestAssemblyDate() : null;
        QueryRange range = queryRange(date, from, to, latestDate);
        List<AssemblyAnalysisResult> rows = assemblyRepository.findDashboardRows(
                range.from(),
                range.to(),
                PageRequest.of(0, normalizeLimit(limit))
        );
        if (rows.isEmpty()) {
            return AssemblyDashboardResponse.empty(range.selectedDate());
        }

        long vehicleCount = rows.stream()
                .map(row -> row.getAnalysisResult().getCarMasterId())
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long sequenceErrorCount = sum(rows.stream()
                .map(AssemblyAnalysisResult::getSequenceErrorCount)
                .toList());
        long missingPartCount = sum(rows.stream()
                .map(AssemblyAnalysisResult::getMissingPartCount)
                .toList());
        long fasteningErrorCount = sum(rows.stream()
                .map(AssemblyAnalysisResult::getFasteningErrorCount)
                .toList());
        double averageRiskScore = average(rows.stream()
                .map(row -> row.getAnalysisResult().getRiskScore())
                .filter(Objects::nonNull)
                .toList());

        AssemblyDashboardResponse.Summary summary = new AssemblyDashboardResponse.Summary(
                vehicleCount,
                sequenceErrorCount,
                missingPartCount,
                fasteningErrorCount,
                averageRiskScore
        );

        List<AssemblyDashboardResponse.VehicleRow> vehicles = rows.stream()
                .map(row -> {
                    ManufacturingAnalysisResult result = row.getAnalysisResult();
                    return new AssemblyDashboardResponse.VehicleRow(
                            result.getCarMasterId(),
                            carDisplayId(result.getCarMasterId()),
                            row.getExpectedSequence(),
                            row.getActualSequence(),
                            zeroIfNull(row.getSequenceErrorCount()),
                            zeroIfNull(row.getMissingPartCount()),
                            zeroIfNull(row.getFasteningErrorCount()),
                            result.getRiskScore(),
                            severityName(result),
                            statusLabel(result),
                            displayTime(result)
                    );
                })
                .toList();

        AssemblyAnalysisResult alertRow = rows.stream()
                .max(Comparator
                        .comparing((AssemblyAnalysisResult row) -> riskScore(row.getAnalysisResult()))
                        .thenComparing(row -> displayTime(row.getAnalysisResult()),
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);

        return new AssemblyDashboardResponse(range.selectedDate(), summary, vehicles, assemblyAlert(alertRow));
    }

    @Cacheable(cacheNames = "process-paint-dates-v1", key = "'all'")
    public ProcessAvailableDatesResponse getPaintDates() {
        return ProcessAvailableDatesResponse.of(availablePaintDates());
    }

    @Cacheable(cacheNames = "process-assembly-dates-v1", key = "'all'")
    public ProcessAvailableDatesResponse getAssemblyDates() {
        return ProcessAvailableDatesResponse.of(availableAssemblyDates());
    }

    private Map<ProcessCode, EnumMap<EquipmentOperationStatus, Long>> initialEquipmentStatusCounts() {
        EnumMap<ProcessCode, EnumMap<EquipmentOperationStatus, Long>> countsByProcess =
                new EnumMap<>(ProcessCode.class);
        for (ProcessCode processCode : List.of(
                ProcessCode.PRESS,
                ProcessCode.BODY,
                ProcessCode.PAINT,
                ProcessCode.ASSEMBLY
        )) {
            EnumMap<EquipmentOperationStatus, Long> statusCounts =
                    new EnumMap<>(EquipmentOperationStatus.class);
            for (EquipmentOperationStatus status : EquipmentOperationStatus.values()) {
                statusCounts.put(status, 0L);
            }
            countsByProcess.put(processCode, statusCounts);
        }
        return countsByProcess;
    }

    private EquipmentOperationRateResponse.Item equipmentOperationRateItem(
            ProcessCode processCode,
            EnumMap<EquipmentOperationStatus, Long> statusCounts
    ) {
        long runningCount = statusCounts.get(EquipmentOperationStatus.RUNNING);
        long warningCount = statusCounts.get(EquipmentOperationStatus.WARNING);
        long stoppedCount = statusCounts.get(EquipmentOperationStatus.STOPPED);
        long faultCount = statusCounts.get(EquipmentOperationStatus.FAULT);
        long operatingCount = runningCount + warningCount;
        long totalCount = runningCount + warningCount + stoppedCount + faultCount;
        return new EquipmentOperationRateResponse.Item(
                processCode.name(),
                processName(processCode),
                runningCount,
                warningCount,
                operatingCount,
                stoppedCount,
                faultCount,
                totalCount,
                percentage(operatingCount, totalCount),
                new EnumMap<>(statusCounts)
        );
    }

    private String processName(ProcessCode processCode) {
        return switch (processCode) {
            case PRESS -> "프레스";
            case BODY -> "차체";
            case PAINT -> "도장";
            case ASSEMBLY -> "의장";
        };
    }

    private PaintDashboardResponse.Alert paintAlert(PaintAnalysisResult row) {
        if (row == null) {
            return null;
        }
        ManufacturingAnalysisResult result = row.getAnalysisResult();
        List<String> messages = new ArrayList<>();
        if ("DEFECT".equalsIgnoreCase(row.getVisionLabel())) {
            messages.add("비전 불량 라벨 감지: " + row.getVisionLabel());
        }
        if (row.getDefectScore() != null && row.getDefectScore() >= DEFECT_SCORE_ALERT_THRESHOLD) {
            messages.add("불량 점수 상승: defect_score " + format(row.getDefectScore()));
        }
        if (row.getSurfaceQualityScore() != null
                && row.getSurfaceQualityScore() < SURFACE_QUALITY_ALERT_THRESHOLD) {
            messages.add("표면 품질 점수 저하: surface_quality_score "
                    + format(row.getSurfaceQualityScore()));
        }
        if (row.getThicknessValue() != null
                && (row.getThicknessValue() < THICKNESS_MIN || row.getThicknessValue() > THICKNESS_MAX)) {
            messages.add("도장 두께 이상 의심: thickness_value " + format(row.getThicknessValue()));
        }
        if (row.getThermalStdTemp() != null
                && row.getThermalStdTemp() >= THERMAL_STD_TEMP_ALERT_THRESHOLD) {
            messages.add("온도 균일도 이상: thermal_std_temp " + format(row.getThermalStdTemp()));
        }
        messages.add("위험도/등급: " + format(riskScore(result)) + " / " + severityName(result));
        if (row.getSurfaceQualityScore() != null && row.getSurfaceQualityScore() < SURFACE_QUALITY_ALERT_THRESHOLD
                && row.getThicknessValue() != null
                && (row.getThicknessValue() < THICKNESS_MIN || row.getThicknessValue() > THICKNESS_MAX)) {
            messages.add("표면 품질 점수 저하 및 두께 이상 의심");
        }
        return new PaintDashboardResponse.Alert("도장 품질 이상 감지", messages);
    }

    private AssemblyDashboardResponse.Alert assemblyAlert(AssemblyAnalysisResult row) {
        if (row == null) {
            return null;
        }
        ManufacturingAnalysisResult result = row.getAnalysisResult();
        int sequenceErrors = zeroIfNull(row.getSequenceErrorCount());
        int fasteningErrors = zeroIfNull(row.getFasteningErrorCount());
        int missingParts = zeroIfNull(row.getMissingPartCount());
        List<String> messages = new ArrayList<>();
        messages.add("기준 순서: " + nullToDash(row.getExpectedSequence()));
        messages.add("실제 순서: " + nullToDash(row.getActualSequence()));
        messages.add("순서 오류: " + sequenceErrors + "건");
        messages.add("체결 오류: " + fasteningErrors + "건");
        messages.add("누락 부품: " + missingParts + "건");
        messages.add("위험도/등급: " + format(riskScore(result)) + " / " + severityName(result));
        if (sequenceErrors > 0 && fasteningErrors > 0) {
            messages.add("조립 순서 오류와 체결 오류 동시 감지");
        } else if (sequenceErrors > 0) {
            messages.add("조립 순서 오류 감지");
        } else if (missingParts > 0 || fasteningErrors > 0) {
            messages.add("부품 누락 또는 체결 오류 감지");
        }
        return new AssemblyDashboardResponse.Alert("조립 순서 오류 감지", messages);
    }

    private QueryRange queryRange(
            LocalDate date,
            LocalDateTime from,
            LocalDateTime to,
            LocalDate latestDate
    ) {
        if (from != null || to != null) {
            return new QueryRange(from, to, null);
        }
        LocalDate selectedDate = date != null ? date : latestDate;
        if (selectedDate == null) {
            return new QueryRange(null, null, null);
        }
        return new QueryRange(
                selectedDate.atStartOfDay(),
                selectedDate.plusDays(1).atStartOfDay(),
                selectedDate
        );
    }

    private List<LocalDate> availablePaintDates() {
        return availableDates(paintRepository.findDashboardAnalyzedAtValues());
    }

    private List<LocalDate> availableAssemblyDates() {
        return availableDates(assemblyRepository.findDashboardAnalyzedAtValues());
    }

    private LocalDate latestPaintDate() {
        return latestDate(availablePaintDates());
    }

    private LocalDate latestAssemblyDate() {
        return latestDate(availableAssemblyDates());
    }

    private List<LocalDate> availableDates(List<LocalDateTime> dateTimes) {
        return dateTimes.stream()
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .distinct()
                .sorted()
                .toList();
    }

    private LocalDate latestDate(List<LocalDate> dates) {
        return dates.isEmpty() ? null : dates.get(dates.size() - 1);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private LocalDateTime displayTime(ManufacturingAnalysisResult result) {
        return result.getAnalyzedAt() != null ? result.getAnalyzedAt() : result.getEventTime();
    }

    private boolean isAlert(ManufacturingAnalysisResult result) {
        return result.getSeverity() == Severity.WARNING
                || result.getSeverity() == Severity.CRITICAL
                || Boolean.TRUE.equals(result.getIsAbnormal());
    }

    private String statusLabel(ManufacturingAnalysisResult result) {
        double score = riskScore(result);
        if (result.getSeverity() == Severity.CRITICAL || score >= 80.0) {
            return "위험";
        }
        if (result.getSeverity() == Severity.WARNING || score >= 50.0) {
            return "경고";
        }
        return "정상";
    }

    private String severityName(ManufacturingAnalysisResult result) {
        return result.getSeverity() == null ? null : result.getSeverity().name();
    }

    private double riskScore(ManufacturingAnalysisResult result) {
        return result.getRiskScore() == null ? 0.0 : result.getRiskScore();
    }

    private double percentage(long numerator, long denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        return round(numerator * 100.0 / denominator);
    }

    private double average(List<? extends Number> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        return round(values.stream()
                .mapToDouble(Number::doubleValue)
                .average()
                .orElse(0.0));
    }

    private long sum(List<Integer> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
    }

    private int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }

    private String carDisplayId(Long carMasterId) {
        return carMasterId == null ? null : "CAR-%06d".formatted(carMasterId);
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String format(double value) {
        return String.valueOf(round(value));
    }

    private record QueryRange(LocalDateTime from, LocalDateTime to, LocalDate selectedDate) {
    }
}

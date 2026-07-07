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
import com.aims.assembly.mapper.ProcessDashboardResponseMapper;
import com.aims.assembly.repository.analysis.AssemblyAnalysisResultRepository;
import com.aims.assembly.repository.analysis.PaintAnalysisResultRepository;
import com.aims.assembly.repository.equipment.EquipmentOperationRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProcessDashboardService {
    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 200;
    private static final String STATUS_NORMAL = "NORMAL";
    private static final String STATUS_WARNING = "WARNING";
    private static final String STATUS_DANGER = "DANGER";
    private static final double SURFACE_QUALITY_WARNING_BELOW = 80.0;
    private static final double SURFACE_QUALITY_DANGER_BELOW = 60.0;
    private static final double THICKNESS_TARGET = 115.0;
    private static final double THICKNESS_NORMAL_MIN = 90.0;
    private static final double THICKNESS_NORMAL_MAX = 120.0;
    private static final double THICKNESS_WARNING_MIN = 80.0;
    private static final double THICKNESS_WARNING_MAX = 130.0;
    private static final double DEFECT_SCORE_WARNING_ABOVE = 0.4;
    private static final double DEFECT_SCORE_DANGER_ABOVE = 0.6;
    private static final double THERMAL_STD_TEMP_WARNING_ABOVE = 2.0;
    private static final double THERMAL_STD_TEMP_DANGER_ABOVE = 5.0;

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

        EquipmentOperationRateResponse response = ProcessDashboardResponseMapper.toEquipmentOperationRateResponse(List.of(
                equipmentOperationRateItem(ProcessCode.PRESS, countsByProcess.get(ProcessCode.PRESS)),
                equipmentOperationRateItem(ProcessCode.BODY, countsByProcess.get(ProcessCode.BODY)),
                equipmentOperationRateItem(ProcessCode.PAINT, countsByProcess.get(ProcessCode.PAINT)),
                equipmentOperationRateItem(ProcessCode.ASSEMBLY, countsByProcess.get(ProcessCode.ASSEMBLY))
        ));
        log.info("공정 장비 가동률 조회 완료: 공정수=4");
        return response;
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
        List<PaintAnalysisResult> summaryRows = paintRepository.findDashboardSummaryRows(
                range.from(),
                range.to()
        );
        List<PaintAnalysisResult> chartRows = sortPaintByEventTimeAsc(paintRepository.findDashboardRows(
                range.from(),
                range.to(),
                PageRequest.of(0, normalizeLimit(limit))
        ));
        if (summaryRows.isEmpty()) {
            PaintDashboardResponse emptyResponse = PaintDashboardResponse.empty(
                    range.selectedDate(),
                    range.from(),
                    responseTo(range)
            );
            log.info(
                    "도장 대시보드 조회 완료: date={}, from={}, to={}, 건수=0",
                    range.selectedDate(),
                    range.from(),
                    range.to()
            );
            return emptyResponse;
        }

        long analysisCount = summaryRows.size();
        long defectCount = summaryRows.stream()
                .filter(this::isPaintDefect)
                .count();
        long alertCount = summaryRows.stream()
                .filter(row -> !STATUS_NORMAL.equals(overallPaintStatus(row)))
                .count();
        double averageSurfaceQualityScore = average(summaryRows.stream()
                .map(this::surfaceQualityValue)
                .filter(Objects::nonNull)
                .toList());
        double averageThicknessValue = average(summaryRows.stream()
                .map(PaintAnalysisResult::getThicknessValue)
                .filter(Objects::nonNull)
                .toList());
        double averageThermalStdTemp = average(summaryRows.stream()
                .map(PaintAnalysisResult::getThermalStdTemp)
                .filter(Objects::nonNull)
                .toList());

        PaintDashboardResponse.Summary summary = ProcessDashboardResponseMapper.toPaintSummary(
                analysisCount,
                averageThicknessValue,
                averageSurfaceQualityScore,
                percentage(defectCount, analysisCount),
                alertCount,
                averageThermalStdTemp
        );

        PaintDashboardResponse.Charts charts = paintCharts(chartRows);

        PaintAnalysisResult alertRow = summaryRows.stream()
                .filter(row -> !STATUS_NORMAL.equals(overallPaintStatus(row)))
                .max(Comparator
                        .comparing((PaintAnalysisResult row) -> displayTime(row.getAnalysisResult()),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(row -> row.getAnalysisResult().getId(),
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);

        PaintDashboardResponse response = ProcessDashboardResponseMapper.toPaintDashboardResponse(
                range.selectedDate(),
                range.from(),
                responseTo(range),
                paintStartAt(chartRows),
                paintEndAt(chartRows),
                summary,
                paintThresholds(),
                charts,
                paintAlert(alertRow)
        );
        log.info(
                "도장 대시보드 조회 완료: date={}, from={}, to={}, 건수={}",
                range.selectedDate(),
                range.from(),
                range.to(),
                chartRows.size()
        );
        return response;
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
        List<AssemblyAnalysisResult> summaryRows = assemblyRepository.findDashboardSummaryRows(
                range.from(),
                range.to()
        );
        List<AssemblyAnalysisResult> vehicleRows = sortAssemblyByEventTimeAsc(assemblyRepository.findDashboardRows(
                range.from(),
                range.to(),
                PageRequest.of(0, normalizeLimit(limit))
        ));
        if (summaryRows.isEmpty()) {
            AssemblyDashboardResponse emptyResponse = AssemblyDashboardResponse.empty(
                    range.selectedDate(),
                    range.from(),
                    responseTo(range)
            );
            log.info(
                    "조립 대시보드 조회 완료: date={}, from={}, to={}, 건수=0",
                    range.selectedDate(),
                    range.from(),
                    range.to()
            );
            return emptyResponse;
        }

        long vehicleCount = summaryRows.stream()
                .map(row -> row.getAnalysisResult().getCarMasterId())
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long sequenceErrorCount = sum(summaryRows.stream()
                .map(AssemblyAnalysisResult::getSequenceErrorCount)
                .toList());
        long missingPartCount = sum(summaryRows.stream()
                .map(AssemblyAnalysisResult::getMissingPartCount)
                .toList());
        long fasteningErrorCount = sum(summaryRows.stream()
                .map(AssemblyAnalysisResult::getFasteningErrorCount)
                .toList());
        double averageRiskScore = average(summaryRows.stream()
                .map(row -> row.getAnalysisResult().getRiskScore())
                .filter(Objects::nonNull)
                .toList());

        AssemblyDashboardResponse.Summary summary = ProcessDashboardResponseMapper.toAssemblySummary(
                vehicleCount,
                sequenceErrorCount,
                missingPartCount,
                fasteningErrorCount,
                averageRiskScore
        );

        List<AssemblyDashboardResponse.VehicleRow> vehicles = vehicleRows.stream()
                .map(row -> {
                    ManufacturingAnalysisResult result = row.getAnalysisResult();
                    return ProcessDashboardResponseMapper.toAssemblyVehicleRow(
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

        AssemblyAnalysisResult alertRow = vehicleRows.stream()
                .max(Comparator
                        .comparing((AssemblyAnalysisResult row) -> riskScore(row.getAnalysisResult()))
                        .thenComparing(row -> displayTime(row.getAnalysisResult()),
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);

        AssemblyDashboardResponse response = ProcessDashboardResponseMapper.toAssemblyDashboardResponse(
                range.selectedDate(),
                range.from(),
                responseTo(range),
                assemblyStartAt(vehicleRows),
                assemblyEndAt(vehicleRows),
                summary,
                vehicles,
                assemblyAlert(alertRow)
        );
        log.info(
                "조립 대시보드 조회 완료: date={}, from={}, to={}, 건수={}",
                range.selectedDate(),
                range.from(),
                range.to(),
                vehicleRows.size()
        );
        return response;
    }

    @Cacheable(cacheNames = "process-paint-dates-v1", key = "'all'")
    public ProcessAvailableDatesResponse getPaintDates() {
        ProcessAvailableDatesResponse response = ProcessAvailableDatesResponse.of(availablePaintDates());
        log.info("도장 조회 가능 날짜 조회 완료");
        return response;
    }

    @Cacheable(cacheNames = "process-assembly-dates-v1", key = "'all'")
    public ProcessAvailableDatesResponse getAssemblyDates() {
        ProcessAvailableDatesResponse response = ProcessAvailableDatesResponse.of(availableAssemblyDates());
        log.info("조립 조회 가능 날짜 조회 완료");
        return response;
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
        return ProcessDashboardResponseMapper.toEquipmentOperationRateItem(
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
            return ProcessDashboardResponseMapper.toPaintAlert(
                    "최근 도장 상태 정상",
                    List.of("선택한 시간 범위 내 신규 위험 알람 없음")
            );
        }
        ManufacturingAnalysisResult result = row.getAnalysisResult();
        String status = overallPaintStatus(row);
        List<String> messages = new ArrayList<>();
        messages.add("비전 판정: " + nullToDash(row.getVisionLabel()));
        messages.add("이상 위치: " + nullToDash(row.getImagePosition()));
        messages.add("도막 두께: " + formatNullable(row.getThicknessValue()) + " μm");
        messages.add("표면 품질 점수: " + formatNullable(surfaceQualityValue(row)) + "점");
        messages.add("열 편차: " + formatNullable(row.getThermalStdTemp()) + "℃");
        messages.add("불량 점수: " + formatNullable(row.getDefectScore()));
        messages.add("상태: " + status);
        PaintDashboardResponse.Alert.Detail detail = new PaintDashboardResponse.Alert.Detail(
                displayTime(result),
                row.getVisionLabel(),
                row.getImagePosition(),
                row.getThicknessValue(),
                surfaceQualityValue(row),
                row.getDefectScore(),
                row.getThermalStdTemp(),
                result.getRiskScore(),
                status
        );
        return ProcessDashboardResponseMapper.toPaintAlert("도장 품질 이상 감지", messages, detail);
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
        return ProcessDashboardResponseMapper.toAssemblyAlert("조립 순서 오류 감지", messages);
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
        return availableDates(paintRepository.findDashboardEventTimeValues());
    }

    private List<LocalDate> availableAssemblyDates() {
        return availableDates(assemblyRepository.findDashboardEventTimeValues());
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
        return result.getEventTime();
    }

    private PaintDashboardResponse.Thresholds paintThresholds() {
        return new PaintDashboardResponse.Thresholds(
                new PaintDashboardResponse.HigherIsBetterThreshold(
                        "표면 품질 점수",
                        "점",
                        "HIGHER_IS_BETTER",
                        SURFACE_QUALITY_WARNING_BELOW,
                        SURFACE_QUALITY_DANGER_BELOW
                ),
                new PaintDashboardResponse.InRangeThreshold(
                        "도막 두께",
                        "μm",
                        "IN_RANGE_IS_BETTER",
                        THICKNESS_TARGET,
                        THICKNESS_NORMAL_MIN,
                        THICKNESS_NORMAL_MAX,
                        THICKNESS_WARNING_MIN,
                        THICKNESS_WARNING_MAX
                ),
                new PaintDashboardResponse.LowerIsBetterThreshold(
                        "불량 점수",
                        "",
                        "LOWER_IS_BETTER",
                        DEFECT_SCORE_WARNING_ABOVE,
                        DEFECT_SCORE_DANGER_ABOVE
                ),
                new PaintDashboardResponse.LowerIsBetterThreshold(
                        "온도 편차",
                        "℃",
                        "LOWER_IS_BETTER",
                        THERMAL_STD_TEMP_WARNING_ABOVE,
                        THERMAL_STD_TEMP_DANGER_ABOVE
                )
        );
    }

    private PaintDashboardResponse.Charts paintCharts(List<PaintAnalysisResult> rows) {
        return new PaintDashboardResponse.Charts(
                metricChart(
                        "표면 품질 점수 추이",
                        "surfaceQualityScore",
                        "점",
                        rows.stream()
                                .map(row -> metricPoint(row, surfaceQualityValue(row), surfaceQualityStatus(row)))
                                .toList(),
                        List.of()
                ),
                metricChart(
                        "도막 두께 추이",
                        "thicknessValue",
                        "μm",
                        rows.stream()
                                .map(row -> metricPoint(row, row.getThicknessValue(), thicknessStatus(row)))
                                .toList(),
                        List.of()
                ),
                metricChart(
                        "불량 점수 추이",
                        "defectScore",
                        "",
                        rows.stream()
                                .map(row -> metricPoint(row, row.getDefectScore(), defectScoreStatus(row)))
                                .toList(),
                        rows.stream()
                                .filter(this::isDefectMarker)
                                .map(row -> new PaintDashboardResponse.MetricMarker(
                                        displayTime(row.getAnalysisResult()),
                                        row.getDefectScore(),
                                        row.getVisionLabel(),
                                        row.getImagePosition(),
                                        defectScoreStatus(row),
                                        row.getAnalysisResult().getId()
                                ))
                                .toList()
                ),
                metricChart(
                        "온도 편차 추이",
                        "thermalStdTemp",
                        "℃",
                        rows.stream()
                                .map(row -> metricPoint(row, row.getThermalStdTemp(), thermalStdTempStatus(row)))
                                .toList(),
                        List.of()
                )
        );
    }

    private PaintDashboardResponse.MetricChart metricChart(
            String title,
            String metricKey,
            String unit,
            List<PaintDashboardResponse.MetricPoint> points,
            List<PaintDashboardResponse.MetricMarker> markers
    ) {
        return new PaintDashboardResponse.MetricChart(title, metricKey, unit, points, markers);
    }

    private PaintDashboardResponse.MetricPoint metricPoint(
            PaintAnalysisResult row,
            Double value,
            String status
    ) {
        ManufacturingAnalysisResult result = row.getAnalysisResult();
        return new PaintDashboardResponse.MetricPoint(
                displayTime(result),
                value,
                status,
                row.getVisionLabel(),
                row.getImagePosition(),
                result.getRiskScore(),
                result.getId()
        );
    }

    private Double surfaceQualityValue(PaintAnalysisResult row) {
        Double value = row.getSurfaceQualityScore();
        if (value != null
                && value == 0.0
                && normalVision(row.getVisionLabel())
                && (row.getAnalysisResult().getSeverity() == null
                || row.getAnalysisResult().getSeverity() == Severity.NORMAL)
                && riskScore(row.getAnalysisResult()) == 0.0) {
            return null;
        }
        return value;
    }

    private String surfaceQualityStatus(PaintAnalysisResult row) {
        Double value = surfaceQualityValue(row);
        if (value == null) {
            return STATUS_NORMAL;
        }
        if (value < SURFACE_QUALITY_DANGER_BELOW) {
            return STATUS_DANGER;
        }
        if (value < SURFACE_QUALITY_WARNING_BELOW) {
            return STATUS_WARNING;
        }
        return STATUS_NORMAL;
    }

    private String thicknessStatus(PaintAnalysisResult row) {
        Double value = row.getThicknessValue();
        if (value == null) {
            return STATUS_NORMAL;
        }
        if (value < THICKNESS_WARNING_MIN || value > THICKNESS_WARNING_MAX) {
            return STATUS_DANGER;
        }
        if (value < THICKNESS_NORMAL_MIN || value > THICKNESS_NORMAL_MAX) {
            return STATUS_WARNING;
        }
        return STATUS_NORMAL;
    }

    private String defectScoreStatus(PaintAnalysisResult row) {
        Double value = row.getDefectScore();
        String status = STATUS_NORMAL;
        if (value != null && value >= DEFECT_SCORE_DANGER_ABOVE) {
            status = STATUS_DANGER;
        } else if (value != null && value >= DEFECT_SCORE_WARNING_ABOVE) {
            status = STATUS_WARNING;
        }
        if (!normalVision(row.getVisionLabel()) && STATUS_NORMAL.equals(status)) {
            return STATUS_WARNING;
        }
        return status;
    }

    private String thermalStdTempStatus(PaintAnalysisResult row) {
        Double value = row.getThermalStdTemp();
        if (value == null) {
            return STATUS_NORMAL;
        }
        if (value >= THERMAL_STD_TEMP_DANGER_ABOVE) {
            return STATUS_DANGER;
        }
        if (value >= THERMAL_STD_TEMP_WARNING_ABOVE) {
            return STATUS_WARNING;
        }
        return STATUS_NORMAL;
    }

    private String overallPaintStatus(PaintAnalysisResult row) {
        String status = maxStatus(
                surfaceQualityStatus(row),
                thicknessStatus(row),
                defectScoreStatus(row),
                thermalStdTempStatus(row)
        );
        Severity severity = row.getAnalysisResult().getSeverity();
        if (severity == Severity.CRITICAL) {
            return STATUS_DANGER;
        }
        if (severity == Severity.WARNING && STATUS_NORMAL.equals(status)) {
            return STATUS_WARNING;
        }
        return status;
    }

    private String maxStatus(String... statuses) {
        String result = STATUS_NORMAL;
        for (String status : statuses) {
            if (STATUS_DANGER.equals(status)) {
                return STATUS_DANGER;
            }
            if (STATUS_WARNING.equals(status)) {
                result = STATUS_WARNING;
            }
        }
        return result;
    }

    private boolean isDefectMarker(PaintAnalysisResult row) {
        String status = defectScoreStatus(row);
        return isPaintDefect(row);
    }

    private boolean isPaintDefect(PaintAnalysisResult row) {
        String status = defectScoreStatus(row);
        return !normalVision(row.getVisionLabel())
                || STATUS_WARNING.equals(status)
                || STATUS_DANGER.equals(status);
    }

    private boolean normalVision(String visionLabel) {
        return visionLabel == null || STATUS_NORMAL.equalsIgnoreCase(visionLabel);
    }

    private List<PaintAnalysisResult> sortPaintByEventTimeAsc(List<PaintAnalysisResult> rows) {
        return rows.stream()
                .sorted(Comparator
                        .comparing((PaintAnalysisResult row) -> displayTime(row.getAnalysisResult()),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(row -> row.getAnalysisResult().getId(),
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private List<AssemblyAnalysisResult> sortAssemblyByEventTimeAsc(List<AssemblyAnalysisResult> rows) {
        return rows.stream()
                .sorted(Comparator
                        .comparing((AssemblyAnalysisResult row) -> displayTime(row.getAnalysisResult()),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(row -> row.getAnalysisResult().getId(),
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private LocalDateTime paintStartAt(List<PaintAnalysisResult> rows) {
        return rows.isEmpty() ? null : displayTime(rows.get(0).getAnalysisResult());
    }

    private LocalDateTime paintEndAt(List<PaintAnalysisResult> rows) {
        return rows.isEmpty() ? null : displayTime(rows.get(rows.size() - 1).getAnalysisResult());
    }

    private LocalDateTime assemblyStartAt(List<AssemblyAnalysisResult> rows) {
        return rows.isEmpty() ? null : displayTime(rows.get(0).getAnalysisResult());
    }

    private LocalDateTime assemblyEndAt(List<AssemblyAnalysisResult> rows) {
        return rows.isEmpty() ? null : displayTime(rows.get(rows.size() - 1).getAnalysisResult());
    }

    private LocalDateTime responseTo(QueryRange range) {
        if (range.selectedDate() != null && range.to() != null) {
            LocalDateTime nextDayStart = range.selectedDate().plusDays(1).atStartOfDay();
            if (nextDayStart.equals(range.to())) {
                return LocalDateTime.of(range.selectedDate(), LocalTime.MAX);
            }
        }
        return range.to();
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

    private String formatNullable(Double value) {
        return value == null ? "-" : format(value);
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

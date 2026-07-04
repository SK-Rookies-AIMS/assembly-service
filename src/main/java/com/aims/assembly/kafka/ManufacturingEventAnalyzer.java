package com.aims.assembly.kafka;

import com.aims.assembly.domain.enums.EquipmentOperationStatus;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.kafka.model.EquipmentStatusEvent;
import com.aims.assembly.kafka.model.ManufacturingAlertEvent;
import com.aims.assembly.kafka.model.ManufacturingAnalysisEvent;
import com.aims.assembly.kafka.model.ManufacturingRawEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 제조 원천 이벤트에서 공정 위험도와 AI 위험도를 계산하고 후속 이벤트로 변환한다.
 * 실제 모델 연동 전에도 PRD의 입력 필드와 판정 기준을 검증할 수 있도록 규칙 기반으로 계산한다.
 *
 * <p>riskScore 계산 기준 (2026-06 개정):
 * <pre>riskScore = processRisk</pre>
 * bottleneckRisk / defectTransferRisk 는 analyzeBottleneck / analyzeDefectTransfer AI 경로에서만
 * 사용되며, detail API 응답에는 포함되지 않는다.
 *
 * <p>설비 이상 판단 기준:
 * equipmentStatus(envelope) / equipmentStatus.operationStatus 가
 * WARNING | STOPPED | FAULT 중 하나인 경우.
 * 수치 계산식은 더 이상 equipmentFault 판단에 사용되지 않는다.
 */
@Component
public class ManufacturingEventAnalyzer {

    public ManufacturingAnalysisEvent analyze(ManufacturingRawEvent event) {
        return analyze(event, "PROCESS_RISK_ANALYSIS");
    }

    public ManufacturingAnalysisEvent analyzeBottleneck(ManufacturingRawEvent event) {
        return analyze(event, "BOTTLENECK_ANALYSIS");
    }

    public ManufacturingAnalysisEvent analyzeDefectTransfer(ManufacturingRawEvent event) {
        return analyze(event, "DEFECT_TRANSFER_PREDICTION");
    }

    /**
     * /analysis-results/{eventId}/detail API 에서 processRisk 계산 과정을 노출할 때 사용.
     * bottleneckRisk / defectTransferRisk / equipmentRisk 계산은 포함하지 않는다.
     */
    public AnalysisDetail analyzeDetail(ManufacturingRawEvent event) {
        double cycleTimeSec = number(event.eventJson(), "processMetrics", "cycleTimeSec");
        double stationDelaySec = number(event.eventJson(), "processMetrics", "stationDelaySec");

        ProcessComponent processComponent = processComponent(event, cycleTimeSec, stationDelaySec);
        double processRiskScore = round(clamp(processComponent.score()));

        // equipmentFault: 상태값 기반 판단만 수행 (수치 계산식 제거)
        boolean equipmentFault = isEquipmentAbnormalStatus(event);
        boolean sequenceError = event.processCode() == ProcessCode.ASSEMBLY
                && number(event.eventJson(), "processData", "assembly", "sequenceErrorCount") > 0;

        boolean isAbnormal = processRiskScore >= 60 || equipmentFault || sequenceError;
        String abnormalType = abnormalType(processRiskScore, equipmentFault, sequenceError);
        String equipmentStatusReason = buildEquipmentStatusReason(event, equipmentFault);
        String message = mainReason(event.processCode(), processRiskScore, equipmentFault, sequenceError, "PROCESS_RISK_ANALYSIS");

        return new AnalysisDetail(
                "0-100",
                processRiskScore,
                riskLevel(processRiskScore),
                isAbnormal,
                abnormalType,
                "riskScore = processRisk",
                new ProcessRiskDetail(
                        processRiskScore,
                        processComponent.usedFields(),
                        processComponent.formula()
                ),
                equipmentFault,
                equipmentStatusReason,
                decisionReason(processRiskScore, equipmentFault, sequenceError),
                message
        );
    }

    private ManufacturingAnalysisEvent analyze(
            ManufacturingRawEvent event,
            String analysisType
    ) {
        // PRD processMetrics 입력값 추출
        double cycleTimeSec = number(event.eventJson(), "processMetrics", "cycleTimeSec");
        double waitingTimeSec = number(event.eventJson(), "processMetrics", "waitingTimeSec");
        double stationDelaySec = number(event.eventJson(), "processMetrics", "stationDelaySec");
        double equipmentIdleTimeSec =
                number(event.eventJson(), "processMetrics", "equipmentIdleTimeSec");
        double processingTimeSec =
                number(event.eventJson(), "processMetrics", "processingTimeSec");
        double queueLength = number(event.eventJson(), "processMetrics", "queueLength");
        double wipCount = number(event.eventJson(), "processMetrics", "wipCount");

        // PRD sensor 입력값 추출
        double rmsAmpere = number(event.eventJson(), "sensor", "current", "rmsAmpere");
        double vibrationScore = number(event.eventJson(), "sensor", "vibration", "vibrationScore");
        double robotVibrationScore =
                number(event.eventJson(), "sensor", "robotArmVibration", "vibrationScore");
        double maxTemperature = number(event.eventJson(), "sensor", "thermal", "maxTemperature");

        // 대기열, WIP, 지연 시간, 유휴 시간 기반 병목 위험도 계산 (다른 팀원 담당 영역 - 유지)
        double bottleneckRisk = clamp(
                stationDelaySec * 5
                        + waitingTimeSec * 2
                        + queueLength * 3
                        + wipCount
                        + equipmentIdleTimeSec
                        + Math.max(0, cycleTimeSec - targetCycleTime(event))
        );

        // 전류, 진동, 유휴 시간, 원천 설비 상태 기반 설비 위험도 계산 (참고값)
        double equipmentRisk = clamp(
                rmsAmpere * 8
                        + vibrationScore * 45
                        + robotVibrationScore * 35
                        + equipmentIdleTimeSec * 1.5
                        + operationStatusWeight(event)
        );

        // 전류, 일반 진동, 로봇 진동, 열화상 기반 불량 전이 위험도 계산 (다른 팀원 담당 영역 - 유지)
        double defectTransferRisk = clamp(
                rmsAmpere * 6
                        + vibrationScore * 35
                        + robotVibrationScore * 35
                        + Math.max(0, maxTemperature - 40) * 2
        );

        // PRESS/BODY/PAINT/ASSEMBLY별 전용 위험도 계산
        double processRisk = calculateProcessRisk(event, cycleTimeSec, stationDelaySec);
        ManufacturingAnalysisEvent.ProcessRisk processRiskScores =
                processRiskScores(event.processCode(), processRisk);

        // riskScore = processRisk (단독). bottleneck/defect/equipment 는 최종 score에 미포함
        double overallRisk = switch (analysisType) {
            case "BOTTLENECK_ANALYSIS" -> bottleneckRisk;
            case "DEFECT_TRANSFER_PREDICTION" -> defectTransferRisk;
            default -> clamp(processRisk);
        };
        overallRisk = round(overallRisk);
        String riskLevel = riskLevel(overallRisk);

        boolean bottleneck = bottleneckRisk >= 60;
        boolean qualityDefect = isQualityDefect(event, defectTransferRisk);
        // equipmentFault: 상태값 기반 판단 (수치 계산식 제거)
        boolean equipmentFault = isEquipmentAbnormalStatus(event);
        boolean sequenceError = event.processCode() == ProcessCode.ASSEMBLY
                && number(event.eventJson(), "processData", "assembly", "sequenceErrorCount") > 0;

        List<String> detailReasons = List.of(
                "cycleTimeSec=" + cycleTimeSec,
                "stationDelaySec=" + stationDelaySec,
                "queueLength=" + queueLength,
                "wipCount=" + wipCount
        );

        return new ManufacturingAnalysisEvent(
                "ANL-" + UUID.randomUUID(),
                event.eventId(),
                event.eventTime(),
                LocalDateTime.now(),
                text(event.eventJson(), "location", "factoryCode"),
                text(event.eventJson(), "location", "lineCode"),
                event.processCode(),
                event.equipmentCode(),
                text(event.eventJson(), "equipment", "equipmentName"),
                event.equipmentType(),
                text(event.eventJson(), "product", "productId"),
                text(event.eventJson(), "product", "carId"),
                event.carMasterId(),
                analysisType,
                new ManufacturingAnalysisEvent.RiskScores(
                        overallRisk,
                        round(bottleneckRisk),
                        round(defectTransferRisk),
                        round(equipmentRisk),
                        processRiskScores
                ),
                calculateOperationRate(cycleTimeSec, processingTimeSec, equipmentIdleTimeSec),
                riskLevel,
                new ManufacturingAnalysisEvent.AnalysisResult(
                        "BOTTLENECK_ANALYSIS".equals(analysisType) || "DEFECT_TRANSFER_PREDICTION".equals(analysisType)
                                ? overallRisk >= 60 || bottleneck || qualityDefect || equipmentFault || sequenceError
                                : overallRisk >= 60 || equipmentFault || sequenceError,
                        bottleneck,
                        qualityDefect,
                        equipmentFault,
                        sequenceError
                ),
                new ManufacturingAnalysisEvent.Reason(
                        mainReason(event.processCode(), overallRisk, equipmentFault, sequenceError, analysisType),
                        detailReasons
                ),
                new ManufacturingAnalysisEvent.Recommendation(
                        recommendationType(event.processCode()),
                        recommendationMessage(event.processCode())
                )
        );
    }

    public EquipmentStatusEvent toEquipmentEvent(ManufacturingAnalysisEvent analysis) {
        // 분석 결과를 설비 카드 및 실시간 상태 조회용 메시지로 변환
        return new EquipmentStatusEvent(
                "EQEVT-" + UUID.randomUUID(),
                analysis.eventId(),
                LocalDateTime.now(),
                analysis.factoryCode(),
                analysis.lineCode(),
                analysis.processCode(),
                analysis.equipmentCode(),
                analysis.equipmentName(),
                analysis.equipmentType(),
                "CRITICAL".equals(analysis.riskLevel()) ? "STOPPED" : "RUNNING",
                analysis.riskLevel(),
                overallRiskScore(analysis),
                analysis.operationRate()
        );
    }

    public EquipmentStatusEvent toEquipmentStatusEvent(ManufacturingRawEvent event) {
        String operationStatus = equipmentOperationStatus(event);
        String riskLevel = "FAULT".equals(operationStatus) || "STOPPED".equals(operationStatus) ? "CRITICAL"
                : ("WARNING".equals(operationStatus) ? "WARNING" : "LOW");
        return new EquipmentStatusEvent(
                "EQEVT-" + UUID.randomUUID(),
                event.eventId(),
                event.eventTime() == null ? LocalDateTime.now() : event.eventTime(),
                text(event.eventJson(), "location", "factoryCode"),
                text(event.eventJson(), "location", "lineCode"),
                event.processCode(),
                event.equipmentCode(),
                text(event.eventJson(), "equipment", "equipmentName"),
                event.equipmentType(),
                operationStatus,
                riskLevel,
                "CRITICAL".equals(riskLevel) ? 100.0 : ("WARNING".equals(riskLevel) ? 60.0 : 0.0),
                0.0,
                event.equipmentId(),
                "FAULT",
                buildEquipmentStatusReason(event, true)
        );
    }

    public ManufacturingAlertEvent toAlertEvent(ManufacturingAnalysisEvent analysis) {
        // WARNING 또는 CRITICAL 분석 결과(processRisk 기반)를 OPEN 상태의 알림으로 변환
        return new ManufacturingAlertEvent(
                "ALT-" + UUID.randomUUID(),
                analysis.eventId(),
                analysis.analysisId(),
                LocalDateTime.now(),
                analysis.factoryCode(),
                analysis.lineCode(),
                analysis.processCode(),
                analysis.equipmentCode(),
                analysis.equipmentName(),
                analysis.carMasterId(),
                null,       // equipmentId: analysis 이벤트에 없음 - null 허용
                "MANUFACTURING_ABNORMAL",
                alertTitle(analysis),
                analysis.equipmentCode() + " 설비의 제조 공정 위험이 감지되었습니다. (processRisk 기반)",
                analysis.riskLevel(),
                overallRiskScore(analysis),
                "OPEN",
                true,
                analysis.reason().detailReasons(),
                analysis.recommendation().message()
        );
    }

    /**
     * Case B: 설비 이상 상태(WARNING/STOPPED/FAULT) 감지 시 즉시 발행할 alert 이벤트 생성.
     * riskScore 분석 결과와 무관하게 raw 이벤트 수신 직후 발행된다.
     */
    public ManufacturingAlertEvent toEquipmentStatusAlert(ManufacturingRawEvent event) {
        String statusReason = buildEquipmentStatusReason(event, true);
        return new ManufacturingAlertEvent(
                "ALT-" + UUID.randomUUID(),
                event.eventId(),
                null,       // raw 단계이므로 analysisId 없음
                LocalDateTime.now(),
                text(event.eventJson(), "location", "factoryCode"),
                text(event.eventJson(), "location", "lineCode"),
                event.processCode(),
                event.equipmentCode(),
                text(event.eventJson(), "equipment", "equipmentName"),
                event.carMasterId(),
                event.equipmentId(),
                "EQUIPMENT_ABNORMAL",
                "설비 이상 감지",
                event.equipmentCode() + " 설비 이상 상태가 감지되었습니다. " + statusReason,
                "CRITICAL",
                100.0,
                "OPEN",
                true,
                List.of(statusReason),
                "설비 상태를 즉시 확인하고 안전 절차를 따르세요."
        );
    }

    /**
     * 설비 이상 여부 공개 판단 메서드.
     * Consumer에서 Case B alert 발행 여부 결정에 사용된다.
     */
    public boolean isEquipmentAbnormal(ManufacturingRawEvent event) {
        return isEquipmentAbnormalStatus(event);
    }

    public boolean requiresAlert(ManufacturingAnalysisEvent analysis) {
        // PRD 발행 조건: WARNING/CRITICAL 또는 개별 이상 플래그 발생
        ManufacturingAnalysisEvent.AnalysisResult result = analysis.analysisResult();
        return !"LOW".equals(analysis.riskLevel())
                || overallRiskScore(analysis) >= 80
                || result.isEquipmentFault()
                || result.isQualityDefect()
                || result.isBottleneck()
                || result.isSequenceError();
    }

    private double overallRiskScore(ManufacturingAnalysisEvent analysis) {
        if (analysis.riskScores() == null || analysis.riskScores().overallRiskScore() == null) {
            return 0.0;
        }
        return analysis.riskScores().overallRiskScore();
    }

    private double calculateProcessRisk(
            ManufacturingRawEvent event,
            double cycleTimeSec,
            double stationDelaySec
    ) {
        return processComponent(event, cycleTimeSec, stationDelaySec).score();
    }

    private ProcessComponent processComponent(
            ManufacturingRawEvent event,
            double cycleTimeSec,
            double stationDelaySec
    ) {
        return switch (event.processCode()) {
            case PRESS -> {
                boolean countIncrease = bool(
                        event.eventJson(),
                        "processData",
                        "press",
                        "countIncreaseYn"
                );
                double rmsAmpere = number(event.eventJson(), "sensor", "current", "rmsAmpere");
                double targetCycleTimeSec = targetCycleTime(event);
                double cycleOverTargetSec = Math.max(0, cycleTimeSec - targetCycleTimeSec);
                
                double score = clamp(
                        Math.min(40.0, stationDelaySec * 4.0)
                        + Math.min(35.0, cycleOverTargetSec * 3.0)
                        + Math.min(20.0, Math.max(0.0, rmsAmpere - 1.5) * 8.0)
                        + (countIncrease ? 0.0 : 20.0)
                );
                yield new ProcessComponent(
                        score,
                        Map.of(
                                "processCode", event.processCode().name(),
                                "stationDelaySec", stationDelaySec,
                                "cycleTimeSec", cycleTimeSec,
                                "targetCycleTimeSec", targetCycleTimeSec,
                                "cycleOverTargetSec", cycleOverTargetSec,
                                "rmsAmpere", rmsAmpere,
                                "countIncreaseYn", countIncrease
                        ),
                        "min(40, stationDelaySec * 4) + min(35, max(0, cycleTimeSec - targetCycleTimeSec) * 3) + min(20, max(0, rmsAmpere - 1.5) * 8) + (countIncreaseYn ? 0 : 20)"
                );
            }
            case BODY -> {
                double robotScore = number(
                        event.eventJson(),
                        "sensor",
                        "robotArmVibration",
                        "vibrationScore"
                );
                double vibrationPeak = number(
                        event.eventJson(),
                        "sensor",
                        "robotArmVibration",
                        "vibrationPeak"
                );
                String robotMotionStatus = text(event.eventJson(), "processData", "body", "robotMotionStatus");
                String robotOperationMode = text(event.eventJson(), "processData", "body", "robotOperationMode");
                String frequencyPeakBand = text(event.eventJson(), "processData", "body", "frequencyPeakBand");
                Object frequencyBandsObj = value(event.eventJson(), "processData", "body", "frequencyBands");
                var frequencyBands = com.aims.assembly.service.body.BodyFrequencyBandSupport.toDoubleMap(frequencyBandsObj);
                Double peakBandValue = com.aims.assembly.service.body.BodyFrequencyBandSupport.resolvePeakValue(
                        frequencyPeakBand,
                        frequencyBands,
                        vibrationPeak > 0 ? vibrationPeak : null
                );
                double peakValue = peakBandValue != null ? peakBandValue : 0.0;

                boolean isMotionAbnormal = robotMotionStatus != null
                        && ("ABNORMAL".equalsIgnoreCase(robotMotionStatus)
                        || "COLLISION_RISK".equalsIgnoreCase(robotMotionStatus));
                boolean isOperationAbnormal = robotOperationMode != null
                        && ("AUTO_MANUAL_STOPPED".equalsIgnoreCase(robotOperationMode)
                        || "STOPPED".equalsIgnoreCase(robotOperationMode)
                        || "MANUAL".equalsIgnoreCase(robotOperationMode));

                double score = clamp(
                        Math.min(50.0, robotScore * 40.0)
                        + Math.min(30.0, peakValue * 1000.0)
                        + (isMotionAbnormal ? 30.0 : 0.0)
                        + (isOperationAbnormal ? 20.0 : 0.0)
                );
                yield new ProcessComponent(
                        score,
                        Map.of(
                                "processCode", event.processCode().name(),
                                "robotVibrationScore", robotScore,
                                "frequencyPeakValue", peakValue,
                                "robotMotionStatus", String.valueOf(robotMotionStatus),
                                "robotOperationMode", String.valueOf(robotOperationMode),
                                "frequencyPeakBand", String.valueOf(frequencyPeakBand)
                        ),
                        "min(50, robotVibrationScore * 40) + min(30, frequencyPeakValue * 1000) + motion/operation penalties"
                );
            }
            case PAINT -> {
                double defectScore =
                        number(event.eventJson(), "processData", "paint", "defectScore");
                double thermalDeviation =
                        number(event.eventJson(), "processData", "paint", "thermalStdTemp");
                double surfaceQuality =
                        number(event.eventJson(), "processData", "paint", "surfaceQualityScore");
                
                double score = clamp(
                        Math.min(45.0, defectScore * 35.0)
                        + Math.min(25.0, thermalDeviation * 3.0)
                        + Math.min(30.0, Math.max(0.0, 90.0 - surfaceQuality))
                );
                yield new ProcessComponent(
                        score,
                        Map.of(
                                "processCode", event.processCode().name(),
                                "defectScore", defectScore,
                                "thermalStdTemp", thermalDeviation,
                                "surfaceQualityScore", surfaceQuality,
                                "visionLabel", String.valueOf(text(event.eventJson(), "processData", "paint", "visionLabel"))
                        ),
                        "min(45, defectScore * 35) + min(25, thermalStdTemp * 3) + min(30, max(0, 90 - surfaceQualityScore))"
                );
            }
            case ASSEMBLY -> {
                double sequenceErrors = number(
                        event.eventJson(),
                        "processData",
                        "assembly",
                        "sequenceErrorCount"
                );
                double missingParts = number(
                        event.eventJson(),
                        "processData",
                        "assembly",
                        "missingPartCount"
                );
                double fasteningErrors = number(
                        event.eventJson(),
                        "processData",
                        "assembly",
                        "fasteningErrorCount"
                );
                double score = clamp(
                        Math.min(45.0, sequenceErrors * 4.0)
                        + Math.min(35.0, missingParts * 3.0)
                        + Math.min(20.0, fasteningErrors * 2.0)
                );
                yield new ProcessComponent(
                        score,
                        Map.of(
                                "processCode", event.processCode().name(),
                                "sequenceErrorCount", sequenceErrors,
                                "missingPartCount", missingParts,
                                "fasteningErrorCount", fasteningErrors
                        ),
                        "min(45, sequenceErrorCount * 4) + min(35, missingPartCount * 3) + min(20, fasteningErrorCount * 2)"
                );
            }
        };
    }

    private ManufacturingAnalysisEvent.ProcessRisk processRiskScores(
            ProcessCode processCode,
            double score
    ) {
        double rounded = round(score);
        return switch (processCode) {
            case PRESS -> new ManufacturingAnalysisEvent.ProcessRisk(
                    rounded, null, null, null
            );
            case BODY -> new ManufacturingAnalysisEvent.ProcessRisk(
                    null, rounded, null, null
            );
            case PAINT -> new ManufacturingAnalysisEvent.ProcessRisk(
                    null, null, rounded, null
            );
            case ASSEMBLY -> new ManufacturingAnalysisEvent.ProcessRisk(
                    null, null, null, rounded
            );
        };
    }

    private boolean isQualityDefect(ManufacturingRawEvent event, double defectTransferRisk) {
        return defectTransferRisk >= 60
                || "DEFECT".equalsIgnoreCase(
                        text(event.eventJson(), "processData", "paint", "visionLabel")
                )
                || number(event.eventJson(), "processData", "assembly", "missingPartCount") > 0
                || number(event.eventJson(), "processData", "assembly", "fasteningErrorCount") > 0;
    }

    private boolean isEquipmentAbnormalStatus(ManufacturingRawEvent event) {
        // 1) envelope 컬럼의 equipmentStatus 확인
        if (isAbnormalStatusValue(event.equipmentStatus())) return true;
        // 2) event_json 내부 equipmentStatus.operationStatus 확인
        if (isAbnormalStatusValue(text(event.eventJson(), "equipmentStatus", "operationStatus"))) return true;
        // 3) eventType 키워드 기반 확인
        return false;
    }

    private boolean isAbnormalStatusValue(String status) {
        return EquipmentOperationStatus.from(status)
                .filter(value -> value != EquipmentOperationStatus.RUNNING)
                .isPresent();
    }

    private String equipmentOperationStatus(ManufacturingRawEvent event) {
        String status = event.equipmentStatus();
        if (status == null || status.isBlank()) {
            status = text(event.eventJson(), "equipmentStatus", "operationStatus");
        }
        String rawStatus = status;
        return EquipmentOperationStatus.from(rawStatus)
                .map(EquipmentOperationStatus::name)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid equipment operationStatus: " + rawStatus));
    }

    private String buildEquipmentStatusReason(ManufacturingRawEvent event, boolean isAbnormal) {
        if (!isAbnormal) return "Equipment status is normal.";
        StringBuilder sb = new StringBuilder("Equipment status indicates abnormal condition.");
        String topStatus = event.equipmentStatus();
        if (isAbnormalStatusValue(topStatus)) {
            sb.append(" equipmentStatus(envelope)=").append(topStatus);
        }
        String opStatus = text(event.eventJson(), "equipmentStatus", "operationStatus");
        if (isAbnormalStatusValue(opStatus)) {
            sb.append(" operationStatus=").append(opStatus);
        }
        String eventType = event.eventType();
        if (eventType != null) {
            String upper = eventType.toUpperCase();
            if (upper.contains("FAULT") || upper.contains("STOPPED") || upper.contains("WARNING")) {
                sb.append(" eventType=").append(eventType);
            }
        }
        return sb.toString();
    }

    private double operationStatusWeight(ManufacturingRawEvent event) {
        String status = text(event.eventJson(), "equipmentStatus", "operationStatus");
        if (status == null) {
            status = event.equipmentStatus();
        }
        return switch (status == null ? "" : status.toUpperCase()) {
            case "FAULT" -> 50;
            case "WARNING" -> 25;
            default -> 0;
        };
    }

    private double targetCycleTime(ManufacturingRawEvent event) {
        double target = number(event.eventJson(), "processData", "press", "targetCycleTimeSec");
        return target > 0 ? target : 40;
    }

    private String mainReason(
            ProcessCode processCode,
            double overallRisk,
            boolean equipmentFault,
            boolean sequenceError,
            String analysisType
    ) {
        if (equipmentFault) {
            return "설비 상태값에서 이상(WARNING/STOPPED/FAULT)이 감지되었습니다.";
        }
        if (sequenceError) {
            return "의장 공정의 작업 순서 오류가 감지되었습니다.";
        }
        if (!"BOTTLENECK_ANALYSIS".equals(analysisType) && !"DEFECT_TRANSFER_PREDICTION".equals(analysisType)) {
            if (overallRisk >= 80) {
                return switch (processCode) {
                    case PRESS -> "프레스 공정 위험이 감지되었습니다.";
                    case BODY -> "차체 공정 위험이 감지되었습니다.";
                    case PAINT -> "도장 공정 위험이 감지되었습니다.";
                    case ASSEMBLY -> "의장 공정 위험이 감지되었습니다.";
                };
            }
            if (overallRisk >= 60) {
                return switch (processCode) {
                    case PRESS -> "프레스 공정 위험 경보가 발생했습니다.";
                    case BODY -> "차체 공정 위험 경보가 발생했습니다.";
                    case PAINT -> "도장 공정 위험 경보가 발생했습니다.";
                    case ASSEMBLY -> "의장 공정 위험 경보가 발생했습니다.";
                };
            }
        }
        return "주요 공정 지표가 정상 범위입니다.";
    }

    private String recommendationType(ProcessCode processCode) {
        return switch (processCode) {
            case PRESS -> "CHECK_PRESS_EQUIPMENT_AND_QUEUE";
            case BODY -> "CHECK_ROBOT_VIBRATION";
            case PAINT -> "CHECK_PAINT_QUALITY";
            case ASSEMBLY -> "CHECK_ASSEMBLY_SEQUENCE";
        };
    }

    private String abnormalType(double processRiskScore, boolean equipmentFault, boolean sequenceError) {
        if (equipmentFault) return "EQUIPMENT";
        if (sequenceError || processRiskScore >= 60) return "PROCESS";
        return null;
    }

    private String decisionReason(double processRiskScore, boolean equipmentFault, boolean sequenceError) {
        if (processRiskScore >= 60) return "processRisk >= 60 (riskScore = processRisk)";
        if (equipmentFault) return "Equipment status is WARNING/STOPPED/FAULT";
        if (sequenceError) return "ASSEMBLY sequenceErrorCount > 0";
        return "processRisk < 60 and no abnormal detail flag";
    }

    private String recommendationMessage(ProcessCode processCode) {
        return switch (processCode) {
            case PRESS -> "프레스 설비 상태, 전류 RMS 값과 대기열을 확인하세요.";
            case BODY -> "로봇 암 진동과 충돌 위험을 확인하세요.";
            case PAINT -> "열화상, 도막 두께와 비전 불량 결과를 확인하세요.";
            case ASSEMBLY -> "작업 순서, 누락 부품과 체결 오류를 확인하세요.";
        };
    }

    private String alertTitle(ManufacturingAnalysisEvent analysis) {
        ManufacturingAnalysisEvent.AnalysisResult result = analysis.analysisResult();
        if (result.isEquipmentFault()) return "설비 이상 감지";
        if (result.isQualityDefect()) return "품질 불량 위험";
        if (result.isSequenceError()) return "조립 순서 오류";
        if (result.isBottleneck()) return "공정 병목 위험";
        return "제조 공정 위험";
    }

    private double calculateOperationRate(
            double cycleTimeSec,
            double processingTimeSec,
            double equipmentIdleTimeSec
    ) {
        // 이벤트 단위 가동률은 실제 작업 시간을 사이클 또는 작업+유휴 시간으로 나누어 계산한다.
        double plannedTimeSec = cycleTimeSec > 0
                ? cycleTimeSec
                : processingTimeSec + equipmentIdleTimeSec;
        if (plannedTimeSec <= 0) {
            return 0;
        }
        return round(clamp(processingTimeSec / plannedTimeSec * 100));
    }

    private String riskLevel(double score) {
        if (score >= 80) {
            return "CRITICAL";
        }
        if (score >= 60) {
            return "WARNING";
        }
        return "LOW";
    }

    private double clamp(double score) {
        return Math.max(0, Math.min(100, score));
    }

    private double round(double score) {
        return Math.round(score * 10.0) / 10.0;
    }

    private double number(Map<String, Object> source, String... path) {
        Object value = value(source, path);
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private boolean bool(Map<String, Object> source, String... path) {
        Object value = value(source, path);
        if (value instanceof String s) {
            return "Y".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s);
        }
        return value instanceof Boolean bool && bool;
    }

    private String text(Map<String, Object> source, String... path) {
        Object value = value(source, path);
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private Object value(Map<String, Object> source, String... path) {
        Object current = source;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(key);
        }
        return current;
    }

    /**
     * /analysis-results/{eventId}/detail API 응답용 분석 상세 레코드.
     * processRisk 계산 결과와 설비 이상 판단만 포함한다.
     */
    public record AnalysisDetail(
            String riskScoreScale,
            double riskScore,
            String riskLevel,
            boolean isAbnormal,
            String abnormalType,
            String overallFormula,
            ProcessRiskDetail processRisk,
            boolean isEquipmentAbnormal,
            String equipmentStatusReason,
            String finalDecisionReason,
            String analysisMessageReason
    ) {
    }

    /**
     * processRisk 계산 결과 레코드. detail API 에서 계산 근거 공개용.
     */
    public record ProcessRiskDetail(
            double score,
            Map<String, Object> usedFields,
            String formula
    ) {
    }

    private record ProcessComponent(
            double score,
            Map<String, Object> usedFields,
            String formula
    ) {
    }
}

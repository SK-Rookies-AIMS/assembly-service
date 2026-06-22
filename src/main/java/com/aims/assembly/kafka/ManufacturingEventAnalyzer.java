package com.aims.assembly.kafka;

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

        // 대기열, WIP, 지연 시간, 유휴 시간 기반 병목 위험도 계산
        double bottleneckRisk = clamp(
                stationDelaySec * 5
                        + waitingTimeSec * 2
                        + queueLength * 3
                        + wipCount
                        + equipmentIdleTimeSec
                        + Math.max(0, cycleTimeSec - targetCycleTime(event))
        );

        // 전류, 진동, 유휴 시간, 원천 설비 상태 기반 설비 위험도 계산
        double equipmentRisk = clamp(
                rmsAmpere * 8
                        + vibrationScore * 45
                        + robotVibrationScore * 35
                        + equipmentIdleTimeSec * 1.5
                        + healthStatusWeight(event)
        );

        // 전류, 일반 진동, 로봇 진동, 열화상 기반 불량 전이 위험도 계산
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

        double overallRisk = switch (analysisType) {
            case "BOTTLENECK_ANALYSIS" -> bottleneckRisk;
            case "DEFECT_TRANSFER_PREDICTION" -> defectTransferRisk;
            default -> clamp(
                    bottleneckRisk * 0.30
                            + defectTransferRisk * 0.20
                            + equipmentRisk * 0.25
                            + processRisk * 0.25
            );
        };
        overallRisk = round(overallRisk);
        String riskLevel = riskLevel(overallRisk);

        boolean bottleneck = bottleneckRisk >= 60;
        boolean qualityDefect = isQualityDefect(event, defectTransferRisk);
        boolean equipmentFault = equipmentRisk >= 60
                || "FAULT".equalsIgnoreCase(event.equipmentStatus())
                || "ERROR".equalsIgnoreCase(
                        text(event.eventJson(), "equipmentStatus", "operationStatus"));
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
                        overallRisk >= 60,
                        bottleneck,
                        qualityDefect,
                        equipmentFault,
                        sequenceError
                ),
                new ManufacturingAnalysisEvent.Reason(
                        mainReason(bottleneck, qualityDefect, equipmentFault, sequenceError),
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
                switch (analysis.riskLevel()) {
                    case "CRITICAL" -> "FAULT";
                    case "WARNING" -> "WARNING";
                    default -> "NORMAL";
                },
                analysis.riskLevel(),
                analysis.riskScores().overallRiskScore(),
                analysis.operationRate()
        );
    }

    public ManufacturingAlertEvent toAlertEvent(ManufacturingAnalysisEvent analysis) {
        // WARNING 또는 CRITICAL 분석 결과를 OPEN 상태의 알림으로 변환
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
                alertType(analysis),
                alertTitle(analysis),
                analysis.equipmentCode() + " 설비의 제조 위험이 감지되었습니다.",
                analysis.riskLevel(),
                analysis.riskScores().overallRiskScore(),
                "OPEN",
                true,
                analysis.reason().detailReasons(),
                analysis.recommendation().message()
        );
    }

    public boolean requiresAlert(ManufacturingAnalysisEvent analysis) {
        // PRD 발행 조건: WARNING/CRITICAL 또는 개별 이상 플래그 발생
        ManufacturingAnalysisEvent.AnalysisResult result = analysis.analysisResult();
        return !"LOW".equals(analysis.riskLevel())
                || analysis.riskScores().overallRiskScore() >= 80
                || result.isEquipmentFault()
                || result.isQualityDefect()
                || result.isBottleneck()
                || result.isSequenceError();
    }

    private double calculateProcessRisk(
            ManufacturingRawEvent event,
            double cycleTimeSec,
            double stationDelaySec
    ) {
        return clamp(switch (event.processCode()) {
            case PRESS -> {
                boolean countIncrease = bool(
                        event.eventJson(),
                        "processData",
                        "press",
                        "countIncreaseYn"
                );
                double rmsAmpere = number(event.eventJson(), "sensor", "current", "rmsAmpere");
                yield stationDelaySec * 6
                        + Math.max(0, cycleTimeSec - targetCycleTime(event)) * 5
                        + rmsAmpere * 8
                        + (countIncrease ? 0 : 35);
            }
            case BODY -> {
                double robotScore = number(
                        event.eventJson(),
                        "sensor",
                        "robotArmVibration",
                        "vibrationScore"
                );
                double frequency = number(
                        event.eventJson(),
                        "sensor",
                        "robotArmVibration",
                        "frequencyHz"
                );
                yield robotScore * 70 + Math.max(0, frequency - 100) * 0.08;
            }
            case PAINT -> {
                double defectScore =
                        number(event.eventJson(), "processData", "paint", "defectScore");
                double thermalDeviation =
                        number(event.eventJson(), "processData", "paint", "thermalStdTemp");
                double surfaceQuality =
                        number(event.eventJson(), "processData", "paint", "surfaceQualityScore");
                yield defectScore * 65
                        + thermalDeviation * 5
                        + Math.max(0, 80 - surfaceQuality);
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
                yield sequenceErrors * 35 + missingParts * 40 + fasteningErrors * 30;
            }
        });
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

    private double targetCycleTime(ManufacturingRawEvent event) {
        double target = number(event.eventJson(), "processData", "press", "targetCycleTimeSec");
        return target > 0 ? target : 40;
    }

    private double healthStatusWeight(ManufacturingRawEvent event) {
        String healthStatus = text(event.eventJson(), "equipmentStatus", "healthStatus");
        if (healthStatus == null) {
            healthStatus = event.equipmentStatus();
        }
        return switch (healthStatus == null ? "" : healthStatus.toUpperCase()) {
            case "FAULT" -> 50;
            case "WARNING" -> 25;
            case "MAINTENANCE" -> 15;
            default -> 0;
        };
    }

    private String mainReason(
            boolean bottleneck,
            boolean qualityDefect,
            boolean equipmentFault,
            boolean sequenceError
    ) {
        if (equipmentFault) {
            return "설비 센서 또는 상태값에서 고장 위험이 감지되었습니다.";
        }
        if (qualityDefect) {
            return "품질 센서와 공정 데이터에서 불량 위험이 감지되었습니다.";
        }
        if (sequenceError) {
            return "의장 공정의 작업 순서 오류가 감지되었습니다.";
        }
        if (bottleneck) {
            return "사이클타임과 대기열 증가로 병목 위험이 감지되었습니다.";
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

    private String recommendationMessage(ProcessCode processCode) {
        return switch (processCode) {
            case PRESS -> "프레스 설비 상태, 전류 RMS 값과 대기열을 확인하세요.";
            case BODY -> "로봇 암 진동과 충돌 위험을 확인하세요.";
            case PAINT -> "열화상, 도막 두께와 비전 불량 결과를 확인하세요.";
            case ASSEMBLY -> "작업 순서, 누락 부품과 체결 오류를 확인하세요.";
        };
    }

    private String alertType(ManufacturingAnalysisEvent analysis) {
        ManufacturingAnalysisEvent.AnalysisResult result = analysis.analysisResult();
        if (result.isEquipmentFault()) {
            return "EQUIPMENT_FAULT";
        }
        if (result.isQualityDefect()) {
            return "QUALITY_DEFECT";
        }
        if (result.isSequenceError()) {
            return "ASSEMBLY_SEQUENCE_ERROR";
        }
        if (result.isBottleneck()) {
            return "BOTTLENECK_RISK";
        }
        return "PROCESS_RISK";
    }

    private String alertTitle(ManufacturingAnalysisEvent analysis) {
        return switch (alertType(analysis)) {
            case "EQUIPMENT_FAULT" -> "설비 고장 위험";
            case "QUALITY_DEFECT" -> "품질 불량 위험";
            case "ASSEMBLY_SEQUENCE_ERROR" -> "조립 순서 오류";
            case "BOTTLENECK_RISK" -> "공정 병목 위험";
            default -> "제조 공정 위험";
        };
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
}

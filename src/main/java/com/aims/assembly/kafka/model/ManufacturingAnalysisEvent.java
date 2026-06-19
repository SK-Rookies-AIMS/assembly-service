package com.aims.assembly.kafka.model;

import com.aims.assembly.domain.enums.ProcessCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 공정 분석과 AI 분석 결과를 analysis 토픽으로 전달하는 메시지.
 */
public record ManufacturingAnalysisEvent(
        String analysisId,
        String eventId,
        LocalDateTime eventTime,
        LocalDateTime analyzedAt,
        String factoryCode,
        String lineCode,
        ProcessCode processCode,
        String stationCode,
        String equipmentCode,
        String equipmentName,
        String equipmentType,
        String productId,
        String carId,
        Long carMasterId,
        String analysisType,
        RiskScores riskScores,
        double operationRate,
        String riskLevel,
        AnalysisResult analysisResult,
        Reason reason,
        Recommendation recommendation
) {
    public record RiskScores(
            double overallRiskScore,
            double bottleneckRisk,
            double defectTransferRisk,
            double equipmentRisk,
            ProcessRisk processRisk
    ) {
    }

    /**
     * 공정에 따라 하나의 전용 위험도만 채우고 나머지는 null로 유지한다.
     */
    public record ProcessRisk(
            Double pressStopRisk,
            Double robotCollisionRisk,
            Double paintQualityRisk,
            Double assemblySequenceRisk
    ) {
    }

    public record AnalysisResult(
            boolean isAbnormal,
            boolean isBottleneck,
            boolean isQualityDefect,
            boolean isEquipmentFault,
            boolean isSequenceError
    ) {
    }

    public record Reason(
            String mainReason,
            List<String> detailReasons
    ) {
    }

    public record Recommendation(
            String actionType,
            String message
    ) {
    }
}

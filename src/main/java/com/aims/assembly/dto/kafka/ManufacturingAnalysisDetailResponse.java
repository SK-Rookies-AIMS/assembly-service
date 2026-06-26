package com.aims.assembly.dto.kafka;

import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.kafka.ManufacturingEventAnalyzer;

/**
 * GET /api/kafka/manufacturing/analysis-results/{eventId}/detail 응답 DTO.
 *
 * <p>riskScore 기준 (2026-06 개정):
 * <pre>riskScore = processRisk</pre>
 * bottleneckRisk / defectTransferRisk / equipmentRisk 는 응답에 포함되지 않는다.
 *
 * <p>equipmentStatusCheck: 설비 이상 여부를 상태값(FAULT/STOPPED/ERROR/DOWN) 기반으로 판단한 결과.
 * <p>alertDecision: alert topic 발행 여부를 나타낸다.
 * <p>processSpecificResult: 공정별 저장 테이블 저장 여부.
 */
public record ManufacturingAnalysisDetailResponse(
        String eventId,
        ProcessCode processCode,
        Double riskScore,
        String riskScoreScale,
        String severity,
        Boolean isAbnormal,
        String abnormalType,
        String overallFormula,
        ManufacturingEventAnalyzer.ProcessRiskDetail processRisk,
        EquipmentStatusCheck equipmentStatusCheck,
        String finalDecisionReason,
        String analysisMessageReason,
        String storedAnalysisMessage,
        AlertDecision alertDecision,
        ProcessSpecificResult processSpecificResult
) {
    /**
     * 설비 이상 상태 판단 결과.
     * isEquipmentAbnormal 이 true 이면 EQUIPMENT_STATUS 알람이 alert topic 으로 발행된다.
     */
    public record EquipmentStatusCheck(
            boolean isEquipmentAbnormal,
            String reason
    ) {
    }

    /**
     * alert topic 발행 결정 정보.
     */
    public record AlertDecision(
            boolean processRiskAlertPublished,
            boolean equipmentStatusAlertPublished,
            String alertTopic
    ) {
    }

    /**
     * 공정별 결과 테이블 저장 정보.
     */
    public record ProcessSpecificResult(
            boolean saved,
            String table,
            Long resultId
    ) {
    }

    public static ManufacturingAnalysisDetailResponse from(
            String eventId,
            ProcessCode processCode,
            String storedSeverity,
            Boolean storedIsAbnormal,
            String storedAbnormalType,
            String storedAnalysisMessage,
            ManufacturingEventAnalyzer.AnalysisDetail detail,
            String alertTopicName,
            ProcessSpecificResult processSpecificResult
    ) {
        boolean processRiskAlert = detail.riskScore() >= 60;
        boolean equipmentStatusAlert = detail.isEquipmentAbnormal();

        return new ManufacturingAnalysisDetailResponse(
                eventId,
                processCode,
                detail.riskScore(),
                detail.riskScoreScale(),
                storedSeverity,
                storedIsAbnormal,
                storedAbnormalType,
                detail.overallFormula(),
                detail.processRisk(),
                new EquipmentStatusCheck(
                        detail.isEquipmentAbnormal(),
                        detail.equipmentStatusReason()
                ),
                detail.finalDecisionReason(),
                detail.analysisMessageReason(),
                storedAnalysisMessage,
                new AlertDecision(
                        processRiskAlert,
                        equipmentStatusAlert,
                        alertTopicName
                ),
                processSpecificResult
        );
    }
}

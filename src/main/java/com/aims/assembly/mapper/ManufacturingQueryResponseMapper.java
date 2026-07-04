package com.aims.assembly.mapper;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.dto.kafka.ManufacturingAnalysisDetailResponse;
import com.aims.assembly.dto.kafka.ManufacturingAnalysisResultResponse;
import com.aims.assembly.dto.kafka.ManufacturingCarCompletionResponse;
import com.aims.assembly.kafka.ManufacturingEventAnalyzer;

import java.util.List;

public final class ManufacturingQueryResponseMapper {

    private ManufacturingQueryResponseMapper() {
    }

    public static ManufacturingAnalysisResultResponse toAnalysisResultResponse(ManufacturingAnalysisResult result) {
        return ManufacturingAnalysisResultResponse.from(result);
    }

    public static ManufacturingCarCompletionResponse toCarCompletionResponse(
            Long carMasterId,
            String status,
            boolean finalCompleted,
            int completedNormalProcessCount,
            int requiredProcessCount,
            List<ManufacturingCarCompletionResponse.ProcessCompletion> processes
    ) {
        return new ManufacturingCarCompletionResponse(
                carMasterId,
                status,
                finalCompleted,
                completedNormalProcessCount,
                requiredProcessCount,
                processes
        );
    }

    public static ManufacturingCarCompletionResponse.ProcessCompletion toProcessCompletion(
            ProcessCode processCode,
            boolean exists,
            boolean normalCompleted,
            Boolean isAbnormal,
            String severity,
            String eventId,
            String analysisId
    ) {
        return new ManufacturingCarCompletionResponse.ProcessCompletion(
                processCode,
                exists,
                normalCompleted,
                isAbnormal,
                severity,
                eventId,
                analysisId
        );
    }

    public static ManufacturingAnalysisDetailResponse.ProcessSpecificResult toProcessSpecificResult(
            boolean saved,
            String table,
            Long resultId
    ) {
        return new ManufacturingAnalysisDetailResponse.ProcessSpecificResult(saved, table, resultId);
    }

    public static ManufacturingAnalysisDetailResponse toDetailResponse(
            String eventId,
            ProcessCode processCode,
            String storedSeverity,
            Boolean storedIsAbnormal,
            String storedAbnormalType,
            String storedAnalysisMessage,
            ManufacturingEventAnalyzer.AnalysisDetail detail,
            String alertTopicName,
            ManufacturingAnalysisDetailResponse.ProcessSpecificResult processSpecificResult
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
                new ManufacturingAnalysisDetailResponse.EquipmentStatusCheck(
                        detail.isEquipmentAbnormal(),
                        detail.equipmentStatusReason()
                ),
                detail.finalDecisionReason(),
                detail.analysisMessageReason(),
                storedAnalysisMessage,
                new ManufacturingAnalysisDetailResponse.AlertDecision(
                        processRiskAlert,
                        equipmentStatusAlert,
                        alertTopicName
                ),
                processSpecificResult
        );
    }
}

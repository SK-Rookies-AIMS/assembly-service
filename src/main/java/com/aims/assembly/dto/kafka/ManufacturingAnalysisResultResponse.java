package com.aims.assembly.dto.kafka;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.domain.enums.Severity;

import java.time.LocalDateTime;

public record ManufacturingAnalysisResultResponse(
        String eventId,
        String analysisId,
        LocalDateTime eventTime,
        Long carMasterId,
        Long equipmentId,
        ProcessCode processCode,
        Boolean isAbnormal,
        String abnormalType,
        Severity severity,
        Double riskScore,
        String riskScoreScale,
        String analysisMessage,
        LocalDateTime analyzedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ManufacturingAnalysisResultResponse from(ManufacturingAnalysisResult result) {
        return new ManufacturingAnalysisResultResponse(
                result.getEventId(),
                result.getAnalysisId(),
                result.getEventTime(),
                result.getCarMasterId(),
                result.getEquipmentId(),
                result.getProcessCode(),
                result.getIsAbnormal(),
                result.getAbnormalType(),
                result.getSeverity(),
                result.getRiskScore(),
                "0-100",
                result.getAnalysisMessage(),
                result.getAnalyzedAt(),
                result.getCreatedAt(),
                result.getUpdatedAt()
        );
    }
}

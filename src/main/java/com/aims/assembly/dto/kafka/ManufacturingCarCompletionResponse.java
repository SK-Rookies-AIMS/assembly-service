package com.aims.assembly.dto.kafka;

import com.aims.assembly.domain.enums.ProcessCode;

import java.util.List;

public record ManufacturingCarCompletionResponse(
        Long carMasterId,
        String status,
        boolean finalCompleted,
        int completedNormalProcessCount,
        int requiredProcessCount,
        List<ProcessCompletion> processes
) {
    public record ProcessCompletion(
            ProcessCode processCode,
            boolean exists,
            boolean normalCompleted,
            Boolean isAbnormal,
            String severity,
            String eventId,
            String analysisId
    ) {
    }
}

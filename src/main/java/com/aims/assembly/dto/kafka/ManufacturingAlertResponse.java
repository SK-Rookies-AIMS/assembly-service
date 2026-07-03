package com.aims.assembly.dto.kafka;

import com.aims.assembly.domain.enums.ProcessCode;
import java.time.LocalDateTime;

public record ManufacturingAlertResponse(
        String alertId,
        String eventId,
        String analysisId,
        LocalDateTime createdAt,
        ProcessCode processCode,
        String equipmentCode,
        String equipmentName,
        Long carMasterId,
        Long equipmentId,
        String alertType,
        String alertTitle,
        String alertMessage,
        String riskLevel,
        double riskScore,
        String alertStatus,
        LocalDateTime recordedAt
) {}

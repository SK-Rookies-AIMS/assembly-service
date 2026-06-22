package com.aims.assembly.kafka.model;

import com.aims.assembly.domain.enums.ProcessCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * WARNING 또는 CRITICAL 분석 결과에서 생성되는 실시간 알림 메시지.
 */
public record ManufacturingAlertEvent(
        String alertId,
        String eventId,
        String analysisId,
        LocalDateTime createdAt,
        String factoryCode,
        String lineCode,
        ProcessCode processCode,
        String equipmentCode,
        String equipmentName,
        String alertType,
        String alertTitle,
        String alertMessage,
        String riskLevel,
        double riskScore,
        String alertStatus,
        boolean needAction,
        List<String> reason,
        String recommendedAction
) {
}

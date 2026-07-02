package com.aims.assembly.kafka.model;

import com.aims.assembly.domain.enums.ProcessCode;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 대시보드와 실시간 상태 저장소에서 사용하는 설비 중심 상태 메시지.
 */
public record EquipmentStatusEvent(
        String equipmentEventId,
        String eventId,
        LocalDateTime eventTime,
        String factoryCode,
        String lineCode,
        ProcessCode processCode,
        String equipmentCode,
        String equipmentName,
        String equipmentType,
        String operationStatus,
        String riskLevel,
        double overallRiskScore,
        double operationRate,
        Long equipmentId,
        String changeType,
        String reason
) {
    public EquipmentStatusEvent(
            String equipmentEventId, String eventId, LocalDateTime eventTime,
            String factoryCode, String lineCode, ProcessCode processCode,
            String equipmentCode, String equipmentName,
            String equipmentType, String operationStatus,
            String riskLevel, double overallRiskScore, double operationRate
    ) {
        this(equipmentEventId, eventId, eventTime, factoryCode, lineCode, processCode,
                equipmentCode, equipmentName, equipmentType, operationStatus,
                riskLevel, overallRiskScore, operationRate, null,
                isBlocking(operationStatus) ? "FAULT" : "RECOVERED", null);
    }

    private static boolean isBlocking(String operationStatus) {
        if (operationStatus == null || operationStatus.isBlank()) {
            return false;
        }
        String normalized = operationStatus.trim().toUpperCase(Locale.ROOT);
        return "FAULT".equals(normalized) || "STOPPED".equals(normalized);
    }
}

package com.aims.assembly.kafka.model;

import com.aims.assembly.domain.enums.ProcessCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * WARNING / CRITICAL 분석 결과 또는 설비 이상 상태에서 생성되는 실시간 알림 메시지.
 *
 * <p>alertType 구분:
 * <ul>
 *   <li>{@code PROCESS_RISK} - 제조 공정 분석 결과 riskScore(processRisk) >= 60</li>
 *   <li>{@code EQUIPMENT_STATUS} - 설비 상태값(WARNING/STOPPED/FAULT) 이상 감지</li>
 * </ul>
 * alert topic message key 는 {@code alertId} 를 사용한다.
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
        Long carMasterId,
        Long equipmentId,
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

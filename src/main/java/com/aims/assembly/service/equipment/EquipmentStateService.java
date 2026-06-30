package com.aims.assembly.service.equipment;

import com.aims.assembly.domain.equipment.Equipment;
import com.aims.assembly.domain.enums.EquipmentHealthStatus;
import com.aims.assembly.domain.enums.EquipmentOperationStatus;
import com.aims.assembly.kafka.model.EquipmentStatusEvent;
import com.aims.assembly.kafka.model.ManufacturingAnalysisEvent;
import com.aims.assembly.repository.equipment.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EquipmentStateService {

    private final EquipmentRepository equipmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional("sampleTransactionManager")
    public EquipmentStatusEvent markFault(ManufacturingAnalysisEvent analysis) {
        Equipment equipment = equipmentRepository.findByEquipmentCode(analysis.equipmentCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Equipment not found: " + analysis.equipmentCode()));
        String reason = analysis.reason() == null ? "Equipment fault detected"
                : analysis.reason().mainReason();
        boolean stopRequired = analysis.analysisResult().isEquipmentFault();
        LocalDateTime changedAt = LocalDateTime.now();
        equipment.markFault(changedAt, reason, stopRequired);
        EquipmentStatusEvent event = statusEvent(equipment, analysis.eventId(), changedAt,
                "FAULT", analysis.riskLevel(), analysis.riskScores().overallRiskScore(),
                analysis.operationRate());
        eventPublisher.publishEvent(new EquipmentStateCommittedEvent(event));
        return event;
    }

    @Transactional("sampleTransactionManager")
    public EquipmentStatusEvent recover(String equipmentCode, String reason) {
        Equipment equipment = equipmentRepository.findByEquipmentCode(equipmentCode)
                .orElseThrow(() -> new IllegalArgumentException("Equipment not found: " + equipmentCode));
        LocalDateTime changedAt = LocalDateTime.now();
        equipment.recover(changedAt, reason);
        EquipmentStatusEvent event = statusEvent(equipment,
                "RECOVERY-" + UUID.randomUUID(), changedAt, "RECOVERED", "NORMAL", 0, 0);
        eventPublisher.publishEvent(new EquipmentStateCommittedEvent(event));
        return event;
    }

    @Transactional("sampleTransactionManager")
    public void applyStatusEvent(EquipmentStatusEvent event) {
        Equipment equipment = equipmentRepository.findById(event.equipmentId())
                .or(() -> equipmentRepository.findByEquipmentCode(event.equipmentCode()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Equipment not found: id=" + event.equipmentId()
                                + ", code=" + event.equipmentCode()));
        equipment.applyStatus(
                operationStatus(event.operationStatus()),
                healthStatus(event.healthStatus()),
                event.eventTime() == null ? LocalDateTime.now() : event.eventTime(),
                event.reason()
        );
    }

    private EquipmentOperationStatus operationStatus(String status) {
        if (status == null || status.isBlank()) {
            return EquipmentOperationStatus.RUNNING;
        }
        return switch (status.toUpperCase()) {
            case "ERROR", "DOWN", "FAILURE", "CRITICAL" -> EquipmentOperationStatus.FAULT;
            default -> EquipmentOperationStatus.valueOf(status.toUpperCase());
        };
    }

    private EquipmentHealthStatus healthStatus(String status) {
        if (status == null || status.isBlank()) {
            return EquipmentHealthStatus.NORMAL;
        }
        return switch (status.toUpperCase()) {
            case "FAULT", "ERROR", "STOPPED", "DOWN", "FAILURE", "CRITICAL" ->
                    EquipmentHealthStatus.CRITICAL;
            case "WARNING" -> EquipmentHealthStatus.WARNING;
            default -> EquipmentHealthStatus.NORMAL;
        };
    }

    private EquipmentStatusEvent statusEvent(
            Equipment equipment, String sourceEventId, LocalDateTime eventTime,
            String changeType, String riskLevel, double riskScore, double operationRate
    ) {
        return new EquipmentStatusEvent(
                UUID.randomUUID().toString(), sourceEventId, eventTime, null, null,
                equipment.getProcessCode(), equipment.getEquipmentCode(),
                equipment.getEquipmentName(), equipment.getEquipmentType() == null ? null
                        : equipment.getEquipmentType().name(),
                equipment.getCurrentStatus() == null ? null : equipment.getCurrentStatus().name(),
                equipment.getHealthStatus().name(), riskLevel, riskScore, operationRate,
                equipment.getId(), changeType, equipment.getReason());
    }

    public record EquipmentStateCommittedEvent(EquipmentStatusEvent event) {}
}

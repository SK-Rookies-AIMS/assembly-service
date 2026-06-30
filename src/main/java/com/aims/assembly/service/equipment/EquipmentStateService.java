package com.aims.assembly.service.equipment;

import com.aims.assembly.domain.equipment.Equipment;
import com.aims.assembly.domain.enums.EquipmentOperationStatus;
import com.aims.assembly.kafka.model.EquipmentStatusEvent;
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
    public EquipmentStatusEvent recover(String equipmentCode, String reason) {
        Equipment equipment = equipmentRepository.findByEquipmentCode(equipmentCode)
                .orElseThrow(() -> new IllegalArgumentException("Equipment not found: " + equipmentCode));
        LocalDateTime changedAt = LocalDateTime.now();
        equipment.applyStatus(EquipmentOperationStatus.RUNNING, changedAt, reason);
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
                event.eventTime() == null ? LocalDateTime.now() : event.eventTime(),
                event.reason()
        );
    }

    private EquipmentOperationStatus operationStatus(String status) {
        if (status == null || status.isBlank()) {
            return EquipmentOperationStatus.RUNNING;
        }
        return EquipmentOperationStatus.valueOf(status.toUpperCase());
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
                riskLevel, riskScore, operationRate,
                equipment.getId(), changeType, equipment.getReason());
    }

    public record EquipmentStateCommittedEvent(EquipmentStatusEvent event) {}
}

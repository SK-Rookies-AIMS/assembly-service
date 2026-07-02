package com.aims.assembly.domain.equipment;

import com.aims.assembly.domain.enums.EquipmentOperationStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentTest {
    @Test
    void applyStatusUpdatesLatestStateTimesAndReason() {
        Equipment equipment = Equipment.builder()
                .currentStatus(EquipmentOperationStatus.RUNNING).build();
        LocalDateTime faultAt = LocalDateTime.of(2026, 6, 22, 10, 0);
        
        equipment.applyStatus(EquipmentOperationStatus.FAULT, faultAt, "vibration threshold");

        assertThat(equipment.getCurrentStatus()).isEqualTo(EquipmentOperationStatus.FAULT);
        assertThat(equipment.getLastFaultTime()).isEqualTo(faultAt);
        assertThat(equipment.getReason()).isEqualTo("vibration threshold");

        LocalDateTime recoveredAt = faultAt.plusHours(1);
        equipment.applyStatus(EquipmentOperationStatus.RUNNING, recoveredAt, "inspection complete");
        assertThat(equipment.getCurrentStatus()).isEqualTo(EquipmentOperationStatus.RUNNING);
        assertThat(equipment.getLastRecoveredTime()).isEqualTo(recoveredAt);
    }

    @Test
    void applyWarningStatusDoesNotUpdateLastFaultTime() {
        LocalDateTime existingFaultAt = LocalDateTime.of(2026, 6, 22, 9, 0);
        LocalDateTime existingRecoveredAt = LocalDateTime.of(2026, 6, 22, 8, 0);
        Equipment equipment = Equipment.builder()
                .currentStatus(EquipmentOperationStatus.RUNNING)
                .lastFaultTime(existingFaultAt)
                .lastRecoveredTime(existingRecoveredAt)
                .build();
        LocalDateTime warningAt = LocalDateTime.of(2026, 6, 22, 10, 0);

        equipment.applyStatus(EquipmentOperationStatus.WARNING, warningAt, "temperature threshold");

        assertThat(equipment.getCurrentStatus()).isEqualTo(EquipmentOperationStatus.WARNING);
        assertThat(equipment.getLastFaultTime()).isEqualTo(existingFaultAt);
        assertThat(equipment.getLastRecoveredTime()).isEqualTo(existingRecoveredAt);
        assertThat(equipment.getReason()).isEqualTo("temperature threshold");
    }
}

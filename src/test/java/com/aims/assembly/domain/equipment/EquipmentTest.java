package com.aims.assembly.domain.equipment;

import com.aims.assembly.domain.enums.EquipmentHealthStatus;
import com.aims.assembly.domain.enums.EquipmentOperationStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentTest {
    @Test
    void faultAndRecoveryUpdateLatestStateTimesAndReason() {
        Equipment equipment = Equipment.builder()
                .currentStatus(EquipmentOperationStatus.RUNNING)
                .healthStatus(EquipmentHealthStatus.NORMAL).build();
        LocalDateTime faultAt = LocalDateTime.of(2026, 6, 22, 10, 0);
        equipment.markFault(faultAt, "vibration threshold", true);

        assertThat(equipment.getCurrentStatus()).isEqualTo(EquipmentOperationStatus.FAULT);
        assertThat(equipment.getHealthStatus()).isEqualTo(EquipmentHealthStatus.CRITICAL);
        assertThat(equipment.getLastFaultTime()).isEqualTo(faultAt);
        assertThat(equipment.getReason()).isEqualTo("vibration threshold");

        LocalDateTime recoveredAt = faultAt.plusHours(1);
        equipment.recover(recoveredAt, "inspection complete");
        assertThat(equipment.getCurrentStatus()).isEqualTo(EquipmentOperationStatus.RUNNING);
        assertThat(equipment.getHealthStatus()).isEqualTo(EquipmentHealthStatus.NORMAL);
        assertThat(equipment.getLastRecoveredTime()).isEqualTo(recoveredAt);
    }

    @Test
    void nonStoppingFaultMarksHealthWarning() {
        Equipment equipment = Equipment.builder()
                .currentStatus(EquipmentOperationStatus.RUNNING)
                .healthStatus(EquipmentHealthStatus.NORMAL).build();
        LocalDateTime faultAt = LocalDateTime.of(2026, 6, 22, 10, 0);

        equipment.markFault(faultAt, "temperature threshold", false);

        assertThat(equipment.getCurrentStatus()).isEqualTo(EquipmentOperationStatus.WARNING);
        assertThat(equipment.getHealthStatus()).isEqualTo(EquipmentHealthStatus.WARNING);
        assertThat(equipment.getLastFaultTime()).isEqualTo(faultAt);
        assertThat(equipment.getReason()).isEqualTo("temperature threshold");
    }
}

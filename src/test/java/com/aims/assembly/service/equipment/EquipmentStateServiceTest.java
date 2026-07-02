package com.aims.assembly.service.equipment;

import com.aims.assembly.domain.equipment.Equipment;
import com.aims.assembly.domain.enums.EquipmentOperationStatus;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.kafka.model.EquipmentStatusEvent;
import com.aims.assembly.repository.equipment.EquipmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EquipmentStateServiceTest {

    @Test
    void appliesAllValidKafkaStatusesToSampleEquipmentCurrentStatus() {
        EquipmentRepository repository = mock(EquipmentRepository.class);
        EquipmentStateService service = new EquipmentStateService(
                repository,
                mock(ApplicationEventPublisher.class)
        );

        for (EquipmentOperationStatus status : EquipmentOperationStatus.values()) {
            Equipment equipment = Equipment.builder()
                    .id(10L)
                    .equipmentCode("EQ-1")
                    .currentStatus(EquipmentOperationStatus.RUNNING)
                    .build();
            when(repository.findById(10L)).thenReturn(Optional.of(equipment));

            service.applyStatusEvent(event(status.name()));

            assertThat(equipment.getCurrentStatus()).isEqualTo(status);
        }
    }

    @Test
    void normalizesLowercaseKafkaStatusBeforeApplying() {
        EquipmentRepository repository = mock(EquipmentRepository.class);
        Equipment equipment = Equipment.builder()
                .id(10L)
                .equipmentCode("EQ-1")
                .currentStatus(EquipmentOperationStatus.RUNNING)
                .build();
        when(repository.findById(10L)).thenReturn(Optional.of(equipment));
        EquipmentStateService service = new EquipmentStateService(
                repository,
                mock(ApplicationEventPublisher.class)
        );

        service.applyStatusEvent(event(" warning "));

        assertThat(equipment.getCurrentStatus()).isEqualTo(EquipmentOperationStatus.WARNING);
    }

    @Test
    void skipsUnknownKafkaStatusWithoutUpdatingEquipment() {
        EquipmentRepository repository = mock(EquipmentRepository.class);
        EquipmentStateService service = new EquipmentStateService(
                repository,
                mock(ApplicationEventPublisher.class)
        );

        service.applyStatusEvent(event("ERROR"));

        verifyNoInteractions(repository);
    }

    private EquipmentStatusEvent event(String operationStatus) {
        return new EquipmentStatusEvent(
                "EQEVT-1",
                "EVT-1",
                LocalDateTime.of(2026, 7, 2, 10, 0),
                null,
                null,
                ProcessCode.PRESS,
                "EQ-1",
                null,
                "HYDRAULIC_PRESS",
                operationStatus,
                null,
                0.0,
                0.0,
                10L,
                "FAULT",
                "status event"
        );
    }
}

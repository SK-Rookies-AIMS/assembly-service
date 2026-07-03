package com.aims.assembly.domain.event;

import com.aims.assembly.domain.car.CarMaster;
import com.aims.assembly.domain.enums.*;
import com.aims.assembly.domain.equipment.Equipment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ManufacturingEventJsonTest {
    @Test
    void allowsNullEventTime() {
        ManufacturingEventJson event = ManufacturingEventJson.builder().build();
        assertThat(event.getEventTime()).isNull();
    }

    @Test
    void preservesSourceJsonAndStatusDefaults() {
        JsonNode json = JsonNodeFactory.instance.objectNode().put("temperature", 32.5);
        ManufacturingEventJson event = ManufacturingEventJson.builder()
                .eventId("EVT-1").eventTime(LocalDateTime.of(2026, 6, 17, 10, 30))
                .carMaster(CarMaster.builder().vehicleId("V1").carType("SEDAN")
                        .engineType("EV").carColor("WHITE").build())
                .equipment(Equipment.builder().equipmentCode("PRESS-1")
                        .equipmentType(EquipmentType.HYDRAULIC_PRESS).build())
                .processCode(ProcessCode.PRESS).eventJson(json).build();

        assertThat(event.getEventJson()).isSameAs(json);
        assertThat(event.getDispatchStatus()).isEqualTo(DispatchStatus.PENDING);
        assertThat(event.getAnalysisStatus()).isEqualTo(AnalysisStatus.NOT_ANALYZED);
        assertThat(event.getIsSent()).isFalse();
        assertThat(event.getRetryCount()).isZero();
    }
}

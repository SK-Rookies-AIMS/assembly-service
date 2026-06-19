package com.aims.assembly.domain.event;

import com.aims.assembly.domain.car.CarMaster;
import com.aims.assembly.domain.enums.EquipmentType;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.domain.equipment.Equipment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("제조 이벤트 JSON 엔티티 생성 테스트")
class ManufacturingEventJsonTest {

    @Test
    @DisplayName("빌더로 제조 이벤트 JSON을 생성하면 입력한 이벤트 정보와 연관 엔티티가 저장된다")
    void createManufacturingEventJsonWithBuilder() {
        LocalDateTime eventTime = LocalDateTime.of(2026, 6, 17, 10, 30);
        CarMaster carMaster = createCarMaster();
        Equipment equipment = createEquipment();
        JsonNode eventJson = createPressEventJson();

        ManufacturingEventJson event = ManufacturingEventJson.builder()
                .eventId("EVT-20260617-001")
                .eventTime(eventTime)
                .carMaster(carMaster)
                .equipment(equipment)
                .processCode(ProcessCode.PRESS)
                .stationCode("PRESS-ST-01")
                .equipmentCode("PRESS-001")
                .equipmentType("HYDRAULIC_PRESS")
                .equipmentStatus("NORMAL")
                .eventType("PROCESS_STARTED")
                .eventJson(eventJson)
                .build();

        assertAll(
                () -> assertEquals("EVT-20260617-001", event.getEventId()),
                () -> assertEquals(eventTime, event.getEventTime()),
                () -> assertSame(carMaster, event.getCarMaster()),
                () -> assertSame(equipment, event.getEquipment()),
                () -> assertEquals(ProcessCode.PRESS, event.getProcessCode()),
                () -> assertEquals("PRESS-ST-01", event.getStationCode()),
                () -> assertEquals("PRESS-001", event.getEquipmentCode()),
                () -> assertEquals("HYDRAULIC_PRESS", event.getEquipmentType()),
                () -> assertEquals("NORMAL", event.getEquipmentStatus()),
                () -> assertEquals("PROCESS_STARTED", event.getEventType()),
                () -> assertEquals(eventJson, event.getEventJson()),
                () -> assertFalse(event.getIsSent())
        );
    }

    @Test
    @DisplayName("빌더로 전송 완료 상태의 제조 이벤트 JSON을 생성할 수 있다")
    void createSentManufacturingEventJsonWithBuilder() {
        LocalDateTime eventTime = LocalDateTime.of(2026, 6, 17, 11, 0);
        LocalDateTime sentAt = LocalDateTime.of(2026, 6, 17, 11, 1);
        CarMaster carMaster = createCarMaster();
        Equipment equipment = createEquipment();
        JsonNode eventJson = JsonNodeFactory.instance.objectNode()
                .put("robotCurrentRms", 12.3);

        ManufacturingEventJson event = ManufacturingEventJson.builder()
                .eventId("EVT-20260617-002")
                .eventTime(eventTime)
                .carMaster(carMaster)
                .equipment(equipment)
                .processCode(ProcessCode.BODY)
                .equipmentCode("ROBOT-001")
                .eventJson(eventJson)
                .isSent(true)
                .sentAt(sentAt)
                .build();

        assertAll(
                () -> assertEquals("EVT-20260617-002", event.getEventId()),
                () -> assertEquals(eventTime, event.getEventTime()),
                () -> assertSame(carMaster, event.getCarMaster()),
                () -> assertSame(equipment, event.getEquipment()),
                () -> assertEquals(ProcessCode.BODY, event.getProcessCode()),
                () -> assertEquals("ROBOT-001", event.getEquipmentCode()),
                () -> assertEquals(eventJson, event.getEventJson()),
                () -> assertEquals(true, event.getIsSent()),
                () -> assertEquals(sentAt, event.getSentAt())
        );
    }

    @Test
    @DisplayName("공정 코드는 각 enum 값에 맞는 한글 설명을 제공한다")
    void processCodeHasKoreanDescription() {
        assertAll(
                () -> assertEquals("프레스", ProcessCode.PRESS.getDescription()),
                () -> assertEquals("차체", ProcessCode.BODY.getDescription()),
                () -> assertEquals("도장", ProcessCode.PAINT.getDescription()),
                () -> assertEquals("의장", ProcessCode.ASSEMBLY.getDescription())
        );
    }

    private CarMaster createCarMaster() {
        return CarMaster.builder()
                .vehicleId("VH-001")
                .carType("SEDAN")
                .engineType("GASOLINE")
                .carColor("WHITE")
                .fuelEfficiency(15)
                .build();
    }

    private Equipment createEquipment() {
        return Equipment.builder()
                .processCode(ProcessCode.PRESS)
                .equipmentCode("PRESS-001")
                .equipmentName("Main Press")
                .equipmentType(EquipmentType.HYDRAULIC_PRESS)
                .build();
    }

    private JsonNode createPressEventJson() {
        return JsonNodeFactory.instance.objectNode()
                .put("temperature", 32.5)
                .put("pressure", 120);
    }
}

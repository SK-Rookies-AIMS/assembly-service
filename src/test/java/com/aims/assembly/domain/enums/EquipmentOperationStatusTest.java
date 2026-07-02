package com.aims.assembly.domain.enums;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentOperationStatusTest {

    @ParameterizedTest
    @CsvSource({
            "RUNNING,RUNNING",
            "running,RUNNING",
            "' warning ',WARNING",
            "stopped,STOPPED",
            "fault,FAULT"
    })
    void normalizesValidKafkaStatusValues(String input, EquipmentOperationStatus expected) {
        assertThat(EquipmentOperationStatus.from(input)).contains(expected);
    }

    @Test
    void rejectsUnknownKafkaStatusValues() {
        assertThat(EquipmentOperationStatus.from("ERROR")).isEmpty();
        assertThat(EquipmentOperationStatus.from("DOWN")).isEmpty();
        assertThat(EquipmentOperationStatus.from("")).isEmpty();
    }
}

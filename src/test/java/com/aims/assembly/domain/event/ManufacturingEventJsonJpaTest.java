package com.aims.assembly.domain.event;

import org.junit.jupiter.api.Test;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

import static org.assertj.core.api.Assertions.assertThat;

class ManufacturingEventJsonJpaTest {
    @Test
    void equipmentMetadataIsOwnedOnlyByEquipmentRelation() {
        assertThat(ManufacturingEventJson.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("equipmentCode", "equipmentType");
        Table table = ManufacturingEventJson.class.getAnnotation(Table.class);
        assertThat(table.indexes()).extracting(Index::name)
                .doesNotContain("idx_equipment_time");
    }

    @Test
    void eventTimeColumnIsNullable() throws Exception {
        Column column = ManufacturingEventJson.class.getDeclaredField("eventTime")
                .getAnnotation(Column.class);
        assertThat(column.nullable()).isTrue();
    }

    @Test
    void schedulerIndexMatchesReadyQuery() {
        Table table = ManufacturingEventJson.class.getAnnotation(Table.class);
        assertThat(table.indexes()).extracting(Index::name)
                .contains("idx_dispatch_event_time")
                .doesNotContain("idx_event_time_sent");
        assertThat(table.indexes()).filteredOn(i -> i.name().equals("idx_dispatch_event_time"))
                .extracting(Index::columnList)
                .containsExactly("dispatch_status, event_time, is_sent");
    }
}

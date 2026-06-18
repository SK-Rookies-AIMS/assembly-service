package com.aims.assembly.domain.event;

import com.aims.assembly.domain.car.CarMaster;
import com.aims.assembly.domain.commons.BaseEntity;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.domain.equipment.Equipment;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "manufacturing_event_json",
        indexes = {
                @Index(name = "idx_event_time_sent", columnList = "event_time, is_sent"),
                @Index(name = "idx_process_time", columnList = "process_code, event_time"),
                @Index(name = "idx_equipment_time", columnList = "equipment_code, event_time"),
                @Index(name = "idx_car_time", columnList = "car_master_id, event_time"),
                @Index(name = "idx_event_id", columnList = "event_id"),
                @Index(name = "idx_equipment_id_time", columnList = "equipment_id, event_time")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ManufacturingEventJson extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "car_master_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_manufacturing_event_json_car_master")
    )
    private CarMaster carMaster;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "equipment_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_manufacturing_event_json_equipment")
    )
    private Equipment equipment;

    @Enumerated(EnumType.STRING)
    @Column(name = "process_code", nullable = false, columnDefinition = "ENUM('PRESS', 'BODY', 'PAINT', 'ASSEMBLY')")
    private ProcessCode processCode;

    @Column(name = "station_code", length = 50)
    private String stationCode;

    @Column(name = "equipment_code", nullable = false, length = 50)
    private String equipmentCode;

    @Column(name = "equipment_type", length = 50)
    private String equipmentType;

    @Column(name = "equipment_status", length = 30)
    private String equipmentStatus;

    @Column(name = "event_type", length = 50)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_json", nullable = false, columnDefinition = "JSON")
    private JsonNode eventJson;

    @Builder.Default
    @Column(name = "is_sent", columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean isSent = false;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}

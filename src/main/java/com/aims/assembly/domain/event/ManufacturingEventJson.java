package com.aims.assembly.domain.event;

import com.aims.assembly.domain.car.CarMaster;
import com.aims.assembly.domain.commons.BaseEntity;
import com.aims.assembly.domain.enums.*;
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
                @Index(name = "idx_dispatch_event_time",
                        columnList = "dispatch_status, event_time, is_sent"),
                @Index(name = "idx_process_time",
                        columnList = "process_code, event_time"),
                @Index(name = "idx_car_time",
                        columnList = "car_master_id, event_time"),
                @Index(name = "idx_event_id",
                        columnList = "event_id"),
                @Index(name = "idx_equipment_id_time",
                        columnList = "equipment_id, event_time")
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "car_master_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_manufacturing_event_json_car_master"
            )
    )
    private CarMaster carMaster;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "equipment_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_manufacturing_event_json_equipment"
            )
    )
    private Equipment equipment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_json", nullable = false, columnDefinition = "JSON")
    private JsonNode eventJson;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "event_time", nullable = true)
    private LocalDateTime eventTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "process_code", nullable = false)
    private ProcessCode processCode;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "dispatch_status", length = 20)
    private DispatchStatus dispatchStatus = DispatchStatus.PENDING;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", length = 20)
    private AnalysisStatus analysisStatus = AnalysisStatus.NOT_ANALYZED;

    @Builder.Default
    @Column(name = "is_sent")
    private Boolean isSent = false;

    @Builder.Default
    @Column(name = "retry_count")
    private Long retryCount = 0L;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}

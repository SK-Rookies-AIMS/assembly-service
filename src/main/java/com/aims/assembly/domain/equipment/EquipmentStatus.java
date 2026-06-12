package com.aims.assembly.domain.equipment;

import com.aims.assembly.domain.commons.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "equipment_status")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentStatus extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long equipmentId;

    @Enumerated(EnumType.STRING)
    private EquipmentStatusType status;
}
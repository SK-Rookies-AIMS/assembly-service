package com.aims.assembly.domain.equipment;

import com.aims.assembly.domain.enums.EquipmentType;
import com.aims.assembly.domain.enums.ProcessCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "equipment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "process_code", columnDefinition = "ENUM('PRESS', 'BODY', 'PAINT', 'ASSEMBLY')")
    private ProcessCode processCode;

    @Column(name = "equipment_code", length = 50)
    private String equipmentCode;

    @Column(name = "equipment_name", length = 100)
    private String equipmentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "equipment_type", columnDefinition = "ENUM('HYDRAULIC_PRESS', 'ROBOT_ARM', 'CAMERA', 'CONVEYOR')")
    private EquipmentType equipmentType;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

package com.aims.assembly.domain.equipment;

import com.aims.assembly.domain.enums.EquipmentOperationStatus;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

    @Column(name = "equipment_code", length = 50)
    private String equipmentCode;

    @Column(name = "equipment_name", length = 100)
    private String equipmentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "equipment_type")
    private EquipmentType equipmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "process_code")
    private ProcessCode processCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", columnDefinition = "ENUM('RUNNING','WARNING','STOPPED','FAULT')")
    private EquipmentOperationStatus currentStatus;

    @Column(name = "last_fault_time")
    private LocalDateTime lastFaultTime;

    @Column(name = "last_recovered_time")
    private LocalDateTime lastRecoveredTime;

    @Column(name = "reason", length = 255)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void applyStatus(
            EquipmentOperationStatus operationStatus,
            LocalDateTime changedAt,
            String reason
    ) {
        this.currentStatus = operationStatus;
        if (operationStatus == EquipmentOperationStatus.RUNNING) {
            this.lastRecoveredTime = changedAt;
        } else if (operationStatus == EquipmentOperationStatus.STOPPED
                || operationStatus == EquipmentOperationStatus.FAULT) {
            this.lastFaultTime = changedAt;
        }
        this.reason = reason;
    }
}

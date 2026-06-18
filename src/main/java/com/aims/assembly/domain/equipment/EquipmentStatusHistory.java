package com.aims.assembly.domain.equipment;

import com.aims.assembly.domain.commons.BaseEntity;
import com.aims.assembly.domain.enums.EquipmentOperationStatus;
import com.aims.assembly.domain.enums.ProcessCode;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "equipment_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EquipmentStatusHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 설비 상태 이력 ID

    @Column(name = "event_id", length = 100, nullable = false)
    private String eventId; // 원천 이벤트 ID

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId; // 설비 ID

    @Enumerated(EnumType.STRING)
    @Column(name = "process_code", length = 20, nullable = false)
    private ProcessCode processCode; // 공정 코드

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_status", length = 30, nullable = false)
    private EquipmentOperationStatus operationStatus; // 설비 가동 상태

    @Column(name = "status_changed_time", nullable = false)
    private LocalDateTime statusChangedTime; // 설비 상태 변경 시간
}

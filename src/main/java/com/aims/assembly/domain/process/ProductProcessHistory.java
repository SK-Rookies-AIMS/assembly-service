package com.aims.assembly.domain.process;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_process_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductProcessHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "manufacturing_event_id")
    private Long manufacturingEventId;

    @Column(name = "car_master_id")
    private Long carMasterId;

    @Column(name = "process_code")
    private String processCode;

    @Column(name = "equipment_code")
    private String equipmentCode;

    @Column(name = "station_code")
    private String stationCode;

    @Column(name = "lot_code")
    private String lotCode;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "process_time")
    private Double processTime;

    @Column(name = "waiting_time")
    private Double waitingTime;

    @Column(name = "result_status")
    private String resultStatus;

    @Column(name = "sequence_no")
    private Integer sequenceNo;

    @Column(name = "previous_process_code")
    private String previousProcessCode;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

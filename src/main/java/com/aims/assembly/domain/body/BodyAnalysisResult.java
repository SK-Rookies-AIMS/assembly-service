package com.aims.assembly.domain.body;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "body_analysis_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BodyAnalysisResult {

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

    @Column(name = "robot_current_rms")
    private Double robotCurrentRms;

    @Column(name = "vibration_value")
    private Double vibrationValue;

    @Column(name = "robot_motion_status")
    private String robotMotionStatus;

    @Column(name = "operation_rate")
    private Double operationRate;

    @Column(name = "threshold_value")
    private Double thresholdValue;

    @Column(name = "is_abnormal")
    private Boolean isAbnormal;

    @Column(name = "abnormal_type")
    private String abnormalType;

    @Column(name = "risk_score")
    private Double riskScore;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;
}

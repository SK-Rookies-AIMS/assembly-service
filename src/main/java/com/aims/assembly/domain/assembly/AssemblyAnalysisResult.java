package com.aims.assembly.domain.assembly;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "assembly_analysis_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssemblyAnalysisResult {

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

    @Column(name = "operation_rate")
    private Double operationRate;

    @Column(name = "assembly_sequence_status")
    private String assemblySequenceStatus;

    @Column(name = "missing_part_count")
    private Integer missingPartCount;

    @Column(name = "fastening_error_count")
    private Integer fasteningErrorCount;

    @Column(name = "sequence_error_count")
    private Integer sequenceErrorCount;

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
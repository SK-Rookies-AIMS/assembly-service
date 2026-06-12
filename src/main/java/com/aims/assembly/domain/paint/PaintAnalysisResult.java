package com.aims.assembly.domain.paint;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "paint_analysis_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaintAnalysisResult {

    @Id
    private Long id;

    @Column(name = "manufacturing_event_id")
    private Long manufacturingEventId;

    @Column(name = "thermal_vision_id")
    private Long thermalVisionId;

    @Column(name = "car_master_id")
    private Long carMasterId;

    @Column(name = "process_code")
    private String processCode;

    @Column(name = "equipment_code")
    private String equipmentCode;

    @Column(name = "operation_rate")
    private Double operationRate;

    @Column(name = "defect_count")
    private Integer defectCount;

    @Column(name = "defect_rate")
    private Double defectRate;

    @Column(name = "thermal_avg_temp")
    private Double thermalAvgTemp;

    @Column(name = "thermal_max_temp")
    private Double thermalMaxTemp;

    @Column(name = "surface_quality_score")
    private Double surfaceQualityScore;

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

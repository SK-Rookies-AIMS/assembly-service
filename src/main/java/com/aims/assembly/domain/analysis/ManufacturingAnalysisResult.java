package com.aims.assembly.domain.analysis;

import com.aims.assembly.domain.commons.BaseEntity;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.domain.enums.Severity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "manufacturing_analysis_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManufacturingAnalysisResult extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 분석 결과 PK

    @Column(name = "analysis_id", length = 100, nullable = false)
    private String analysisId; // 분석 ID, Kafka/분석 추적용 컬럼

    @Column(name = "event_id", length = 100, nullable = false)
    private String eventId; // 원천 이벤트 ID

    @Column(name = "car_master_id", nullable = false)
    private Long carMasterId; // 차량 마스터 ID

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId; // 설비 ID

    @Enumerated(EnumType.STRING)
    @Column(name = "process_code", length = 20, nullable = false)
    private ProcessCode processCode; // 공정 코드

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime; // 이벤트 발생 시간

    @Column(name = "is_abnormal", nullable = false)
    private Boolean isAbnormal; // 이상 여부

    @Column(name = "abnormal_type", length = 50)
    private String abnormalType; // 이상 유형

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20)
    private Severity severity; // 심각도

    @Column(name = "risk_score")
    private Double riskScore; // 위험도 점수

    @Column(name = "analysis_message", length = 500)
    private String analysisMessage; // 분석 메시지

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt; // 분석 수행 시간
}
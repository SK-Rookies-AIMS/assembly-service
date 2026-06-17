package com.aims.assembly.domain.analysis;

import com.aims.assembly.domain.car.CarMaster;
import com.aims.assembly.domain.enums.ProcessCode;
import jakarta.persistence.*;
import lombok.*;

import javax.print.attribute.standard.Severity;
import java.time.LocalDateTime;

@Entity
@Table(name = "manufacturing_analysis_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManufacturingAnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id; // 분석 결과 PK

    @Column(name = "analysis_id", length = 100, nullable = false, unique = true)
    private String analysisId; // 분석 결과 ID

    @Column(name = "event_id", length = 100, nullable = false)
    private String eventId; // 원천 이벤트 ID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_master_id", nullable = false)
    private CarMaster carMaster; // 차량 마스터

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment; // 설비

    @Enumerated(EnumType.STRING)
    @Column(name = "process_code", nullable = false, length = 20)
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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt; // 생성 시각

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (analyzedAt == null) {
            analyzedAt = LocalDateTime.now();
        }
    }
}

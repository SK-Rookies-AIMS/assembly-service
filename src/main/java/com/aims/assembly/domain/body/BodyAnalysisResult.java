package com.aims.assembly.domain.body;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "body_analysis_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BodyAnalysisResult {

    @Id
    @Column(name = "analysis_result_id")
    private Long analysisResultId; // 공통 분석 결과 ID, PK 겸 FK

    @Column(name = "robot_motion_status", length = 30)
    private String robotMotionStatus; // 로봇 동작 상태

    @Column(name = "robot_operation_mode", length = 30)
    private String robotOperationMode; // 로봇 운전 모드

    @Column(name = "robot_vibration_score")
    private Double robotVibrationScore; // 로봇 진동 점수

    @Column(name = "frequency_peak_band", length = 30)
    private String frequencyPeakBand; // 최대 진동 주파수 대역

    @Column(name = "frequency_peak_value")
    private Double frequencyPeakValue; // 최대 진동 대역 값

    @Column(name = "frequency_bands_json", columnDefinition = "JSON")
    private String frequencyBandsJson; // 주파수 대역별 진동값 JSON

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "analysis_result_id", nullable = false)
    private ManufacturingAnalysisResult analysisResult;
}

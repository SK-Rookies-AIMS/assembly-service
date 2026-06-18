package com.aims.assembly.domain.paint;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "paint_analysis_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PaintAnalysisResult {

    @Id
    @Column(name = "analysis_result_id")
    private Long analysisResultId; // 공통 분석 결과 ID, PK 겸 FK

    @Column(name = "image_position", length = 20)
    private String imagePosition; // 이미지 촬영 위치

    @Column(name = "thermal_std_temp")
    private Double thermalStdTemp; // 온도 표준편차

    @Column(name = "thickness_value")
    private Double thicknessValue; // 도장 두께

    @Column(name = "defect_score")
    private Double defectScore; // 비전 불량 점수

    @Column(name = "vision_label", length = 30)
    private String visionLabel; // 비전 라벨

    @Column(name = "surface_quality_score")
    private Double surfaceQualityScore; // 표면 품질 점수

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "analysis_result_id", nullable = false)
    private ManufacturingAnalysisResult analysisResult;
}

package com.aims.assembly.domain.press;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "press_analysis_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PressAnalysisResult {

    @Id
    @Column(name = "analysis_result_id")
    private Long analysisResultId; // 공통 분석 결과 ID, PK 겸 FK

    @Column(name = "count_increase_yn")
    private Boolean countIncreaseYn; // 생산 카운트 증가 여부

    @Column(name = "target_cycle_time_sec")
    private Double targetCycleTimeSec; // 기준 사이클타임 초

    @Column(name = "actual_cycle_time_sec")
    private Double actualCycleTimeSec; // 실제 사이클타임 초

    @Column(name = "cycle_time_gap_sec")
    private Double cycleTimeGapSec; // 사이클타임 차이 초

    @Column(name = "timestamp_delay_sec")
    private Double timestampDelaySec; // 이벤트 지연 시간 초

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "analysis_result_id", nullable = false)
    private ManufacturingAnalysisResult analysisResult;
}

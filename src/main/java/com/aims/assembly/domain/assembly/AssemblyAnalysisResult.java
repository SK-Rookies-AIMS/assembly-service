package com.aims.assembly.domain.assembly;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "assembly_analysis_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AssemblyAnalysisResult {

    @Id
    @Column(name = "analysis_result_id")
    private Long analysisResultId; // 공통 분석 결과 ID, PK 겸 FK

    @Column(name = "expected_sequence", length = 500)
    private String expectedSequence; // 기준 작업 순서

    @Column(name = "actual_sequence", length = 500)
    private String actualSequence; // 실제 작업 순서

    @Column(name = "sequence_error_count")
    private Integer sequenceErrorCount; // 작업 순서 오류 수

    @Column(name = "missing_part_count")
    private Integer missingPartCount; // 누락 부품 수

    @Column(name = "fastening_error_count")
    private Integer fasteningErrorCount; // 체결 오류 수

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "analysis_result_id", nullable = false)
    private ManufacturingAnalysisResult analysisResult;
}
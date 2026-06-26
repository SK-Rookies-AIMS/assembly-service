package com.aims.assembly.repository.analysis;

import com.aims.assembly.domain.paint.PaintAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaintAnalysisResultRepository extends JpaRepository<PaintAnalysisResult, Long> {
    Optional<PaintAnalysisResult> findByAnalysisResult_AnalysisId(String analysisId);
}

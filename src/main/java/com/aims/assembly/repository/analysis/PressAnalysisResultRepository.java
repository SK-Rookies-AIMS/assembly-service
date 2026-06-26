package com.aims.assembly.repository.analysis;

import com.aims.assembly.domain.press.PressAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PressAnalysisResultRepository extends JpaRepository<PressAnalysisResult, Long> {
    Optional<PressAnalysisResult> findByAnalysisResult_AnalysisId(String analysisId);
}

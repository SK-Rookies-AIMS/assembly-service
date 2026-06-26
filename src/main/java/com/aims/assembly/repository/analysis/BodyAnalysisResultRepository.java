package com.aims.assembly.repository.analysis;

import com.aims.assembly.domain.body.BodyAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BodyAnalysisResultRepository extends JpaRepository<BodyAnalysisResult, Long> {
    Optional<BodyAnalysisResult> findByAnalysisResult_AnalysisId(String analysisId);
}

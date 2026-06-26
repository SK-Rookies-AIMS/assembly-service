package com.aims.assembly.repository.analysis;

import com.aims.assembly.domain.assembly.AssemblyAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssemblyAnalysisResultRepository extends JpaRepository<AssemblyAnalysisResult, Long> {
    Optional<AssemblyAnalysisResult> findByAnalysisResult_AnalysisId(String analysisId);
}

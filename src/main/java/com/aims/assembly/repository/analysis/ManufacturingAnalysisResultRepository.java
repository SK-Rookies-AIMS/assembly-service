package com.aims.assembly.repository.analysis;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManufacturingAnalysisResultRepository
        extends JpaRepository<ManufacturingAnalysisResult, Long> {
}

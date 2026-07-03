package com.aims.assembly.repository.analysis;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManufacturingAnalysisResultRepository
        extends JpaRepository<ManufacturingAnalysisResult, Long> {
    Optional<ManufacturingAnalysisResult> findFirstByEventIdOrderByAnalyzedAtDescCreatedAtDesc(
            String eventId
    );

    List<ManufacturingAnalysisResult> findAllByOrderByAnalyzedAtDescCreatedAtDesc(
            Pageable pageable
    );

    List<ManufacturingAnalysisResult> findByCarMasterIdOrderByEventTimeAscAnalyzedAtAsc(
            Long carMasterId
    );

}

package com.aims.assembly.repository.analysis;

import com.aims.assembly.domain.paint.PaintAnalysisResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaintAnalysisResultRepository extends JpaRepository<PaintAnalysisResult, Long> {
    Optional<PaintAnalysisResult> findByAnalysisResult_AnalysisId(String analysisId);

    @Query("""
            select paint
            from PaintAnalysisResult paint
            join fetch paint.analysisResult result
            where result.processCode = com.aims.assembly.domain.enums.ProcessCode.PAINT
              and (:from is null or result.analyzedAt >= :from)
              and (:to is null or result.analyzedAt < :to)
            order by result.analyzedAt asc, result.eventTime asc, result.id asc
            """)
    List<PaintAnalysisResult> findDashboardRows(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    @Query("""
            select result.analyzedAt
            from PaintAnalysisResult paint
            join paint.analysisResult result
            where result.processCode = com.aims.assembly.domain.enums.ProcessCode.PAINT
              and result.analyzedAt is not null
            order by result.analyzedAt asc
            """)
    List<LocalDateTime> findDashboardAnalyzedAtValues();
}

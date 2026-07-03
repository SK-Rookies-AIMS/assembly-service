package com.aims.assembly.repository.analysis;

import com.aims.assembly.domain.assembly.AssemblyAnalysisResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AssemblyAnalysisResultRepository extends JpaRepository<AssemblyAnalysisResult, Long> {
    Optional<AssemblyAnalysisResult> findByAnalysisResult_AnalysisId(String analysisId);

    @Query("""
            select assembly
            from AssemblyAnalysisResult assembly
            join fetch assembly.analysisResult result
            where result.processCode = com.aims.assembly.domain.enums.ProcessCode.ASSEMBLY
              and (:from is null or result.analyzedAt >= :from)
              and (:to is null or result.analyzedAt < :to)
            order by result.analyzedAt desc, result.eventTime desc, result.id desc
            """)
    List<AssemblyAnalysisResult> findDashboardRows(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    @Query("""
            select result.analyzedAt
            from AssemblyAnalysisResult assembly
            join assembly.analysisResult result
            where result.processCode = com.aims.assembly.domain.enums.ProcessCode.ASSEMBLY
              and result.analyzedAt is not null
            order by result.analyzedAt asc
            """)
    List<LocalDateTime> findDashboardAnalyzedAtValues();
}

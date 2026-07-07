package com.aims.assembly.repository.analysis;

import com.aims.assembly.domain.press.PressAnalysisResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PressAnalysisResultRepository extends JpaRepository<PressAnalysisResult, Long> {
    Optional<PressAnalysisResult> findByAnalysisResult_AnalysisId(String analysisId);

    @Query("""
            select press
            from PressAnalysisResult press
            join fetch press.analysisResult analysis
            where analysis.eventId in :eventIds
            """)
    List<PressAnalysisResult> findDashboardByEventIds(
            @Param("eventIds") List<String> eventIds
    );

    @Query("""
            select press
            from PressAnalysisResult press
            join fetch press.analysisResult analysis
            where analysis.eventTime between :from and :to
            order by analysis.eventTime desc, analysis.id desc
            """)
    List<PressAnalysisResult> findDashboardByEventTimeBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    @Query("""
            select press
            from PressAnalysisResult press
            join fetch press.analysisResult analysis
            order by analysis.eventTime desc, analysis.id desc
            """)
    List<PressAnalysisResult> findRecentDashboard(Pageable pageable);

    @Query("""
            select max(analysis.riskScore)
            from PressAnalysisResult press
            join press.analysisResult analysis
            where analysis.eventTime between :from and :to
            """)
    Double findMaxRiskScoreByEventTimeBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
    SELECT
        DATE(ar.eventTime) AS date,
        MIN(ar.eventId) AS sampleEventId
    FROM PressAnalysisResult par
    JOIN par.analysisResult ar
    WHERE ar.eventTime IS NOT NULL
    GROUP BY DATE(ar.eventTime)
    ORDER BY DATE(ar.eventTime) DESC
""")
    List<PressDateOptionProjection> findPressAnalysisDateOptions();

    interface PressDateOptionProjection {
        LocalDate getDate();
        String getSampleEventId();
    }
}

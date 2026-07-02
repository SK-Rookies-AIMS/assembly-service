package com.aims.assembly.repository.analysis;

import com.aims.assembly.domain.body.BodyAnalysisResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BodyAnalysisResultRepository extends JpaRepository<BodyAnalysisResult, Long> {
    Optional<BodyAnalysisResult> findByAnalysisResult_AnalysisId(String analysisId);

    @Query("""
            select body
            from BodyAnalysisResult body
            join fetch body.analysisResult analysis
            where analysis.eventTime between :from and :to
            order by analysis.eventTime desc, analysis.id desc
            """)
    List<BodyAnalysisResult> findDashboardByEventTimeBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    @Query("""
    SELECT
        DATE(ar.eventTime) AS date,
        MIN(ar.eventId) AS sampleEventId
    FROM BodyAnalysisResult bar
    JOIN bar.analysisResult ar
    WHERE ar.eventTime IS NOT NULL
    GROUP BY DATE(ar.eventTime)
    ORDER BY DATE(ar.eventTime) DESC
""")
    List<BodyDateOptionProjection> findBodyAnalysisDateOptions();

    @Query("""
            select body
            from BodyAnalysisResult body
            join fetch body.analysisResult analysis
            where analysis.eventId = :eventId
            """)
    Optional<BodyAnalysisResult> findByEventId(@Param("eventId") String eventId);

    interface BodyDateOptionProjection {
        LocalDate getDate();
        String getSampleEventId();
    }
}

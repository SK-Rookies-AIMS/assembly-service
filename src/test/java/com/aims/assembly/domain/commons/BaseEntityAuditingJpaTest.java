package com.aims.assembly.domain.commons;

import com.aims.assembly.config.JpaAuditingConfig;
import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.domain.enums.Severity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.jdbc.time_zone=Asia/Seoul"
})
@Import(JpaAuditingConfig.class)
class BaseEntityAuditingJpaTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void manufacturingAnalysisResultAuditingUsesAsiaSeoulTime() {
        LocalDateTime beforeSave = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        ManufacturingAnalysisResult result = ManufacturingAnalysisResult.builder()
                .analysisId("ANL-AUDIT-KST")
                .eventId("EVT-AUDIT-KST")
                .carMasterId(1L)
                .equipmentId(1L)
                .processCode(ProcessCode.PAINT)
                .eventTime(LocalDateTime.of(2026, 7, 7, 10, 47, 15))
                .isAbnormal(false)
                .severity(Severity.NORMAL)
                .riskScore(0.0)
                .analysisMessage("audit test")
                .analyzedAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")))
                .build();

        entityManager.persist(result);
        entityManager.flush();
        entityManager.clear();
        LocalDateTime afterSave = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        ManufacturingAnalysisResult saved = entityManager.find(
                ManufacturingAnalysisResult.class,
                result.getId()
        );

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isBetween(beforeSave, afterSave);
        assertThat(saved.getUpdatedAt()).isBetween(beforeSave, afterSave);
        assertThat(Math.abs(Duration.between(saved.getAnalyzedAt(), saved.getCreatedAt()).toHours()))
                .isLessThan(9);
    }
}

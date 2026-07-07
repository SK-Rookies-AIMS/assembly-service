package com.aims.assembly.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.auditing.DateTimeProvider;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class JpaAuditingConfigTest {

    @Test
    void auditingDateTimeProviderUsesAsiaSeoulLocalDateTime() {
        JpaAuditingConfig config = new JpaAuditingConfig();
        DateTimeProvider provider = config.auditingDateTimeProvider();

        LocalDateTime before = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        LocalDateTime provided = LocalDateTime.from(provider.getNow().orElseThrow());
        LocalDateTime after = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        assertThat(provided).isBetween(before, after);
    }
}

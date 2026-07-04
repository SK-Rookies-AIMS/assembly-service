package com.aims.assembly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.TimeZone;

@EnableScheduling
@EnableJpaAuditing(dateTimeProviderRef = "koreaDateTimeProvider")
@ConfigurationPropertiesScan
@SpringBootApplication
public class AssemblyApplication {

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone(KOREA_ZONE));
		SpringApplication.run(AssemblyApplication.class, args);
	}

	@Bean
	public org.springframework.data.auditing.DateTimeProvider koreaDateTimeProvider() {
		return () -> Optional.of(LocalDateTime.now(KOREA_ZONE));
	}

}

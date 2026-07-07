package com.aims.assembly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.ZoneId;
import java.util.TimeZone;

@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
public class AssemblyApplication {

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone(KOREA_ZONE));
		SpringApplication.run(AssemblyApplication.class, args);
	}
}

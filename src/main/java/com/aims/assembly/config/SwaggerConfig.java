package com.aims.assembly.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

	@Bean
	public OpenAPI openAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("AIMS 제조 서비스 API")
						.description("AIMS 제조 서비스 API documentation")
						.version("v1"))
				.addServersItem(new Server().url("/"));
	}

	@Bean
	public GroupedOpenApi assemblyApi() {
		return GroupedOpenApi.builder()
				.group("assembly")
				.pathsToMatch("/api/**")
				.build();
	}
}

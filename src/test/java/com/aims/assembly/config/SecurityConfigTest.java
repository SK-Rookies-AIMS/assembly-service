package com.aims.assembly.config;

import com.aims.assembly.config.jwt.TokenProvider;
import com.aims.assembly.config.security.JsonAccessDeniedHandler;
import com.aims.assembly.config.security.JsonAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityConfigTest {

    @Test
    void processDashboardEndpointsArePublicAndOtherApiRequiresAuthentication() throws Exception {
        MockMvc mockMvc = mockMvc();

        mockMvc.perform(get("/api/process/equipment/operation-rate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
        mockMvc.perform(get("/api/process/paint"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/process/assembly"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/private"))
                .andExpect(status().isUnauthorized());
    }

    private MockMvc mockMvc() {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestSecurityConfig.class);
        context.refresh();
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Configuration
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class TestSecurityConfig {

        @Bean
        TokenProvider tokenProvider() {
            return mock(TokenProvider.class);
        }

        @Bean
        JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint() {
            return new JsonAuthenticationEntryPoint(new ObjectMapper());
        }

        @Bean
        JsonAccessDeniedHandler jsonAccessDeniedHandler() {
            return new JsonAccessDeniedHandler(new ObjectMapper());
        }

        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    @RestController
    static class TestController {

        @GetMapping("/api/process/equipment/operation-rate")
        String equipmentOperationRate() {
            return "{\"data\":{\"items\":[]}}";
        }

        @GetMapping("/api/process/paint")
        String paint() {
            return "{\"data\":{}}";
        }

        @GetMapping("/api/process/assembly")
        String assembly() {
            return "{\"data\":{}}";
        }

        @GetMapping("/api/private")
        String privateApi() {
            return "{\"data\":{}}";
        }
    }
}

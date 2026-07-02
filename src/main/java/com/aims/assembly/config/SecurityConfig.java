package com.aims.assembly.config;

import com.aims.assembly.config.jwt.JwtAuthenticationFilter;
import com.aims.assembly.config.jwt.TokenProvider;
import com.aims.assembly.config.security.JsonAccessDeniedHandler;
import com.aims.assembly.config.security.JsonAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@EnableWebSecurity
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final TokenProvider tokenProvider;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/process/events/**").permitAll()
                        .requestMatchers("/api/process/sample").permitAll()
                        .requestMatchers("/api/process/equipment/operation-rate").permitAll()
                        .requestMatchers("/api/process/equipment/**").permitAll()
                        .requestMatchers("/api/process/press/**").permitAll()
                        .requestMatchers("/api/process/body/**").permitAll()
                        .requestMatchers("/api/process/paint").permitAll()
                        .requestMatchers("/api/process/paint/dates").permitAll()
                        .requestMatchers("/api/process/assembly").permitAll()
                        .requestMatchers("/api/process/assembly/dates").permitAll()
                        .requestMatchers("/api/process/equipment/operation-rate").permitAll()
                        .requestMatchers("/api/kafka/manufacturing/**").permitAll()
                        .requestMatchers(
                                "/",
                                "/api/process/health",
                                "/actuator/health/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(tokenProvider);
    }
}

package com.aims.assembly.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 빈 Map을 null로 직렬화하지 않도록 설정
        mapper.disable(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS);
        return mapper;
    }
}
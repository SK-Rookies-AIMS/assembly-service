package com.aims.assembly.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class QueryDSLConfig {

    @PersistenceContext(unitName = "main")
    private EntityManager mainEntityManager;

    @PersistenceContext(unitName = "sample")
    private EntityManager sampleEntityManager;

    @Primary
    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(mainEntityManager);
    }

    @Bean
    public JPAQueryFactory sampleJpaQueryFactory() {
        return new JPAQueryFactory(sampleEntityManager);
    }
}

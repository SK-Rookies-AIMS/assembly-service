package com.aims.assembly.config;

import com.aims.assembly.properties.AppDataSourceProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@RequiredArgsConstructor
public class DataSourceConfig {

    private final AppDataSourceProperties appDataSourceProperties;

    @Primary
    @Bean(name = "mainDataSource")
    public DataSource mainDataSource() {
        return createDataSource(appDataSourceProperties.getMain());
    }

    @Bean(name = "sampleDataSource")
    public DataSource sampleDataSource() {
        return createDataSource(appDataSourceProperties.getSample());
    }

    @Primary
    @Bean(name = "mainJdbcTemplate")
    public JdbcTemplate mainJdbcTemplate(@Qualifier("mainDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "sampleJdbcTemplate")
    public JdbcTemplate sampleJdbcTemplate(@Qualifier("sampleDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    private HikariDataSource createDataSource(AppDataSourceProperties.Database properties) {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName(properties.getDriverClassName());
        config.setJdbcUrl(properties.getJdbcUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setMaximumPoolSize(properties.getMaximumPoolSize());
        config.setMinimumIdle(properties.getMinimumIdle());

        if (properties.getPoolName() != null && !properties.getPoolName().isBlank()) {
            config.setPoolName(properties.getPoolName());
        }

        return new HikariDataSource(config);
    }
}

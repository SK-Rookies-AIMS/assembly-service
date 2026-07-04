package com.aims.assembly.config;

import com.aims.assembly.domain.car.CarMaster;
import com.aims.assembly.domain.equipment.Equipment;
import com.aims.assembly.domain.event.ManufacturingEventJson;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class JpaConfig {

    private static final String DOMAIN_PACKAGE = "com.aims.assembly.domain";
    private static final String KOREA_TIME_ZONE = "Asia/Seoul";

    private static final List<String> SAMPLE_ENTITY_CLASS_NAMES = List.of(
            CarMaster.class.getName(),
            Equipment.class.getName(),
            ManufacturingEventJson.class.getName()
    );

    @Value("${spring.jpa.hibernate.ddl-auto:none}")
    private String ddlAuto;

    @Value("${spring.jpa.properties.hibernate.format_sql:false}")
    private String formatSql;

    @Primary
    @Bean(name = "entityManagerFactory")
    public LocalContainerEntityManagerFactoryBean mainEntityManagerFactory(
            @Qualifier("mainDataSource") DataSource dataSource
    ) {
        LocalContainerEntityManagerFactoryBean factoryBean = entityManagerFactory(dataSource, "main");
        factoryBean.setPackagesToScan(DOMAIN_PACKAGE);
        factoryBean.setManagedClassNameFilter(className -> !SAMPLE_ENTITY_CLASS_NAMES.contains(className));
        return factoryBean;
    }

    @Bean(name = "sampleEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean sampleEntityManagerFactory(
            @Qualifier("sampleDataSource") DataSource dataSource
    ) {
        LocalContainerEntityManagerFactoryBean factoryBean = entityManagerFactory(dataSource, "sample");
        factoryBean.setManagedTypes(PersistenceManagedTypes.of(SAMPLE_ENTITY_CLASS_NAMES, List.of()));
        return factoryBean;
    }

    @Primary
    @Bean(name = "transactionManager")
    public PlatformTransactionManager mainTransactionManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory
    ) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean(name = "sampleTransactionManager")
    public PlatformTransactionManager sampleTransactionManager(
            @Qualifier("sampleEntityManagerFactory") EntityManagerFactory entityManagerFactory
    ) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    private LocalContainerEntityManagerFactoryBean entityManagerFactory(
            DataSource dataSource,
            String persistenceUnitName
    ) {
        LocalContainerEntityManagerFactoryBean factoryBean = new LocalContainerEntityManagerFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setPersistenceUnitName(persistenceUnitName);
        factoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factoryBean.setJpaPropertyMap(jpaProperties());
        return factoryBean;
    }

    private Map<String, Object> jpaProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", ddlAuto);
        properties.put("hibernate.format_sql", formatSql);
        properties.put("hibernate.jdbc.time_zone", KOREA_TIME_ZONE);
        return properties;
    }
}

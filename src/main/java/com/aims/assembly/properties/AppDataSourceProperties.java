package com.aims.assembly.properties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.datasource")
public class AppDataSourceProperties {

    private Database main = new Database();
    private Database sample = new Database();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Database {

        private String driverClassName;
        private String jdbcUrl;
        private String username;
        private String password;
        private int maximumPoolSize = 10;
        private int minimumIdle = 2;
        private String poolName;
    }
}

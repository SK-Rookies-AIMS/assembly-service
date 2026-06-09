package com.aims.assembly.config.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secretKey;
    private Expiration expiration = new Expiration();

    @Getter
    @Setter
    public static class Expiration {

        private long access = 3_600_000L;
        private long refresh = 1_209_600_000L;
    }
}

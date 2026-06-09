package com.aims.assembly.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.redis.cache")
public class RedisCacheProperties {

    private Duration ttl = Duration.ofMinutes(10);
}

package com.aims.assembly.config;

import com.aims.assembly.properties.RedisCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnection;
import java.time.Duration;

@Slf4j
@EnableCaching
@Configuration
@RequiredArgsConstructor
public class RedisCacheConfig implements CachingConfigurer {

    private final RedisCacheProperties redisCacheProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void checkRedisConnection(ApplicationReadyEvent event) {
        try {
            RedisConnectionFactory factory = event.getApplicationContext().getBean(RedisConnectionFactory.class);
            RedisConnection connection = factory.getConnection();
            String ping = connection.ping();
            log.info("Redis connection successful! Ping response: {}", ping);
            connection.close();
        } catch (Exception e) {
            log.error("Failed to connect to Redis: {}", e.getMessage());
            
            // Log environment variables to see if .env is loaded
            String redisHost = event.getApplicationContext().getEnvironment().getProperty("spring.data.redis.host");
            String redisPort = event.getApplicationContext().getEnvironment().getProperty("spring.data.redis.port");
            String oldRedisHost = event.getApplicationContext().getEnvironment().getProperty("spring.redis.host");
            String oldRedisPort = event.getApplicationContext().getEnvironment().getProperty("spring.redis.port");
            log.error("Configured properties - spring.data.redis: {}:{}, spring.redis: {}:{}", redisHost, redisPort, oldRedisHost, oldRedisPort);
        }
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(redisCacheProperties.getTtl())
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisJsonSerializer()));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(cacheConfig)
                .withCacheConfiguration("press-anomaly-dashboard-v2", cacheConfig)
                .withCacheConfiguration("body-anomaly-dashboard-v2", cacheConfig)
                .withCacheConfiguration("process-paint-dashboard-v1",
                        cacheConfig.entryTtl(Duration.ofMinutes(3)))
                .withCacheConfiguration("process-assembly-dashboard-v1",
                        cacheConfig.entryTtl(Duration.ofMinutes(3)))
                .withCacheConfiguration("process-paint-dates-v1",
                        cacheConfig.entryTtl(Duration.ofMinutes(10)))
                .withCacheConfiguration("process-assembly-dates-v1",
                        cacheConfig.entryTtl(Duration.ofMinutes(10)))
                .withCacheConfiguration("process-equipment-operation-rate-v1",
                        cacheConfig.entryTtl(Duration.ofSeconds(20)))
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis cache get failed. cache={}, key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Redis cache put failed. cache={}, key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis cache evict failed. cache={}, key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Redis cache clear failed. cache={}", cache.getName(), exception);
            }
        };
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(redisJsonSerializer());
        redisTemplate.setHashValueSerializer(redisJsonSerializer());
        return redisTemplate;
    }

    private GenericJacksonJsonRedisSerializer redisJsonSerializer() {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.aims.assembly.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.util.")
                .build();

        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(typeValidator)
                .build();
    }
}

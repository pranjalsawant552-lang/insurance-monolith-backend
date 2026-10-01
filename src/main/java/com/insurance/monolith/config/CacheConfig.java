package com.insurance.monolith.config;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.time.Duration;

/**
 * MONOLITH CONVERSION NOTE: auth-service and product-service each used
 * Spring's single global `spring.cache.redis.time-to-live` property, but
 * with two *different* values (auth wanted 15 minutes on its
 * "rolePermissions" cache; product wanted 1 hour on its "products" cache).
 * That property can only hold one value for the whole app, so merging it
 * naively would have silently given one of the two modules the wrong TTL.
 * This customizer restores both original TTLs, per cache name, explicitly.
 */
@Configuration
public class CacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return builder -> builder
                .withCacheConfiguration("rolePermissions",
                        RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(15)))
                .withCacheConfiguration("products",
                        RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofHours(1)));
    }
}

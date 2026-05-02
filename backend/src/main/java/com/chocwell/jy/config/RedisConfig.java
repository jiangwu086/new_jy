package com.chocwell.jy.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 缓存配置
 *
 * 缓存命名规范：
 *   locations  — 教育点位列表（TTL 10 分钟）
 *   articles   — 文章列表    （TTL 5  分钟）
 *   gifts      — 礼品列表    （TTL 5  分钟）
 *
 * 启用方式：在需要缓存的 Service 方法上加 @Cacheable / @CacheEvict
 *
 * 实现说明：
 *   使用 Spring Data Redis 自带的 GenericJackson2JsonRedisSerializer。
 *   它内部使用 WRAPPER_ARRAY 类型注入策略（["全限定类名", 真实值]），
 *   既能正确还原 List<T> / Map<K,V> 等泛型容器内每个元素的具体类型，
 *   又不会与 JavaTimeModule 的 LocalDateTime 字符串序列化方式冲突，
 *   是缓存任意 POJO 的稳妥方案。
 */
@EnableCaching
@Configuration
public class RedisConfig {

    /**
     * 构建支持 LocalDateTime 序列化的 ObjectMapper
     * 类型注入策略由 GenericJackson2JsonRedisSerializer 内部统一配置，这里不再重复设置。
     */
    private ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * 默认缓存配置（所有未单独配置的 cache 使用此 TTL）
     */
    private RedisCacheConfiguration defaultConfig() {
        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(buildObjectMapper());

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))       // 默认 10 分钟
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(serializer))
                .disableCachingNullValues();             // 不缓存 null，防止缓存穿透
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 各 cache 独立 TTL 配置
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("locations", defaultConfig().entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put("articles",  defaultConfig().entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put("gifts",     defaultConfig().entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig())
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}

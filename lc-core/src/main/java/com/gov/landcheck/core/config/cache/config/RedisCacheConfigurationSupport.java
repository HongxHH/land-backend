package com.gov.landcheck.core.config.cache.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gov.landcheck.core.config.cache.constant.CacheNames;

@Configuration
@EnableCaching
public class RedisCacheConfigurationSupport {

        /**
         * 供 HTTP 等使用的默认 ObjectMapper（不含 default typing，不要求 JSON 带 @class）。
         * 标记为 @Primary，避免 Redis 专用的 cacheObjectMapper 被用于请求体反序列化。
         * <p>
         * 需要向 Redis 写入“纯 JSON”、与 {@code GenericJackson2JsonRedisSerializer} / default
         * typing 无关的模块应注入本 Bean，勿使用 {@link #cacheObjectMapper()}。
         */
        @Bean
        @Primary
        public ObjectMapper objectMapper() {
                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new JavaTimeModule());
                mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                return mapper;
        }

        /**
         * 仅用于 Redis 序列化/反序列化的 ObjectMapper，启用 default typing 以支持多态类型。
         * 通过 @Qualifier("cacheObjectMapper") 注入，不参与 HTTP 消息转换。
         */
        @Bean(name = "cacheObjectMapper")
        public ObjectMapper cacheObjectMapper() {
                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new JavaTimeModule());
                mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
                mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
                return mapper;
        }

        /**
         * 缓存用 RedisTemplate，必须使用带 default typing 的 cacheObjectMapper，
         * 否则反序列化会得到 LinkedHashMap 导致 ClassCastException。标记 @Primary 确保 CacheOpsService
         * 等注入此实例。
         */
        @Bean
        @Primary
        public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                        @Qualifier("cacheObjectMapper") ObjectMapper cacheObjectMapper) {
                RedisTemplate<String, Object> template = new RedisTemplate<>();
                template.setConnectionFactory(connectionFactory);
                StringRedisSerializer keySerializer = new StringRedisSerializer();
                GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(
                                cacheObjectMapper);
                template.setKeySerializer(keySerializer);
                template.setHashKeySerializer(keySerializer);
                template.setValueSerializer(valueSerializer);
                template.setHashValueSerializer(valueSerializer);
                template.afterPropertiesSet();
                return template;
        }

        @Bean
        public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory,
                        ObjectMapper cacheObjectMapper,
                        CacheProperties cacheProperties) {
                GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(
                                cacheObjectMapper);

                RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                                .serializeKeysWith(RedisSerializationContext.SerializationPair
                                                .fromSerializer(new StringRedisSerializer()))
                                .serializeValuesWith(
                                                RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                                .entryTtl(Duration.ofSeconds(cacheProperties.getDefaultTtlSeconds()))
                                .disableCachingNullValues();

                Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
                cacheConfigs.put(CacheNames.PROJECT_BY_ID,
                                defaultConfig.entryTtl(
                                                Duration.ofSeconds(cacheProperties.getTtl().getProject().getById())));
                cacheConfigs.put(CacheNames.PROJECT_CONTRACTS_BY_PROJECT,
                                defaultConfig.entryTtl(Duration
                                                .ofSeconds(cacheProperties.getTtl().getProject().getByRelation())));
                cacheConfigs.put(CacheNames.PROJECT_CONTRACT_WITH_PARCELS,
                                defaultConfig.entryTtl(Duration
                                                .ofSeconds(cacheProperties.getTtl().getProject().getByRelation())));
                cacheConfigs.put(CacheNames.PROJECT_PARSED_REPORTS_BY_PROJECT,
                                defaultConfig.entryTtl(Duration
                                                .ofSeconds(cacheProperties.getTtl().getProject().getByRelation())));
                cacheConfigs.put(CacheNames.PROJECT_ROOMS_BY_PROJECT_REPORT,
                                defaultConfig.entryTtl(Duration
                                                .ofSeconds(cacheProperties.getTtl().getProject().getByRelation())));
                cacheConfigs.put(CacheNames.PROJECT_ALL_LIST,
                                defaultConfig.entryTtl(Duration
                                                .ofSeconds(cacheProperties.getTtl().getProject().getByRelation())));
                cacheConfigs.put(CacheNames.PROJECT_QUERY_PROJECTS,
                                defaultConfig.entryTtl(
                                                Duration.ofSeconds(cacheProperties.getTtl().getProject().getQuery())));
                cacheConfigs.put(CacheNames.PROJECT_QUERY_CONTRACTS,
                                defaultConfig.entryTtl(
                                                Duration.ofSeconds(cacheProperties.getTtl().getProject().getQuery())));
                cacheConfigs.put(CacheNames.PROJECT_QUERY_SURVEY_REPORTS,
                                defaultConfig.entryTtl(
                                                Duration.ofSeconds(cacheProperties.getTtl().getProject().getQuery())));
                cacheConfigs.put(CacheNames.PROJECT_QUERY_ROOMS,
                                defaultConfig.entryTtl(
                                                Duration.ofSeconds(cacheProperties.getTtl().getProject().getQuery())));

                return RedisCacheManager.builder(connectionFactory)
                                .cacheDefaults(defaultConfig)
                                .withInitialCacheConfigurations(cacheConfigs)
                                .build();
        }

        @Bean
        public CacheErrorHandler cacheErrorHandler() {
                return new CacheErrorHandler() {
                        @Override
                        public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                                // Fail-open: tolerate cache read failure.
                        }

                        @Override
                        public void handleCachePutError(RuntimeException exception, Cache cache, Object key,
                                        Object value) {
                                // Fail-open: tolerate cache write failure.
                        }

                        @Override
                        public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                                // Fail-open: tolerate cache evict failure.
                        }

                        @Override
                        public void handleCacheClearError(RuntimeException exception, Cache cache) {
                                // Fail-open: tolerate cache clear failure.
                        }
                };
        }
}

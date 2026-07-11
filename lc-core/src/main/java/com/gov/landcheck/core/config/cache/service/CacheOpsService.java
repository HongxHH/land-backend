package com.gov.landcheck.core.config.cache.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;

import com.gov.landcheck.core.config.cache.config.CacheProperties;
import com.gov.landcheck.core.config.cache.model.CacheLoadOptions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
/**
 * 统一封装 Redis 缓存读写逻辑的服务类，采用典型的旁路缓存（Cache-Aside）模式。
 * 设计要点：
 * 缓存永远只是加速手段：Redis 异常或未命中时会优雅回退到数据源（fail-open）。
 * 支持基于 Redis 的简单分布式锁，防止热点 key 在失效瞬间被高并发同时回源（缓存击穿）。
 * 在写入时对 TTL 施加抖动，降低大量 key 在同一时间集体失效导致的缓存雪崩风险。
 *
 */
public class CacheOpsService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheProperties cacheProperties;

    public CacheOpsService(RedisTemplate<String, Object> redisTemplate,
            CacheProperties cacheProperties) {
        this.redisTemplate = redisTemplate;
        this.cacheProperties = cacheProperties;
    }

    /**
     * 基于旁路缓存模式的统一读取入口。
     * 流程：先尝试从 Redis 读取；命中则直接返回；未命中则通过 {@code loader} 从数据源加载并按策略写回缓存。
     * 注意：任何 Redis 相关异常都只会记录日志并回退到数据源，避免缓存故障影响主业务链路。
     */
    public <T> T getOrLoad(String key, CacheLoadOptions options, Supplier<T> loader) {
        if (!cacheProperties.isEnabled()) {
            return loader.get();
        }
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return cast(cached);
            }
        } catch (Exception ex) {
            // 读缓存失败时选择 fail-open：仅记录日志，直接走后端数据源，保证可用性优先于缓存一致性
            log.warn("Redis read failed for key={}, fallback to DB", key, ex);
            return loader.get();
        }

        if (options.isUseLock()) {
            // 对热点 key 允许使用分布式锁串行化加载，缓解缓存击穿时的大量并发回源
            return loadWithLock(key, options, loader);
        }
        // 无锁场景下直接回源一次并尝试写入缓存
        T value = loader.get();
        writeIfNeeded(key, value, options);
        return value;
    }

    /**
     * 写入单个缓存 key，应用侧仍然是「写库 + 失效缓存」为主，直接写缓存主要用于「读后回填」场景。
     */
    public void put(String key, Object value, long ttlSeconds) {
        if (!cacheProperties.isEnabled()) {
            return;
        }
        if (value == null) {
            return;
        }
        try {
            // TTL 上叠加随机抖动，避免大量 key 在同一时间点同时过期造成流量尖峰
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(applyJitter(ttlSeconds)));
        } catch (Exception ex) {
            log.error("Redis write failed for key={}", key, ex);
        }
    }

    /**
     * 精确删除单个 key，对应单条记录或聚合结果的直接失效。
     */
    public void evict(String key) {
        if (!cacheProperties.isEnabled()) {
            return;
        }
        try {
            redisTemplate.delete(key);
        } catch (Exception ex) {
            log.error("Redis evict failed for key={}", key, ex);
        }
    }

    /**
     * 基于通配符 pattern 的批量删除，通常用于查询结果集等一类缓存的整体失效。
     * 这里使用 SCAN 游标分批删除，避免 KEYS 的全量阻塞问题。
     */
    public void evictByPattern(String pattern) {
        if (!cacheProperties.isEnabled()) {
            return;
        }
        try {
            var keys = scanKeys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ex) {
            log.error("Redis evict by pattern failed for pattern={}", pattern, ex);
        }
    }

    @SuppressWarnings("deprecation")
    private List<String> scanKeys(String pattern) {
        return redisTemplate.execute((RedisConnection connection) -> {
            List<String> keys = new ArrayList<>();
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(500).build();
            RedisSerializer<String> keySerializer = redisTemplate.getStringSerializer();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    byte[] keyBytes = cursor.next();
                    String key = keySerializer.deserialize(keyBytes);
                    if (key != null && !key.isBlank()) {
                        keys.add(key);
                    }
                }
            }
            return keys;
        });
    }

    /**
     * 使用简单的 Redis 分布式锁防止缓存击穿。
     * 核心思路：多个实例竞争获取 {@code key:lock}；只有获取到锁的线程回源并回填缓存，其余线程等待期间优先读取已填充的缓存。
     */
    private <T> T loadWithLock(String key, CacheLoadOptions options, Supplier<T> loader) {
        String lockKey = key + ":lock";
        String lockValue = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(options.getLockWaitMs());
        boolean locked = false;
        try {
            // 在可配置的等待窗口内自旋获取锁，期间不断检查缓存是否已被其它线程填充
            while (System.nanoTime() < deadline) {
                Boolean acquired = redisTemplate.opsForValue()
                        .setIfAbsent(lockKey, lockValue, options.getLockLeaseMs(), TimeUnit.MILLISECONDS);
                if (Boolean.TRUE.equals(acquired)) {
                    locked = true;
                    break;
                }
                // 未抢到锁时优先尝试直接读缓存，若其它线程已填充则立即复用结果避免多次回源
                Object cached = redisTemplate.opsForValue().get(key);
                if (cached != null) {
                    return cast(cached);
                }
                // 简单小睡一会儿再重试获取锁，避免紧密自旋压垮 Redis
                Thread.sleep(10L);
            }
            T value = loader.get();
            writeIfNeeded(key, value, options);
            return value;
        } catch (Exception ex) {
            log.warn("Redis lock load failed for key={}, fallback to DB", key, ex);
            return loader.get();
        } finally {
            if (locked) {
                try {
                    Object current = redisTemplate.opsForValue().get(lockKey);
                    if (Objects.equals(current, lockValue)) {
                        // 仅在仍然持有自己写入的锁值时删除锁，避免误删其它线程刚刚续约/接手的锁
                        redisTemplate.delete(lockKey);
                    }
                } catch (Exception ex) {
                    log.warn("Release lock failed for key={}", lockKey, ex);
                }
            }
        }
    }

    /**
     * 根据配置决定是否写入缓存：
     * <ul>
     * <li>当结果为 {@code null} 且不允许缓存空值时，直接跳过写入，避免占用空间与增加一致性风险。</li>
     * <li>否则按统一 TTL 策略写入缓存。</li>
     * </ul>
     */
    private <T> void writeIfNeeded(String key, T value, CacheLoadOptions options) {
        if (value == null && !options.isCacheNullValue()) {
            return;
        }
        put(key, value, options.getTtlSeconds());
    }

    /**
     * 在基础 TTL 上追加随机抖动，使不同 key 的过期时间分散在一个区间内。
     * <p>
     * 典型用途是防止「大量 key 在同一秒过期」引发的瞬时回源洪峰。
     * </p>
     */
    private long applyJitter(long ttlSeconds) {
        if (ttlSeconds <= 1L) {
            return ttlSeconds;
        }
        double ratio = Math.max(0D, cacheProperties.getJitterRatio());
        long jitter = (long) (ttlSeconds * ratio * Math.random());
        return Math.max(1L, ttlSeconds + jitter);
    }

    @SuppressWarnings("unchecked")
    private <T> T cast(Object object) {
        return (T) object;
    }
}

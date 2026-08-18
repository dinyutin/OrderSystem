package com.example.ordersystem.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RedisService {
    private static final Logger log = LoggerFactory.getLogger(RedisService.class);
    private final StringRedisTemplate redisTemplate;
    private final Counter failures;

    public RedisService(StringRedisTemplate redisTemplate, MeterRegistry registry) {
        this.redisTemplate = redisTemplate;
        this.failures = registry.counter("redis.cache.failures");
    }

    public Integer getStock(long productId) {
        try {
            String value = redisTemplate.opsForValue().get(stockKey(productId));
            return value == null ? null : Integer.valueOf(value);
        } catch (RuntimeException exception) {
            failures.increment();
            log.warn("Redis read failed; falling back to MySQL: productId={}", productId);
            return null;
        }
    }

    public void setStock(long productId, int stock) {
        try {
            // Jitter prevents many hot keys expiring at exactly the same time.
            Duration ttl = Duration.ofMinutes(ThreadLocalRandom.current().nextLong(25, 36));
            redisTemplate.opsForValue().set(stockKey(productId), String.valueOf(stock), ttl);
        } catch (RuntimeException exception) {
            failures.increment();
            log.warn("Redis write failed; MySQL remains source of truth: productId={}", productId);
        }
    }

    private String stockKey(long productId) {
        return "product:stock:" + productId;
    }
}

package com.example.ordersystem.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import com.example.ordersystem.dto.AsyncOrderResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RedisService {
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
            local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
            if stock < tonumber(ARGV[1]) then return 0 end
            redis.call('DECRBY', KEYS[1], ARGV[1])
            redis.call('SET', KEYS[2], '1', 'EX', 900)
            redis.call('SET', KEYS[3], 'PROCESSING', 'EX', 900)
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> COMPENSATE = new DefaultRedisScript<>("""
            if redis.call('DEL', KEYS[2]) == 1 then redis.call('INCRBY', KEYS[1], ARGV[1]) end
            redis.call('SET', KEYS[3], ARGV[2], 'EX', 900)
            return 1
            """, Long.class);
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

    public boolean reserve(long productId, int quantity, String requestId) {
        Long result = redisTemplate.execute(RESERVE,
                List.of(stockKey(productId), reservationKey(requestId), requestStatusKey(requestId)),
                String.valueOf(quantity));
        return Long.valueOf(1).equals(result);
    }

    public void compensate(long productId, int quantity, String requestId, String status) {
        redisTemplate.execute(COMPENSATE,
                List.of(stockKey(productId), reservationKey(requestId), requestStatusKey(requestId)),
                String.valueOf(quantity), status);
    }

    public void completeRequest(String requestId, String orderId) {
        redisTemplate.opsForValue().set(requestStatusKey(requestId), "RESERVED", Duration.ofMinutes(15));
        redisTemplate.opsForValue().set(requestOrderKey(requestId), orderId, Duration.ofMinutes(15));
        redisTemplate.delete(reservationKey(requestId));
    }

    public AsyncOrderResponse getRequest(String requestId) {
        String status = redisTemplate.opsForValue().get(requestStatusKey(requestId));
        if (status == null) return new AsyncOrderResponse(requestId, "NOT_FOUND", null, "找不到訂單請求");
        String orderId = redisTemplate.opsForValue().get(requestOrderKey(requestId));
        return new AsyncOrderResponse(requestId, status, orderId, null);
    }

    private String stockKey(long productId) {
        return "product:stock:" + productId;
    }

    private String reservationKey(String requestId) { return "order:reservation:" + requestId; }
    private String requestStatusKey(String requestId) { return "order:request:" + requestId + ":status"; }
    private String requestOrderKey(String requestId) { return "order:request:" + requestId + ":order-id"; }
}

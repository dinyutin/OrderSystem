package com.example.ordersystem.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisService {
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private final StringRedisTemplate redisTemplate;

    public RedisService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Integer getStock(long productId) {
        String value = redisTemplate.opsForValue().get(stockKey(productId));
        return value == null ? null : Integer.valueOf(value);
    }

    public void setStock(long productId, int stock) {
        redisTemplate.opsForValue().set(stockKey(productId), String.valueOf(stock), CACHE_TTL);
    }

    private String stockKey(long productId) {
        return "product:stock:" + productId;
    }
}

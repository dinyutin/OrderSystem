package com.example.ordersystem.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RedisService {

    private final StringRedisTemplate redisTemplate;

    public RedisService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * **獲取庫存**
     */
    public Integer getStock(String key) {
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Integer.parseInt(value) : null;
    }

    /**
     * **設置庫存**
     */
    public void setStock(String key, int stock) {
        redisTemplate.opsForValue().set(key, String.valueOf(stock), 10, TimeUnit.MINUTES);
    }

    /**
     * **安全扣減庫存**
     * - **確保不會變成負數**
     * - **庫存不足時，返回 false**
     */
    public boolean decrementStock(String key) {
        Integer stock = getStock(key);
        if (stock == null || stock <= 0) {
            System.out.println("Redis: 商品庫存不足，無法扣減");
            return false;
        }

        Long newStock = redisTemplate.opsForValue().decrement(key);
        if (newStock != null && newStock >= 0) {
            return true; // 扣減成功
        } else {
            // **如果庫存變成負數，回滾**
            redisTemplate.opsForValue().increment(key);
            System.out.println("Redis: 扣庫存失敗 (可能超賣)，回滾庫存");
            return false;
        }
    }

    /**
     * **恢復 Redis 商品庫存 (當 Kafka 失敗時回滾)**
     */
    public void incrementStock(String key) {
        redisTemplate.opsForValue().increment(key);
    }
}

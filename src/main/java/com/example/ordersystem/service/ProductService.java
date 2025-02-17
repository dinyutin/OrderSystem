package com.example.ordersystem.service;

import com.example.ordersystem.entity.ProductEntity;
import com.example.ordersystem.repository.ProductRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final RedisService redisService;
    private final KafkaProducerService kafkaProducerService;
    private final RedissonClient redissonClient;

    public ProductService(ProductRepository productRepository, RedisService redisService, KafkaProducerService kafkaProducerService, RedissonClient redissonClient) {
        this.productRepository = productRepository;
        this.redisService = redisService;
        this.kafkaProducerService = kafkaProducerService;
        this.redissonClient = redissonClient;
    }

    /**
     * **獲取商品庫存**
     */
    public int getStock(long productId) {
        String stockKey = "product_stock_" + productId;
        Integer stock = redisService.getStock(stockKey);
        if (stock != null) {
            return stock;
        }
        // **如果 Redis 沒有，就去 MySQL 讀取**
        Optional<ProductEntity> productOpt = productRepository.findById(productId);
        if (productOpt.isPresent()) {
            int currentStock = productOpt.get().getStock();
            redisService.setStock(stockKey, currentStock);//  更新 Redis
            return currentStock;
        }
        return 0;
    }
    public Optional<ProductEntity> findProductById(long productId) {
        return productRepository.findById(productId);
    }
    /**
     * **建立新商品**
     */
    public ProductEntity createProduct(String name, int stock) {
        // **先建立 ProductEntity**
        ProductEntity product = new ProductEntity(name, stock);

        // **先存入 MySQL，取得返回的已儲存物件**
        ProductEntity savedProduct = productRepository.save(product);

        // **同步初始化 Redis (用 savedProduct 的 ID)**
        String stockKey = "product_stock_" + savedProduct.getProductId();
        redisService.setStock(stockKey, stock);

        // **返回已存入的產品**
        return savedProduct;
    }

    /**
     * **使用 Redisson 分布式鎖 扣減庫存**
     */
    @Transactional
    public boolean reduceStock(long productId) {
        String lockKey = "lock_product_" + productId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                try {
                    int stock = getStock(productId);
                    if (stock > 0) {
                        boolean redisSuccess = redisService.decrementStock("product_stock_" + productId);
                        if (!redisSuccess) {
                            System.out.println("Redis 庫存扣減失敗，取消訂單");
                            return false;
                        }

                        boolean kafkaSuccess = kafkaProducerService.sendStockUpdate(productId, stock - 1);
                        if (!kafkaSuccess) {
                            System.out.println("Kafka 訊息發送失敗，恢復 Redis 庫存");
                            redisService.incrementStock("product_stock_" + productId);
                            return false;
                        }

                        return true;
                    } else {
                        System.out.println("庫存不足，無法扣減");
                        return false;
                    }
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }
}

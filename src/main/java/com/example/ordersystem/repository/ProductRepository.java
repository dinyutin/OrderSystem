package com.example.ordersystem.repository;

import com.example.ordersystem.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductEntity,  Long> {
    Optional<ProductEntity> findByProductId(long productId);
    @Modifying
    @Transactional
    @Query("UPDATE ProductEntity p SET p.stock = p.stock - 1 WHERE p.productId = :productId AND p.stock > 0")
    int updateStock(@Param("productId") long productId);
}

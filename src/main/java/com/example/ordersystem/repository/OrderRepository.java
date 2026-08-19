package com.example.ordersystem.repository;

import com.example.ordersystem.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    java.util.Optional<OrderEntity> findByOrderId(String orderId);
    java.util.Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderEntity o WHERE o.orderId = :orderId")
    java.util.Optional<OrderEntity> lockByOrderId(@Param("orderId") String orderId);

    @Query(value = "SELECT * FROM orders WHERE status = 'RESERVED' AND expires_at <= :now " +
            "ORDER BY expires_at LIMIT 100 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OrderEntity> lockExpiredReservations(@Param("now") Instant now);
}

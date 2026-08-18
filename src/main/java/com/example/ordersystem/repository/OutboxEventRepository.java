package com.example.ordersystem.repository;

import com.example.ordersystem.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {
    List<OutboxEventEntity> findTop100ByStatusAndNextAttemptAtLessThanEqualOrderById(
            String status, Instant now);
    long countByStatus(String status);
}

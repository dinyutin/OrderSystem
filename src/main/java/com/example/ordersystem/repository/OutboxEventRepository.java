package com.example.ordersystem.repository;

import com.example.ordersystem.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {
    @Query(value = "SELECT * FROM outbox_events WHERE status = 'PENDING' " +
            "AND next_attempt_at <= :now ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<OutboxEventEntity> lockNextBatch(Instant now);
    long countByStatus(String status);
}

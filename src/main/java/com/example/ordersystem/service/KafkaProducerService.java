package com.example.ordersystem.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    // 發送訂單消息到 Kafka
    public void sendOrder(String message) {
        kafkaTemplate.send("order-topic", message);
    }

    public boolean sendStockUpdate(long productId, int newStock) {
        try {
            String message = productId + ":" + newStock;
            kafkaTemplate.send("stock-update-topic", message);
            System.out.println(" Kafka 訊息已發送：" + message);
            return true; //  成功回傳 true
        } catch (Exception e) {
            System.out.println("Kafka 訊息發送失敗：" + e.getMessage());
            return false; // 失敗回傳 false
        }
    }
}

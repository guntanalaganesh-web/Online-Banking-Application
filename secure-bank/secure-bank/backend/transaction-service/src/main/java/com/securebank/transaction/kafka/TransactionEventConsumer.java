package com.securebank.transaction.kafka;

import com.securebank.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventConsumer {
    
    private final TransactionService transactionService;
    
    @KafkaListener(
            topics = "transaction-events",
            groupId = "transaction-processor",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleTransactionEvent(ConsumerRecord<String, Map<String, Object>> record, Acknowledgment ack) {
        try {
            Map<String, Object> event = record.value();
            String eventType = (String) event.get("eventType");
            
            log.info("Received transaction event: {} - {}", eventType, record.key());
            
            switch (eventType) {
                case "TRANSACTION_CREATED" -> {
                    String transactionId = (String) event.get("transactionId");
                    transactionService.processTransaction(UUID.fromString(transactionId));
                }
                case "RETRY_TRANSACTION" -> {
                    String transactionId = (String) event.get("transactionId");
                    log.info("Retrying transaction: {}", transactionId);
                    transactionService.processTransaction(UUID.fromString(transactionId));
                }
                default -> log.debug("Ignoring event type: {}", eventType);
            }
            
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing transaction event: {}", e.getMessage(), e);
            // In production, implement dead letter queue
            ack.acknowledge(); // Acknowledge to prevent infinite retry
        }
    }
    
    @KafkaListener(
            topics = "fraud-decisions",
            groupId = "transaction-fraud-handler",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleFraudDecision(ConsumerRecord<String, Map<String, Object>> record, Acknowledgment ack) {
        try {
            Map<String, Object> decision = record.value();
            String transactionId = (String) decision.get("transactionId");
            String decisionType = (String) decision.get("decision");
            
            log.info("Received fraud decision for {}: {}", transactionId, decisionType);
            
            if ("APPROVED".equals(decisionType)) {
                transactionService.processTransaction(UUID.fromString(transactionId));
            } else if ("REJECTED".equals(decisionType)) {
                // Handle rejection
                log.warn("Transaction {} rejected by fraud review", transactionId);
            }
            
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing fraud decision: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }
}

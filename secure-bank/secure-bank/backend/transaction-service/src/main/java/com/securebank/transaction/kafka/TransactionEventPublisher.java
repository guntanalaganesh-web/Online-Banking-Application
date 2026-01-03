package com.securebank.transaction.kafka;

import com.securebank.transaction.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventPublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final String TRANSACTION_TOPIC = "transaction-events";
    private static final String FRAUD_ALERT_TOPIC = "fraud-alerts";
    private static final String NOTIFICATION_TOPIC = "notifications";
    
    public void publishTransactionCreated(Transaction transaction) {
        Map<String, Object> event = buildEvent("TRANSACTION_CREATED", transaction);
        sendEvent(TRANSACTION_TOPIC, transaction.getReferenceNumber(), event);
    }
    
    public void publishTransactionCompleted(Transaction transaction) {
        Map<String, Object> event = buildEvent("TRANSACTION_COMPLETED", transaction);
        sendEvent(TRANSACTION_TOPIC, transaction.getReferenceNumber(), event);
        
        // Send notification
        sendNotification(transaction, "Transaction Completed", 
                String.format("Your transaction of %s %s has been completed. Reference: %s",
                        transaction.getCurrency(), transaction.getAmount(), transaction.getReferenceNumber()));
    }
    
    public void publishTransactionFailed(Transaction transaction) {
        Map<String, Object> event = buildEvent("TRANSACTION_FAILED", transaction);
        event.put("failureReason", transaction.getFailureReason());
        sendEvent(TRANSACTION_TOPIC, transaction.getReferenceNumber(), event);
        
        // Send notification
        sendNotification(transaction, "Transaction Failed",
                String.format("Your transaction %s has failed. Reason: %s",
                        transaction.getReferenceNumber(), transaction.getFailureReason()));
    }
    
    public void publishFraudAlert(Transaction transaction) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("eventType", "FRAUD_ALERT");
        alert.put("transactionId", transaction.getId().toString());
        alert.put("referenceNumber", transaction.getReferenceNumber());
        alert.put("amount", transaction.getAmount());
        alert.put("sourceAccount", transaction.getSourceAccountNumber());
        alert.put("destinationAccount", transaction.getDestinationAccountNumber());
        alert.put("fraudScore", transaction.getFraudScore());
        alert.put("timestamp", LocalDateTime.now().toString());
        alert.put("priority", transaction.getFraudScore().doubleValue() > 0.8 ? "HIGH" : "MEDIUM");
        
        sendEvent(FRAUD_ALERT_TOPIC, transaction.getReferenceNumber(), alert);
        log.warn("Fraud alert published for transaction: {}", transaction.getReferenceNumber());
    }
    
    private void sendNotification(Transaction transaction, String title, String message) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "TRANSACTION");
        notification.put("accountNumber", transaction.getSourceAccountNumber());
        notification.put("title", title);
        notification.put("message", message);
        notification.put("referenceNumber", transaction.getReferenceNumber());
        notification.put("timestamp", LocalDateTime.now().toString());
        
        sendEvent(NOTIFICATION_TOPIC, transaction.getSourceAccountNumber(), notification);
    }
    
    private Map<String, Object> buildEvent(String eventType, Transaction transaction) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("transactionId", transaction.getId().toString());
        event.put("referenceNumber", transaction.getReferenceNumber());
        event.put("type", transaction.getType().name());
        event.put("status", transaction.getStatus().name());
        event.put("amount", transaction.getAmount());
        event.put("currency", transaction.getCurrency());
        event.put("sourceAccount", transaction.getSourceAccountNumber());
        event.put("destinationAccount", transaction.getDestinationAccountNumber());
        event.put("timestamp", LocalDateTime.now().toString());
        return event;
    }
    
    private void sendEvent(String topic, String key, Object event) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);
        
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send event to topic {}: {}", topic, ex.getMessage());
            } else {
                log.debug("Event sent to topic {} with offset {}", 
                        topic, result.getRecordMetadata().offset());
            }
        });
    }
}

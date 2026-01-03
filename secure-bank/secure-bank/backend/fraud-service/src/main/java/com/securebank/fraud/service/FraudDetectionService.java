package com.securebank.fraud.service;

import com.securebank.fraud.model.*;
import com.securebank.fraud.ml.FraudMLModel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class FraudDetectionService {
    
    private final FraudMLModel mlModel;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Counter fraudDetectedCounter;
    private final Counter transactionsAnalyzedCounter;
    
    // In-memory cache for real-time velocity checks
    private final Map<String, List<TransactionVelocity>> velocityCache = new ConcurrentHashMap<>();
    
    // Thresholds
    private static final BigDecimal HIGH_RISK_THRESHOLD = new BigDecimal("0.75");
    private static final BigDecimal MEDIUM_RISK_THRESHOLD = new BigDecimal("0.50");
    private static final int MAX_TRANSACTIONS_PER_HOUR = 10;
    private static final BigDecimal MAX_AMOUNT_PER_HOUR = new BigDecimal("50000");
    
    public FraudDetectionService(
            FraudMLModel mlModel,
            KafkaTemplate<String, Object> kafkaTemplate,
            MeterRegistry meterRegistry) {
        this.mlModel = mlModel;
        this.kafkaTemplate = kafkaTemplate;
        
        this.fraudDetectedCounter = Counter.builder("fraud.detected.total")
                .description("Total fraudulent transactions detected")
                .register(meterRegistry);
        this.transactionsAnalyzedCounter = Counter.builder("fraud.analyzed.total")
                .description("Total transactions analyzed")
                .register(meterRegistry);
    }
    
    @KafkaListener(topics = "transaction-events", groupId = "fraud-detector")
    public void analyzeTransaction(Map<String, Object> event) {
        String eventType = (String) event.get("eventType");
        
        if (!"TRANSACTION_CREATED".equals(eventType)) {
            return;
        }
        
        transactionsAnalyzedCounter.increment();
        
        String transactionId = (String) event.get("transactionId");
        String sourceAccount = (String) event.get("sourceAccount");
        BigDecimal amount = new BigDecimal(event.get("amount").toString());
        String transactionType = (String) event.get("type");
        
        log.info("Analyzing transaction for fraud: {}", transactionId);
        
        // Build feature vector for ML model
        FraudFeatures features = buildFeatures(event);
        
        // Get ML model prediction
        BigDecimal mlScore = mlModel.predict(features);
        
        // Apply rule-based checks
        RuleCheckResult ruleResult = applyRules(sourceAccount, amount, transactionType);
        
        // Combine scores (weighted average)
        BigDecimal finalScore = mlScore.multiply(new BigDecimal("0.6"))
                .add(ruleResult.getScore().multiply(new BigDecimal("0.4")));
        
        FraudCheckResult result = FraudCheckResult.builder()
                .transactionId(transactionId)
                .score(finalScore)
                .mlScore(mlScore)
                .ruleScore(ruleResult.getScore())
                .riskLevel(determineRiskLevel(finalScore))
                .flagged(finalScore.compareTo(MEDIUM_RISK_THRESHOLD) >= 0)
                .reasons(ruleResult.getReasons())
                .analyzedAt(LocalDateTime.now())
                .build();
        
        // Update velocity cache
        updateVelocityCache(sourceAccount, amount);
        
        // Publish result
        publishFraudResult(result);
        
        if (result.isFlagged()) {
            fraudDetectedCounter.increment();
            log.warn("Potential fraud detected: {} - Score: {} - Risk: {}", 
                    transactionId, finalScore, result.getRiskLevel());
        }
    }
    
    private FraudFeatures buildFeatures(Map<String, Object> event) {
        String sourceAccount = (String) event.get("sourceAccount");
        BigDecimal amount = new BigDecimal(event.get("amount").toString());
        
        // Get historical data
        List<TransactionVelocity> recentTransactions = velocityCache.getOrDefault(
                sourceAccount, Collections.emptyList());
        
        int hourlyCount = (int) recentTransactions.stream()
                .filter(t -> t.getTimestamp().isAfter(LocalDateTime.now().minusHours(1)))
                .count();
        
        BigDecimal hourlyAmount = recentTransactions.stream()
                .filter(t -> t.getTimestamp().isAfter(LocalDateTime.now().minusHours(1)))
                .map(TransactionVelocity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return FraudFeatures.builder()
                .amount(amount)
                .transactionType((String) event.get("type"))
                .hourOfDay(LocalDateTime.now().getHour())
                .dayOfWeek(LocalDateTime.now().getDayOfWeek().getValue())
                .hourlyTransactionCount(hourlyCount)
                .hourlyTransactionAmount(hourlyAmount)
                .isInternational(event.get("type").toString().contains("SWIFT"))
                .destinationAccount((String) event.get("destinationAccount"))
                .build();
    }
    
    private RuleCheckResult applyRules(String accountNumber, BigDecimal amount, String type) {
        List<String> reasons = new ArrayList<>();
        BigDecimal score = BigDecimal.ZERO;
        
        // Rule 1: Velocity check - too many transactions
        List<TransactionVelocity> recent = velocityCache.getOrDefault(accountNumber, Collections.emptyList());
        long hourlyCount = recent.stream()
                .filter(t -> t.getTimestamp().isAfter(LocalDateTime.now().minusHours(1)))
                .count();
        
        if (hourlyCount >= MAX_TRANSACTIONS_PER_HOUR) {
            score = score.add(new BigDecimal("0.30"));
            reasons.add("High transaction velocity: " + hourlyCount + " transactions in last hour");
        }
        
        // Rule 2: Amount velocity check
        BigDecimal hourlyAmount = recent.stream()
                .filter(t -> t.getTimestamp().isAfter(LocalDateTime.now().minusHours(1)))
                .map(TransactionVelocity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (hourlyAmount.add(amount).compareTo(MAX_AMOUNT_PER_HOUR) > 0) {
            score = score.add(new BigDecimal("0.25"));
            reasons.add("High amount velocity: $" + hourlyAmount + " in last hour");
        }
        
        // Rule 3: Large transaction check
        if (amount.compareTo(new BigDecimal("10000")) > 0) {
            score = score.add(new BigDecimal("0.15"));
            reasons.add("Large transaction amount: $" + amount);
        }
        
        // Rule 4: Off-hours transaction (late night/early morning)
        int hour = LocalDateTime.now().getHour();
        if (hour >= 0 && hour < 6) {
            score = score.add(new BigDecimal("0.10"));
            reasons.add("Transaction during off-hours: " + hour + ":00");
        }
        
        // Rule 5: International transfer check
        if ("SWIFT_TRANSFER".equals(type)) {
            score = score.add(new BigDecimal("0.10"));
            reasons.add("International wire transfer");
        }
        
        // Cap score at 1.0
        if (score.compareTo(BigDecimal.ONE) > 0) {
            score = BigDecimal.ONE;
        }
        
        return new RuleCheckResult(score, reasons);
    }
    
    private String determineRiskLevel(BigDecimal score) {
        if (score.compareTo(HIGH_RISK_THRESHOLD) >= 0) {
            return "HIGH";
        } else if (score.compareTo(MEDIUM_RISK_THRESHOLD) >= 0) {
            return "MEDIUM";
        }
        return "LOW";
    }
    
    private void updateVelocityCache(String accountNumber, BigDecimal amount) {
        velocityCache.computeIfAbsent(accountNumber, k -> new ArrayList<>())
                .add(new TransactionVelocity(LocalDateTime.now(), amount));
        
        // Cleanup old entries (keep last 24 hours)
        velocityCache.get(accountNumber).removeIf(
                t -> t.getTimestamp().isBefore(LocalDateTime.now().minusHours(24)));
    }
    
    private void publishFraudResult(FraudCheckResult result) {
        Map<String, Object> event = new HashMap<>();
        event.put("transactionId", result.getTransactionId());
        event.put("score", result.getScore());
        event.put("riskLevel", result.getRiskLevel());
        event.put("flagged", result.isFlagged());
        event.put("reasons", result.getReasons());
        event.put("analyzedAt", result.getAnalyzedAt().toString());
        
        kafkaTemplate.send("fraud-results", result.getTransactionId(), event);
    }
}

@lombok.Data
@lombok.Builder
class FraudCheckResult {
    private String transactionId;
    private BigDecimal score;
    private BigDecimal mlScore;
    private BigDecimal ruleScore;
    private String riskLevel;
    private boolean flagged;
    private List<String> reasons;
    private LocalDateTime analyzedAt;
}

@lombok.Data
@lombok.AllArgsConstructor
class RuleCheckResult {
    private BigDecimal score;
    private List<String> reasons;
}

@lombok.Data
@lombok.AllArgsConstructor
class TransactionVelocity {
    private LocalDateTime timestamp;
    private BigDecimal amount;
}

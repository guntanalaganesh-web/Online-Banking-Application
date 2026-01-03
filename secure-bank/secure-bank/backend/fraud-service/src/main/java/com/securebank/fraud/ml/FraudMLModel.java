package com.securebank.fraud.ml;

import com.securebank.fraud.model.FraudFeatures;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Machine Learning model for fraud detection.
 * In production, this would integrate with a trained model (TensorFlow, PyTorch, or ML service).
 * This implementation uses a simplified scoring algorithm that mimics ML behavior.
 */
@Component
@Slf4j
public class FraudMLModel {
    
    // Feature weights (would be learned from training data)
    private Map<String, Double> featureWeights;
    
    // Historical statistics for normalization
    private double meanAmount = 500.0;
    private double stdAmount = 2000.0;
    private double meanHourlyCount = 3.0;
    private double stdHourlyCount = 2.0;
    
    @PostConstruct
    public void init() {
        // Initialize feature weights (simulating trained model)
        featureWeights = new HashMap<>();
        featureWeights.put("amount_normalized", 0.25);
        featureWeights.put("velocity_score", 0.20);
        featureWeights.put("time_risk", 0.15);
        featureWeights.put("amount_velocity", 0.15);
        featureWeights.put("international_flag", 0.10);
        featureWeights.put("pattern_anomaly", 0.15);
        
        log.info("Fraud ML model initialized with {} features", featureWeights.size());
    }
    
    public BigDecimal predict(FraudFeatures features) {
        try {
            // Normalize features
            double amountNormalized = normalizeAmount(features.getAmount().doubleValue());
            double velocityScore = calculateVelocityScore(features);
            double timeRisk = calculateTimeRisk(features.getHourOfDay(), features.getDayOfWeek());
            double amountVelocity = normalizeAmountVelocity(features.getHourlyTransactionAmount().doubleValue());
            double internationalFlag = features.isInternational() ? 1.0 : 0.0;
            double patternAnomaly = detectPatternAnomaly(features);
            
            // Calculate weighted sum (simulating neural network output)
            double rawScore = 
                    amountNormalized * featureWeights.get("amount_normalized") +
                    velocityScore * featureWeights.get("velocity_score") +
                    timeRisk * featureWeights.get("time_risk") +
                    amountVelocity * featureWeights.get("amount_velocity") +
                    internationalFlag * featureWeights.get("international_flag") +
                    patternAnomaly * featureWeights.get("pattern_anomaly");
            
            // Apply sigmoid activation (like neural network output layer)
            double probability = sigmoid(rawScore * 2 - 1);
            
            // Apply calibration
            probability = calibrate(probability);
            
            return BigDecimal.valueOf(probability).setScale(4, RoundingMode.HALF_UP);
            
        } catch (Exception e) {
            log.error("Error in fraud prediction: {}", e.getMessage());
            return new BigDecimal("0.50"); // Return medium risk on error
        }
    }
    
    private double normalizeAmount(double amount) {
        // Z-score normalization
        double zScore = (amount - meanAmount) / stdAmount;
        // Convert to 0-1 scale using sigmoid
        return sigmoid(zScore);
    }
    
    private double calculateVelocityScore(FraudFeatures features) {
        double countScore = (features.getHourlyTransactionCount() - meanHourlyCount) / stdHourlyCount;
        return Math.min(1.0, Math.max(0.0, sigmoid(countScore)));
    }
    
    private double calculateTimeRisk(int hour, int dayOfWeek) {
        // Higher risk during off-hours (midnight to 6am)
        double hourRisk = 0.0;
        if (hour >= 0 && hour < 6) {
            hourRisk = 0.7 + (6 - hour) * 0.05; // Peak risk at midnight
        } else if (hour >= 22 || hour < 8) {
            hourRisk = 0.3;
        }
        
        // Weekend slightly higher risk
        double dayRisk = (dayOfWeek == 6 || dayOfWeek == 7) ? 0.1 : 0.0;
        
        return Math.min(1.0, hourRisk + dayRisk);
    }
    
    private double normalizeAmountVelocity(double hourlyAmount) {
        // Risk increases with hourly spending
        double threshold = 10000.0;
        if (hourlyAmount <= threshold) {
            return hourlyAmount / threshold * 0.3;
        }
        return Math.min(1.0, 0.3 + (hourlyAmount - threshold) / (threshold * 4) * 0.7);
    }
    
    private double detectPatternAnomaly(FraudFeatures features) {
        double anomalyScore = 0.0;
        
        // Check for unusual patterns
        
        // Round number amounts (potential automated fraud)
        double amount = features.getAmount().doubleValue();
        if (amount == Math.floor(amount) && amount > 100) {
            anomalyScore += 0.2;
        }
        
        // Large international transfers
        if (features.isInternational() && amount > 5000) {
            anomalyScore += 0.3;
        }
        
        // Rapid successive transactions
        if (features.getHourlyTransactionCount() > 5) {
            anomalyScore += 0.2;
        }
        
        // New destination patterns would be checked here
        // (requires historical data)
        
        return Math.min(1.0, anomalyScore);
    }
    
    private double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }
    
    private double calibrate(double probability) {
        // Platt scaling calibration (simplified)
        // In production, parameters would be learned from validation data
        double a = 1.2;
        double b = -0.1;
        return sigmoid(a * probability + b);
    }
    
    /**
     * Update model with new labeled data (online learning simulation)
     */
    public void updateModel(FraudFeatures features, boolean wasFraud) {
        // In production, this would trigger model retraining or online updates
        log.info("Model update triggered - Label: {}", wasFraud);
        
        // Simple weight adjustment (gradient descent simulation)
        if (wasFraud) {
            // Increase sensitivity for similar patterns
            featureWeights.replaceAll((k, v) -> Math.min(0.35, v + 0.01));
        }
    }
    
    public Map<String, Double> getFeatureImportance() {
        return new HashMap<>(featureWeights);
    }
}

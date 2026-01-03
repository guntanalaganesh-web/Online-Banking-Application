package com.securebank.fraud.model;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudFeatures {
    private BigDecimal amount;
    private String transactionType;
    private int hourOfDay;
    private int dayOfWeek;
    private int hourlyTransactionCount;
    private BigDecimal hourlyTransactionAmount;
    private boolean isInternational;
    private String sourceAccount;
    private String destinationAccount;
    private String deviceFingerprint;
    private String ipAddress;
    private String geoLocation;
    private Double distanceFromLastTransaction;
    private Integer timeSinceLastTransaction; // seconds
    private Boolean isNewDestination;
    private Integer accountAgeInDays;
    private Integer totalTransactionCount;
    private BigDecimal averageTransactionAmount;
}

package com.securebank.transaction.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_source_account", columnList = "sourceAccountNumber"),
    @Index(name = "idx_dest_account", columnList = "destinationAccountNumber"),
    @Index(name = "idx_reference", columnList = "referenceNumber", unique = true),
    @Index(name = "idx_created_at", columnList = "createdAt"),
    @Index(name = "idx_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false, unique = true, length = 36)
    private String referenceNumber;
    
    @Column(nullable = false, length = 20)
    private String sourceAccountNumber;
    
    @Column(length = 20)
    private String destinationAccountNumber;
    
    @Column(length = 34)
    private String destinationIban;
    
    @Column(length = 11)
    private String destinationSwiftCode;
    
    @Column(length = 100)
    private String destinationBankName;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;
    
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(precision = 19, scale = 4)
    private BigDecimal fee;
    
    @Column(length = 3)
    private String currency;
    
    @Column(length = 3)
    private String destinationCurrency;
    
    @Column(precision = 10, scale = 6)
    private BigDecimal exchangeRate;
    
    @Column(length = 500)
    private String description;
    
    @Column(length = 100)
    private String beneficiaryName;
    
    // SWIFT specific fields
    @Column(length = 35)
    private String swiftMessageType; // MT103, MT202, etc.
    
    @Column(length = 16)
    private String swiftTransactionRef;
    
    // Fraud detection
    @Column(precision = 5, scale = 4)
    private BigDecimal fraudScore;
    
    private boolean fraudChecked;
    
    private boolean flaggedForReview;
    
    @Column(length = 500)
    private String fraudReviewNotes;
    
    // Compliance
    private boolean amlChecked;
    
    private boolean sanctionsChecked;
    
    // Processing metadata
    private LocalDateTime processedAt;
    
    private LocalDateTime completedAt;
    
    @Column(length = 500)
    private String failureReason;
    
    private int retryCount;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    public enum TransactionType {
        DEPOSIT, WITHDRAWAL, INTERNAL_TRANSFER, DOMESTIC_TRANSFER, 
        SWIFT_TRANSFER, BILL_PAYMENT, LOAN_PAYMENT, FEE, INTEREST
    }
    
    public enum TransactionStatus {
        PENDING, PROCESSING, FRAUD_REVIEW, COMPLIANCE_REVIEW, 
        APPROVED, COMPLETED, FAILED, CANCELLED, REVERSED
    }
}

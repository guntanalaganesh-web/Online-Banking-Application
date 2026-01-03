package com.securebank.account.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "accounts", indexes = {
    @Index(name = "idx_account_number", columnList = "accountNumber", unique = true),
    @Index(name = "idx_user_id", columnList = "userId"),
    @Index(name = "idx_swift_code", columnList = "swiftCode")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false, unique = true, length = 20)
    private String accountNumber;
    
    @Column(nullable = false)
    private UUID userId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;
    
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;
    
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal availableBalance;
    
    @Column(length = 3)
    private String currency;
    
    @Column(length = 11)
    private String swiftCode;
    
    @Column(length = 34)
    private String iban;
    
    @Column(precision = 5, scale = 2)
    private BigDecimal interestRate;
    
    @Column(precision = 19, scale = 4)
    private BigDecimal dailyLimit;
    
    @Column(precision = 19, scale = 4)
    private BigDecimal monthlyLimit;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    private LocalDateTime lastTransactionAt;
    
    @Version
    private Long version;
    
    public enum AccountType {
        CHECKING, SAVINGS, BUSINESS, INVESTMENT, LOAN
    }
    
    public enum AccountStatus {
        ACTIVE, INACTIVE, FROZEN, CLOSED, PENDING_VERIFICATION
    }
}

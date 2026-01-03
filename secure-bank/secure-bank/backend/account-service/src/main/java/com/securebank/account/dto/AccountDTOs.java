package com.securebank.account.dto;

import com.securebank.account.model.Account.AccountType;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountRequest {
    @NotNull(message = "Account type is required")
    private AccountType accountType;
    
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;
    
    @DecimalMin(value = "0", message = "Daily limit must be positive")
    private BigDecimal dailyLimit;
    
    @DecimalMin(value = "0", message = "Monthly limit must be positive")
    private BigDecimal monthlyLimit;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {
    private UUID id;
    private String accountNumber;
    private String accountType;
    private String status;
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private String currency;
    private String swiftCode;
    private String iban;
    private BigDecimal dailyLimit;
    private LocalDateTime createdAt;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BalanceResponse {
    private String accountNumber;
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private String currency;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {
    @NotBlank(message = "Source account is required")
    private String sourceAccount;
    
    @NotBlank(message = "Destination account is required")
    private String destinationAccount;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;
    
    @Size(max = 500, message = "Description too long")
    private String description;
    
    private String transferType; // INTERNAL, DOMESTIC, SWIFT
}

@Data
@Builder
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private LocalDateTime timestamp;
    
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}

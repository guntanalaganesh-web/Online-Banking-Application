package com.securebank.account.service;

import com.securebank.account.dto.*;
import com.securebank.account.model.Account;
import com.securebank.account.model.Account.AccountStatus;
import com.securebank.account.model.Account.AccountType;
import com.securebank.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {
    
    private final AccountRepository accountRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final SecureRandom random = new SecureRandom();
    
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request, UUID userId) {
        log.info("Creating account for user: {}", userId);
        
        String accountNumber = generateAccountNumber();
        String swiftCode = generateSwiftCode();
        String iban = generateIban(accountNumber);
        
        Account account = Account.builder()
                .accountNumber(accountNumber)
                .userId(userId)
                .accountType(request.getAccountType())
                .status(AccountStatus.PENDING_VERIFICATION)
                .balance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .swiftCode(swiftCode)
                .iban(iban)
                .dailyLimit(request.getDailyLimit() != null ? request.getDailyLimit() : new BigDecimal("10000"))
                .monthlyLimit(request.getMonthlyLimit() != null ? request.getMonthlyLimit() : new BigDecimal("100000"))
                .interestRate(getInterestRate(request.getAccountType()))
                .build();
        
        account = accountRepository.save(account);
        
        // Publish account created event
        publishAccountEvent("ACCOUNT_CREATED", account);
        
        log.info("Account created successfully: {}", account.getAccountNumber());
        return mapToResponse(account);
    }
    
    @Transactional(readOnly = true)
    public AccountResponse getAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));
        return mapToResponse(account);
    }
    
    @Transactional(readOnly = true)
    public List<AccountResponse> getUserAccounts(UUID userId) {
        return accountRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .toList();
    }
    
    @Transactional
    public AccountResponse activateAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        
        if (account.getStatus() != AccountStatus.PENDING_VERIFICATION) {
            throw new InvalidOperationException("Account cannot be activated from current status");
        }
        
        account.setStatus(AccountStatus.ACTIVE);
        account = accountRepository.save(account);
        
        publishAccountEvent("ACCOUNT_ACTIVATED", account);
        return mapToResponse(account);
    }
    
    @Transactional
    public AccountResponse freezeAccount(String accountNumber, String reason) {
        Account account = accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        
        account.setStatus(AccountStatus.FROZEN);
        account = accountRepository.save(account);
        
        log.warn("Account frozen: {} - Reason: {}", accountNumber, reason);
        publishAccountEvent("ACCOUNT_FROZEN", account);
        return mapToResponse(account);
    }
    
    @Transactional
    public BalanceResponse updateBalance(String accountNumber, BigDecimal amount, String transactionType) {
        Account account = accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidOperationException("Account is not active");
        }
        
        BigDecimal newBalance;
        if ("CREDIT".equals(transactionType)) {
            newBalance = account.getBalance().add(amount);
        } else if ("DEBIT".equals(transactionType)) {
            if (account.getAvailableBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException("Insufficient funds");
            }
            newBalance = account.getBalance().subtract(amount);
        } else {
            throw new InvalidOperationException("Invalid transaction type");
        }
        
        account.setBalance(newBalance);
        account.setAvailableBalance(newBalance); // Simplified - real system would handle holds
        account.setLastTransactionAt(LocalDateTime.now());
        accountRepository.save(account);
        
        return new BalanceResponse(accountNumber, newBalance, account.getAvailableBalance(), account.getCurrency());
    }
    
    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        return new BalanceResponse(accountNumber, account.getBalance(), account.getAvailableBalance(), account.getCurrency());
    }
    
    @Transactional(readOnly = true)
    public BigDecimal getTotalBalance(UUID userId) {
        BigDecimal total = accountRepository.getTotalBalanceByUserId(userId);
        return total != null ? total : BigDecimal.ZERO;
    }
    
    private String generateAccountNumber() {
        String accountNumber;
        do {
            accountNumber = String.format("%010d", random.nextLong(10_000_000_000L));
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }
    
    private String generateSwiftCode() {
        return "SBKUS33XXX"; // Bank's SWIFT code
    }
    
    private String generateIban(String accountNumber) {
        return "US" + String.format("%02d", random.nextInt(100)) + "SBKUS" + accountNumber;
    }
    
    private BigDecimal getInterestRate(AccountType type) {
        return switch (type) {
            case SAVINGS -> new BigDecimal("2.50");
            case CHECKING -> new BigDecimal("0.10");
            case BUSINESS -> new BigDecimal("1.00");
            case INVESTMENT -> new BigDecimal("4.00");
            case LOAN -> new BigDecimal("7.50");
        };
    }
    
    private void publishAccountEvent(String eventType, Account account) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", eventType,
                "accountNumber", account.getAccountNumber(),
                "userId", account.getUserId().toString(),
                "timestamp", LocalDateTime.now().toString()
            );
            kafkaTemplate.send("account-events", account.getAccountNumber(), event);
        } catch (Exception e) {
            log.error("Failed to publish account event", e);
        }
    }
    
    private AccountResponse mapToResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType().name())
                .status(account.getStatus().name())
                .balance(account.getBalance())
                .availableBalance(account.getAvailableBalance())
                .currency(account.getCurrency())
                .swiftCode(account.getSwiftCode())
                .iban(account.getIban())
                .dailyLimit(account.getDailyLimit())
                .createdAt(account.getCreatedAt())
                .build();
    }
}

class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String message) { super(message); }
}

class InvalidOperationException extends RuntimeException {
    public InvalidOperationException(String message) { super(message); }
}

class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) { super(message); }
}

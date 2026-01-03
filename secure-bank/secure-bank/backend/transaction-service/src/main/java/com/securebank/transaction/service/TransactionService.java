package com.securebank.transaction.service;

import com.securebank.transaction.dto.*;
import com.securebank.transaction.kafka.TransactionEventPublisher;
import com.securebank.transaction.model.Transaction;
import com.securebank.transaction.model.Transaction.TransactionStatus;
import com.securebank.transaction.model.Transaction.TransactionType;
import com.securebank.transaction.repository.TransactionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class TransactionService {
    
    private final TransactionRepository transactionRepository;
    private final TransactionEventPublisher eventPublisher;
    private final AccountServiceClient accountClient;
    private final FraudServiceClient fraudClient;
    private final Counter transactionCounter;
    private final Timer transactionTimer;
    
    public TransactionService(
            TransactionRepository transactionRepository,
            TransactionEventPublisher eventPublisher,
            AccountServiceClient accountClient,
            FraudServiceClient fraudClient,
            MeterRegistry meterRegistry) {
        this.transactionRepository = transactionRepository;
        this.eventPublisher = eventPublisher;
        this.accountClient = accountClient;
        this.fraudClient = fraudClient;
        
        this.transactionCounter = Counter.builder("transactions.total")
                .description("Total transactions processed")
                .register(meterRegistry);
        this.transactionTimer = Timer.builder("transactions.processing.time")
                .description("Transaction processing time")
                .register(meterRegistry);
    }
    
    @Transactional
    public TransactionResponse initiateTransfer(TransferRequest request, UUID userId) {
        return transactionTimer.record(() -> {
            log.info("Initiating transfer: {} -> {}, amount: {}", 
                    request.getSourceAccount(), request.getDestinationAccount(), request.getAmount());
            
            // Generate reference number
            String referenceNumber = generateReferenceNumber();
            
            // Determine transaction type
            TransactionType type = determineTransactionType(request);
            
            // Calculate fees
            BigDecimal fee = calculateFee(type, request.getAmount());
            BigDecimal totalAmount = request.getAmount().add(fee);
            
            // Create transaction record
            Transaction transaction = Transaction.builder()
                    .referenceNumber(referenceNumber)
                    .sourceAccountNumber(request.getSourceAccount())
                    .destinationAccountNumber(request.getDestinationAccount())
                    .destinationIban(request.getDestinationIban())
                    .destinationSwiftCode(request.getSwiftCode())
                    .destinationBankName(request.getBankName())
                    .type(type)
                    .status(TransactionStatus.PENDING)
                    .amount(request.getAmount())
                    .fee(fee)
                    .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                    .destinationCurrency(request.getDestinationCurrency())
                    .exchangeRate(request.getExchangeRate())
                    .description(request.getDescription())
                    .beneficiaryName(request.getBeneficiaryName())
                    .fraudChecked(false)
                    .amlChecked(false)
                    .sanctionsChecked(false)
                    .retryCount(0)
                    .build();
            
            if (type == TransactionType.SWIFT_TRANSFER) {
                transaction.setSwiftMessageType("MT103");
                transaction.setSwiftTransactionRef(generateSwiftRef());
            }
            
            transaction = transactionRepository.save(transaction);
            
            // Publish event for async processing
            eventPublisher.publishTransactionCreated(transaction);
            
            transactionCounter.increment();
            
            return mapToResponse(transaction);
        });
    }
    
    @Transactional
    public void processTransaction(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found"));
        
        try {
            log.info("Processing transaction: {}", transaction.getReferenceNumber());
            transaction.setStatus(TransactionStatus.PROCESSING);
            transaction.setProcessedAt(LocalDateTime.now());
            transactionRepository.save(transaction);
            
            // Step 1: Fraud check
            FraudCheckResult fraudResult = fraudClient.checkTransaction(transaction);
            transaction.setFraudChecked(true);
            transaction.setFraudScore(fraudResult.getScore());
            
            if (fraudResult.isFlagged()) {
                transaction.setStatus(TransactionStatus.FRAUD_REVIEW);
                transaction.setFlaggedForReview(true);
                transactionRepository.save(transaction);
                eventPublisher.publishFraudAlert(transaction);
                log.warn("Transaction flagged for fraud review: {}", transaction.getReferenceNumber());
                return;
            }
            
            // Step 2: Compliance checks (AML, Sanctions)
            if (!performComplianceChecks(transaction)) {
                transaction.setStatus(TransactionStatus.COMPLIANCE_REVIEW);
                transactionRepository.save(transaction);
                return;
            }
            
            // Step 3: Validate and debit source account
            accountClient.debitAccount(transaction.getSourceAccountNumber(), 
                    transaction.getAmount().add(transaction.getFee()));
            
            // Step 4: Credit destination account (for internal transfers)
            if (transaction.getType() == TransactionType.INTERNAL_TRANSFER) {
                accountClient.creditAccount(transaction.getDestinationAccountNumber(), 
                        transaction.getAmount());
                transaction.setStatus(TransactionStatus.COMPLETED);
                transaction.setCompletedAt(LocalDateTime.now());
            } else if (transaction.getType() == TransactionType.SWIFT_TRANSFER) {
                // SWIFT transfers go through external processing
                processSwiftTransfer(transaction);
                transaction.setStatus(TransactionStatus.APPROVED);
            } else {
                transaction.setStatus(TransactionStatus.APPROVED);
            }
            
            transactionRepository.save(transaction);
            eventPublisher.publishTransactionCompleted(transaction);
            
            log.info("Transaction processed successfully: {}", transaction.getReferenceNumber());
            
        } catch (InsufficientFundsException e) {
            handleTransactionFailure(transaction, "Insufficient funds");
        } catch (Exception e) {
            log.error("Transaction processing failed: {}", transaction.getReferenceNumber(), e);
            handleTransactionFailure(transaction, e.getMessage());
        }
    }
    
    private void processSwiftTransfer(Transaction transaction) {
        log.info("Processing SWIFT transfer: {} - {}", 
                transaction.getSwiftTransactionRef(), transaction.getDestinationSwiftCode());
        
        // In production, this would integrate with SWIFT network
        // Simulating SWIFT message creation (MT103)
        SwiftMessage message = SwiftMessage.builder()
                .messageType("MT103")
                .senderRef(transaction.getSwiftTransactionRef())
                .senderBic("SBKUS33XXX")
                .receiverBic(transaction.getDestinationSwiftCode())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .beneficiaryName(transaction.getBeneficiaryName())
                .beneficiaryAccount(transaction.getDestinationIban())
                .remittanceInfo(transaction.getDescription())
                .build();
        
        // Send to SWIFT gateway (mock)
        log.info("SWIFT MT103 message created: {}", message);
    }
    
    private boolean performComplianceChecks(Transaction transaction) {
        // AML Check
        transaction.setAmlChecked(true);
        
        // Sanctions screening
        transaction.setSanctionsChecked(true);
        
        // Large transaction reporting (CTR for amounts > $10,000)
        if (transaction.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            log.info("CTR required for transaction: {}", transaction.getReferenceNumber());
        }
        
        return true;
    }
    
    private void handleTransactionFailure(Transaction transaction, String reason) {
        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setFailureReason(reason);
        transaction.setRetryCount(transaction.getRetryCount() + 1);
        transactionRepository.save(transaction);
        eventPublisher.publishTransactionFailed(transaction);
    }
    
    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(String referenceNumber) {
        Transaction transaction = transactionRepository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found"));
        return mapToResponse(transaction);
    }
    
    @Transactional(readOnly = true)
    public List<TransactionResponse> getAccountTransactions(String accountNumber, int limit) {
        return transactionRepository.findByAccountNumber(accountNumber, limit).stream()
                .map(this::mapToResponse)
                .toList();
    }
    
    private TransactionType determineTransactionType(TransferRequest request) {
        if (request.getSwiftCode() != null && !request.getSwiftCode().isEmpty()) {
            return TransactionType.SWIFT_TRANSFER;
        } else if (request.getDestinationAccount() != null) {
            return TransactionType.INTERNAL_TRANSFER;
        }
        return TransactionType.DOMESTIC_TRANSFER;
    }
    
    private BigDecimal calculateFee(TransactionType type, BigDecimal amount) {
        return switch (type) {
            case SWIFT_TRANSFER -> new BigDecimal("25.00");
            case DOMESTIC_TRANSFER -> amount.multiply(new BigDecimal("0.001")).setScale(2, RoundingMode.HALF_UP);
            case INTERNAL_TRANSFER -> BigDecimal.ZERO;
            default -> BigDecimal.ZERO;
        };
    }
    
    private String generateReferenceNumber() {
        return "TXN" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    private String generateSwiftRef() {
        return "SBK" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) 
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
    
    private TransactionResponse mapToResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .referenceNumber(transaction.getReferenceNumber())
                .type(transaction.getType().name())
                .status(transaction.getStatus().name())
                .amount(transaction.getAmount())
                .fee(transaction.getFee())
                .currency(transaction.getCurrency())
                .sourceAccount(transaction.getSourceAccountNumber())
                .destinationAccount(transaction.getDestinationAccountNumber())
                .beneficiaryName(transaction.getBeneficiaryName())
                .description(transaction.getDescription())
                .createdAt(transaction.getCreatedAt())
                .completedAt(transaction.getCompletedAt())
                .build();
    }
}

// Supporting classes
@lombok.Data
@lombok.Builder
class SwiftMessage {
    private String messageType;
    private String senderRef;
    private String senderBic;
    private String receiverBic;
    private BigDecimal amount;
    private String currency;
    private String beneficiaryName;
    private String beneficiaryAccount;
    private String remittanceInfo;
}

@lombok.Data
class FraudCheckResult {
    private BigDecimal score;
    private boolean flagged;
    private String reason;
}

class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(String message) { super(message); }
}

class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) { super(message); }
}

// Feign clients (interfaces)
interface AccountServiceClient {
    void debitAccount(String accountNumber, BigDecimal amount);
    void creditAccount(String accountNumber, BigDecimal amount);
}

interface FraudServiceClient {
    FraudCheckResult checkTransaction(Transaction transaction);
}

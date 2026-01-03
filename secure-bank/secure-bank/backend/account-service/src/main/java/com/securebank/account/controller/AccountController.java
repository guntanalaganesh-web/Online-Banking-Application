package com.securebank.account.controller;

import com.securebank.account.dto.*;
import com.securebank.account.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Account Management", description = "APIs for managing bank accounts")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {
    
    private final AccountService accountService;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    @Operation(summary = "Create a new account")
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @Valid @RequestBody CreateAccountRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        
        UUID userId = UUID.fromString(jwt.getSubject());
        log.info("Creating account for user: {}", userId);
        
        AccountResponse response = accountService.createAccount(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
    
    @GetMapping("/{accountNumber}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    @Operation(summary = "Get account details")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccount(
            @PathVariable String accountNumber) {
        
        AccountResponse response = accountService.getAccount(accountNumber);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    @Operation(summary = "Get all accounts for a user")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getUserAccounts(
            @PathVariable UUID userId) {
        
        List<AccountResponse> accounts = accountService.getUserAccounts(userId);
        return ResponseEntity.ok(ApiResponse.success(accounts));
    }
    
    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get current user's accounts")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getMyAccounts(
            @AuthenticationPrincipal Jwt jwt) {
        
        UUID userId = UUID.fromString(jwt.getSubject());
        List<AccountResponse> accounts = accountService.getUserAccounts(userId);
        return ResponseEntity.ok(ApiResponse.success(accounts));
    }
    
    @GetMapping("/{accountNumber}/balance")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    @Operation(summary = "Get account balance")
    public ResponseEntity<ApiResponse<BalanceResponse>> getBalance(
            @PathVariable String accountNumber) {
        
        BalanceResponse balance = accountService.getBalance(accountNumber);
        return ResponseEntity.ok(ApiResponse.success(balance));
    }
    
    @GetMapping("/user/{userId}/total-balance")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Operation(summary = "Get total balance across all accounts")
    public ResponseEntity<ApiResponse<BigDecimal>> getTotalBalance(
            @PathVariable UUID userId) {
        
        BigDecimal totalBalance = accountService.getTotalBalance(userId);
        return ResponseEntity.ok(ApiResponse.success(totalBalance));
    }
    
    @PutMapping("/{accountNumber}/activate")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    @Operation(summary = "Activate an account")
    public ResponseEntity<ApiResponse<AccountResponse>> activateAccount(
            @PathVariable String accountNumber) {
        
        AccountResponse response = accountService.activateAccount(accountNumber);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @PutMapping("/{accountNumber}/freeze")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER', 'FRAUD_ANALYST')")
    @Operation(summary = "Freeze an account")
    public ResponseEntity<ApiResponse<AccountResponse>> freezeAccount(
            @PathVariable String accountNumber,
            @RequestParam String reason) {
        
        log.warn("Freezing account: {} - Reason: {}", accountNumber, reason);
        AccountResponse response = accountService.freezeAccount(accountNumber, reason);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

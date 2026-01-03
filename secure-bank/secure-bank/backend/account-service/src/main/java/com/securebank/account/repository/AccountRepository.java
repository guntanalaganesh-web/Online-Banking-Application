package com.securebank.account.repository;

import com.securebank.account.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.*;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    
    Optional<Account> findByAccountNumber(String accountNumber);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);
    
    List<Account> findByUserId(UUID userId);
    
    List<Account> findByUserIdAndStatus(UUID userId, Account.AccountStatus status);
    
    @Query("SELECT a FROM Account a WHERE a.userId = :userId AND a.accountType = :type")
    List<Account> findByUserIdAndType(@Param("userId") UUID userId, @Param("type") Account.AccountType type);
    
    @Query("SELECT SUM(a.balance) FROM Account a WHERE a.userId = :userId AND a.status = 'ACTIVE'")
    BigDecimal getTotalBalanceByUserId(@Param("userId") UUID userId);
    
    @Query("SELECT a FROM Account a WHERE a.swiftCode = :swiftCode AND a.status = 'ACTIVE'")
    List<Account> findBySwiftCode(@Param("swiftCode") String swiftCode);
    
    @Query("SELECT a FROM Account a WHERE a.iban = :iban")
    Optional<Account> findByIban(@Param("iban") String iban);
    
    boolean existsByAccountNumber(String accountNumber);
    
    @Query("SELECT COUNT(a) FROM Account a WHERE a.userId = :userId AND a.status = 'ACTIVE'")
    long countActiveAccountsByUserId(@Param("userId") UUID userId);
}

package com.securebank.account.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_email", columnList = "email", unique = true),
    @Index(name = "idx_phone", columnList = "phoneNumber")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = false)
    private String passwordHash;
    
    @Column(nullable = false)
    private String firstName;
    
    @Column(nullable = false)
    private String lastName;
    
    private String middleName;
    
    @Column(length = 20)
    private String phoneNumber;
    
    private LocalDate dateOfBirth;
    
    @Column(length = 20)
    private String ssn; // Encrypted
    
    @Embedded
    private Address address;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycStatus kycStatus;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();
    
    private boolean mfaEnabled;
    
    private String mfaSecret;
    
    private int failedLoginAttempts;
    
    private LocalDateTime lockoutUntil;
    
    private LocalDateTime lastLoginAt;
    
    private String lastLoginIp;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    public enum UserStatus {
        ACTIVE, INACTIVE, SUSPENDED, LOCKED, PENDING_VERIFICATION
    }
    
    public enum KycStatus {
        NOT_STARTED, PENDING, VERIFIED, REJECTED, EXPIRED
    }
    
    public enum Role {
        CUSTOMER, TELLER, MANAGER, ADMIN, COMPLIANCE_OFFICER, FRAUD_ANALYST
    }
    
    public String getFullName() {
        return firstName + " " + (middleName != null ? middleName + " " : "") + lastName;
    }
}

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
class Address {
    private String street;
    private String city;
    private String state;
    private String postalCode;
    private String country;
}

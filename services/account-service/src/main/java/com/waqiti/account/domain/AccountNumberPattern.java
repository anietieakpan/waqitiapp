package com.waqiti.account.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing custom account number patterns for different account types and regions
 */
@Entity
@Table(name = "account_number_patterns", indexes = {
        @Index(name = "idx_account_type_region", columnList = "account_type, region_id"),
        @Index(name = "idx_active", columnList = "active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountNumberPattern {
    
    @Id
    private String id;
    
    @Column(name = "account_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private Account.AccountType accountType;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private Region region;
    
    @Column(name = "pattern", nullable = false, length = 100)
    private String pattern; // e.g., "{PREFIX}{YYYY}{REGION}{RANDOM6}"
    
    @Column(name = "prefix", length = 10)
    private String prefix; // e.g., "WLT", "SAV", "BIZ"
    
    @Column(name = "branch_code", length = 10)
    private String branchCode;
    
    @Column(name = "region_code", length = 10)
    private String regionCode;
    
    @Column(name = "currency_code", length = 3)
    private String currencyCode; // e.g., "USD", "EUR"
    
    @Column(name = "include_check_digit")
    private boolean includeCheckDigit = true;
    
    @Column(name = "min_length")
    private int minLength = 10;
    
    @Column(name = "max_length")
    private int maxLength = 20;
    
    @Column(name = "validation_regex", length = 200)
    private String validationRegex; // Optional regex for additional validation
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Column(name = "active")
    private boolean active = true;
    
    @Column(name = "priority")
    private int priority = 0; // Higher priority patterns are preferred
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "created_by", length = 100)
    private String createdBy;
    
    @Column(name = "updated_by", length = 100)
    private String updatedBy;
    
    /**
     * Supported pattern placeholders:
     * 
     * Date/Time:
     * {YYYY} - 4-digit year (e.g., 2024)
     * {YY} - 2-digit year (e.g., 24)
     * {MM} - 2-digit month (e.g., 07)
     * {DD} - 2-digit day (e.g., 15)
     * 
     * Location:
     * {PREFIX} - Account type prefix
     * {BRANCH} - Branch code
     * {REGION} - Region code
     * {CURRENCY} - Currency code
     * 
     * Random:
     * {RANDOM4} - 4-digit random number
     * {RANDOM6} - 6-digit random number
     * {RANDOM8} - 8-digit random number
     * {SEQUENCE} - 6-digit sequential number (default)
     * 
     * Examples:
     * - "{PREFIX}{YYYY}{REGION}{RANDOM6}" → "WLT202400123456"
     * - "{PREFIX}-{YY}{MM}-{BRANCH}-{RANDOM4}" → "SAV-2407-001-5678"
     * - "{CURRENCY}{PREFIX}{RANDOM8}" → "USDWLT12345678"
     */
    
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }
    
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
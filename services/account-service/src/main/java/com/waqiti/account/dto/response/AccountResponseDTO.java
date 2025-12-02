package com.waqiti.account.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.waqiti.common.dto.base.BaseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Response DTO for account information
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Getter
@Setter
@ToString(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Account response payload")
public class AccountResponseDTO extends BaseDTO {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    @Schema(description = "Unique account number")
    private String accountNumber;
    
    @Schema(description = "User ID who owns the account")
    private UUID userId;
    
    @Schema(description = "Account type")
    private String accountType;
    
    @Schema(description = "Account status")
    private String status;
    
    @Schema(description = "Account name/alias")
    private String accountName;
    
    @Schema(description = "Account currency ISO code")
    private String currency;
    
    @Schema(description = "Current account balance")
    private BigDecimal balance;
    
    @Schema(description = "Available balance (considering holds)")
    private BigDecimal availableBalance;
    
    @Schema(description = "Ledger balance (end of day balance)")
    private BigDecimal ledgerBalance;
    
    @Schema(description = "Total amount on hold")
    private BigDecimal holdAmount;
    
    @Schema(description = "Account category")
    private String accountCategory;
    
    @Schema(description = "Parent account ID for sub-accounts")
    private UUID parentAccountId;
    
    @Schema(description = "Overdraft protection enabled")
    private Boolean overdraftProtection;
    
    @Schema(description = "Overdraft limit")
    private BigDecimal overdraftLimit;
    
    @Schema(description = "Daily transaction limit")
    private BigDecimal dailyTransactionLimit;
    
    @Schema(description = "Monthly transaction limit")
    private BigDecimal monthlyTransactionLimit;
    
    @Schema(description = "Daily spent amount")
    private BigDecimal dailySpent;
    
    @Schema(description = "Monthly spent amount")
    private BigDecimal monthlySpent;
    
    @Schema(description = "Account tier level")
    private String tierLevel;
    
    @Schema(description = "KYC verification level")
    private String kycLevel;
    
    @Schema(description = "KYC verified date")
    private LocalDateTime kycVerifiedAt;
    
    @Schema(description = "International transactions enabled")
    private Boolean internationalEnabled;
    
    @Schema(description = "Virtual card enabled")
    private Boolean virtualCardEnabled;
    
    @Schema(description = "Account opening date")
    private LocalDateTime openedAt;
    
    @Schema(description = "Account closure date")
    private LocalDateTime closedAt;
    
    @Schema(description = "Last transaction date")
    private LocalDateTime lastTransactionAt;
    
    @Schema(description = "Interest rate (for savings accounts)")
    private BigDecimal interestRate;
    
    @Schema(description = "Last interest calculation date")
    private LocalDateTime lastInterestCalculatedAt;
    
    @Schema(description = "Account frozen flag")
    private Boolean frozen;
    
    @Schema(description = "Freeze reason")
    private String freezeReason;
    
    @Schema(description = "Frozen date")
    private LocalDateTime frozenAt;
    
    @Schema(description = "Risk score")
    private Integer riskScore;
    
    @Schema(description = "Account tags for categorization")
    private Set<String> tags;
    
    @Schema(description = "Number of payment methods")
    private Integer paymentMethodCount;
    
    @Schema(description = "Number of sub-accounts")
    private Integer subAccountCount;
    
    @Schema(description = "Is account active")
    private Boolean isAccountActive;
    
    @Schema(description = "Days since last transaction")
    private Long daysSinceLastTransaction;
    
    @Schema(description = "Account age in days")
    private Long accountAgeInDays;
    
    /**
     * Calculate derived fields
     */
    public void calculateDerivedFields() {
        // Calculate hold amount
        if (balance != null && availableBalance != null) {
            holdAmount = balance.subtract(availableBalance);
        }
        
        // Calculate days since last transaction
        if (lastTransactionAt != null) {
            daysSinceLastTransaction = java.time.Duration.between(
                lastTransactionAt, LocalDateTime.now()
            ).toDays();
        }
        
        // Calculate account age
        if (openedAt != null) {
            accountAgeInDays = java.time.Duration.between(
                openedAt, LocalDateTime.now()
            ).toDays();
        }
        
        // Determine if account is active
        isAccountActive = "ACTIVE".equals(status) && 
                         Boolean.FALSE.equals(frozen) && 
                         Boolean.TRUE.equals(getActive());
    }
    
    /**
     * Mask sensitive information for security
     */
    public void maskSensitiveData() {
        if (accountNumber != null && accountNumber.length() > 8) {
            accountNumber = accountNumber.substring(0, 4) + 
                          "****" + 
                          accountNumber.substring(accountNumber.length() - 4);
        }
    }
    
    /**
     * Builder class for fluent construction
     */
    public static class AccountResponseDTOBuilder<C extends AccountResponseDTO, B extends AccountResponseDTOBuilder<C, B>> 
            extends BaseDTOBuilder<C, B> {
        
        /**
         * Build and calculate derived fields
         */
        @Override
        public C build() {
            C dto = super.build();
            dto.calculateDerivedFields();
            return dto;
        }
    }
}
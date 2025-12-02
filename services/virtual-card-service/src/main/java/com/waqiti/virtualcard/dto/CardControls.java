package com.waqiti.virtualcard.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for card transaction controls
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardControls {
    
    private boolean allowOnlineTransactions = true;
    
    private boolean allowInternationalTransactions = true;
    
    private boolean allowAtmWithdrawals = true;
    
    private boolean allowContactlessPayments = true;
    
    private boolean allowRecurringPayments = true;
    
    private boolean allowChipTransactions = true;
    
    private boolean allowMagneticStripe = true;
    
    private boolean allowManualKeyEntry = true;
    
    @Min(value = 1, message = "Max transactions must be at least 1")
    private Integer maxTransactions;
    
    @Min(value = 1, message = "Max daily transactions must be at least 1")
    private Integer maxDailyTransactions;
    
    @Min(value = 1, message = "Max weekly transactions must be at least 1")
    private Integer maxWeeklyTransactions;
    
    @Min(value = 1, message = "Max monthly transactions must be at least 1")
    private Integer maxMonthlyTransactions;
    
    @Size(max = 100, message = "Too many allowed countries")
    private List<@Size(min = 2, max = 3, message = "Country code must be 2-3 characters") String> allowedCountries;
    
    @Size(max = 100, message = "Too many blocked countries")
    private List<@Size(min = 2, max = 3, message = "Country code must be 2-3 characters") String> blockedCountries;
    
    @Size(max = 100, message = "Too many allowed merchant categories")
    private List<@Size(min = 4, max = 4, message = "MCC must be 4 characters") String> allowedMerchantCategories;
    
    @Size(max = 100, message = "Too many blocked merchant categories")
    private List<@Size(min = 4, max = 4, message = "MCC must be 4 characters") String> blockedMerchantCategories;
    
    @Size(max = 100, message = "Too many allowed merchants")
    private List<String> allowedMerchants;
    
    @Size(max = 100, message = "Too many blocked merchants")
    private List<String> blockedMerchants;
    
    private Map<String, String> timeRestrictions;
    
    private LocalTime dailyStartTime;
    
    private LocalTime dailyEndTime;
    
    @Min(value = 1, message = "Expiry minutes must be at least 1")
    private Integer expiryMinutes;
    
    private boolean velocityCheckEnabled = true;
    
    private boolean fraudCheckEnabled = true;
    
    private boolean highRiskMerchantBlock = true;
    
    private boolean gamblingBlock = false;
    
    private boolean adultContentBlock = false;
    
    private boolean cryptoPurchaseBlock = false;
    
    private boolean cashAdvanceBlock = false;
    
    private boolean requireMfaForOnline = false;
    
    private boolean requireMfaForInternational = false;
    
    private BigDecimal requireMfaAboveAmount;
    
    private boolean notificationOnAllTransactions = false;
    
    private boolean notificationOnDeclined = true;
    
    private boolean notificationOnHighAmount = true;
    
    private BigDecimal highAmountThreshold;
}
/**
 * Compliance Service
 * Handles AML/KYC compliance checks for cryptocurrency transactions
 */
package com.waqiti.crypto.compliance;

import com.waqiti.crypto.dto.ComplianceResult;
import com.waqiti.crypto.dto.SendCryptocurrencyRequest;
import com.waqiti.crypto.entity.CryptoCurrency;
import com.waqiti.common.kyc.service.KYCClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceService {

    @Value("${compliance.transaction.limit.daily:50000}")
    private BigDecimal dailyTransactionLimit;

    @Value("${compliance.transaction.limit.single:10000}")
    private BigDecimal singleTransactionLimit;

    @Value("${compliance.transaction.reporting.threshold:10000}")
    private BigDecimal reportingThreshold;

    private final SanctionsScreeningService sanctionsScreeningService;
    private final KYCClientService kycClientService;

    /**
     * Validate wallet creation eligibility
     */
    public void validateWalletCreation(UUID userId, CryptoCurrency currency) {
        log.debug("Validating wallet creation for user: {} currency: {}", userId, currency);
        
        // Check if user is verified
        if (!isUserVerified(userId)) {
            throw new ComplianceException("User must complete KYC verification before creating crypto wallets");
        }
        
        // Check if user is in allowed jurisdiction
        if (!isJurisdictionAllowed(userId)) {
            throw new ComplianceException("Cryptocurrency services not available in your jurisdiction");
        }
        
        // Check if currency is allowed for user
        if (!isCurrencyAllowedForUser(userId, currency)) {
            throw new ComplianceException("This cryptocurrency is not available for your account");
        }
    }

    /**
     * Screen cryptocurrency transaction for compliance
     */
    public ComplianceResult screenCryptoTransaction(UUID userId, SendCryptocurrencyRequest request) {
        log.info("Screening crypto transaction for user: {} amount: {} {} to: {}", 
                userId, request.getAmount(), request.getCurrency(), request.getToAddress());
        
        ComplianceResult result = new ComplianceResult();
        result.setPassed(true);
        
        // 1. Check transaction limits
        if (!checkTransactionLimits(userId, request.getAmount(), request.getCurrency())) {
            result.setPassed(false);
            result.setBlocked(true);
            result.setReason("Transaction exceeds allowed limits");
            return result;
        }
        
        // 2. Screen destination address for sanctions
        if (sanctionsScreeningService.isSanctionedAddress(request.getToAddress(), request.getCurrency())) {
            result.setPassed(false);
            result.setBlocked(true);
            result.setReason("Destination address is sanctioned");
            result.setRequiresReporting(true);
            return result;
        }
        
        // 3. Check if reporting is required
        BigDecimal usdValue = calculateUsdValue(request.getAmount(), request.getCurrency());
        if (usdValue.compareTo(reportingThreshold) >= 0) {
            result.setRequiresReporting(true);
            result.setReportingReason("Transaction exceeds reporting threshold");
        }
        
        // 4. Enhanced KYC check for high-value transactions
        if (usdValue.compareTo(new BigDecimal("10000")) > 0) {
            if (!kycClientService.canUserMakeHighValueTransfer(userId.toString())) {
                result.setPassed(false);
                result.setBlocked(true);
                result.setReason("Enhanced KYC verification required for crypto transactions over $10,000");
                return result;
            }
        }
        
        // 5. Check user risk profile
        UserRiskProfile riskProfile = getUserRiskProfile(userId);
        if (riskProfile == UserRiskProfile.HIGH) {
            result.setRequiresManualReview(true);
            result.setReviewReason("High-risk user profile");
        }
        
        // 6. Check destination country (if available)
        if (isHighRiskJurisdiction(request.getToAddress())) {
            result.setRequiresManualReview(true);
            result.setReviewReason("Transaction to high-risk jurisdiction");
        }
        
        log.info("Compliance screening result for user {}: passed={} blocked={} reporting={} review={}", 
                userId, result.isPassed(), result.isBlocked(), result.isRequiresReporting(), 
                result.isRequiresManualReview());
        
        return result;
    }

    /**
     * Check transaction limits
     */
    private boolean checkTransactionLimits(UUID userId, BigDecimal amount, CryptoCurrency currency) {
        // Convert to USD for limit checking
        BigDecimal usdValue = calculateUsdValue(amount, currency);
        
        // Check single transaction limit
        if (usdValue.compareTo(singleTransactionLimit) > 0) {
            log.warn("Transaction exceeds single limit for user {}: {} USD", userId, usdValue);
            return false;
        }
        
        // Check daily limit
        BigDecimal dailyTotal = getDailyTransactionTotal(userId);
        if (dailyTotal.add(usdValue).compareTo(dailyTransactionLimit) > 0) {
            log.warn("Transaction would exceed daily limit for user {}: {} USD", userId, dailyTotal.add(usdValue));
            return false;
        }
        
        return true;
    }

    /**
     * Check if user is verified (KYC complete)
     */
    private boolean isUserVerified(UUID userId) {
        return kycClientService.isUserAdvancedVerified(userId.toString());
    }

    /**
     * Check if user's jurisdiction allows crypto
     */
    private boolean isJurisdictionAllowed(UUID userId) {
        // Check user's jurisdiction against restricted list
        String userCountry = kycClientService.getUserCountry(userId.toString());
        String userState = kycClientService.getUserState(userId.toString());
        
        // Restricted countries
        Set<String> restrictedCountries = Set.of("CN", "KP", "IR", "CU", "SY");
        if (restrictedCountries.contains(userCountry)) {
            log.warn("User {} from restricted country: {}", userId, userCountry);
            return false;
        }
        
        // US state restrictions (e.g., New York BitLicense)
        if ("US".equals(userCountry) && "NY".equals(userState)) {
            log.warn("User {} from restricted US state: {}", userId, userState);
            return false;
        }
        
        return true;
    }

    /**
     * Check if specific currency is allowed for user
     */
    private boolean isCurrencyAllowedForUser(UUID userId, CryptoCurrency currency) {
        String userCountry = kycClientService.getUserCountry(userId.toString());
        String userState = kycClientService.getUserState(userId.toString());
        
        // US NY BitLicense restrictions - only certain currencies allowed
        if ("US".equals(userCountry) && "NY".equals(userState)) {
            Set<CryptoCurrency> nyAllowedCurrencies = Set.of(
                CryptoCurrency.BITCOIN,
                CryptoCurrency.ETHEREUM,
                CryptoCurrency.USDC
            );
            return nyAllowedCurrencies.contains(currency);
        }
        
        // Japan restrictions - no privacy coins
        if ("JP".equals(userCountry)) {
            // Add privacy coin check if we support them
            return true;
        }
        
        return true;
    }

    /**
     * Get user's risk profile
     */
    private UserRiskProfile getUserRiskProfile(UUID userId) {
        // Calculate risk score based on multiple factors
        int riskScore = 0;
        
        // Factor 1: KYC verification level
        String kycLevel = kycClientService.getUserVerificationLevel(userId.toString());
        if ("BASIC".equals(kycLevel)) {
            riskScore += 20;
        } else if ("NONE".equals(kycLevel)) {
            riskScore += 40;
        }
        
        // Factor 2: Transaction history
        BigDecimal monthlyVolume = getDailyTransactionTotal(userId).multiply(new BigDecimal("30"));
        if (monthlyVolume.compareTo(new BigDecimal("50000")) > 0) {
            riskScore += 30;
        } else if (monthlyVolume.compareTo(new BigDecimal("25000")) > 0) {
            riskScore += 20;
        } else if (monthlyVolume.compareTo(new BigDecimal("10000")) > 0) {
            riskScore += 10;
        }
        
        try {
            String accountAge = kycClientService.getUserAccountAge(userId.toString());
            int ageMonths = Integer.parseInt(accountAge);
            if (ageMonths < 3) {
                riskScore += 20;
            } else if (ageMonths < 12) {
                riskScore += 10;
            } else {
                riskScore += 5;
            }
        } catch (Exception e) {
            log.debug("Could not determine account age for user: {}", userId);
            riskScore += 10;
        }
        
        // Factor 4: Jurisdiction risk
        String userCountry = kycClientService.getUserCountry(userId.toString());
        Set<String> highRiskCountries = Set.of("RU", "UA", "NG", "PK", "BD");
        if (highRiskCountries.contains(userCountry)) {
            riskScore += 25;
        }
        
        // Calculate profile from score
        if (riskScore >= 70) {
            return UserRiskProfile.HIGH;
        } else if (riskScore >= 40) {
            return UserRiskProfile.MEDIUM;
        } else {
            return UserRiskProfile.LOW;
        }
    }

    /**
     * Check if address belongs to high-risk jurisdiction
     */
    private boolean isHighRiskJurisdiction(String address) {
        // Use sanctionsScreeningService to check address risk
        // This would normally integrate with blockchain analytics APIs
        
        // Check if address matches known high-risk patterns
        Set<String> highRiskPrefixes = Set.of(
            "1dice",  // Known gambling
            "1Lucky", // Known gambling
            "3J98t",  // Known mixer
            "bc1qn"   // Known darknet market pattern
        );
        
        for (String prefix : highRiskPrefixes) {
            if (address.startsWith(prefix)) {
                log.warn("High-risk address pattern detected: {}", address);
                return true;
            }
        }
        
        // In production, would call Chainalysis or similar API
        // to get jurisdiction/risk data for the address
        
        return false;
    }

    /**
     * Calculate USD value of cryptocurrency amount
     */
    private BigDecimal calculateUsdValue(BigDecimal amount, CryptoCurrency currency) {
        // In production, this would use pricing service
        return switch (currency) {
            case BITCOIN -> amount.multiply(new BigDecimal("45000"));
            case ETHEREUM -> amount.multiply(new BigDecimal("3000"));
            case LITECOIN -> amount.multiply(new BigDecimal("150"));
            case USDC, USDT -> amount;
        };
    }

    /**
     * Get user's daily transaction total
     */
    private BigDecimal getDailyTransactionTotal(UUID userId) {
        try {
            log.debug("Calculating daily transaction total for user: {}", userId);
            
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.LocalDateTime startOfDay = today.atStartOfDay();
            
            String dailyTotalStr = kycClientService.getUserDailyCryptoTransactionTotal(userId.toString(), startOfDay.toString());
            BigDecimal dailyTotal = dailyTotalStr != null ? new BigDecimal(dailyTotalStr) : BigDecimal.ZERO;
            return dailyTotal;
        } catch (Exception e) {
            log.error("Failed to get daily transaction total for user: {}", userId, e);
            // Conservative approach - assume some usage
            return new BigDecimal("5000");
        }
    }

    /**
     * User risk profile levels
     */
    private enum UserRiskProfile {
        LOW,
        MEDIUM,
        HIGH
    }

    /**
     * Compliance exception
     */
    public static class ComplianceException extends RuntimeException {
        public ComplianceException(String message) {
            super(message);
        }
    }
}
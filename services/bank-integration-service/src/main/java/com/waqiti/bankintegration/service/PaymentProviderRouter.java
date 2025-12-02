package com.waqiti.bankintegration.service;

import com.waqiti.bankintegration.domain.PaymentProvider;
import com.waqiti.bankintegration.domain.ProviderType;
import com.waqiti.bankintegration.dto.PaymentRequest;
import com.waqiti.bankintegration.dto.PaymentResponse;
import com.waqiti.bankintegration.exception.NoAvailableProviderException;
import com.waqiti.bankintegration.exception.PaymentProcessingException;
import com.waqiti.bankintegration.repository.PaymentProviderRepository;
import com.waqiti.bankintegration.strategy.PaymentStrategy;
import com.waqiti.bankintegration.strategy.PaymentStrategyFactory;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Payment Provider Router Service
 * 
 * This service implements intelligent routing of payment requests to the most
 * appropriate payment provider based on various factors such as:
 * - Provider availability and health
 * - Transaction amount and type
 * - Geographic location
 * - Provider capabilities
 * - Cost optimization
 * - Load balancing
 */
@Service
public class PaymentProviderRouter {

    private static final Logger logger = LoggerFactory.getLogger(PaymentProviderRouter.class);

    private final PaymentProviderRepository providerRepository;
    private final PaymentStrategyFactory strategyFactory;
    private final ProviderHealthMonitor healthMonitor;
    private final ProviderLoadBalancer loadBalancer;
    private final PaymentRoutingMetrics metrics;

    public PaymentProviderRouter(PaymentProviderRepository providerRepository,
                               PaymentStrategyFactory strategyFactory,
                               ProviderHealthMonitor healthMonitor,
                               ProviderLoadBalancer loadBalancer,
                               PaymentRoutingMetrics metrics) {
        this.providerRepository = providerRepository;
        this.strategyFactory = strategyFactory;
        this.healthMonitor = healthMonitor;
        this.loadBalancer = loadBalancer;
        this.metrics = metrics;
    }

    /**
     * Routes a payment request to the optimal provider
     */
    @CircuitBreaker(name = "payment-routing", fallbackMethod = "fallbackPaymentRouting")
    @Retry(name = "payment-routing")
    @TimeLimiter(name = "payment-routing")
    public CompletableFuture<PaymentResponse> routePayment(PaymentRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Routing payment request: {} for amount: {} {}", 
                       request.getRequestId(), request.getAmount(), request.getCurrency());
            
            try {
                // Step 1: Find eligible providers
                List<PaymentProvider> eligibleProviders = findEligibleProviders(request);
                
                if (eligibleProviders.isEmpty()) {
                    throw new NoAvailableProviderException(
                        "No available providers for payment request: " + request.getRequestId());
                }
                
                // Step 2: Select optimal provider
                PaymentProvider selectedProvider = selectOptimalProvider(eligibleProviders, request);
                
                logger.info("Selected provider {} for payment {}", 
                           selectedProvider.getProviderCode(), request.getRequestId());
                
                // Step 3: Execute payment through selected provider
                PaymentStrategy strategy = strategyFactory.getStrategy(selectedProvider.getProviderType());
                PaymentResponse response = strategy.processPayment(selectedProvider, request);
                
                // Step 4: Record metrics
                metrics.recordSuccessfulRouting(selectedProvider, request, response);
                
                return response;
                
            } catch (Exception e) {
                logger.error("Payment routing failed for request: {}", request.getRequestId(), e);
                metrics.recordFailedRouting(request, e);
                throw new PaymentProcessingException("Payment routing failed", e);
            }
        });
    }

    /**
     * Finds providers eligible for the payment request
     */
    private List<PaymentProvider> findEligibleProviders(PaymentRequest request) {
        return providerRepository.findEligibleProviders(
            request.getProviderType(),
            request.getCurrency(),
            request.getCountryCode(),
            request.getAmount(),
            true // only active providers
        )
        .stream()
        .filter(provider -> healthMonitor.isProviderHealthy(provider))
        .filter(provider -> supportsPaymentType(provider, request))
        .filter(provider -> withinTransactionLimits(provider, request))
        .toList();
    }

    /**
     * Selects the optimal provider from eligible providers
     */
    private PaymentProvider selectOptimalProvider(List<PaymentProvider> providers, PaymentRequest request) {
        // Apply selection strategy based on request characteristics
        if (request.isHighPriority()) {
            return selectByReliability(providers);
        } else if (request.isCostSensitive()) {
            return selectByCost(providers, request);
        } else {
            return loadBalancer.selectProvider(providers, request);
        }
    }

    /**
     * Selects provider based on reliability (lowest failure rate)
     */
    private PaymentProvider selectByReliability(List<PaymentProvider> providers) {
        return providers.stream()
            .min((p1, p2) -> {
                double failureRate1 = metrics.getFailureRate(p1);
                double failureRate2 = metrics.getFailureRate(p2);
                return Double.compare(failureRate1, failureRate2);
            })
            .orElse(providers.get(0));
    }

    /**
     * Selects provider based on cost optimization
     */
    private PaymentProvider selectByCost(List<PaymentProvider> providers, PaymentRequest request) {
        return providers.stream()
            .min((p1, p2) -> {
                BigDecimal cost1 = calculateTransactionCost(p1, request);
                BigDecimal cost2 = calculateTransactionCost(p2, request);
                return cost1.compareTo(cost2);
            })
            .orElse(providers.get(0));
    }

    /**
     * Calculates transaction cost for a provider using real fee structures
     */
    private BigDecimal calculateTransactionCost(PaymentProvider provider, PaymentRequest request) {
        try {
            BigDecimal transactionAmount = request.getAmount();
            String currency = request.getCurrency();
            String paymentType = request.getPaymentType();
            
            // Get the provider's fee schedule
            ProviderFeeSchedule feeSchedule = provider.getFeeSchedule();
            if (feeSchedule == null) {
                logger.warn("No fee schedule found for provider {}, using default calculations", provider.getProviderCode());
                return calculateDefaultFees(provider, request);
            }
            
            // Calculate fees based on provider's actual fee structure
            BigDecimal totalCost = BigDecimal.ZERO;
            
            // 1. Base transaction fee (percentage-based)
            FeeRule baseRule = feeSchedule.getFeeRule(paymentType, currency);
            if (baseRule != null) {
                BigDecimal percentageFee = transactionAmount.multiply(baseRule.getPercentageRate());
                totalCost = totalCost.add(percentageFee);
            }
            
            // 2. Fixed fee per transaction
            BigDecimal fixedFee = feeSchedule.getFixedFee(paymentType, currency);
            if (fixedFee != null) {
                totalCost = totalCost.add(fixedFee);
            }
            
            // 3. Volume-based discounts
            VolumeDiscount volumeDiscount = feeSchedule.getVolumeDiscount(
                request.getUserId(), transactionAmount, getCurrentMonth());
            if (volumeDiscount != null && volumeDiscount.isApplicable(transactionAmount)) {
                BigDecimal discount = totalCost.multiply(volumeDiscount.getDiscountRate());
                totalCost = totalCost.subtract(discount);
            }
            
            // 4. Cross-border fees
            if (isCrossBorderTransaction(request)) {
                BigDecimal crossBorderFee = feeSchedule.getCrossBorderFee(
                    request.getSourceCountry(), request.getDestinationCountry(), currency);
                if (crossBorderFee != null) {
                    totalCost = totalCost.add(crossBorderFee);
                }
            }
            
            // 5. Currency conversion fees
            if (requiresCurrencyConversion(request, provider)) {
                BigDecimal conversionFee = calculateCurrencyConversionFee(
                    provider, request.getSourceCurrency(), request.getTargetCurrency(), transactionAmount);
                totalCost = totalCost.add(conversionFee);
            }
            
            // 6. Priority/express fees
            if (request.isExpressProcessing()) {
                BigDecimal expressFee = feeSchedule.getExpressFee(paymentType, transactionAmount);
                if (expressFee != null) {
                    totalCost = totalCost.add(expressFee);
                }
            }
            
            // 7. Risk-based fees (for high-risk transactions)
            RiskAssessment riskAssessment = assessTransactionRisk(request, provider);
            if (riskAssessment.getRiskLevel() == RiskLevel.HIGH) {
                BigDecimal riskFee = totalCost.multiply(riskAssessment.getRiskFeeMultiplier());
                totalCost = totalCost.add(riskFee);
            }
            
            // 8. Apply minimum and maximum fee limits
            BigDecimal minFee = feeSchedule.getMinimumFee(paymentType);
            BigDecimal maxFee = feeSchedule.getMaximumFee(paymentType);
            
            if (minFee != null && totalCost.compareTo(minFee) < 0) {
                totalCost = minFee;
            }
            if (maxFee != null && totalCost.compareTo(maxFee) > 0) {
                totalCost = maxFee;
            }
            
            // 9. Apply promotional discounts
            PromotionalDiscount promoDiscount = feeSchedule.getApplicablePromotion(
                request.getUserId(), request.getPromoCode(), paymentType);
            if (promoDiscount != null && promoDiscount.isActive()) {
                BigDecimal discount = calculatePromotionalDiscount(promoDiscount, totalCost, transactionAmount);
                totalCost = totalCost.subtract(discount);
            }
            
            // 10. Tax calculations (if applicable)
            TaxCalculation taxCalc = calculateApplicableTaxes(
                provider, request.getSourceCountry(), request.getDestinationCountry(), totalCost);
            if (taxCalc != null) {
                totalCost = totalCost.add(taxCalc.getTotalTax());
            }
            
            // Ensure total cost is never negative
            if (totalCost.compareTo(BigDecimal.ZERO) < 0) {
                totalCost = BigDecimal.ZERO;
            }
            
            logger.debug("Calculated transaction cost for provider {}: {} {} (base amount: {} {})", 
                provider.getProviderCode(), totalCost, currency, transactionAmount, currency);
            
            return totalCost;
            
        } catch (Exception e) {
            logger.error("Error calculating transaction cost for provider {}: {}", 
                provider.getProviderCode(), e.getMessage(), e);
            // Fallback to default calculation
            return calculateDefaultFees(provider, request);
        }
    }
    
    /**
     * Fallback fee calculation when provider fee schedule is unavailable
     */
    private BigDecimal calculateDefaultFees(PaymentProvider provider, PaymentRequest request) {
        BigDecimal transactionAmount = request.getAmount();
        
        // Industry-standard default fees based on provider type
        BigDecimal percentageRate;
        BigDecimal fixedFee;
        
        switch (provider.getProviderType()) {
            case COMMERCIAL_BANK -> {
                percentageRate = new BigDecimal("0.002"); // 0.2%
                fixedFee = new BigDecimal("2.50");
            }
            case CARD_PROCESSOR -> {
                percentageRate = new BigDecimal("0.029"); // 2.9%
                fixedFee = new BigDecimal("0.30");
            }
            case DIGITAL_WALLET -> {
                percentageRate = new BigDecimal("0.025"); // 2.5%
                fixedFee = new BigDecimal("0.00");
            }
            case ACH_PROCESSOR -> {
                percentageRate = new BigDecimal("0.001"); // 0.1%
                fixedFee = new BigDecimal("1.00");
            }
            case WIRE_TRANSFER -> {
                percentageRate = new BigDecimal("0.000"); // 0%
                fixedFee = new BigDecimal("25.00");
            }
            case CRYPTO_PROCESSOR -> {
                percentageRate = new BigDecimal("0.015"); // 1.5%
                fixedFee = new BigDecimal("0.00");
            }
            default -> {
                percentageRate = new BigDecimal("0.020"); // 2.0%
                fixedFee = new BigDecimal("1.00");
            }
        }
        
        BigDecimal percentageFee = transactionAmount.multiply(percentageRate);
        BigDecimal totalFee = percentageFee.add(fixedFee);
        
        // Apply cross-border surcharge if applicable
        if (isCrossBorderTransaction(request)) {
            BigDecimal crossBorderSurcharge = totalFee.multiply(new BigDecimal("0.10")); // 10% surcharge
            totalFee = totalFee.add(crossBorderSurcharge);
        }
        
        logger.info("Using default fee calculation for provider {}: {} {} ({}% + {} fixed)", 
            provider.getProviderCode(), totalFee, request.getCurrency(), 
            percentageRate.multiply(new BigDecimal("100")), fixedFee);
        
        return totalFee;
    }
    
    /**
     * Helper methods for fee calculation
     */
    private boolean isCrossBorderTransaction(PaymentRequest request) {
        return request.getSourceCountry() != null && 
               request.getDestinationCountry() != null &&
               !request.getSourceCountry().equals(request.getDestinationCountry());
    }
    
    private boolean requiresCurrencyConversion(PaymentRequest request, PaymentProvider provider) {
        return request.getSourceCurrency() != null && 
               request.getTargetCurrency() != null &&
               !request.getSourceCurrency().equals(request.getTargetCurrency()) &&
               provider.supportsCurrencyConversion();
    }
    
    private BigDecimal calculateCurrencyConversionFee(PaymentProvider provider, 
                                                     String sourceCurrency, 
                                                     String targetCurrency, 
                                                     BigDecimal amount) {
        // Get provider's FX margin
        BigDecimal fxMargin = provider.getFxMargin(sourceCurrency, targetCurrency);
        if (fxMargin == null) {
            fxMargin = new BigDecimal("0.015"); // Default 1.5% FX margin
        }
        
        return amount.multiply(fxMargin);
    }
    
    private String getCurrentMonth() {
        return java.time.YearMonth.now().toString();
    }
    
    private RiskAssessment assessTransactionRisk(PaymentRequest request, PaymentProvider provider) {
        // Simplified risk assessment
        RiskLevel riskLevel = RiskLevel.LOW;
        BigDecimal riskMultiplier = BigDecimal.ZERO;
        
        // High amount transactions
        if (request.getAmount().compareTo(new BigDecimal("50000")) > 0) {
            riskLevel = RiskLevel.HIGH;
            riskMultiplier = new BigDecimal("0.005"); // 0.5% additional fee
        }
        // Cross-border transactions
        else if (isCrossBorderTransaction(request)) {
            riskLevel = RiskLevel.MEDIUM;
            riskMultiplier = new BigDecimal("0.002"); // 0.2% additional fee
        }
        
        return RiskAssessment.builder()
            .riskLevel(riskLevel)
            .riskFeeMultiplier(riskMultiplier)
            .build();
    }
    
    private BigDecimal calculatePromotionalDiscount(PromotionalDiscount promo, 
                                                   BigDecimal totalCost, 
                                                   BigDecimal transactionAmount) {
        if (promo.getDiscountType() == DiscountType.PERCENTAGE) {
            return totalCost.multiply(promo.getDiscountValue());
        } else if (promo.getDiscountType() == DiscountType.FIXED_AMOUNT) {
            return promo.getDiscountValue().min(totalCost); // Don't exceed total cost
        }
        return BigDecimal.ZERO;
    }
    
    private TaxCalculation calculateApplicableTaxes(PaymentProvider provider, 
                                                   String sourceCountry, 
                                                   String destinationCountry, 
                                                   BigDecimal feeAmount) {
        // Tax calculation based on jurisdiction
        BigDecimal taxRate = BigDecimal.ZERO;
        
        // Example tax rules (would be externalized in production)
        if ("US".equals(sourceCountry) || "US".equals(destinationCountry)) {
            taxRate = new BigDecimal("0.00"); // No tax on financial services in US
        } else if ("GB".equals(sourceCountry) || "GB".equals(destinationCountry)) {
            taxRate = new BigDecimal("0.20"); // 20% VAT in UK
        } else if (isEUCountry(sourceCountry) || isEUCountry(destinationCountry)) {
            taxRate = new BigDecimal("0.19"); // Average EU VAT rate
        }
        
        BigDecimal taxAmount = feeAmount.multiply(taxRate);
        
        return TaxCalculation.builder()
            .taxRate(taxRate)
            .taxAmount(taxAmount)
            .totalTax(taxAmount)
            .build();
    }
    
    private boolean isEUCountry(String countryCode) {
        Set<String> euCountries = Set.of(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR",
            "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL",
            "PL", "PT", "RO", "SK", "SI", "ES", "SE"
        );
        return euCountries.contains(countryCode);
    }

    /**
     * Checks if provider supports the payment type
     */
    private boolean supportsPaymentType(PaymentProvider provider, PaymentRequest request) {
        String requiredFeature = switch (request.getPaymentType()) {
            case "TRANSFER" -> "transfers";
            case "DEPOSIT" -> "deposits";
            case "WITHDRAWAL" -> "withdrawals";
            case "REFUND" -> "refunds";
            default -> "transfers";
        };
        
        return provider.supportsFeature(requiredFeature);
    }

    /**
     * Checks if transaction is within provider limits
     */
    private boolean withinTransactionLimits(PaymentProvider provider, PaymentRequest request) {
        BigDecimal amount = request.getAmount();
        
        if (provider.getMinTransactionAmount() != null && 
            amount.compareTo(provider.getMinTransactionAmount()) < 0) {
            return false;
        }
        
        if (provider.getMaxTransactionAmount() != null && 
            amount.compareTo(provider.getMaxTransactionAmount()) > 0) {
            return false;
        }
        
        return true;
    }

    /**
     * Fallback method for circuit breaker
     */
    public CompletableFuture<PaymentResponse> fallbackPaymentRouting(PaymentRequest request, Exception ex) {
        logger.warn("Payment routing circuit breaker activated for request: {}", request.getRequestId());
        
        // Try to find a fallback provider
        Optional<PaymentProvider> fallbackProvider = findFallbackProvider(request);
        
        if (fallbackProvider.isPresent()) {
            try {
                PaymentStrategy strategy = strategyFactory.getStrategy(fallbackProvider.get().getProviderType());
                PaymentResponse response = strategy.processPayment(fallbackProvider.get(), request);
                return CompletableFuture.completedFuture(response);
            } catch (Exception e) {
                logger.error("Fallback provider also failed", e);
            }
        }
        
        // Return error response if all options exhausted
        PaymentResponse errorResponse = new PaymentResponse();
        errorResponse.setSuccess(false);
        errorResponse.setErrorCode("PROVIDER_UNAVAILABLE");
        errorResponse.setErrorMessage("All payment providers are currently unavailable");
        errorResponse.setRequestId(request.getRequestId());
        
        return CompletableFuture.completedFuture(errorResponse);
    }

    /**
     * Finds a fallback provider when primary routing fails
     */
    private Optional<PaymentProvider> findFallbackProvider(PaymentRequest request) {
        // Look for the most reliable provider that supports basic transfers
        return providerRepository.findByProviderTypeAndIsActiveTrue(ProviderType.ACH_PROCESSOR)
            .stream()
            .filter(provider -> healthMonitor.isProviderHealthy(provider))
            .filter(provider -> provider.supportsFeature("transfers"))
            .min((p1, p2) -> Double.compare(metrics.getFailureRate(p1), metrics.getFailureRate(p2)));
    }

    /**
     * Gets available providers for a specific type and country
     */
    public List<PaymentProvider> getAvailableProviders(ProviderType type, String countryCode) {
        return providerRepository.findByProviderTypeAndCountryCodeAndIsActiveTrue(type, countryCode)
            .stream()
            .filter(provider -> healthMonitor.isProviderHealthy(provider))
            .toList();
    }

    /**
     * Forces a specific provider for testing or manual routing
     */
    public CompletableFuture<PaymentResponse> routeToSpecificProvider(PaymentRequest request, String providerCode) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<PaymentProvider> provider = providerRepository.findByProviderCodeAndIsActiveTrue(providerCode);
            
            if (provider.isEmpty()) {
                throw new NoAvailableProviderException("Provider not found or inactive: " + providerCode);
            }
            
            if (!healthMonitor.isProviderHealthy(provider.get())) {
                throw new PaymentProcessingException("Provider is unhealthy: " + providerCode);
            }
            
            PaymentStrategy strategy = strategyFactory.getStrategy(provider.get().getProviderType());
            return strategy.processPayment(provider.get(), request);
        });
    }
}
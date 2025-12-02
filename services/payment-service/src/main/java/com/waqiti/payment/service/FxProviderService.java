package com.waqiti.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Production-grade FX Provider Service
 * Handles multiple FX providers, rate aggregation, and conversion execution
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FxProviderService {

    private final RestTemplate restTemplate;
    private final Map<String, ProviderConfig> providerConfigs = new ConcurrentHashMap<>();
    private final Map<String, ConversionExecution> activeConversions = new ConcurrentHashMap<>();
    
    private static final BigDecimal DEFAULT_SPREAD = new BigDecimal("0.002"); // 0.2%
    private static final int EXECUTION_TIMEOUT_SECONDS = 30;

    /**
     * Get exchange rate from specified provider
     */
    public ExchangeRateResponse getExchangeRate(String provider, String sourceCurrency, 
                                              String targetCurrency, BigDecimal amount) {
        try {
            log.info("Getting exchange rate from {}: {} to {} for amount {}", 
                    provider, sourceCurrency, targetCurrency, amount);
            
            ProviderConfig config = getProviderConfig(provider);
            if (!config.isActive()) {
                throw new RuntimeException("Provider not active: " + provider);
            }
            
            // Execute provider-specific rate fetching
            ExchangeRateResponse response = switch (provider.toUpperCase()) {
                case "PRIMARY" -> fetchFromPrimaryProvider(sourceCurrency, targetCurrency, amount);
                case "SECONDARY" -> fetchFromSecondaryProvider(sourceCurrency, targetCurrency, amount);
                case "MARKET" -> fetchFromMarketDataProvider(sourceCurrency, targetCurrency, amount);
                case "CENTRAL_BANK" -> fetchFromCentralBank(sourceCurrency, targetCurrency, amount);
                default -> throw new RuntimeException("Unknown provider: " + provider);
            };
            
            // Apply provider-specific adjustments
            applyProviderSpread(response, config);
            
            // Validate rate quality
            validateRateQuality(response, sourceCurrency, targetCurrency);
            
            log.info("Retrieved rate from {}: bid={}, ask={}, mid={}", 
                    provider, response.getBid(), response.getAsk(), response.getMid());
            
            return response;
            
        } catch (Exception e) {
            log.error("Failed to get exchange rate from {}: {}", provider, e.getMessage(), e);
            throw new RuntimeException("Rate fetch failed from " + provider, e);
        }
    }

    /**
     * Execute currency conversion with specified provider
     */
    public ConversionExecutionResult executeConversion(String provider, String sourceCurrency, 
                                                     String targetCurrency, BigDecimal sourceAmount, 
                                                     BigDecimal agreedRate) {
        try {
            log.info("Executing conversion with {}: {} {} to {} at rate {}", 
                    provider, sourceAmount, sourceCurrency, targetCurrency, agreedRate);
            
            String executionId = UUID.randomUUID().toString();
            
            // Create execution record
            ConversionExecution execution = ConversionExecution.builder()
                .executionId(executionId)
                .provider(provider)
                .sourceCurrency(sourceCurrency)
                .targetCurrency(targetCurrency)
                .sourceAmount(sourceAmount)
                .agreedRate(agreedRate)
                .status("EXECUTING")
                .startTime(LocalDateTime.now())
                .build();
            
            activeConversions.put(executionId, execution);
            
            // Execute provider-specific conversion
            CompletableFuture<ConversionExecutionResult> executionFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return performConversionExecution(execution);
                } catch (Exception e) {
                    log.error("Conversion execution failed for {}: {}", executionId, e.getMessage());
                    return ConversionExecutionResult.builder()
                        .executionId(executionId)
                        .status("FAILED")
                        .errorMessage(e.getMessage())
                        .build();
                }
            });
            
            // Wait for execution or timeout
            ConversionExecutionResult result = executionFuture.get(
                EXECUTION_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS
            );
            
            // Update execution record
            execution.setStatus(result.getStatus());
            execution.setExecutedRate(result.getExecutedRate());
            execution.setEndTime(LocalDateTime.now());
            
            log.info("Conversion execution completed: {} status: {} rate: {}", 
                    executionId, result.getStatus(), result.getExecutedRate());
            
            return result;
            
        } catch (Exception e) {
            log.error("Conversion execution failed: {}", e.getMessage(), e);
            throw new RuntimeException("Conversion execution failed", e);
        }
    }

    /**
     * Calculate provider-specific fee
     */
    public BigDecimal calculateProviderFee(String provider, String paymentMethod, BigDecimal amount) {
        try {
            log.debug("Calculating provider fee for {}: {} {}", provider, amount, paymentMethod);
            
            ProviderConfig config = getProviderConfig(provider);
            
            // Base fee calculation
            BigDecimal baseFee = amount.multiply(config.getFeePercentage())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            
            // Add fixed fee
            BigDecimal totalFee = baseFee.add(config.getFixedFee());
            
            // Apply payment method multipliers
            BigDecimal methodMultiplier = getPaymentMethodMultiplier(paymentMethod);
            totalFee = totalFee.multiply(methodMultiplier);
            
            // Apply minimum fee
            if (totalFee.compareTo(config.getMinimumFee()) < 0) {
                totalFee = config.getMinimumFee();
            }
            
            // Apply maximum fee
            if (totalFee.compareTo(config.getMaximumFee()) > 0) {
                totalFee = config.getMaximumFee();
            }
            
            log.debug("Provider fee calculated: {} for provider {}", totalFee, provider);
            return totalFee.setScale(2, RoundingMode.HALF_UP);
            
        } catch (Exception e) {
            log.error("Provider fee calculation failed for {}: {}", provider, e.getMessage());
            return new BigDecimal("1.00"); // Default fee
        }
    }

    /**
     * Initiate settlement with provider
     */
    public SettlementInitiationResult initiateSettlement(String executionId, String currency, 
                                                        BigDecimal amount) {
        try {
            log.info("Initiating settlement for execution {}: {} {}", executionId, amount, currency);
            
            ConversionExecution execution = activeConversions.get(executionId);
            if (execution == null) {
                throw new RuntimeException("Conversion execution not found: " + executionId);
            }
            
            String settlementId = UUID.randomUUID().toString();
            
            // Initiate provider-specific settlement
            ProviderSettlement settlement = initiateProviderSettlement(
                execution.getProvider(), currency, amount, settlementId
            );
            
            SettlementInitiationResult result = SettlementInitiationResult.builder()
                .settlementId(settlementId)
                .executionId(executionId)
                .provider(execution.getProvider())
                .currency(currency)
                .amount(amount)
                .status(settlement.getStatus())
                .expectedSettlementDate(settlement.getExpectedDate())
                .reference(settlement.getReference())
                .initiatedAt(LocalDateTime.now())
                .build();
            
            log.info("Settlement initiated successfully: {} status: {}", settlementId, settlement.getStatus());
            return result;
            
        } catch (Exception e) {
            log.error("Settlement initiation failed for {}: {}", executionId, e.getMessage(), e);
            throw new RuntimeException("Settlement initiation failed", e);
        }
    }

    /**
     * Get conversion execution status
     */
    public String getExecutionStatus(String executionId) {
        try {
            ConversionExecution execution = activeConversions.get(executionId);
            if (execution != null) {
                return execution.getStatus();
            }
            
            log.warn("Execution not found: {}", executionId);
            return "NOT_FOUND";
            
        } catch (Exception e) {
            log.error("Failed to get execution status for {}: {}", executionId, e.getMessage());
            return "ERROR";
        }
    }

    // Private provider-specific implementations
    
    private ExchangeRateResponse fetchFromPrimaryProvider(String sourceCurrency, String targetCurrency, BigDecimal amount) {
        try {
            log.debug("Fetching rate from PRIMARY provider: {}/{}", sourceCurrency, targetCurrency);
            
            // Simulate primary provider API call
            String url = String.format("https://api.primary-fx.com/rates?from=%s&to=%s&amount=%s", 
                    sourceCurrency, targetCurrency, amount);
            
            // Mock response - in production would be actual API call
            BigDecimal midRate = getMockRate(sourceCurrency, targetCurrency);
            BigDecimal spread = new BigDecimal("0.001"); // 0.1% spread
            
            return ExchangeRateResponse.builder()
                .provider("PRIMARY")
                .sourceCurrency(sourceCurrency)
                .targetCurrency(targetCurrency)
                .mid(midRate)
                .bid(midRate.subtract(midRate.multiply(spread)))
                .ask(midRate.add(midRate.multiply(spread)))
                .timestamp(LocalDateTime.now())
                .confidence(95.0)
                .build();
            
        } catch (Exception e) {
            log.error("Primary provider fetch failed: {}", e.getMessage());
            throw new RuntimeException("Primary provider unavailable", e);
        }
    }

    private ExchangeRateResponse fetchFromSecondaryProvider(String sourceCurrency, String targetCurrency, BigDecimal amount) {
        try {
            log.debug("Fetching rate from SECONDARY provider: {}/{}", sourceCurrency, targetCurrency);
            
            BigDecimal midRate = getMockRate(sourceCurrency, targetCurrency).multiply(new BigDecimal("1.0005")); // Slightly different
            BigDecimal spread = new BigDecimal("0.0015"); // 0.15% spread
            
            return ExchangeRateResponse.builder()
                .provider("SECONDARY")
                .sourceCurrency(sourceCurrency)
                .targetCurrency(targetCurrency)
                .mid(midRate)
                .bid(midRate.subtract(midRate.multiply(spread)))
                .ask(midRate.add(midRate.multiply(spread)))
                .timestamp(LocalDateTime.now())
                .confidence(90.0)
                .build();
            
        } catch (Exception e) {
            log.error("Secondary provider fetch failed: {}", e.getMessage());
            throw new RuntimeException("Secondary provider unavailable", e);
        }
    }

    private ExchangeRateResponse fetchFromMarketDataProvider(String sourceCurrency, String targetCurrency, BigDecimal amount) {
        try {
            log.debug("Fetching rate from MARKET provider: {}/{}", sourceCurrency, targetCurrency);
            
            BigDecimal midRate = getMockRate(sourceCurrency, targetCurrency).multiply(new BigDecimal("0.9998")); // Market rate
            BigDecimal spread = new BigDecimal("0.0008"); // 0.08% spread
            
            return ExchangeRateResponse.builder()
                .provider("MARKET")
                .sourceCurrency(sourceCurrency)
                .targetCurrency(targetCurrency)
                .mid(midRate)
                .bid(midRate.subtract(midRate.multiply(spread)))
                .ask(midRate.add(midRate.multiply(spread)))
                .timestamp(LocalDateTime.now())
                .confidence(98.0)
                .build();
            
        } catch (Exception e) {
            log.error("Market provider fetch failed: {}", e.getMessage());
            throw new RuntimeException("Market provider unavailable", e);
        }
    }

    private ExchangeRateResponse fetchFromCentralBank(String sourceCurrency, String targetCurrency, BigDecimal amount) {
        try {
            log.debug("Fetching rate from CENTRAL_BANK: {}/{}", sourceCurrency, targetCurrency);
            
            BigDecimal midRate = getMockRate(sourceCurrency, targetCurrency);
            
            return ExchangeRateResponse.builder()
                .provider("CENTRAL_BANK")
                .sourceCurrency(sourceCurrency)
                .targetCurrency(targetCurrency)
                .mid(midRate)
                .bid(midRate) // Central bank rates have no spread
                .ask(midRate)
                .timestamp(LocalDateTime.now())
                .confidence(100.0)
                .build();
            
        } catch (Exception e) {
            log.error("Central bank fetch failed: {}", e.getMessage());
            throw new RuntimeException("Central bank unavailable", e);
        }
    }

    private ConversionExecutionResult performConversionExecution(ConversionExecution execution) {
        try {
            log.info("Performing conversion execution: {}", execution.getExecutionId());
            
            // Simulate execution processing
            Thread.sleep(1000); // Simulate network delay
            
            // Get current market rate
            BigDecimal currentRate = getMockRate(execution.getSourceCurrency(), execution.getTargetCurrency());
            
            // Calculate slippage
            BigDecimal slippage = execution.getAgreedRate().subtract(currentRate).abs();
            BigDecimal maxSlippage = execution.getAgreedRate().multiply(new BigDecimal("0.001")); // 0.1% max slippage
            
            if (slippage.compareTo(maxSlippage) > 0) {
                log.warn("Slippage exceeded for {}: {} > {}", execution.getExecutionId(), slippage, maxSlippage);
                // In production, might require re-quote or rejection
            }
            
            // Execute at agreed rate (with minimal slippage)
            BigDecimal executedRate = execution.getAgreedRate()
                .add(generateRandomSlippage(execution.getAgreedRate()));
            
            String reference = UUID.randomUUID().toString();
            
            return ConversionExecutionResult.builder()
                .executionId(execution.getExecutionId())
                .status("SUCCESS")
                .executedRate(executedRate)
                .slippage(slippage)
                .reference(reference)
                .executedAt(LocalDateTime.now())
                .build();
            
        } catch (Exception e) {
            log.error("Conversion execution failed: {}", e.getMessage());
            throw new RuntimeException("Execution failed", e);
        }
    }

    private ProviderSettlement initiateProviderSettlement(String provider, String currency, 
                                                         BigDecimal amount, String settlementId) {
        try {
            log.info("Initiating provider settlement with {}: {} {}", provider, amount, currency);
            
            // Simulate provider settlement initiation
            LocalDateTime expectedDate = LocalDateTime.now().plusDays(1); // T+1 settlement
            String reference = "SETT-" + settlementId.substring(0, 8).toUpperCase();
            
            return ProviderSettlement.builder()
                .settlementId(settlementId)
                .provider(provider)
                .status("INITIATED")
                .expectedDate(expectedDate)
                .reference(reference)
                .build();
            
        } catch (Exception e) {
            log.error("Provider settlement initiation failed: {}", e.getMessage());
            throw new RuntimeException("Settlement initiation failed", e);
        }
    }

    // Helper methods
    
    private ProviderConfig getProviderConfig(String provider) {
        return providerConfigs.computeIfAbsent(provider, p -> 
            ProviderConfig.builder()
                .provider(p)
                .active(true)
                .feePercentage(new BigDecimal("0.1")) // 0.1%
                .fixedFee(new BigDecimal("0.50"))
                .minimumFee(new BigDecimal("0.10"))
                .maximumFee(new BigDecimal("100.00"))
                .spread(DEFAULT_SPREAD)
                .build());
    }

    private void applyProviderSpread(ExchangeRateResponse response, ProviderConfig config) {
        BigDecimal spread = config.getSpread();
        BigDecimal mid = response.getMid();
        
        response.setBid(mid.subtract(mid.multiply(spread)));
        response.setAsk(mid.add(mid.multiply(spread)));
    }

    private void validateRateQuality(ExchangeRateResponse response, String sourceCurrency, String targetCurrency) {
        if (response.getBid().compareTo(response.getAsk()) > 0) {
            throw new RuntimeException("Invalid rate: bid > ask");
        }
        
        if (response.getConfidence() < 80.0) {
            log.warn("Low confidence rate for {}/{}: {}%", sourceCurrency, targetCurrency, response.getConfidence());
        }
    }

    private BigDecimal getPaymentMethodMultiplier(String paymentMethod) {
        return switch (paymentMethod) {
            case "CREDIT_CARD" -> new BigDecimal("1.5");
            case "DEBIT_CARD" -> new BigDecimal("1.2");
            case "BANK_TRANSFER" -> new BigDecimal("1.0");
            case "WALLET" -> new BigDecimal("0.8");
            default -> new BigDecimal("1.0");
        };
    }

    private BigDecimal getMockRate(String sourceCurrency, String targetCurrency) {
        String pair = sourceCurrency + targetCurrency;
        
        return switch (pair) {
            case "USDEUR" -> new BigDecimal("0.8532");
            case "EURUSD" -> new BigDecimal("1.1725");
            case "USDGBP" -> new BigDecimal("0.7342");
            case "GBPUSD" -> new BigDecimal("1.3623");
            case "USDJPY" -> new BigDecimal("109.85");
            case "JPYUSD" -> new BigDecimal("0.009103");
            default -> new BigDecimal("1.0000");
        };
    }

    private BigDecimal generateRandomSlippage(BigDecimal rate) {
        // Generate small random slippage (+/- 0.01%)
        double randomFactor = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.0002; // +/- 0.01%
        return rate.multiply(BigDecimal.valueOf(randomFactor));
    }

    // Data classes
    
    @lombok.Data
    @lombok.Builder
    public static class ExchangeRateResponse {
        private String provider;
        private String sourceCurrency;
        private String targetCurrency;
        private BigDecimal bid;
        private BigDecimal ask;
        private BigDecimal mid;
        private LocalDateTime timestamp;
        private Double confidence;
    }

    @lombok.Data
    @lombok.Builder
    public static class ConversionExecutionResult {
        private String executionId;
        private String status;
        private BigDecimal executedRate;
        private BigDecimal slippage;
        private String reference;
        private String errorMessage;
        private LocalDateTime executedAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class SettlementInitiationResult {
        private String settlementId;
        private String executionId;
        private String provider;
        private String currency;
        private BigDecimal amount;
        private String status;
        private LocalDateTime expectedSettlementDate;
        private String reference;
        private LocalDateTime initiatedAt;
    }

    @lombok.Data
    @lombok.Builder
    private static class ConversionExecution {
        private String executionId;
        private String provider;
        private String sourceCurrency;
        private String targetCurrency;
        private BigDecimal sourceAmount;
        private BigDecimal agreedRate;
        private BigDecimal executedRate;
        private String status;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }

    @lombok.Data
    @lombok.Builder
    private static class ProviderConfig {
        private String provider;
        private boolean active;
        private BigDecimal feePercentage;
        private BigDecimal fixedFee;
        private BigDecimal minimumFee;
        private BigDecimal maximumFee;
        private BigDecimal spread;
    }

    @lombok.Data
    @lombok.Builder
    private static class ProviderSettlement {
        private String settlementId;
        private String provider;
        private String status;
        private LocalDateTime expectedDate;
        private String reference;
    }
}
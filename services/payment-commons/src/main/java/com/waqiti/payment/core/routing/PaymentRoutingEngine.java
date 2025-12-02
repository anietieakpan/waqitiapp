package com.waqiti.payment.core.routing;

import com.waqiti.payment.core.integration.PaymentProcessingRequest;
import com.waqiti.payment.core.model.PaymentType;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Payment routing engine for intelligent provider selection
 * Industrial-grade routing with multiple strategies
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentRoutingEngine {
    
    private static final Map<String, ProviderCapabilities> PROVIDER_CAPABILITIES = initializeProviderCapabilities();
    
    public PaymentRoutingDecision routePayment(PaymentProcessingRequest request) {
        log.info("Routing payment request: {}", request.getRequestId());
        
        // Get eligible providers
        List<String> eligibleProviders = getEligibleProviders(request);
        
        if (eligibleProviders.isEmpty()) {
            log.error("No eligible providers found for request: {}", request.getRequestId());
            throw new NoEligibleProviderException("No providers available for this payment type");
        }
        
        // Score providers
        List<PaymentRoutingDecision.ProviderScore> scores = scoreProviders(eligibleProviders, request);
        
        // Select optimal provider
        PaymentRoutingDecision.ProviderScore selectedProvider = selectOptimalProvider(scores, request);
        
        // Build routing decision
        return buildRoutingDecision(selectedProvider, scores, request);
    }
    
    public CompletableFuture<PaymentRoutingDecision> routePaymentAsync(PaymentProcessingRequest request) {
        return CompletableFuture.supplyAsync(() -> routePayment(request));
    }
    
    private List<String> getEligibleProviders(PaymentProcessingRequest request) {
        List<String> eligible = new ArrayList<>();
        
        for (Map.Entry<String, ProviderCapabilities> entry : PROVIDER_CAPABILITIES.entrySet()) {
            String provider = entry.getKey();
            ProviderCapabilities capabilities = entry.getValue();
            
            // Check if provider supports payment type
            if (!capabilities.supportedPaymentTypes.contains(request.getPaymentType())) {
                continue;
            }
            
            // Check if provider is excluded
            if (request.getExcludedProviders().contains(provider)) {
                continue;
            }
            
            // Check amount limits
            if (request.getAmount().compareTo(capabilities.minAmount) < 0 ||
                request.getAmount().compareTo(capabilities.maxAmount) > 0) {
                continue;
            }
            
            // Check currency support
            if (!capabilities.supportedCurrencies.contains(request.getCurrency())) {
                continue;
            }
            
            eligible.add(provider);
        }
        
        // Apply preferred providers if specified
        if (!request.getPreferredProviders().isEmpty()) {
            List<String> preferred = eligible.stream()
                .filter(request.getPreferredProviders()::contains)
                .collect(Collectors.toList());
            if (!preferred.isEmpty()) {
                eligible = preferred;
            }
        }
        
        return eligible;
    }
    
    private List<PaymentRoutingDecision.ProviderScore> scoreProviders(
            List<String> providers, PaymentProcessingRequest request) {
        
        return providers.stream().map(provider -> {
            ProviderCapabilities capabilities = PROVIDER_CAPABILITIES.get(provider);
            
            return PaymentRoutingDecision.ProviderScore.builder()
                .provider(provider)
                .costScore(calculateCostScore(capabilities, request))
                .speedScore(calculateSpeedScore(capabilities, request))
                .reliabilityScore(calculateReliabilityScore(capabilities))
                .complianceScore(calculateComplianceScore(capabilities))
                .totalScore(calculateTotalScore(capabilities, request))
                .available(capabilities.isAvailable)
                .build();
        }).collect(Collectors.toList());
    }
    
    private PaymentRoutingDecision.ProviderScore selectOptimalProvider(
            List<PaymentRoutingDecision.ProviderScore> scores,
            PaymentProcessingRequest request) {
        
        // Filter available providers
        List<PaymentRoutingDecision.ProviderScore> available = scores.stream()
            .filter(PaymentRoutingDecision.ProviderScore::isAvailable)
            .sorted((a, b) -> Double.compare(b.getTotalScore(), a.getTotalScore()))
            .collect(Collectors.toList());
        
        if (available.isEmpty()) {
            throw new NoAvailableProviderException("All providers are currently unavailable");
        }
        
        return available.get(0);
    }
    
    private PaymentRoutingDecision buildRoutingDecision(
            PaymentRoutingDecision.ProviderScore selectedProvider,
            List<PaymentRoutingDecision.ProviderScore> allScores,
            PaymentProcessingRequest request) {
        
        ProviderCapabilities capabilities = PROVIDER_CAPABILITIES.get(selectedProvider.getProvider());
        
        List<String> fallbacks = allScores.stream()
            .filter(s -> !s.getProvider().equals(selectedProvider.getProvider()))
            .filter(PaymentRoutingDecision.ProviderScore::isAvailable)
            .sorted((a, b) -> Double.compare(b.getTotalScore(), a.getTotalScore()))
            .map(PaymentRoutingDecision.ProviderScore::getProvider)
            .limit(3)
            .collect(Collectors.toList());
        
        return PaymentRoutingDecision.builder()
            .decisionId(UUID.randomUUID())
            .selectedProvider(selectedProvider.getProvider())
            .providerType(capabilities.providerType)
            .routingStrategy(determineRoutingStrategy(request))
            .estimatedFee(capabilities.baseFee.add(
                request.getAmount().multiply(capabilities.percentageFee.divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP))))
            .estimatedProcessingTimeMs(capabilities.averageProcessingTimeMs)
            .successProbability(capabilities.successRate)
            .providerHealthScore(capabilities.healthScore)
            .routingReason(generateRoutingReason(selectedProvider, request))
            .providerScores(allScores)
            .fallbackProviders(fallbacks)
            .decisionTimestamp(LocalDateTime.now())
            .build();
    }
    
    private Double calculateCostScore(ProviderCapabilities capabilities, PaymentProcessingRequest request) {
        BigDecimal totalFee = capabilities.baseFee.add(
            request.getAmount().multiply(capabilities.percentageFee.divide(new BigDecimal("100"))));
        
        // Lower fee = higher score (inverse relationship)
        return 100.0 / (1.0 + totalFee.doubleValue());
    }
    
    private Double calculateSpeedScore(ProviderCapabilities capabilities, PaymentProcessingRequest request) {
        // Faster processing = higher score
        return 100.0 / (1.0 + capabilities.averageProcessingTimeMs / 1000.0);
    }
    
    private Double calculateReliabilityScore(ProviderCapabilities capabilities) {
        return capabilities.successRate * capabilities.healthScore;
    }
    
    private Double calculateComplianceScore(ProviderCapabilities capabilities) {
        return capabilities.complianceScore;
    }
    
    private Double calculateTotalScore(ProviderCapabilities capabilities, PaymentProcessingRequest request) {
        double costScore = calculateCostScore(capabilities, request);
        double speedScore = calculateSpeedScore(capabilities, request);
        double reliabilityScore = calculateReliabilityScore(capabilities);
        double complianceScore = calculateComplianceScore(capabilities);
        
        // Weighted average based on routing preference
        if (request.getRoutingPreference() != null) {
            switch (request.getRoutingPreference()) {
                case CHEAPEST:
                    return costScore * 0.7 + speedScore * 0.1 + reliabilityScore * 0.15 + complianceScore * 0.05;
                case FASTEST:
                    return costScore * 0.1 + speedScore * 0.7 + reliabilityScore * 0.15 + complianceScore * 0.05;
                case MOST_RELIABLE:
                    return costScore * 0.1 + speedScore * 0.1 + reliabilityScore * 0.7 + complianceScore * 0.1;
                default:
                    break;
            }
        }
        
        // Default balanced scoring
        return costScore * 0.25 + speedScore * 0.25 + reliabilityScore * 0.35 + complianceScore * 0.15;
    }
    
    private PaymentRoutingDecision.RoutingStrategy determineRoutingStrategy(PaymentProcessingRequest request) {
        if (request.getRoutingPreference() != null) {
            switch (request.getRoutingPreference()) {
                case CHEAPEST: return PaymentRoutingDecision.RoutingStrategy.COST_OPTIMIZED;
                case FASTEST: return PaymentRoutingDecision.RoutingStrategy.SPEED_OPTIMIZED;
                case MOST_RELIABLE: return PaymentRoutingDecision.RoutingStrategy.RELIABILITY_OPTIMIZED;
                case PREFERRED: return PaymentRoutingDecision.RoutingStrategy.PREFERRED_PROVIDER;
                default: return PaymentRoutingDecision.RoutingStrategy.BALANCED;
            }
        }
        return PaymentRoutingDecision.RoutingStrategy.BALANCED;
    }
    
    private String generateRoutingReason(PaymentRoutingDecision.ProviderScore selected, 
                                        PaymentProcessingRequest request) {
        StringBuilder reason = new StringBuilder();
        reason.append("Selected ").append(selected.getProvider());
        reason.append(" based on ");
        
        if (request.getRoutingPreference() != null) {
            reason.append(request.getRoutingPreference().name().toLowerCase()).append(" routing preference");
        } else {
            reason.append("balanced scoring");
        }
        
        reason.append(". Total score: ").append(String.format("%.2f", selected.getTotalScore()));
        return reason.toString();
    }
    
    private static Map<String, ProviderCapabilities> initializeProviderCapabilities() {
        Map<String, ProviderCapabilities> capabilities = new HashMap<>();
        
        // Example provider configurations
        capabilities.put("STRIPE", ProviderCapabilities.builder()
            .providerType(com.waqiti.payment.core.model.ProviderType.STRIPE)
            .supportedPaymentTypes(Set.of(PaymentType.CARD, PaymentType.WALLET, PaymentType.BANK_TRANSFER))
            .supportedCurrencies(Set.of("USD", "EUR", "GBP", "CAD", "AUD"))
            .minAmount(new BigDecimal("0.50"))
            .maxAmount(new BigDecimal("999999.99"))
            .baseFee(new BigDecimal("0.30"))
            .percentageFee(new BigDecimal("2.9"))
            .averageProcessingTimeMs(2000L)
            .successRate(0.995)
            .healthScore(0.98)
            .complianceScore(0.99)
            .isAvailable(true)
            .build());
        
        capabilities.put("PAYPAL", ProviderCapabilities.builder()
            .providerType(com.waqiti.payment.core.model.ProviderType.PAYPAL)
            .supportedPaymentTypes(Set.of(PaymentType.WALLET, PaymentType.BANK_TRANSFER))
            .supportedCurrencies(Set.of("USD", "EUR", "GBP", "CAD"))
            .minAmount(new BigDecimal("0.01"))
            .maxAmount(new BigDecimal("100000.00"))
            .baseFee(new BigDecimal("0.30"))
            .percentageFee(new BigDecimal("2.9"))
            .averageProcessingTimeMs(3000L)
            .successRate(0.99)
            .healthScore(0.95)
            .complianceScore(0.98)
            .isAvailable(true)
            .build());
        
        return capabilities;
    }
    
    @lombok.Data
    @lombok.Builder
    private static class ProviderCapabilities {
        private com.waqiti.payment.core.model.ProviderType providerType;
        private Set<PaymentType> supportedPaymentTypes;
        private Set<String> supportedCurrencies;
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private BigDecimal baseFee;
        private BigDecimal percentageFee;
        private Long averageProcessingTimeMs;
        private Double successRate;
        private Double healthScore;
        private Double complianceScore;
        private boolean isAvailable;
    }
    
    public static class NoEligibleProviderException extends RuntimeException {
        public NoEligibleProviderException(String message) {
            super(message);
        }
    }
    
    public static class NoAvailableProviderException extends RuntimeException {
        public NoAvailableProviderException(String message) {
            super(message);
        }
    }
}
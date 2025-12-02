package com.waqiti.payment.core.routing;

import com.waqiti.payment.core.model.ProviderType;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Payment routing decision model
 * Comprehensive routing decision with provider selection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class PaymentRoutingDecision {
    
    private UUID decisionId;
    private String selectedProvider;
    private ProviderType providerType;
    private RoutingStrategy routingStrategy;
    private BigDecimal estimatedFee;
    private Long estimatedProcessingTimeMs;
    private Double successProbability;
    private Double providerHealthScore;
    private String routingReason;
    
    @Builder.Default
    private List<ProviderScore> providerScores = new ArrayList<>();
    
    @Builder.Default
    private List<String> fallbackProviders = new ArrayList<>();
    
    private Map<String, Object> routingMetadata;
    private LocalDateTime decisionTimestamp;
    private Long decisionTimeMs;
    
    @Builder.Default
    private List<RoutingFactor> consideredFactors = new ArrayList<>();
    
    public enum RoutingStrategy {
        COST_OPTIMIZED,
        SPEED_OPTIMIZED,
        RELIABILITY_OPTIMIZED,
        BALANCED,
        PREFERRED_PROVIDER,
        FALLBACK,
        LOAD_BALANCED,
        GEOGRAPHIC,
        CUSTOM
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderScore {
        private String provider;
        private Double totalScore;
        private Double costScore;
        private Double speedScore;
        private Double reliabilityScore;
        private Double complianceScore;
        private Map<String, Double> additionalScores;
        private boolean available;
        private String unavailableReason;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoutingFactor {
        private String factorName;
        private Double weight;
        private String value;
        private Double impact;
    }
    
    public boolean hasAlternatives() {
        return !fallbackProviders.isEmpty();
    }
    
    public String getNextFallbackProvider() {
        return fallbackProviders.isEmpty() ? null : fallbackProviders.get(0);
    }
}
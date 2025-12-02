package com.waqiti.bankintegration.domain;

import com.waqiti.bankintegration.dto.ProviderType;
import com.waqiti.common.domain.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Payment Provider Entity
 * 
 * Represents a payment provider configuration in the system
 */
@Data
@Entity
@Table(name = "payment_providers")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PaymentProvider extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String providerCode;
    
    @Column(nullable = false)
    private String providerName;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProviderType providerType;
    
    @Column(nullable = false)
    private boolean active = true;
    
    @Column(nullable = false)
    private boolean sandboxMode = false;
    
    // Credentials (encrypted in production)
    @Column(name = "api_key")
    private String apiKey;
    
    @Column(name = "api_secret")
    private String apiSecret;
    
    @Column(name = "webhook_secret")
    private String webhookSecret;
    
    // Additional configuration as JSON
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> configuration = new HashMap<>();
    
    // Provider limits
    @Column(name = "min_amount", precision = 19, scale = 4)
    private BigDecimal minAmount;
    
    @Column(name = "max_amount", precision = 19, scale = 4)
    private BigDecimal maxAmount;
    
    @Column(name = "daily_limit", precision = 19, scale = 4)
    private BigDecimal dailyLimit;
    
    // Supported features
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Boolean> supportedFeatures = new HashMap<>();
    
    // Supported currencies (comma-separated)
    @Column(name = "supported_currencies")
    private String supportedCurrencies;
    
    // Webhook configuration
    @Column(name = "webhook_url")
    private String webhookUrl;
    
    @Column(name = "webhook_events")
    private String webhookEvents;
    
    // Fee structure
    @Column(name = "fixed_fee", precision = 19, scale = 4)
    private BigDecimal fixedFee;
    
    @Column(name = "percentage_fee", precision = 5, scale = 4)
    private BigDecimal percentageFee;
    
    // Retry configuration
    @Column(name = "max_retry_attempts")
    private Integer maxRetryAttempts = 3;
    
    @Column(name = "retry_delay_seconds")
    private Integer retryDelaySeconds = 60;
    
    // API base URL for provider
    @Column(name = "api_base_url")
    private String apiBaseUrl;
    
    // Priority for provider selection
    @Column(nullable = false)
    private Integer priority = 100;
    
    @PrePersist
    @PreUpdate
    protected void validateConfiguration() {
        if (configuration == null) {
            configuration = new HashMap<>();
        }
        if (supportedFeatures == null) {
            supportedFeatures = new HashMap<>();
        }
        
        // Set default supported features based on provider type
        if (supportedFeatures.isEmpty()) {
            switch (providerType) {
                case STRIPE:
                    supportedFeatures.put("cards", true);
                    supportedFeatures.put("bank_accounts", true);
                    supportedFeatures.put("refunds", true);
                    supportedFeatures.put("webhooks", true);
                    supportedFeatures.put("3ds", true);
                    break;
                case PAYPAL:
                    supportedFeatures.put("paypal_checkout", true);
                    supportedFeatures.put("refunds", true);
                    supportedFeatures.put("webhooks", true);
                    supportedFeatures.put("recurring", true);
                    break;
                case PLAID:
                    supportedFeatures.put("account_linking", true);
                    supportedFeatures.put("balance_check", true);
                    supportedFeatures.put("identity_verification", true);
                    supportedFeatures.put("transactions", true);
                    break;
                case ACH:
                    supportedFeatures.put("bank_transfers", true);
                    supportedFeatures.put("micro_deposits", true);
                    supportedFeatures.put("same_day_ach", true);
                    break;
                default:
                    // Default features
                    supportedFeatures.put("payments", true);
            }
        }
    }
    
    public boolean supportsFeature(String feature) {
        return Boolean.TRUE.equals(supportedFeatures.get(feature));
    }
    
    public boolean supportsCurrency(String currency) {
        if (supportedCurrencies == null || supportedCurrencies.isEmpty()) {
            return true; // Support all currencies if not specified
        }
        return supportedCurrencies.contains(currency.toUpperCase());
    }
    
    /**
     * Get configuration as JSON string for backward compatibility
     */
    public String getConfiguration() {
        if (configuration == null || configuration.isEmpty()) {
            return "{}";
        }
        
        try {
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, String> entry : configuration.entrySet()) {
                if (!first) {
                    json.append(",");
                }
                json.append("\"").append(entry.getKey()).append("\":\"")
                    .append(entry.getValue()).append("\"");
                first = false;
            }
            json.append("}");
            return json.toString();
        } catch (Exception e) {
            return "{}";
        }
    }
}
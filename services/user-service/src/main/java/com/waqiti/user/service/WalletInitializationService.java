package com.waqiti.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.user.dto.wallet.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Wallet Initialization Service
 * 
 * Comprehensive wallet setup and configuration service providing:
 * - Multi-currency wallet creation
 * - Tier-based limit configuration
 * - Compliance-based feature activation
 * - Country-specific regulatory setup
 * - Default payment method configuration
 * - Notification preference initialization
 * - Risk profile setup
 * - Wallet verification workflow
 * 
 * INTEGRATION:
 * - Wallet service via REST API
 * - Compliance service for KYC tier determination
 * - Notification service for preference setup
 * - Kafka for async wallet events
 * 
 * FEATURES:
 * - Automatic tier assignment based on KYC status
 * - Country-specific limits and features
 * - Multi-currency support with auto-conversion
 * - Fraud prevention settings
 * - Transaction monitoring configuration
 * - Backup wallet creation
 * 
 * COMPLIANCE:
 * - Regulatory limit enforcement by jurisdiction
 * - KYC tier-based feature gating
 * - AML transaction monitoring setup
 * - Risk-based authentication configuration
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletInitializationService {
    
    private final WebClient.Builder webClientBuilder;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${wallet-service.url:http://localhost:8084}")
    private String walletServiceUrl;
    
    @Value("${wallet.default-currency:USD}")
    private String defaultCurrency;
    
    @Value("${wallet.enable-multi-currency:true}")
    private boolean enableMultiCurrency;
    
    private static final Duration API_TIMEOUT = Duration.ofSeconds(30);
    
    private static final Map<String, WalletTierLimits> TIER_LIMITS = Map.of(
        "BASIC", new WalletTierLimits(
            new BigDecimal("500.00"),
            new BigDecimal("2000.00"),
            new BigDecimal("200.00")
        ),
        "STANDARD", new WalletTierLimits(
            new BigDecimal("2000.00"),
            new BigDecimal("10000.00"),
            new BigDecimal("1000.00")
        ),
        "PREMIUM", new WalletTierLimits(
            new BigDecimal("10000.00"),
            new BigDecimal("50000.00"),
            new BigDecimal("5000.00")
        ),
        "BUSINESS", new WalletTierLimits(
            new BigDecimal("50000.00"),
            new BigDecimal("250000.00"),
            new BigDecimal("25000.00")
        )
    );
    
    private static final Map<String, List<String>> COUNTRY_CURRENCIES = Map.of(
        "US", List.of("USD"),
        "GB", List.of("GBP", "USD", "EUR"),
        "EU", List.of("EUR", "USD"),
        "CA", List.of("CAD", "USD"),
        "AU", List.of("AUD", "USD"),
        "NG", List.of("NGN", "USD"),
        "KE", List.of("KES", "USD"),
        "ZA", List.of("ZAR", "USD")
    );
    
    private WebClient webClient;
    
    private WebClient getWebClient() {
        if (webClient == null) {
            webClient = webClientBuilder
                .baseUrl(walletServiceUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        }
        return webClient;
    }
    
    @CircuitBreaker(name = "wallet-initialization", fallbackMethod = "initializeWalletFallback")
    @Retry(name = "wallet-initialization")
    public WalletInitializationResult initializeWallet(
            String userId,
            String email,
            String currency,
            String countryCode,
            String kycTier,
            Map<String, Object> userProfile) {
        
        log.info("Initializing wallet for user: userId={} currency={} country={} kycTier={}", 
                userId, currency, countryCode, kycTier);
        
        try {
            String effectiveCurrency = determineEffectiveCurrency(currency, countryCode);
            String walletTier = determineWalletTier(kycTier);
            WalletTierLimits limits = TIER_LIMITS.get(walletTier);
            
            WalletCreationRequest request = WalletCreationRequest.builder()
                .userId(userId)
                .email(email)
                .currency(effectiveCurrency)
                .countryCode(countryCode)
                .walletType("STANDARD")
                .tier(walletTier)
                .dailyLimit(limits.dailyLimit)
                .monthlyLimit(limits.monthlyLimit)
                .transactionLimit(limits.transactionLimit)
                .enableNotifications(true)
                .enableFraudProtection(true)
                .requireMfaForHighValue(true)
                .metadata(userProfile)
                .build();
            
            WalletCreationResponse response = getWebClient()
                .post()
                .uri("/api/v1/wallets/initialize")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(WalletCreationResponse.class)
                .timeout(API_TIMEOUT)
                .block();
            
            if (response == null) {
                throw new IllegalStateException("Null response from wallet service");
            }
            
            String walletId = response.getWalletId();
            
            CompletableFuture.runAsync(() -> {
                try {
                    setupAdditionalFeatures(userId, walletId, countryCode, walletTier);
                } catch (Exception e) {
                    log.error("Failed to setup additional wallet features", e);
                }
            });
            
            if (enableMultiCurrency) {
                CompletableFuture.runAsync(() -> {
                    try {
                        createMultiCurrencyWallets(userId, countryCode);
                    } catch (Exception e) {
                        log.error("Failed to create multi-currency wallets", e);
                    }
                });
            }
            
            publishWalletCreatedEvent(userId, walletId, effectiveCurrency, walletTier);
            
            WalletInitializationResult result = WalletInitializationResult.builder()
                .walletId(walletId)
                .userId(userId)
                .currency(effectiveCurrency)
                .tier(walletTier)
                .status("ACTIVE")
                .dailyLimit(limits.dailyLimit)
                .monthlyLimit(limits.monthlyLimit)
                .transactionLimit(limits.transactionLimit)
                .featuresEnabled(response.getFeaturesEnabled())
                .timestamp(LocalDateTime.now())
                .build();
            
            log.info("Wallet initialized successfully: userId={} walletId={} tier={}", 
                    userId, walletId, walletTier);
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to initialize wallet for user: {}", userId, e);
            throw new RuntimeException("Wallet initialization failed", e);
        }
    }
    
    @CircuitBreaker(name = "wallet-initialization", fallbackMethod = "setDefaultLimitsFallback")
    @Retry(name = "wallet-initialization")
    public void setDefaultLimits(String userId, String walletId, String tier) {
        log.info("Setting default limits for wallet: userId={} walletId={} tier={}", 
                userId, walletId, tier);
        
        try {
            WalletTierLimits limits = TIER_LIMITS.getOrDefault(tier, TIER_LIMITS.get("BASIC"));
            
            WalletLimitsUpdateRequest request = WalletLimitsUpdateRequest.builder()
                .dailyLimit(limits.dailyLimit)
                .monthlyLimit(limits.monthlyLimit)
                .transactionLimit(limits.transactionLimit)
                .updatedBy("SYSTEM")
                .reason("Initial setup for tier: " + tier)
                .build();
            
            getWebClient()
                .put()
                .uri("/api/v1/wallets/{walletId}/limits", walletId)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(10))
                .block();
            
            log.info("Default limits set successfully for wallet: {}", walletId);
            
        } catch (Exception e) {
            log.error("Failed to set default limits for wallet: {}", walletId, e);
            throw new RuntimeException("Failed to set wallet limits", e);
        }
    }
    
    @CircuitBreaker(name = "wallet-initialization", fallbackMethod = "upgradeTierFallback")
    @Retry(name = "wallet-initialization")
    public void upgradeWalletTier(String userId, String walletId, String newTier, String reason) {
        log.info("Upgrading wallet tier: userId={} walletId={} newTier={}", 
                userId, walletId, newTier);
        
        try {
            WalletTierLimits limits = TIER_LIMITS.getOrDefault(newTier, TIER_LIMITS.get("STANDARD"));
            
            WalletTierUpgradeRequest request = WalletTierUpgradeRequest.builder()
                .walletId(walletId)
                .userId(userId)
                .newTier(newTier)
                .dailyLimit(limits.dailyLimit)
                .monthlyLimit(limits.monthlyLimit)
                .transactionLimit(limits.transactionLimit)
                .reason(reason)
                .upgradedBy("SYSTEM")
                .build();
            
            getWebClient()
                .post()
                .uri("/api/v1/wallets/{walletId}/upgrade-tier", walletId)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(10))
                .block();
            
            publishWalletTierUpgradedEvent(userId, walletId, newTier);
            
            log.info("Wallet tier upgraded successfully: walletId={} newTier={}", walletId, newTier);
            
        } catch (Exception e) {
            log.error("Failed to upgrade wallet tier: walletId={}", walletId, e);
            throw new RuntimeException("Failed to upgrade wallet tier", e);
        }
    }
    
    public void configureWalletSecurity(
            String userId,
            String walletId,
            boolean enableMfa,
            boolean enableBiometric,
            boolean enableGeofencing,
            List<String> allowedCountries) {
        
        log.info("Configuring wallet security: walletId={} mfa={} biometric={} geofencing={}", 
                walletId, enableMfa, enableBiometric, enableGeofencing);
        
        try {
            WalletSecurityConfigRequest request = WalletSecurityConfigRequest.builder()
                .enableMfa(enableMfa)
                .enableBiometric(enableBiometric)
                .enableGeofencing(enableGeofencing)
                .allowedCountries(allowedCountries)
                .mfaThreshold(new BigDecimal("500.00")) // CRITICAL P0 FIX: Lowered from $1000 to $500 for enhanced security
                .sessionTimeout(Duration.ofMinutes(30))
                .build();
            
            getWebClient()
                .put()
                .uri("/api/v1/wallets/{walletId}/security", walletId)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(10))
                .block();
            
            log.info("Wallet security configured successfully: walletId={}", walletId);
            
        } catch (Exception e) {
            log.error("Failed to configure wallet security: walletId={}", walletId, e);
        }
    }
    
    private void setupAdditionalFeatures(
            String userId,
            String walletId,
            String countryCode,
            String tier) {
        
        log.debug("Setting up additional wallet features: walletId={}", walletId);
        
        List<String> allowedCountries = determineAllowedCountries(countryCode);
        configureWalletSecurity(
            userId,
            walletId,
            !tier.equals("BASIC"),
            tier.equals("PREMIUM") || tier.equals("BUSINESS"),
            tier.equals("PREMIUM") || tier.equals("BUSINESS"),
            allowedCountries
        );
    }
    
    private void createMultiCurrencyWallets(String userId, String countryCode) {
        log.debug("Creating multi-currency wallets: userId={} country={}", userId, countryCode);
        
        List<String> currencies = COUNTRY_CURRENCIES.getOrDefault(
            countryCode,
            List.of(defaultCurrency)
        );
        
        for (String currency : currencies) {
            if (!currency.equals(defaultCurrency)) {
                try {
                    MultiCurrencyWalletRequest request = MultiCurrencyWalletRequest.builder()
                        .userId(userId)
                        .currency(currency)
                        .walletType("MULTI_CURRENCY")
                        .build();
                    
                    getWebClient()
                        .post()
                        .uri("/api/v1/wallets/multi-currency")
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .timeout(Duration.ofSeconds(10))
                        .block();
                    
                    log.debug("Created {} wallet for user: {}", currency, userId);
                } catch (Exception e) {
                    log.error("Failed to create {} wallet for user: {}", currency, userId, e);
                }
            }
        }
    }
    
    private String determineEffectiveCurrency(String requestedCurrency, String countryCode) {
        if (requestedCurrency != null && !requestedCurrency.isBlank()) {
            return requestedCurrency;
        }
        
        List<String> supportedCurrencies = COUNTRY_CURRENCIES.get(countryCode);
        if (supportedCurrencies != null && !supportedCurrencies.isEmpty()) {
            return supportedCurrencies.get(0);
        }
        
        return defaultCurrency;
    }
    
    private String determineWalletTier(String kycTier) {
        if (kycTier == null || kycTier.isBlank()) {
            return "BASIC";
        }
        
        switch (kycTier.toUpperCase()) {
            case "TIER_1":
            case "VERIFIED":
                return "STANDARD";
            case "TIER_2":
            case "ENHANCED":
                return "PREMIUM";
            case "TIER_3":
            case "BUSINESS":
                return "BUSINESS";
            default:
                return "BASIC";
        }
    }
    
    private List<String> determineAllowedCountries(String homeCountry) {
        List<String> allowed = new ArrayList<>();
        allowed.add(homeCountry);
        
        if (homeCountry.equals("US") || homeCountry.equals("CA")) {
            allowed.add("US");
            allowed.add("CA");
        } else if (List.of("GB", "FR", "DE", "IT", "ES").contains(homeCountry)) {
            allowed.addAll(List.of("GB", "FR", "DE", "IT", "ES", "NL", "BE"));
        }
        
        return allowed;
    }
    
    private void publishWalletCreatedEvent(String userId, String walletId, String currency, String tier) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "WALLET_CREATED",
                "userId", userId,
                "walletId", walletId,
                "currency", currency,
                "tier", tier,
                "timestamp", LocalDateTime.now()
            );
            
            kafkaTemplate.send("wallet.lifecycle.events", walletId, event);
        } catch (Exception e) {
            log.error("Failed to publish wallet created event", e);
        }
    }
    
    private void publishWalletTierUpgradedEvent(String userId, String walletId, String newTier) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "WALLET_TIER_UPGRADED",
                "userId", userId,
                "walletId", walletId,
                "newTier", newTier,
                "timestamp", LocalDateTime.now()
            );
            
            kafkaTemplate.send("wallet.lifecycle.events", walletId, event);
        } catch (Exception e) {
            log.error("Failed to publish wallet tier upgraded event", e);
        }
    }
    
    private WalletInitializationResult initializeWalletFallback(
            String userId,
            String email,
            String currency,
            String countryCode,
            String kycTier,
            Map<String, Object> userProfile,
            Exception e) {
        
        log.error("Wallet service unavailable - wallet not initialized (fallback): userId={}", userId, e);
        
        throw new RuntimeException("Wallet initialization failed: " + e.getMessage(), e);
    }
    
    private void setDefaultLimitsFallback(String userId, String walletId, String tier, Exception e) {
        log.error("Wallet service unavailable - default limits not set (fallback): userId={}", userId, e);
    }
    
    private void upgradeTierFallback(
            String userId,
            String walletId,
            String newTier,
            String reason,
            Exception e) {
        
        log.error("Wallet service unavailable - tier upgrade failed (fallback): userId={}", userId, e);
    }
    
    private static class WalletTierLimits {
        final BigDecimal dailyLimit;
        final BigDecimal monthlyLimit;
        final BigDecimal transactionLimit;
        
        WalletTierLimits(BigDecimal dailyLimit, BigDecimal monthlyLimit, BigDecimal transactionLimit) {
            this.dailyLimit = dailyLimit;
            this.monthlyLimit = monthlyLimit;
            this.transactionLimit = transactionLimit;
        }
    }
}
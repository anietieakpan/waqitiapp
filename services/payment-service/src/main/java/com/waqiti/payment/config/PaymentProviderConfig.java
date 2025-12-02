package com.waqiti.payment.config;

import com.waqiti.payment.integration.PaymentProvider;
import com.waqiti.payment.integration.stripe.StripePaymentProvider;
import com.waqiti.payment.integration.paypal.PayPalPaymentProvider;
import com.waqiti.payment.integration.plaid.PlaidBankingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Payment provider configuration and registry
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
@org.springframework.boot.context.properties.EnableConfigurationProperties(PaymentProviderConfig.PaymentProviderProperties.class)
public class PaymentProviderConfig {

    private final StripePaymentProvider stripeProvider;
    private final PayPalPaymentProvider paypalProvider;
    private final PlaidBankingService plaidService;

    @Bean
    @Primary
    public PaymentProviderRegistry paymentProviderRegistry() {
        Map<String, PaymentProvider> providers = new HashMap<>();
        
        // Register active providers
        if (stripeProvider.isAvailable()) {
            providers.put("STRIPE", stripeProvider);
            log.info("Stripe payment provider registered and available");
        }
        
        if (paypalProvider.isAvailable()) {
            providers.put("PAYPAL", paypalProvider);
            log.info("PayPal payment provider registered and available");
        }
        
        log.info("Payment provider registry initialized with {} providers", providers.size());
        
        return new PaymentProviderRegistry(providers);
    }

    @Bean
    public BankingProviderRegistry bankingProviderRegistry() {
        Map<String, Object> providers = new HashMap<>();
        providers.put("PLAID", plaidService);
        
        log.info("Banking provider registry initialized with Plaid integration");
        
        return new BankingProviderRegistry(providers);
    }

    /**
     * Payment provider registry for managing multiple payment providers
     */
    public static class PaymentProviderRegistry {
        private final Map<String, PaymentProvider> providers;

        public PaymentProviderRegistry(Map<String, PaymentProvider> providers) {
            this.providers = providers;
        }

        public PaymentProvider getProvider(String providerName) {
            PaymentProvider provider = providers.get(providerName.toUpperCase());
            if (provider == null) {
                throw new IllegalArgumentException("Payment provider not found: " + providerName);
            }
            return provider;
        }

        public List<String> getAvailableProviders() {
            return List.copyOf(providers.keySet());
        }

        public PaymentProvider getDefaultProvider() {
            // Stripe as default, fallback to first available
            return providers.getOrDefault("STRIPE", providers.values().iterator().next());
        }
    }

    /**
     * Banking provider registry for managing banking integrations
     */
    public static class BankingProviderRegistry {
        private final Map<String, Object> providers;

        public BankingProviderRegistry(Map<String, Object> providers) {
            this.providers = providers;
        }

        public PlaidBankingService getPlaidService() {
            return (PlaidBankingService) providers.get("PLAID");
        }

        public List<String> getAvailableProviders() {
            return List.copyOf(providers.keySet());
        }
    }

    @ConfigurationProperties(prefix = "payment.providers")
    public static class PaymentProviderProperties {
        private boolean stripeEnabled = true;
        private boolean paypalEnabled = true;
        private boolean plaidEnabled = true;
        private String defaultProvider = "STRIPE";

        // Getters and setters
        public boolean isStripeEnabled() { return stripeEnabled; }
        public void setStripeEnabled(boolean stripeEnabled) { this.stripeEnabled = stripeEnabled; }

        public boolean isPaypalEnabled() { return paypalEnabled; }
        public void setPaypalEnabled(boolean paypalEnabled) { this.paypalEnabled = paypalEnabled; }

        public boolean isPlaidEnabled() { return plaidEnabled; }
        public void setPlaidEnabled(boolean plaidEnabled) { this.plaidEnabled = plaidEnabled; }

        public String getDefaultProvider() { return defaultProvider; }
        public void setDefaultProvider(String defaultProvider) { this.defaultProvider = defaultProvider; }
    }
}
package com.waqiti.payment.core.config;

import com.waqiti.payment.core.UnifiedPaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for UnifiedPaymentService
 * Enables automatic integration when payment-commons is on the classpath
 */
@AutoConfiguration
@ConditionalOnClass(UnifiedPaymentService.class)
@ComponentScan(basePackages = {
        "com.waqiti.payment.core.strategy",
        "com.waqiti.payment.core.provider", 
        "com.waqiti.payment.core.service",
        "com.waqiti.payment.core"
})
@Import(PaymentConfiguration.class)
@Slf4j
public class PaymentAutoConfiguration {

    public PaymentAutoConfiguration() {
        log.info("🚀 UnifiedPaymentService Auto-Configuration Activated");
        log.info("   ✅ Payment strategies: P2P, Group, Recurring, BNPL, Merchant, Crypto, Split");
        log.info("   ✅ Payment providers: Stripe, PayPal, Square, Braintree, Adyen, Dwolla, Plaid, Bitcoin, Ethereum");
        log.info("   ✅ Fraud detection: Advanced multi-factor risk analysis");
        log.info("   ✅ Event publishing: Kafka integration");
        log.info("   ✅ Audit trail: Complete payment history and analytics");
    }
}
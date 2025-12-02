package com.waqiti.payment.core.config;

import com.waqiti.payment.core.model.PaymentType;
import com.waqiti.payment.core.model.ProviderType;
import com.waqiti.payment.core.provider.*;
import com.waqiti.payment.core.strategy.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Payment system configuration
 * Wires up all payment strategies and providers for dependency injection
 */
@Configuration
@Slf4j
public class PaymentConfiguration {

    /**
     * Configure payment strategy map for dependency injection
     */
    @Bean
    public Map<PaymentType, PaymentStrategy> paymentStrategies(
            P2PPaymentStrategy p2pStrategy,
            GroupPaymentStrategy groupStrategy,
            RecurringPaymentStrategy recurringStrategy,
            BnplPaymentStrategy bnplStrategy,
            MerchantPaymentStrategy merchantStrategy,
            CryptoPaymentStrategy cryptoStrategy,
            SplitPaymentStrategy splitStrategy) {
        
        List<PaymentStrategy> strategies = List.of(
                p2pStrategy, groupStrategy, recurringStrategy, 
                bnplStrategy, merchantStrategy, cryptoStrategy, splitStrategy
        );
        
        Map<PaymentType, PaymentStrategy> strategyMap = strategies.stream()
                .collect(Collectors.toMap(
                        PaymentStrategy::getPaymentType,
                        Function.identity(),
                        (existing, replacement) -> {
                            log.warn("Duplicate strategy for payment type: {}, using: {}", 
                                    existing.getPaymentType(), replacement.getClass().getSimpleName());
                            return replacement;
                        }
                ));
        
        log.info("Configured {} payment strategies: {}", 
                strategyMap.size(), strategyMap.keySet());
        
        return strategyMap;
    }

    /**
     * Configure payment provider map for dependency injection
     */
    @Bean
    public Map<ProviderType, PaymentProvider> paymentProviders(
            StripePaymentProvider stripeProvider,
            PayPalPaymentProvider paypalProvider,
            SquarePaymentProvider squareProvider,
            BraintreePaymentProvider braintreeProvider,
            AdyenPaymentProvider adyenProvider,
            DwollaPaymentProvider dwollaProvider,
            PlaidPaymentProvider plaidProvider,
            BitcoinPaymentProvider bitcoinProvider,
            EthereumPaymentProvider ethereumProvider) {
        
        List<PaymentProvider> providers = List.of(
                stripeProvider, paypalProvider, squareProvider, 
                braintreeProvider, adyenProvider, dwollaProvider,
                plaidProvider, bitcoinProvider, ethereumProvider
        );
        
        Map<ProviderType, PaymentProvider> providerMap = providers.stream()
                .collect(Collectors.toMap(
                        PaymentProvider::getProviderType,
                        Function.identity(),
                        (existing, replacement) -> {
                            log.warn("Duplicate provider for type: {}, using: {}", 
                                    existing.getProviderType(), replacement.getClass().getSimpleName());
                            return replacement;
                        }
                ));
        
        log.info("Configured {} payment providers: {}", 
                providerMap.size(), providerMap.keySet());
        
        return providerMap;
    }

    /**
     * Configure Kafka template if not already present
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        // In production, this would be properly configured with Kafka settings
        // For now, return a mock implementation or configure based on environment
        log.info("Configuring Kafka template for payment events");
        return new org.springframework.kafka.core.KafkaTemplate<>(
                new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(
                        Map.of(
                                org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, 
                                "localhost:9092",
                                org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                                org.apache.kafka.common.serialization.StringSerializer.class,
                                org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                                org.springframework.kafka.support.serializer.JsonSerializer.class
                        )
                )
        );
    }
}
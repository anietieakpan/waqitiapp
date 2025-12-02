package com.waqiti.billpayment.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;

/**
 * Test configuration for Bill Payment Service
 */
@TestConfiguration
@EmbeddedKafka(partitions = 1, topics = {"bill-payment-events"})
public class TestConfig {

    @Bean
    @Primary
    public EmbeddedKafkaBroker embeddedKafkaBroker() {
        return new EmbeddedKafkaBroker(1);
    }
}

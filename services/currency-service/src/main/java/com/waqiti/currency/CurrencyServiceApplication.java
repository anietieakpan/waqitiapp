package com.waqiti.currency;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Currency Service Application
 * 
 * Provides multi-currency support, real-time exchange rates, and currency conversion
 * capabilities for the Waqiti financial platform.
 * 
 * Key Features:
 * - Real-time exchange rate fetching from multiple providers
 * - Multi-currency account support
 * - Historical exchange rate data
 * - Compliance reporting and suspicious transaction detection
 * - Cross-border transfer support
 * - WebSocket real-time rate updates
 */
@SpringBootApplication
@EnableEurekaClient
@EnableKafka
@EnableCaching
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class CurrencyServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(CurrencyServiceApplication.class, args);
    }
}
package com.waqiti.voice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Voice Payment Service Application
 * 
 * Advanced voice-activated payment processing service that enables hands-free
 * financial transactions through natural language processing and voice biometric authentication.
 * 
 * Key Features:
 * - Multi-language voice recognition and natural language processing
 * - Voice biometric authentication for secure payment authorization
 * - Conversational AI for intuitive payment interactions
 * - Multi-modal interface supporting voice, DTMF, and visual confirmations
 * - Real-time fraud detection and AML compliance for voice transactions
 * - Accessibility features for users with visual or motor impairments
 * - Integration with payment gateways, wallets, and banking systems
 * - Advanced voice analytics and conversation intelligence
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableJpaAuditing
@EnableCaching
@EnableFeignClients
@EnableKafka
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class VoicePaymentServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(VoicePaymentServiceApplication.class, args);
    }
}
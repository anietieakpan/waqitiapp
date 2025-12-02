package com.waqiti.arpayment;

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
 * AR Payment Service Application
 * 
 * Advanced augmented reality payment processing service that enables immersive
 * 3D payment experiences with spatial recognition and gesture-based interactions.
 * 
 * Key Features:
 * - Spatial object recognition for payment terminals and products
 * - Advanced gesture recognition for intuitive payment interactions
 * - Real-time 3D visualization of payment flows and confirmations  
 * - Biometric security integration for AR payment validation
 * - Multi-platform support (ARKit, ARCore, WebXR, HoloLens)
 * - Gamified AR payment experiences with achievements and rewards
 * - Collaborative multi-user AR payment sessions
 * - Accessibility features with voice commands and alternative interactions
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
public class ARPaymentServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ARPaymentServiceApplication.class, args);
    }
}
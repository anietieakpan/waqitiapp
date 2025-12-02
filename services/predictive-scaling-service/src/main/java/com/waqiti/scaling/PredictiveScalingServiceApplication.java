package com.waqiti.scaling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Predictive Scaling Service Application
 * 
 * Advanced machine learning-powered auto-scaling service that predicts resource demands
 * and proactively scales infrastructure before performance degradation occurs.
 * 
 * Key Features:
 * - AI-driven traffic and load prediction using ensemble ML models
 * - Real-time anomaly detection and performance monitoring
 * - Multi-dimensional cost optimization with cloud-native resource management
 * - Kubernetes-native horizontal and vertical pod autoscaling
 * - Advanced capacity planning with predictive analytics
 * - Integration with Prometheus, Grafana, and cloud monitoring systems
 * - Intelligent resource rightsizing and spot instance optimization
 * - SLA-aware scaling with configurable performance thresholds
 * - Multi-cloud support with vendor-specific optimization strategies
 * - Advanced machine learning pipeline with automated model retraining
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
public class PredictiveScalingServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(PredictiveScalingServiceApplication.class, args);
    }
}
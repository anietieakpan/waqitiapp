package com.waqiti.ml;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ML Service Application
 * 
 * Advanced Machine Learning service for:
 * - Real-time fraud detection
 * - Transaction pattern analysis
 * - Risk scoring and assessment
 * - Behavioral analytics
 * - Anomaly detection
 * - Predictive modeling
 */
@SpringBootApplication
@EnableEurekaClient
@EnableFeignClients(basePackages = {"com.waqiti.ml.client", "com.waqiti.common.client"})
@ComponentScan(basePackages = {"com.waqiti.ml", "com.waqiti.common"})
@EnableKafka
@EnableAsync
@EnableScheduling
public class MLServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MLServiceApplication.class, args);
    }
}
package com.waqiti.social;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Social Service Application
 * 
 * Provides comprehensive social features for peer-to-peer payments including:
 * - Social connections and friend networks
 * - Social payment feeds and activity streams
 * - Group payments and bill splitting
 * - Social commerce and marketplace features
 * - Gamification and social challenges
 * - Social analytics and insights
 */
@SpringBootApplication(scanBasePackages = {
    "com.waqiti.social",
    "com.waqiti.common"
})
@EnableEurekaClient
@EnableFeignClients
@EnableJpaRepositories
@EnableKafka
@EnableCaching
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class SocialServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialServiceApplication.class, args);
    }
}
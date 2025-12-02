package com.waqiti.gamification;

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

@SpringBootApplication
@EnableDiscoveryClient
@EnableJpaAuditing
@EnableCaching
@EnableFeignClients
@EnableKafka
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class GamificationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(GamificationServiceApplication.class, args);
    }
}
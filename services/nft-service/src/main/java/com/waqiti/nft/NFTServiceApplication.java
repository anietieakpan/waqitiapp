package com.waqiti.nft;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication(scanBasePackages = {"com.waqiti.nft", "com.waqiti.common"})
@EnableEurekaClient
@EnableFeignClients
@EnableJpaAuditing
@EnableCaching
@EnableKafka
public class NFTServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NFTServiceApplication.class, args);
    }
}
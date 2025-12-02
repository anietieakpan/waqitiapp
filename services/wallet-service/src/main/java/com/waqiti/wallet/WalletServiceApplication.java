package com.waqiti.wallet;

import com.waqiti.payment.commons.config.WaqitiServiceConfiguration;
import org.springframework.context.annotation.Import;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@Import(WaqitiServiceConfiguration.class)
@EnableFeignClients(basePackages = {"com.waqiti.payment.client", "com.waqiti.payment.commons.client", "com.waqiti.wallet.client"})
@EnableJpaRepositories
@EnableScheduling
public class WalletServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(WalletServiceApplication.class, args);
    }
}

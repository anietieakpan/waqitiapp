package com.waqiti.grouppayment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableEurekaClient
@EnableFeignClients
@EnableKafka
@EnableAsync
public class GroupPaymentServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(GroupPaymentServiceApplication.class, args);
    }
}
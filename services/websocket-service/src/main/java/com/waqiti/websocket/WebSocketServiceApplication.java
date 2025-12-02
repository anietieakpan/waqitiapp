package com.waqiti.websocket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableEurekaClient
@EnableKafka
@EnableAsync
@EnableScheduling
public class WebSocketServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(WebSocketServiceApplication.class, args);
    }
}
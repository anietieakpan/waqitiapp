package com.waqiti.user;

import com.waqiti.user.config.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableJpaRepositories
@EnableScheduling
@EnableConfigurationProperties({
    WaqitiUserProperties.class,
    RateLimitingProperties.class,
    LoggingRateLimitingProperties.class,
    LoggingWaqitiUserProperties.class,
    KeycloakProperties.class,
    ServiceAuthProperties.class,
    ExternalServicesProperties.class
})
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
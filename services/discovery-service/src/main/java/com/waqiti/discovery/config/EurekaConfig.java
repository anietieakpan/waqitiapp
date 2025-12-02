package com.waqiti.discovery.config;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.eureka.EurekaServerContext;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

@Configuration
public class EurekaConfig {

    @Bean
    public HealthIndicator eurekaHealthIndicator(
            @Nullable ApplicationInfoManager applicationInfoManager,
            @Nullable EurekaServerContext eurekaServerContext) {
        
        return () -> {
            if (applicationInfoManager == null || eurekaServerContext == null) {
                return Health.down().withDetail("reason", "Eureka server not initialized").build();
            }
            
            int registeredApps = eurekaServerContext.getRegistry().getSortedApplications().size();
            
            return Health.up()
                    .withDetail("registeredApplications", registeredApps)
                    .withDetail("instanceStatus", applicationInfoManager.getInfo().getStatus().toString())
                    .build();
        };
    }
}
package com.waqiti.discovery.health;

import com.netflix.discovery.EurekaClient;
import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.EurekaServerContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator for Eureka Server
 * Monitors the health of the service registry
 */
@Component
@RequiredArgsConstructor
public class EurekaServerHealthIndicator implements HealthIndicator {

    @Nullable
    private final EurekaClient eurekaClient;

    @Override
    public Health health() {
        try {
            EurekaServerContext serverContext = EurekaServerContextHolder.getInstance().getServerContext();
            
            if (serverContext == null) {
                return Health.down()
                    .withDetail("status", "Eureka server context not initialized")
                    .build();
            }

            int registeredInstances = serverContext.getRegistry().getApplications().size();
            
            return Health.up()
                .withDetail("status", "Eureka server is running")
                .withDetail("registeredApplications", registeredInstances)
                .withDetail("upTime", getUpTime())
                .build();
                
        } catch (Exception e) {
            return Health.down()
                .withDetail("status", "Eureka server is down")
                .withDetail("error", e.getMessage())
                .build();
        }
    }

    private String getUpTime() {
        if (eurekaClient != null) {
            return eurekaClient.getApplicationInfoManager()
                .getInfo().getLastUpdatedTimestamp().toString();
        }
        return "Unknown";
    }
}
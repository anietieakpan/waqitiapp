package com.waqiti.saga.compensation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry for compensation strategies by service type
 */
@Slf4j
@Component
public class CompensationStrategyRegistry {
    
    private final Map<String, CompensationFailureHandler.CompensationStrategy> strategies = new HashMap<>();
    
    @PostConstruct
    public void initializeStrategies() {
        // Register default strategies
        registerStrategy("wallet-service", new WalletCompensationStrategy());
        registerStrategy("payment-service", new PaymentCompensationStrategy());
        registerStrategy("notification-service", new NotificationCompensationStrategy());
        registerStrategy("fraud-service", new FraudCheckCompensationStrategy());
        
        log.info("Initialized {} compensation strategies", strategies.size());
    }
    
    public void registerStrategy(String serviceName, CompensationFailureHandler.CompensationStrategy strategy) {
        strategies.put(serviceName, strategy);
        log.debug("Registered compensation strategy for service: {}", serviceName);
    }
    
    public CompensationFailureHandler.CompensationStrategy getStrategy(String serviceName) {
        return strategies.get(serviceName);
    }
}
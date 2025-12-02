package com.waqiti.account.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Clock Configuration for testable time-dependent code
 * 
 * Provides a Clock bean that can be mocked in tests to control time-based behavior.
 * This is a best practice for writing testable code that depends on current time.
 */
@Configuration
public class ClockConfiguration {
    
    /**
     * Provides system default clock for production use.
     * Can be replaced with fixed clock in tests for deterministic behavior.
     * 
     * @return Clock instance using system default zone
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
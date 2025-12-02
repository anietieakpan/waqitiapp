package com.waqiti.common.servicemesh;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;

/**
 * Fault Injection Manager for chaos engineering
 */
@Slf4j
@Component
@Builder
public class FaultInjectionManager {

    @Value("${chaos.fault.delay.enabled:false}")
    private boolean delayEnabled;
    
    @Value("${chaos.fault.delay.percentage:10}")
    private int delayPercentage;
    
    @Value("${chaos.fault.delay.duration:PT1S}")
    private Duration delayDuration;
    
    @Value("${chaos.fault.abort.enabled:false}")
    private boolean abortEnabled;
    
    @Value("${chaos.fault.abort.percentage:5}")
    private int abortPercentage;
    
    @Value("${chaos.fault.abort.http-status:500}")
    private int abortHttpStatus;

    @PostConstruct
    public void initialize() {
        log.info("Initializing Fault Injection Manager - Delay: {}, Abort: {}", delayEnabled, abortEnabled);
        // Initialize fault injection components
        log.info("Fault Injection Manager initialized successfully");
    }
}
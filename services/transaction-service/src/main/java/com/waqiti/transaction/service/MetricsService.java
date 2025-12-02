package com.waqiti.transaction.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {
    
    private final MeterRegistry meterRegistry;
    
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();
    private final Map<String, Timer> timerCache = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing MetricsService with MeterRegistry");
    }
    
    public void recordBlockMetrics(String operation, String blockType, String targetType, 
                                 long processingTime, boolean withinSla, String status) {
        log.debug("Recording block metrics: operation={} blockType={} targetType={} processingTime={}ms withinSla={} status={}", 
                operation, blockType, targetType, processingTime, withinSla, status);
        
        try {
            String counterKey = String.format("transaction.block.%s.%s.%s", operation, blockType, status);
            Counter counter = counterCache.computeIfAbsent(counterKey, key ->
                    Counter.builder("transaction.block.operations")
                            .tag("operation", operation)
                            .tag("blockType", blockType)
                            .tag("status", status)
                            .tag("targetType", targetType)
                            .description("Transaction block operations counter")
                            .register(meterRegistry)
            );
            counter.increment();
            
            String timerKey = String.format("transaction.block.%s.%s", operation, blockType);
            Timer timer = timerCache.computeIfAbsent(timerKey, key ->
                    Timer.builder("transaction.block.processing.time")
                            .tag("operation", operation)
                            .tag("blockType", blockType)
                            .tag("targetType", targetType)
                            .description("Transaction block processing time")
                            .register(meterRegistry)
            );
            timer.record(processingTime, TimeUnit.MILLISECONDS);
            
            if (!withinSla) {
                String slaViolationKey = String.format("transaction.block.sla.violation.%s", blockType);
                Counter slaCounter = counterCache.computeIfAbsent(slaViolationKey, key ->
                        Counter.builder("transaction.block.sla.violations")
                                .tag("blockType", blockType)
                                .tag("operation", operation)
                                .description("Transaction block SLA violations")
                                .register(meterRegistry)
                );
                slaCounter.increment();
            }
            
        } catch (Exception e) {
            log.error("Failed to record block metrics", e);
        }
    }
    
    public void recordBlockEffectiveness(String blockType, Integer blockedCount) {
        log.debug("Recording block effectiveness: blockType={} blockedCount={}", blockType, blockedCount);
        
        try {
            String counterKey = String.format("transaction.block.effectiveness.%s", blockType);
            Counter counter = counterCache.computeIfAbsent(counterKey, key ->
                    Counter.builder("transaction.block.effectiveness")
                            .tag("blockType", blockType)
                            .description("Number of transactions blocked")
                            .register(meterRegistry)
            );
            counter.increment(blockedCount != null ? blockedCount : 0);
            
        } catch (Exception e) {
            log.error("Failed to record block effectiveness metrics", e);
        }
    }
    
    public void recordBlockRiskMetrics(String blockType, Integer originalRiskScore, Integer enhancedRiskScore) {
        log.debug("Recording block risk metrics: blockType={} originalRisk={} enhancedRisk={}", 
                blockType, originalRiskScore, enhancedRiskScore);
        
        try {
            meterRegistry.gauge("transaction.block.risk.original", 
                    Map.of("blockType", blockType), originalRiskScore != null ? originalRiskScore : 0);
            
            meterRegistry.gauge("transaction.block.risk.enhanced", 
                    Map.of("blockType", blockType), enhancedRiskScore != null ? enhancedRiskScore : 0);
            
        } catch (Exception e) {
            log.error("Failed to record block risk metrics", e);
        }
    }
}
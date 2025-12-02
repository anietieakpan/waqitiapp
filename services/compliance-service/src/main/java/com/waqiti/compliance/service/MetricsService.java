package com.waqiti.compliance.service;

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

/**
 * Metrics service for compliance operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {
    
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();
    private final Map<String, Timer> timerCache = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Compliance MetricsService");
    }
    
    public void recordAuditEvent(String eventType, String result, long processingTimeMs) {
        log.debug("Recording audit event metric: type={} result={} time={}ms", 
                eventType, result, processingTimeMs);
        
        try {
            String counterKey = String.format("compliance.audit.%s.%s", eventType, result);
            Counter counter = counterCache.computeIfAbsent(counterKey, key ->
                    Counter.builder("compliance.audit.events")
                            .tag("eventType", eventType)
                            .tag("result", result)
                            .description("Compliance audit events counter")
                            .register(meterRegistry)
            );
            counter.increment();
            
            String timerKey = String.format("compliance.audit.%s", eventType);
            Timer timer = timerCache.computeIfAbsent(timerKey, key ->
                    Timer.builder("compliance.audit.processing.time")
                            .tag("eventType", eventType)
                            .description("Compliance audit processing time")
                            .register(meterRegistry)
            );
            timer.record(processingTimeMs, TimeUnit.MILLISECONDS);
            
        } catch (Exception e) {
            log.error("Failed to record audit event metric", e);
        }
    }
    
    public void recordComplianceCheck(String checkType, boolean passed, long durationMs) {
        log.debug("Recording compliance check metric: type={} passed={} duration={}ms", 
                checkType, passed, durationMs);
        
        try {
            String counterKey = String.format("compliance.check.%s.%s", checkType, passed ? "passed" : "failed");
            Counter counter = counterCache.computeIfAbsent(counterKey, key ->
                    Counter.builder("compliance.checks")
                            .tag("checkType", checkType)
                            .tag("result", passed ? "passed" : "failed")
                            .description("Compliance checks counter")
                            .register(meterRegistry)
            );
            counter.increment();
            
        } catch (Exception e) {
            log.error("Failed to record compliance check metric", e);
        }
    }
    
    public void recordRegulatoryReport(String reportType, String status) {
        log.debug("Recording regulatory report metric: type={} status={}", reportType, status);
        
        try {
            String counterKey = String.format("compliance.report.%s.%s", reportType, status);
            Counter counter = counterCache.computeIfAbsent(counterKey, key ->
                    Counter.builder("compliance.regulatory.reports")
                            .tag("reportType", reportType)
                            .tag("status", status)
                            .description("Regulatory reports counter")
                            .register(meterRegistry)
            );
            counter.increment();
            
        } catch (Exception e) {
            log.error("Failed to record regulatory report metric", e);
        }
    }
}
package com.waqiti.analytics.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

/**
 * Service for managing analytics metrics
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AnalyticsMetricsService {
    
    private final MeterRegistry meterRegistry;
    
    private Counter anomalyResultCounter;
    private Counter classificationCounter;
    private Counter validationCounter;
    private Timer processingTimer;
    
    @PostConstruct
    public void initMetrics() {
        anomalyResultCounter = Counter.builder("analytics.anomaly.results.total")
                .description("Total number of anomaly detection results processed")
                .register(meterRegistry);
                
        classificationCounter = Counter.builder("analytics.classification.total")
                .description("Total number of classifications performed")
                .register(meterRegistry);
                
        validationCounter = Counter.builder("analytics.validation.total")
                .description("Total number of validations performed")
                .register(meterRegistry);
                
        processingTimer = Timer.builder("analytics.processing.duration")
                .description("Time taken to process analytics events")
                .register(meterRegistry);
    }
    
    /**
     * Record anomaly detection result processing
     */
    public void recordAnomalyDetectionResult(String resultType, String status, Double confidence) {
        // Create counter with tags
        Counter.builder("analytics.anomaly.results.total")
                .description("Total number of anomaly detection results processed")
                .tag("result_type", resultType)
                .tag("status", status)
                .tag("confidence_level", getConfidenceLevel(confidence))
                .register(meterRegistry)
                .increment();

        log.debug("Recorded anomaly detection result: type={}, status={}, confidence={}",
                resultType, status, confidence);
    }

    /**
     * Record classification event
     */
    public void recordClassification(String classification, String method, Double confidence) {
        // Create counter with tags
        Counter.builder("analytics.classification.total")
                .description("Total number of classifications performed")
                .tag("classification", classification)
                .tag("method", method)
                .tag("confidence_level", getConfidenceLevel(confidence))
                .register(meterRegistry)
                .increment();

        log.debug("Recorded classification: class={}, method={}, confidence={}",
                classification, method, confidence);
    }

    /**
     * Record validation event
     */
    public void recordValidation(String validationStatus, String method) {
        // Create counter with tags
        Counter.builder("analytics.validation.total")
                .description("Total number of validations performed")
                .tag("status", validationStatus)
                .tag("method", method)
                .register(meterRegistry)
                .increment();

        log.debug("Recorded validation: status={}, method={}", validationStatus, method);
    }
    
    /**
     * Start processing timer
     */
    public Timer.Sample startProcessingTimer() {
        return Timer.start(meterRegistry);
    }
    
    /**
     * Stop processing timer
     */
    public void stopProcessingTimer(Timer.Sample sample, String operationType) {
        sample.stop(Timer.builder("analytics.processing.duration")
                .tag("operation", operationType)
                .register(meterRegistry));
    }
    
    private String getConfidenceLevel(Double confidence) {
        if (confidence == null) {
            return "UNKNOWN";
        }
        
        if (confidence >= 0.8) {
            return "HIGH";
        } else if (confidence >= 0.6) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
}
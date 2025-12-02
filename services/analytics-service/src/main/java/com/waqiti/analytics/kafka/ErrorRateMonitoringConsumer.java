package com.waqiti.analytics.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.ErrorRateEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.analytics.service.ErrorAnalyticsService;
import com.waqiti.analytics.service.AlertService;
import com.waqiti.analytics.model.ErrorRateMetric;
import com.waqiti.analytics.repository.ErrorRateRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Consumer for processing error rate monitoring events.
 * Tracks error rates, analyzes error patterns, and triggers alerts for error spikes.
 */
@Slf4j
@Component
public class ErrorRateMonitoringConsumer extends BaseKafkaConsumer<ErrorRateEvent> {

    private static final String TOPIC = "error-rate-monitoring-events";
    private static final double HIGH_ERROR_RATE_THRESHOLD = 5.0; // 5%
    private static final double CRITICAL_ERROR_RATE_THRESHOLD = 10.0; // 10%

    private final ErrorAnalyticsService errorAnalyticsService;
    private final AlertService alertService;
    private final ErrorRateRepository errorRateRepository;

    // Metrics
    private final Counter processedCounter;
    private final Counter errorSpikeCounter;
    private final Counter criticalErrorRateCounter;
    private final Timer processingTimer;

    @Autowired
    public ErrorRateMonitoringConsumer(
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            ErrorAnalyticsService errorAnalyticsService,
            AlertService alertService,
            ErrorRateRepository errorRateRepository) {
        super(objectMapper, TOPIC);
        this.errorAnalyticsService = errorAnalyticsService;
        this.alertService = alertService;
        this.errorRateRepository = errorRateRepository;

        this.processedCounter = Counter.builder("error_rate_processed_total")
                .description("Total error rate events processed")
                .register(meterRegistry);
        this.errorSpikeCounter = Counter.builder("error_spike_detected_total")
                .description("Total error spikes detected")
                .register(meterRegistry);
        this.criticalErrorRateCounter = Counter.builder("critical_error_rate_total")
                .description("Total critical error rate incidents")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("error_rate_processing_duration")
                .description("Time taken to process error rate events")
                .register(meterRegistry);
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(topics = TOPIC, groupId = "analytics-service-error-rate-group")
    @Transactional
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment ack) {

        Timer.Sample sample = Timer.start();

        try {
            log.info("Processing error rate event - Key: {}, Partition: {}, Offset: {}",
                    key, partition, record.offset());

            ErrorRateEvent event = deserializeEvent(record.value(), ErrorRateEvent.class);

            // Validate required fields
            validateEvent(event);

            // Process the error rate event
            processErrorRateEvent(event);

            processedCounter.increment();
            log.info("Successfully processed error rate event: {} - {}%",
                    event.getServiceId(), event.getErrorRate());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing error rate event: {}", record.value(), e);
            throw new RuntimeException("Failed to process error rate event", e);
        } finally {
            sample.stop(processingTimer);
        }
    }

    private void processErrorRateEvent(ErrorRateEvent event) {
        try {
            // Create error rate metric record
            ErrorRateMetric metric = createErrorRateMetric(event);

            // Check for high error rates
            if (isHighErrorRate(event)) {
                handleHighErrorRate(event, metric);
            }

            // Check for error spikes
            if (isErrorSpike(event)) {
                handleErrorSpike(event, metric);
                errorSpikeCounter.increment();
            }

            // Analyze error patterns
            analyzeErrorPatterns(event, metric);

            // Update service error statistics
            updateServiceErrorStats(event, metric);

            // Save the metric
            errorRateRepository.save(metric);

            // Generate error insights
            generateErrorInsights(event, metric);

            log.info("Processed error rate metric: {} - {}% (Errors: {}/{})",
                    event.getServiceId(), event.getErrorRate(),
                    event.getErrorCount(), event.getTotalRequests());

        } catch (Exception e) {
            log.error("Error processing error rate event: {}", event.getServiceId(), e);
            throw new RuntimeException("Failed to process error rate event", e);
        }
    }

    private ErrorRateMetric createErrorRateMetric(ErrorRateEvent event) {
        return ErrorRateMetric.builder()
                .serviceId(event.getServiceId())
                .serviceName(event.getServiceName())
                .endpoint(event.getEndpoint())
                .method(event.getMethod())
                .errorRate(event.getErrorRate())
                .errorCount(event.getErrorCount())
                .totalRequests(event.getTotalRequests())
                .timeWindow(event.getTimeWindow())
                .errorTypes(event.getErrorTypes())
                .statusCodes(event.getStatusCodes())
                .timestamp(event.getTimestamp())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private boolean isHighErrorRate(ErrorRateEvent event) {
        return event.getErrorRate() > HIGH_ERROR_RATE_THRESHOLD;
    }

    private void handleHighErrorRate(ErrorRateEvent event, ErrorRateMetric metric) {
        try {
            String severity = event.getErrorRate() > CRITICAL_ERROR_RATE_THRESHOLD ? "CRITICAL" : "WARNING";
            metric.setSeverity(severity);

            // Create error rate alert
            alertService.createErrorRateAlert(
                event.getServiceId(),
                event.getEndpoint(),
                event.getErrorRate(),
                severity
            );

            if ("CRITICAL".equals(severity)) {
                criticalErrorRateCounter.increment();

                // Trigger incident response for critical error rates
                errorAnalyticsService.triggerIncidentResponse(
                    event.getServiceId(), event.getErrorRate()
                );
            }

            // Analyze error distribution
            var errorDistribution = errorAnalyticsService.analyzeErrorDistribution(
                event.getErrorTypes(), event.getStatusCodes()
            );

            metric.setErrorDistribution(errorDistribution);

            log.warn("High error rate detected: {} - {}% ({})",
                    event.getServiceId(), event.getErrorRate(), severity);

        } catch (Exception e) {
            log.error("Error handling high error rate: {}", event.getServiceId(), e);
            throw new RuntimeException("Failed to handle high error rate", e);
        }
    }

    private boolean isErrorSpike(ErrorRateEvent event) {
        // Compare current error rate with historical baseline
        double baseline = errorAnalyticsService.getErrorRateBaseline(
            event.getServiceId(), event.getEndpoint()
        );

        // Consider it a spike if current rate is 3x the baseline
        return event.getErrorRate() > (baseline * 3) && event.getErrorRate() > 1.0; // At least 1% to avoid noise
    }

    private void handleErrorSpike(ErrorRateEvent event, ErrorRateMetric metric) {
        try {
            metric.setErrorSpike(true);

            // Calculate spike magnitude
            double baseline = errorAnalyticsService.getErrorRateBaseline(
                event.getServiceId(), event.getEndpoint()
            );
            double spikeMagnitude = event.getErrorRate() / baseline;
            metric.setSpikeMagnitude(spikeMagnitude);

            // Create error spike alert
            alertService.createErrorSpikeAlert(
                event.getServiceId(),
                event.getEndpoint(),
                event.getErrorRate(),
                baseline,
                spikeMagnitude
            );

            // Analyze potential causes
            var causeAnalysis = errorAnalyticsService.analyzePotentialCauses(
                event.getServiceId(), event.getEndpoint(), event.getTimestamp()
            );

            metric.setPotentialCauses(causeAnalysis);

            log.warn("Error spike detected: {} - {}% ({}x baseline)",
                    event.getServiceId(), event.getErrorRate(), Math.round(spikeMagnitude * 10.0) / 10.0);

        } catch (Exception e) {
            log.error("Error handling error spike: {}", event.getServiceId(), e);
            throw new RuntimeException("Failed to handle error spike", e);
        }
    }

    private void analyzeErrorPatterns(ErrorRateEvent event, ErrorRateMetric metric) {
        try {
            // Analyze error patterns over time
            var patternAnalysis = errorAnalyticsService.analyzeErrorPatterns(
                event.getServiceId(), event.getEndpoint(), event.getErrorTypes()
            );

            metric.setPatternAnalysis(patternAnalysis);

            // Check for recurring error patterns
            if (patternAnalysis.hasRecurringPatterns()) {
                alertService.createRecurringErrorAlert(
                    event.getServiceId(), patternAnalysis.getRecurringPatterns()
                );
            }

            // Identify error hotspots
            if (patternAnalysis.isErrorHotspot()) {
                alertService.createErrorHotspotAlert(
                    event.getServiceId(), event.getEndpoint()
                );
            }

        } catch (Exception e) {
            log.error("Error analyzing error patterns: {}", event.getServiceId(), e);
            // Don't fail the processing for pattern analysis errors
        }
    }

    private void updateServiceErrorStats(ErrorRateEvent event, ErrorRateMetric metric) {
        try {
            // Update service-level error statistics
            errorAnalyticsService.updateServiceErrorStats(
                event.getServiceId(), event.getErrorRate(), event.getErrorCount()
            );

            // Update endpoint-level error statistics
            errorAnalyticsService.updateEndpointErrorStats(
                event.getServiceId(), event.getEndpoint(), metric
            );

            // Update global error rate trends
            errorAnalyticsService.updateGlobalErrorTrends(event.getErrorRate());

        } catch (Exception e) {
            log.error("Error updating service error stats: {}", event.getServiceId(), e);
            // Don't fail the processing for stats update errors
        }
    }

    private void generateErrorInsights(ErrorRateEvent event, ErrorRateMetric metric) {
        try {
            // Generate actionable insights based on error patterns
            var insights = errorAnalyticsService.generateErrorInsights(
                event.getServiceId(), event.getEndpoint(), metric
            );

            if (!insights.isEmpty()) {
                // Store insights for development teams
                errorAnalyticsService.storeErrorInsights(
                    event.getServiceId(), insights
                );

                // Send insights to relevant teams
                alertService.sendErrorInsights(
                    event.getServiceId(), insights
                );
            }

        } catch (Exception e) {
            log.error("Error generating error insights: {}", event.getServiceId(), e);
            // Don't fail the processing for insight generation errors
        }
    }

    private void validateEvent(ErrorRateEvent event) {
        if (event.getServiceId() == null || event.getServiceId().trim().isEmpty()) {
            throw new IllegalArgumentException("Service ID cannot be null or empty");
        }
        if (event.getErrorRate() == null || event.getErrorRate() < 0) {
            throw new IllegalArgumentException("Error rate cannot be null or negative");
        }
        if (event.getErrorCount() == null || event.getErrorCount() < 0) {
            throw new IllegalArgumentException("Error count cannot be null or negative");
        }
        if (event.getTotalRequests() == null || event.getTotalRequests() <= 0) {
            throw new IllegalArgumentException("Total requests must be positive");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
    }

    @Override
    protected void handleProcessingError(String message, Exception error) {
        log.error("Error rate processing error: {}", message, error);
        // Additional error handling logic
    }

    @Override
    protected void logProcessingMetrics(String key, long processingTime) {
        log.debug("Processed error rate event - Key: {}, Time: {}ms", key, processingTime);
    }
}
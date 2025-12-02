package com.waqiti.payment.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class MetricsService {

    private final MeterRegistry meterRegistry;

    private Counter batchProcessedCounter;
    private Counter batchFailedCounter;
    private Timer batchProcessingTimer;
    private Counter slaViolationCounter;

    @PostConstruct
    public void initMetrics() {
        batchProcessedCounter = Counter.builder("batch.payments.processed")
            .description("Total number of batch payments processed")
            .register(meterRegistry);

        batchFailedCounter = Counter.builder("batch.payments.failed")
            .description("Total number of batch payments that failed")
            .register(meterRegistry);

        batchProcessingTimer = Timer.builder("batch.processing.duration")
            .description("Time taken to process batch payments")
            .register(meterRegistry);

        slaViolationCounter = Counter.builder("batch.sla.violations")
            .description("Number of SLA violations for batch processing")
            .register(meterRegistry);
    }

    public void recordBatchMetrics(String batchType, Integer paymentCount, Integer processedCount,
                                   Integer failedCount, long totalProcessingTime, Double successRate) {
        log.info("Recording batch metrics: type={}, total={}, processed={}, failed={}, time={}ms, successRate={}%",
            batchType, paymentCount, processedCount, failedCount, totalProcessingTime, successRate);

        // Record counters
        batchProcessedCounter.increment(processedCount);
        batchFailedCounter.increment(failedCount);

        // Record processing time
        batchProcessingTimer.record(totalProcessingTime, TimeUnit.MILLISECONDS);

        // Record custom gauges
        meterRegistry.gauge("batch.success.rate", successRate);
        meterRegistry.gauge("batch.payment.count", paymentCount);

        // Tag metrics by batch type
        Counter.builder("batch.by.type")
            .tag("type", batchType)
            .register(meterRegistry)
            .increment(paymentCount);
    }

    public void recordBatchSLA(String batchId, boolean slaCompliant, long totalProcessingTime) {
        log.info("Recording batch SLA: batchId={}, compliant={}, time={}ms",
            batchId, slaCompliant, totalProcessingTime);

        if (!slaCompliant) {
            slaViolationCounter.increment();
            log.warn("SLA violation detected for batch: {}, processingTime={}ms", batchId, totalProcessingTime);
        }

        // Record SLA compliance rate
        Counter.builder("batch.sla.checks")
            .tag("compliant", String.valueOf(slaCompliant))
            .register(meterRegistry)
            .increment();
    }
}

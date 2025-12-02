package com.waqiti.common.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Metrics tracking for Kafka idempotency operations
 *
 * Tracks:
 * - Messages processed
 * - Duplicates detected
 * - Processing duration
 * - Error rates
 *
 * Integrated with Micrometer for Prometheus/Grafana
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaIdempotencyMetrics {

    private final MeterRegistry meterRegistry;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String METRICS_PREFIX = "kafka.idempotency";

    /**
     * Record successful message processing
     */
    public void recordProcessed(String consumerName, String topic, long durationMs) {
        Counter.builder(METRICS_PREFIX + ".processed")
                .tag("consumer", consumerName)
                .tag("topic", topic)
                .description("Number of Kafka messages processed")
                .register(meterRegistry)
                .increment();

        Timer.builder(METRICS_PREFIX + ".duration")
                .tag("consumer", consumerName)
                .tag("topic", topic)
                .description("Kafka message processing duration")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);

        // Store in Redis for rate calculations
        String key = String.format("kafka:metrics:%s:%s:processed", consumerName, topic);
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofMinutes(10));
    }

    /**
     * Record duplicate message detection
     */
    public void recordDuplicate(String consumerName, String topic) {
        Counter.builder(METRICS_PREFIX + ".duplicates")
                .tag("consumer", consumerName)
                .tag("topic", topic)
                .description("Number of duplicate Kafka messages detected")
                .register(meterRegistry)
                .increment();

        // Store in Redis for rate calculations
        String key = String.format("kafka:metrics:%s:%s:duplicates", consumerName, topic);
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofMinutes(10));
    }

    /**
     * Record processing error
     */
    public void recordError(String consumerName, String topic) {
        Counter.builder(METRICS_PREFIX + ".errors")
                .tag("consumer", consumerName)
                .tag("topic", topic)
                .description("Number of Kafka message processing errors")
                .register(meterRegistry)
                .increment();

        // Store in Redis for rate calculations
        String key = String.format("kafka:metrics:%s:%s:errors", consumerName, topic);
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofMinutes(10));
    }

    /**
     * Get duplicate rate for consumer within time window
     */
    public double getDuplicateRate(String consumerName, String topic, Duration window) {
        try {
            String processedKey = String.format("kafka:metrics:%s:%s:processed", consumerName, topic);
            String duplicatesKey = String.format("kafka:metrics:%s:%s:duplicates", consumerName, topic);

            String processedStr = redisTemplate.opsForValue().get(processedKey);
            String duplicatesStr = redisTemplate.opsForValue().get(duplicatesKey);

            long processed = processedStr != null ? Long.parseLong(processedStr) : 0;
            long duplicates = duplicatesStr != null ? Long.parseLong(duplicatesStr) : 0;

            if (processed + duplicates == 0) {
                return 0.0;
            }

            return (double) duplicates / (processed + duplicates);

        } catch (Exception e) {
            log.error("Failed to calculate duplicate rate", e);
            return 0.0;
        }
    }

    /**
     * Get error rate for consumer within time window
     */
    public double getErrorRate(String consumerName, String topic, Duration window) {
        try {
            String processedKey = String.format("kafka:metrics:%s:%s:processed", consumerName, topic);
            String errorsKey = String.format("kafka:metrics:%s:%s:errors", consumerName, topic);

            String processedStr = redisTemplate.opsForValue().get(processedKey);
            String errorsStr = redisTemplate.opsForValue().get(errorsKey);

            long processed = processedStr != null ? Long.parseLong(processedStr) : 0;
            long errors = errorsStr != null ? Long.parseLong(errorsStr) : 0;

            if (processed + errors == 0) {
                return 0.0;
            }

            return (double) errors / (processed + errors);

        } catch (Exception e) {
            log.error("Failed to calculate error rate", e);
            return 0.0;
        }
    }
}

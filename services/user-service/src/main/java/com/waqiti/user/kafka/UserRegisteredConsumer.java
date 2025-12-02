package com.waqiti.user.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.user.service.UserService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredConsumer {

    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final UniversalDLQHandler dlqHandler;

    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private final Counter processedCounter;
    private final Counter errorCounter;
    private final Timer processingTimer;

    public UserRegisteredConsumer(UserService userService,
                                ObjectMapper objectMapper,
                                MeterRegistry meterRegistry,
                                UniversalDLQHandler dlqHandler) {
        this.userService = userService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.dlqHandler = dlqHandler;
        this.processedCounter = Counter.builder("user_registered_processed_total")
                .description("Total user registered events processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("user_registered_errors_total")
                .description("Total user registered errors")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("user_registered_duration")
                .description("User registered processing duration")
                .register(meterRegistry);
    }

    @KafkaListener(
        topics = "user-registered",
        groupId = "user-service-user-registered-group",
        containerFactory = "kafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation-level=read_committed",
            "spring.kafka.consumer.auto-offset-reset=earliest"
        }
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        autoCreateTopics = "true",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @CircuitBreaker(name = "user-registered", fallbackMethod = "fallbackUserRegistered")
    @Transactional
    public void handleUserRegistered(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(record, topic, partition, offset);

        try {
            if (isAlreadyProcessed(eventId)) {
                log.debug("User registered event already processed: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing user registered event: topic={}, partition={}, offset={}, key={}",
                     topic, partition, offset, record.key());

            String eventData = record.value();
            String correlationId = UUID.randomUUID().toString();

            // Process user registration with onboarding workflow
            userService.processUserRegistered(
                eventData,
                record.key(),
                correlationId
            );

            markAsProcessed(eventId);
            processedCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed user registered event: {} with correlation: {}",
                     eventId, correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Error processing event: topic={}, partition={}, offset={}, error={}",
                    topic, partition, offset, e.getMessage(), e);

            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(result -> log.info("Sent to DLQ: {}", result.getDestinationTopic()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ failed", dlqError);
                    return null;
                });

            throw new RuntimeException("Processing failed", e);
        } finally {
            sample.stop(processingTimer);
        }
    }

    @DltHandler
    public void handleDlt(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        log.error("User registered event sent to DLT: topic={}, key={}, exception={}",
                  topic, record.key(), exceptionMessage);

        // User registration failures require immediate customer onboarding escalation
        userService.escalateUserRegisteredFailure(
            record.value(),
            record.key(),
            exceptionMessage
        );
    }

    public void fallbackUserRegistered(
            ConsumerRecord<String, String> record,
            Exception ex) {
        log.warn("User registered circuit breaker activated for event: {}", record.key(), ex);

        // Fallback to manual user onboarding
        userService.routeToManualOnboarding(
            record.value(),
            record.key(),
            "CIRCUIT_BREAKER_ACTIVATED_USER_REGISTERED"
        );
    }

    private String generateEventId(ConsumerRecord<String, String> record, String topic, int partition, long offset) {
        return String.format("%s-%d-%d-%s", topic, partition, offset, record.key());
    }

    private boolean isAlreadyProcessed(String eventId) {
        Long processTime = processedEvents.get(eventId);
        if (processTime != null) {
            return System.currentTimeMillis() - processTime < Duration.ofHours(24).toMillis();
        }
        return false;
    }

    private void markAsProcessed(String eventId) {
        processedEvents.put(eventId, System.currentTimeMillis());
    }
}
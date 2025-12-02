/**
 * Saga Choreography Coordinator
 * Implements event-driven choreography pattern for distributed sagas
 * Complements the existing orchestration pattern with decentralized coordination
 */
package com.waqiti.common.saga;

import com.waqiti.common.events.SagaEvent;
import com.waqiti.common.events.SagaEventType;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Coordinates saga execution using choreography pattern
 * Each service listens for events and knows what to do next
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SagaChoreographyCoordinator {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SagaStateRepository sagaStateRepository;
    private final SagaStepExecutor stepExecutor;
    private final SagaCompensationService compensationService;
    
    // Track choreography state
    private final Map<String, ChoreographyContext> activeChoreographies = new ConcurrentHashMap<>();
    
    // Service-to-step mappings
    private final Map<String, List<ChoreographyRule>> choreographyRules = new ConcurrentHashMap<>();

    /**
     * Start a choreography-based saga
     */
    public CompletableFuture<SagaResult> startChoreography(String sagaType, Map<String, Object> initialData) {
        String sagaId = UUID.randomUUID().toString();
        
        log.info("Starting choreography saga: id={}, type={}", sagaId, sagaType);
        
        // Create choreography context
        ChoreographyContext context = ChoreographyContext.builder()
            .sagaId(sagaId)
            .sagaType(sagaType)
            .startTime(Instant.now())
            .data(new HashMap<>(initialData))
            .completedSteps(new HashSet<>())
            .failedSteps(new HashSet<>())
            .status(ChoreographyStatus.IN_PROGRESS)
            .build();
        
        activeChoreographies.put(sagaId, context);
        
        // Publish initial event to trigger choreography
        ChoreographyEvent initialEvent = ChoreographyEvent.builder()
            .sagaId(sagaId)
            .sagaType(sagaType)
            .eventType("SAGA_INITIATED")
            .sourceService("saga-coordinator")
            .payload(initialData)
            .timestamp(Instant.now())
            .build();
        
        publishEvent(initialEvent);
        
        // Return future that will complete when saga finishes
        CompletableFuture<SagaResult> future = new CompletableFuture<>();
        context.setCompletionFuture(future);
        
        return future;
    }

    /**
     * Listen for choreography events
     */
    @KafkaListener(topics = "saga-choreography-events", groupId = "choreography-coordinator")
    public void handleChoreographyEvent(ChoreographyEvent event) {
        log.debug("Received choreography event: sagaId={}, type={}, source={}", 
            event.getSagaId(), event.getEventType(), event.getSourceService());
        
        ChoreographyContext context = activeChoreographies.get(event.getSagaId());
        if (context == null) {
            log.warn("No active choreography for saga: {}", event.getSagaId());
            return;
        }
        
        // Update context with event data
        updateContext(context, event);
        
        // Check if saga is complete or failed
        if (isChoreographyComplete(context)) {
            completeChoreography(context);
        } else if (isChoreographyFailed(context)) {
            initiateChoreographyCompensation(context);
        } else {
            // Trigger next steps based on rules
            triggerNextSteps(context, event);
        }
    }

    /**
     * Register choreography rules for a service
     */
    public void registerChoreographyRules(String service, List<ChoreographyRule> rules) {
        choreographyRules.put(service, rules);
        log.info("Registered {} choreography rules for service: {}", rules.size(), service);
    }

    /**
     * Handle step completion event
     */
    @KafkaListener(topics = "saga-step-completed", groupId = "choreography-coordinator")
    public void handleStepCompleted(StepCompletedEvent event) {
        ChoreographyContext context = activeChoreographies.get(event.getSagaId());
        if (context == null) return;
        
        context.getCompletedSteps().add(event.getStepId());
        context.getData().putAll(event.getOutputData());
        
        // Publish choreography event for other services
        ChoreographyEvent choreographyEvent = ChoreographyEvent.builder()
            .sagaId(event.getSagaId())
            .sagaType(context.getSagaType())
            .eventType("STEP_COMPLETED_" + event.getStepId().toUpperCase())
            .sourceService(event.getServiceName())
            .payload(event.getOutputData())
            .timestamp(Instant.now())
            .build();
        
        publishEvent(choreographyEvent);
    }

    /**
     * Handle step failure event
     */
    @KafkaListener(topics = "saga-step-failed", groupId = "choreography-coordinator")
    public void handleStepFailed(StepFailedEvent event) {
        ChoreographyContext context = activeChoreographies.get(event.getSagaId());
        if (context == null) return;
        
        context.getFailedSteps().add(event.getStepId());
        context.setStatus(ChoreographyStatus.FAILED);
        context.setFailureReason(event.getErrorMessage());
        
        // Initiate compensation
        initiateChoreographyCompensation(context);
    }

    /**
     * Update choreography context based on event
     */
    private void updateContext(ChoreographyContext context, ChoreographyEvent event) {
        // Update context data
        if (event.getPayload() != null) {
            context.getData().putAll(event.getPayload());
        }
        
        // Track event in history
        context.getEventHistory().add(event);
        
        // Update last activity time
        context.setLastActivityTime(Instant.now());
    }

    /**
     * Check if choreography is complete
     */
    private boolean isChoreographyComplete(ChoreographyContext context) {
        // Get expected steps for this saga type
        Set<String> expectedSteps = getExpectedSteps(context.getSagaType());
        
        // Check if all expected steps are completed
        return context.getCompletedSteps().containsAll(expectedSteps);
    }

    /**
     * Check if choreography has failed
     */
    private boolean isChoreographyFailed(ChoreographyContext context) {
        return context.getStatus() == ChoreographyStatus.FAILED || 
               !context.getFailedSteps().isEmpty();
    }

    /**
     * Trigger next steps based on choreography rules
     */
    private void triggerNextSteps(ChoreographyContext context, ChoreographyEvent event) {
        // Find applicable rules
        List<ChoreographyRule> applicableRules = findApplicableRules(context, event);
        
        for (ChoreographyRule rule : applicableRules) {
            if (shouldTriggerRule(context, rule)) {
                triggerRule(context, rule);
            }
        }
    }

    /**
     * Find rules that apply to current event
     */
    private List<ChoreographyRule> findApplicableRules(ChoreographyContext context, ChoreographyEvent event) {
        List<ChoreographyRule> applicable = new ArrayList<>();
        
        for (List<ChoreographyRule> serviceRules : choreographyRules.values()) {
            for (ChoreographyRule rule : serviceRules) {
                if (rule.appliesTo(context.getSagaType(), event.getEventType())) {
                    applicable.add(rule);
                }
            }
        }
        
        return applicable;
    }

    /**
     * Check if rule should be triggered
     */
    private boolean shouldTriggerRule(ChoreographyContext context, ChoreographyRule rule) {
        // Check prerequisites
        if (!context.getCompletedSteps().containsAll(rule.getPrerequisites())) {
            return false;
        }
        
        // Check if already triggered
        if (context.getTriggeredRules().contains(rule.getRuleId())) {
            return false;
        }
        
        // Evaluate condition
        if (rule.getCondition() != null) {
            return rule.getCondition().test(context.getData());
        }
        
        return true;
    }

    /**
     * Trigger a choreography rule
     */
    private void triggerRule(ChoreographyContext context, ChoreographyRule rule) {
        log.info("Triggering choreography rule: sagaId={}, ruleId={}", 
            context.getSagaId(), rule.getRuleId());
        
        context.getTriggeredRules().add(rule.getRuleId());
        
        // Create event for target service
        ChoreographyEvent triggerEvent = ChoreographyEvent.builder()
            .sagaId(context.getSagaId())
            .sagaType(context.getSagaType())
            .eventType(rule.getTriggerEventType())
            .sourceService("choreography-coordinator")
            .targetService(rule.getTargetService())
            .payload(preparePayload(context, rule))
            .timestamp(Instant.now())
            .build();
        
        publishEvent(triggerEvent);
    }

    /**
     * Prepare payload for triggered rule
     */
    private Map<String, Object> preparePayload(ChoreographyContext context, ChoreographyRule rule) {
        Map<String, Object> payload = new HashMap<>();
        
        // Add required data fields
        for (String field : rule.getRequiredDataFields()) {
            if (context.getData().containsKey(field)) {
                payload.put(field, context.getData().get(field));
            }
        }
        
        // Add rule metadata
        payload.put("_rule", Map.of(
            "ruleId", rule.getRuleId(),
            "targetStep", rule.getTargetStep()
        ));
        
        return payload;
    }

    /**
     * Complete choreography
     */
    private void completeChoreography(ChoreographyContext context) {
        log.info("Choreography completed: sagaId={}", context.getSagaId());
        
        context.setStatus(ChoreographyStatus.COMPLETED);
        context.setEndTime(Instant.now());
        
        // Create result
        SagaResult result = SagaResultConstructor.create(
            context.getSagaId(),
            SagaStatus.COMPLETED,
            "Choreography completed successfully",
            context.getData(),
            context.getEndTime().toEpochMilli() - context.getStartTime().toEpochMilli()
        );
        
        // Complete future
        if (context.getCompletionFuture() != null) {
            context.getCompletionFuture().complete(result);
        }
        
        // Publish completion event
        publishCompletionEvent(context);
        
        // Clean up
        activeChoreographies.remove(context.getSagaId());
    }

    /**
     * Initiate compensation for failed choreography
     */
    private void initiateChoreographyCompensation(ChoreographyContext context) {
        log.info("Initiating choreography compensation: sagaId={}", context.getSagaId());
        
        context.setStatus(ChoreographyStatus.COMPENSATING);
        
        // Publish compensation events in reverse order
        List<String> completedSteps = new ArrayList<>(context.getCompletedSteps());
        Collections.reverse(completedSteps);
        
        for (String stepId : completedSteps) {
            ChoreographyEvent compensationEvent = ChoreographyEvent.builder()
                .sagaId(context.getSagaId())
                .sagaType(context.getSagaType())
                .eventType("COMPENSATE_" + stepId.toUpperCase())
                .sourceService("choreography-coordinator")
                .payload(Map.of("stepId", stepId, "reason", context.getFailureReason()))
                .timestamp(Instant.now())
                .build();
            
            publishEvent(compensationEvent);
        }
    }

    /**
     * Publish choreography event
     */
    private void publishEvent(ChoreographyEvent event) {
        kafkaTemplate.send("saga-choreography-events", event.getSagaId(), event);
    }

    /**
     * Publish completion event
     */
    private void publishCompletionEvent(ChoreographyContext context) {
        ChoreographyEvent completionEvent = ChoreographyEvent.builder()
            .sagaId(context.getSagaId())
            .sagaType(context.getSagaType())
            .eventType("SAGA_COMPLETED")
            .sourceService("choreography-coordinator")
            .payload(context.getData())
            .timestamp(Instant.now())
            .build();
        
        publishEvent(completionEvent);
    }

    /**
     * Get expected steps for saga type
     */
    private Set<String> getExpectedSteps(String sagaType) {
        // This would be configured per saga type
        // For now, return a default set
        return switch (sagaType) {
            case "P2P_TRANSFER" -> Set.of(
                "validate_sender",
                "validate_receiver",
                "fraud_check",
                "place_hold",
                "transfer_funds",
                "release_hold",
                "send_notifications"
            );
            case "PAYMENT_PROCESSING" -> Set.of(
                "validate_payment",
                "check_balance",
                "process_payment",
                "update_ledger",
                "send_receipt"
            );
            default -> new HashSet<>();
        };
    }

    // Data classes
    
    @Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ChoreographyContext {
        @Builder.Default
        private String sagaId = null;
        private String sagaType;
        private Instant startTime;
        private Instant endTime;
        private Instant lastActivityTime;
        @Builder.Default
        private Map<String, Object> data = new HashMap<>();
        @Builder.Default
        private Set<String> completedSteps = new HashSet<>();
        @Builder.Default
        private Set<String> failedSteps = new HashSet<>();
        @Builder.Default
        private Set<String> triggeredRules = new HashSet<>();
        @Builder.Default
        private List<ChoreographyEvent> eventHistory = new ArrayList<>();
        private ChoreographyStatus status;
        private String failureReason;
        private CompletableFuture<SagaResult> completionFuture;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChoreographyEvent {
        private String sagaId;
        private String sagaType;
        private String eventType;
        private String sourceService;
        private String targetService;
        private Map<String, Object> payload;
        private Instant timestamp;
    }

    @Data
    @Builder
    public static class ChoreographyRule {
        private String ruleId;
        private String sagaType;
        private String triggerEventType;
        private String targetService;
        private String targetStep;
        private Set<String> prerequisites;
        private List<String> requiredDataFields;
        private java.util.function.Predicate<Map<String, Object>> condition;
        
        public boolean appliesTo(String sagaType, String eventType) {
            return this.sagaType.equals(sagaType) && 
                   this.triggerEventType.equals(eventType);
        }
    }

    @Data
    @AllArgsConstructor
    public static class StepCompletedEvent {
        private String sagaId;
        private String stepId;
        private String serviceName;
        private Map<String, Object> outputData;
    }

    @Data
    @AllArgsConstructor
    public static class StepFailedEvent {
        private String sagaId;
        private String stepId;
        private String serviceName;
        private String errorMessage;
    }

    public enum ChoreographyStatus {
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        COMPENSATING,
        COMPENSATED,
        TIMEOUT
    }
}
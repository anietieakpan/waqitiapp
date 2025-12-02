package com.waqiti.transaction.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.transaction.model.*;
import com.waqiti.transaction.repository.TransactionControlRepository;
import com.waqiti.transaction.service.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Production-grade Kafka consumer for transaction control events
 * Handles dynamic transaction rules, velocity controls, risk thresholds, and real-time policy enforcement
 * 
 * Critical for: Risk management, fraud prevention, regulatory compliance, operational controls
 * SLA: Must apply control changes within 1 second for real-time risk mitigation
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TransactionControlConsumer {

    private final TransactionControlRepository controlRepository;
    private final TransactionService transactionService;
    private final VelocityControlService velocityService;
    private final RiskControlService riskService;
    private final ComplianceControlService complianceService;
    private final PolicyEngineService policyService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final CacheService cacheService;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long SLA_THRESHOLD_MS = 1000; // 1 second
    private static final Set<String> CRITICAL_CONTROL_TYPES = Set.of(
        "EMERGENCY_STOP", "FRAUD_THRESHOLD", "SANCTIONS_CONTROL", "REGULATORY_LIMIT", "SYSTEM_CIRCUIT_BREAKER"
    );
    
    @KafkaListener(
        topics = {"transaction-control"},
        groupId = "transaction-control-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "transaction-control-processor", fallbackMethod = "handleTransactionControlFailure")
    @Retry(name = "transaction-control-processor")
    public void processTransactionControlEvent(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        log.info("Processing transaction control event: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, Object> payload = event.getPayload();
            TransactionControlRequest controlRequest = extractControlRequest(payload);
            
            // Validate control request
            validateControlRequest(controlRequest);
            
            // Check for duplicate control request
            if (isDuplicateControlRequest(controlRequest)) {
                log.warn("Duplicate control request detected: {}, skipping", controlRequest.getControlId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Determine control operation type
            ControlOperationType operationType = determineControlOperation(controlRequest);
            
            // Process control request
            ControlProcessingResult result = processControlRequest(controlRequest, operationType);
            
            // Apply real-time controls
            applyRealTimeControls(controlRequest, result);
            
            // Update policy engine
            updatePolicyEngine(controlRequest, result);
            
            // Update caching systems
            updateControlCaches(controlRequest, result);
            
            // Handle cascading effects
            if (controlRequest.hasCascadingEffects()) {
                handleCascadingEffects(controlRequest, result);
            }
            
            // Send control notifications
            sendControlNotifications(controlRequest, result);
            
            // Update monitoring systems
            updateMonitoringSystems(controlRequest, result);
            
            // Create audit trail
            auditControlOperation(controlRequest, result, event);
            
            // Record metrics
            recordControlMetrics(controlRequest, result, startTime);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed transaction control: {} operation: {} in {}ms", 
                    controlRequest.getControlId(), operationType, System.currentTimeMillis() - startTime);
            
        } catch (ValidationException e) {
            log.error("Validation failed for transaction control event: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();
            
        } catch (CriticalControlException e) {
            log.error("Critical control operation failed: {}", eventId, e);
            handleCriticalControlError(event, e);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process transaction control event: {}", eventId, e);
            handleProcessingError(event, e, acknowledgment);
        }
    }

    private TransactionControlRequest extractControlRequest(Map<String, Object> payload) {
        return TransactionControlRequest.builder()
            .controlId(extractString(payload, "controlId", UUID.randomUUID().toString()))
            .controlType(ControlType.fromString(extractString(payload, "controlType", null)))
            .operation(ControlOperation.fromString(extractString(payload, "operation", "CREATE")))
            .scope(ControlScope.fromString(extractString(payload, "scope", "GLOBAL")))
            .targetType(extractString(payload, "targetType", null))
            .targetId(extractString(payload, "targetId", null))
            .ruleName(extractString(payload, "ruleName", null))
            .ruleType(RuleType.fromString(extractString(payload, "ruleType", null)))
            .conditions(extractMap(payload, "conditions"))
            .actions(extractList(payload, "actions"))
            .threshold(extractBigDecimal(payload, "threshold"))
            .currency(extractString(payload, "currency", "USD"))
            .timeWindow(extractLong(payload, "timeWindow", null))
            .timeUnit(extractString(payload, "timeUnit", "MINUTES"))
            .priority(extractInteger(payload, "priority", 100))
            .severity(ControlSeverity.fromString(extractString(payload, "severity", "MEDIUM")))
            .status(ControlStatus.fromString(extractString(payload, "status", "ACTIVE")))
            .effectiveFrom(extractInstant(payload, "effectiveFrom"))
            .effectiveTo(extractInstant(payload, "effectiveTo"))
            .reason(extractString(payload, "reason", null))
            .category(extractString(payload, "category", "RISK"))
            .tags(extractStringList(payload, "tags"))
            .metadata(extractMap(payload, "metadata"))
            .sourceSystem(extractString(payload, "sourceSystem", "UNKNOWN"))
            .requestedBy(extractString(payload, "requestedBy", "SYSTEM"))
            .urgency(extractString(payload, "urgency", "NORMAL"))
            .timestamp(Instant.now())
            .build();
    }

    private void validateControlRequest(TransactionControlRequest request) {
        if (request.getControlType() == null) {
            throw new ValidationException("Control type is required");
        }
        
        if (request.getOperation() == null) {
            throw new ValidationException("Control operation is required");
        }
        
        if (request.getScope() == null) {
            throw new ValidationException("Control scope is required");
        }
        
        // Validate rule-specific requirements
        if (request.getRuleType() != null) {
            validateRuleRequirements(request);
        }
        
        // Validate thresholds
        if (request.getControlType() == ControlType.VELOCITY_LIMIT || 
            request.getControlType() == ControlType.AMOUNT_LIMIT) {
            if (request.getThreshold() == null || request.getThreshold().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("Valid threshold required for limit controls");
            }
        }
        
        // Validate time windows
        if (request.getControlType() == ControlType.VELOCITY_LIMIT && request.getTimeWindow() == null) {
            throw new ValidationException("Time window required for velocity controls");
        }
        
        // Validate permissions
        validateControlPermissions(request);
        
        // Validate effective dates
        if (request.getEffectiveFrom() != null && request.getEffectiveTo() != null) {
            if (request.getEffectiveTo().isBefore(request.getEffectiveFrom())) {
                throw new ValidationException("Effective end date cannot be before start date");
            }
        }
    }

    private void validateRuleRequirements(TransactionControlRequest request) {
        switch (request.getRuleType()) {
            case VELOCITY_RULE:
                if (request.getThreshold() == null || request.getTimeWindow() == null) {
                    throw new ValidationException("Velocity rules require threshold and time window");
                }
                break;
                
            case AMOUNT_RULE:
                if (request.getThreshold() == null) {
                    throw new ValidationException("Amount rules require threshold value");
                }
                break;
                
            case FREQUENCY_RULE:
                if (request.getTimeWindow() == null) {
                    throw new ValidationException("Frequency rules require time window");
                }
                break;
                
            case CONDITIONAL_RULE:
                if (request.getConditions() == null || request.getConditions().isEmpty()) {
                    throw new ValidationException("Conditional rules require conditions");
                }
                if (request.getActions() == null || request.getActions().isEmpty()) {
                    throw new ValidationException("Conditional rules require actions");
                }
                break;
        }
    }

    private void validateControlPermissions(TransactionControlRequest request) {
        // Check if user/system has permission to create/modify this control
        if (!authorizationService.hasControlPermission(
                request.getRequestedBy(),
                request.getControlType(),
                request.getOperation())) {
            throw new ValidationException("Insufficient permissions for control operation");
        }
        
        // Check for critical control override permissions
        if (CRITICAL_CONTROL_TYPES.contains(request.getControlType().toString()) &&
            !authorizationService.hasCriticalControlPermission(request.getRequestedBy())) {
            throw new ValidationException("Critical control operations require special authorization");
        }
        
        // Check for scope-specific permissions
        if (request.getScope() == ControlScope.GLOBAL &&
            !authorizationService.hasGlobalControlPermission(request.getRequestedBy())) {
            throw new ValidationException("Global controls require elevated permissions");
        }
    }

    private boolean isDuplicateControlRequest(TransactionControlRequest request) {
        return controlRepository.existsByControlIdAndTimestampAfter(
            request.getControlId(),
            Instant.now().minus(2, ChronoUnit.MINUTES)
        );
    }

    private ControlOperationType determineControlOperation(TransactionControlRequest request) {
        if (CRITICAL_CONTROL_TYPES.contains(request.getControlType().toString())) {
            return ControlOperationType.CRITICAL_CONTROL;
        } else if (request.getUrgency().equals("URGENT")) {
            return ControlOperationType.URGENT_CONTROL;
        } else if (request.getOperation() == ControlOperation.DELETE) {
            return ControlOperationType.REMOVE_CONTROL;
        } else {
            return ControlOperationType.STANDARD_CONTROL;
        }
    }

    private ControlProcessingResult processControlRequest(TransactionControlRequest request, 
                                                        ControlOperationType operationType) {
        
        ControlProcessingResult result = new ControlProcessingResult();
        result.setControlId(request.getControlId());
        result.setOperationType(operationType);
        result.setProcessingStartTime(Instant.now());
        
        try {
            switch (operationType) {
                case CRITICAL_CONTROL:
                    result = processCriticalControl(request);
                    break;
                    
                case URGENT_CONTROL:
                    result = processUrgentControl(request);
                    break;
                    
                case STANDARD_CONTROL:
                    result = processStandardControl(request);
                    break;
                    
                case REMOVE_CONTROL:
                    result = processRemoveControl(request);
                    break;
            }
            
            result.setStatus(ProcessingStatus.COMPLETED);
            
        } catch (Exception e) {
            log.error("Failed to process control request: {}", request.getControlId(), e);
            result.setStatus(ProcessingStatus.FAILED);
            result.setErrorMessage(e.getMessage());
            throw new ControlProcessingException("Control processing failed", e);
        }
        
        result.setProcessingEndTime(Instant.now());
        result.setProcessingTimeMs(
            ChronoUnit.MILLIS.between(result.getProcessingStartTime(), result.getProcessingEndTime())
        );
        
        return result;
    }

    private ControlProcessingResult processCriticalControl(TransactionControlRequest request) {
        ControlProcessingResult result = new ControlProcessingResult();
        
        // Create immediate control record with highest priority
        TransactionControl control = createControlRecord(request, ControlStatus.ACTIVE, 1);
        result.setControlRecord(control);
        
        // Apply immediate system-wide enforcement
        switch (request.getControlType()) {
            case EMERGENCY_STOP:
                systemControlService.initiateEmergencyStop(request.getReason());
                result.setAffectedTransactions(systemControlService.getActiveTransactionCount());
                break;
                
            case FRAUD_THRESHOLD:
                fraudControlService.updateCriticalThreshold(
                    request.getThreshold(), request.getTimeWindow());
                result.setAffectedTransactions(fraudControlService.getAffectedTransactionCount());
                break;
                
            case SANCTIONS_CONTROL:
                sanctionsControlService.applyCriticalControl(request);
                result.setAffectedTransactions(sanctionsControlService.getBlockedTransactionCount());
                break;
                
            case REGULATORY_LIMIT:
                regulatoryControlService.enforceImmediateLimit(request);
                result.setAffectedTransactions(regulatoryControlService.getAffectedTransactionCount());
                break;
                
            case SYSTEM_CIRCUIT_BREAKER:
                circuitBreakerService.activateSystemCircuitBreaker(request.getReason());
                result.setAffectedTransactions(circuitBreakerService.getBlockedRequestCount());
                break;
        }
        
        // Create emergency alert
        alertingService.createEmergencyAlert(
            "CRITICAL_TRANSACTION_CONTROL",
            String.format("Critical control applied: %s - %s", 
                request.getControlType(), request.getReason()),
            request.getMetadata()
        );
        
        return result;
    }

    private ControlProcessingResult processUrgentControl(TransactionControlRequest request) {
        ControlProcessingResult result = new ControlProcessingResult();
        
        // Create control record with high priority
        TransactionControl control = createControlRecord(request, ControlStatus.ACTIVE, 10);
        result.setControlRecord(control);
        
        // Apply urgent controls with immediate effect
        int affectedCount = applyTransactionControl(request, true);
        result.setAffectedTransactions(affectedCount);
        
        // Send urgent notifications
        notificationService.sendUrgentControlNotification(request, result);
        
        return result;
    }

    private ControlProcessingResult processStandardControl(TransactionControlRequest request) {
        ControlProcessingResult result = new ControlProcessingResult();
        
        // Create control record with standard priority
        TransactionControl control = createControlRecord(request, request.getStatus(), request.getPriority());
        result.setControlRecord(control);
        
        // Apply controls with standard processing
        int affectedCount = applyTransactionControl(request, false);
        result.setAffectedTransactions(affectedCount);
        
        // Schedule activation if future-dated
        if (request.getEffectiveFrom() != null && request.getEffectiveFrom().isAfter(Instant.now())) {
            scheduleControlActivation(request);
        }
        
        return result;
    }

    private ControlProcessingResult processRemoveControl(TransactionControlRequest request) {
        ControlProcessingResult result = new ControlProcessingResult();
        
        // Find existing control
        TransactionControl existingControl = controlRepository.findByControlId(request.getControlId())
            .orElseThrow(() -> new ValidationException("Control not found: " + request.getControlId()));
        
        // Validate removal permissions
        validateRemovalPermissions(existingControl, request);
        
        // Update control status
        existingControl.setStatus(ControlStatus.REMOVED);
        existingControl.setRemovedAt(Instant.now());
        existingControl.setRemovedBy(request.getRequestedBy());
        existingControl.setRemovalReason(request.getReason());
        
        TransactionControl updatedControl = controlRepository.save(existingControl);
        result.setControlRecord(updatedControl);
        
        // Remove active control enforcement
        int affectedCount = removeTransactionControl(existingControl);
        result.setAffectedTransactions(affectedCount);
        
        return result;
    }

    private TransactionControl createControlRecord(TransactionControlRequest request, 
                                                 ControlStatus status, Integer priority) {
        
        TransactionControl control = TransactionControl.builder()
            .controlId(request.getControlId())
            .controlType(request.getControlType())
            .scope(request.getScope())
            .targetType(request.getTargetType())
            .targetId(request.getTargetId())
            .ruleName(request.getRuleName())
            .ruleType(request.getRuleType())
            .conditions(request.getConditions())
            .actions(request.getActions())
            .threshold(request.getThreshold())
            .currency(request.getCurrency())
            .timeWindow(request.getTimeWindow())
            .timeUnit(request.getTimeUnit())
            .priority(priority != null ? priority : request.getPriority())
            .severity(request.getSeverity())
            .status(status)
            .effectiveFrom(request.getEffectiveFrom() != null ? request.getEffectiveFrom() : Instant.now())
            .effectiveTo(request.getEffectiveTo())
            .reason(request.getReason())
            .category(request.getCategory())
            .tags(request.getTags())
            .sourceSystem(request.getSourceSystem())
            .createdBy(request.getRequestedBy())
            .createdAt(request.getTimestamp())
            .metadata(request.getMetadata())
            .build();
        
        return controlRepository.save(control);
    }

    private int applyTransactionControl(TransactionControlRequest request, boolean urgent) {
        int affectedCount = 0;
        
        switch (request.getControlType()) {
            case VELOCITY_LIMIT:
                affectedCount = velocityService.applyVelocityControl(
                    request.getScope(),
                    request.getTargetType(),
                    request.getTargetId(),
                    request.getThreshold(),
                    request.getTimeWindow(),
                    request.getTimeUnit(),
                    urgent
                );
                break;
                
            case AMOUNT_LIMIT:
                affectedCount = amountControlService.applyAmountLimit(
                    request.getScope(),
                    request.getTargetType(),
                    request.getTargetId(),
                    request.getThreshold(),
                    request.getCurrency(),
                    urgent
                );
                break;
                
            case FREQUENCY_LIMIT:
                affectedCount = frequencyControlService.applyFrequencyLimit(
                    request.getScope(),
                    request.getTargetType(),
                    request.getTargetId(),
                    request.getThreshold().intValue(),
                    request.getTimeWindow(),
                    request.getTimeUnit(),
                    urgent
                );
                break;
                
            case GEOGRAPHIC_RESTRICTION:
                affectedCount = geoControlService.applyGeographicRestriction(
                    request.getScope(),
                    request.getTargetType(),
                    request.getTargetId(),
                    request.getConditions(),
                    urgent
                );
                break;
                
            case TIME_RESTRICTION:
                affectedCount = timeControlService.applyTimeRestriction(
                    request.getScope(),
                    request.getTargetType(),
                    request.getTargetId(),
                    request.getConditions(),
                    urgent
                );
                break;
                
            case RISK_THRESHOLD:
                affectedCount = riskService.applyRiskThreshold(
                    request.getScope(),
                    request.getTargetType(),
                    request.getTargetId(),
                    request.getThreshold().intValue(),
                    request.getActions(),
                    urgent
                );
                break;
                
            case COMPLIANCE_RULE:
                affectedCount = complianceService.applyComplianceControl(
                    request.getScope(),
                    request.getTargetType(),
                    request.getTargetId(),
                    request.getConditions(),
                    request.getActions(),
                    urgent
                );
                break;
                
            case CUSTOM_RULE:
                affectedCount = customRuleService.applyCustomRule(
                    request.getScope(),
                    request.getRuleName(),
                    request.getConditions(),
                    request.getActions(),
                    urgent
                );
                break;
        }
        
        return affectedCount;
    }

    private int removeTransactionControl(TransactionControl control) {
        int affectedCount = 0;
        
        switch (control.getControlType()) {
            case VELOCITY_LIMIT:
                affectedCount = velocityService.removeVelocityControl(
                    control.getScope(),
                    control.getTargetType(),
                    control.getTargetId(),
                    control.getControlId()
                );
                break;
                
            case AMOUNT_LIMIT:
                affectedCount = amountControlService.removeAmountLimit(
                    control.getScope(),
                    control.getTargetType(),
                    control.getTargetId(),
                    control.getControlId()
                );
                break;
                
            case FREQUENCY_LIMIT:
                affectedCount = frequencyControlService.removeFrequencyLimit(
                    control.getScope(),
                    control.getTargetType(),
                    control.getTargetId(),
                    control.getControlId()
                );
                break;
                
            case GEOGRAPHIC_RESTRICTION:
                affectedCount = geoControlService.removeGeographicRestriction(
                    control.getScope(),
                    control.getTargetType(),
                    control.getTargetId(),
                    control.getControlId()
                );
                break;
                
            case TIME_RESTRICTION:
                affectedCount = timeControlService.removeTimeRestriction(
                    control.getScope(),
                    control.getTargetType(),
                    control.getTargetId(),
                    control.getControlId()
                );
                break;
                
            case RISK_THRESHOLD:
                affectedCount = riskService.removeRiskThreshold(
                    control.getScope(),
                    control.getTargetType(),
                    control.getTargetId(),
                    control.getControlId()
                );
                break;
                
            case COMPLIANCE_RULE:
                affectedCount = complianceService.removeComplianceControl(
                    control.getScope(),
                    control.getTargetType(),
                    control.getTargetId(),
                    control.getControlId()
                );
                break;
                
            case CUSTOM_RULE:
                affectedCount = customRuleService.removeCustomRule(
                    control.getScope(),
                    control.getRuleName(),
                    control.getControlId()
                );
                break;
        }
        
        return affectedCount;
    }

    private void validateRemovalPermissions(TransactionControl control, TransactionControlRequest request) {
        // Check if control can be removed
        if (control.getStatus() != ControlStatus.ACTIVE) {
            throw new ValidationException("Control is not active: " + control.getControlId());
        }
        
        // Check removal permissions
        if (!authorizationService.hasControlRemovalPermission(
                request.getRequestedBy(),
                control.getControlType(),
                control.getSeverity())) {
            throw new ValidationException("Insufficient permissions to remove control");
        }
        
        // Check for dependent controls
        if (controlRepository.hasDependentControls(control.getControlId())) {
            throw new ValidationException("Cannot remove control: has dependent controls");
        }
        
        // Check for critical control removal restrictions
        if (CRITICAL_CONTROL_TYPES.contains(control.getControlType().toString()) &&
            !request.hasEmergencyOverride()) {
            throw new ValidationException("Critical controls require emergency override for removal");
        }
    }

    private void applyRealTimeControls(TransactionControlRequest request, ControlProcessingResult result) {
        // Update real-time transaction filtering
        if (request.getOperation() == ControlOperation.CREATE || 
            request.getOperation() == ControlOperation.UPDATE) {
            
            transactionFilterService.updateRealTimeFilters(
                request.getControlType(),
                request.getScope(),
                request.getTargetType(),
                request.getTargetId(),
                request.getConditions(),
                request.getActions()
            );
        } else if (request.getOperation() == ControlOperation.DELETE) {
            transactionFilterService.removeRealTimeFilters(
                request.getControlId(),
                request.getTargetType(),
                request.getTargetId()
            );
        }
        
        // Update payment gateway controls
        paymentGatewayService.updateRealTimeControls(request, result);
        
        // Update fraud detection thresholds
        fraudDetectionService.updateRealTimeThresholds(request, result);
    }

    private void updatePolicyEngine(TransactionControlRequest request, ControlProcessingResult result) {
        PolicyUpdate policyUpdate = PolicyUpdate.builder()
            .controlId(request.getControlId())
            .operation(request.getOperation())
            .controlType(request.getControlType())
            .scope(request.getScope())
            .targetType(request.getTargetType())
            .targetId(request.getTargetId())
            .conditions(request.getConditions())
            .actions(request.getActions())
            .priority(request.getPriority())
            .effectiveFrom(request.getEffectiveFrom())
            .effectiveTo(request.getEffectiveTo())
            .build();
        
        policyService.updatePolicy(policyUpdate);
        
        // Refresh policy caches
        policyService.refreshPolicyCaches(request.getScope(), request.getTargetType());
    }

    private void updateControlCaches(TransactionControlRequest request, ControlProcessingResult result) {
        // Update control caches based on scope
        switch (request.getScope()) {
            case GLOBAL:
                cacheService.updateGlobalControlCache(request.getControlType(), result.getControlRecord());
                break;
                
            case CUSTOMER:
                if (request.getTargetId() != null) {
                    cacheService.updateCustomerControlCache(request.getTargetId(), result.getControlRecord());
                }
                break;
                
            case MERCHANT:
                if (request.getTargetId() != null) {
                    cacheService.updateMerchantControlCache(request.getTargetId(), result.getControlRecord());
                }
                break;
                
            case ACCOUNT:
                if (request.getTargetId() != null) {
                    cacheService.updateAccountControlCache(request.getTargetId(), result.getControlRecord());
                }
                break;
        }
        
        // Invalidate affected transaction caches
        if (result.getAffectedTransactions() > 0) {
            cacheService.invalidateTransactionCaches(
                request.getScope(),
                request.getTargetType(),
                request.getTargetId()
            );
        }
    }

    private void handleCascadingEffects(TransactionControlRequest request, ControlProcessingResult result) {
        CascadeAnalysisRequest cascadeRequest = CascadeAnalysisRequest.builder()
            .controlId(request.getControlId())
            .controlType(request.getControlType())
            .scope(request.getScope())
            .targetType(request.getTargetType())
            .targetId(request.getTargetId())
            .operation(request.getOperation())
            .build();
        
        CascadeAnalysisResult cascadeResult = cascadeAnalysisService.analyzeCascadingEffects(cascadeRequest);
        result.setCascadeAnalysisResult(cascadeResult);
        
        // Apply cascading updates if needed
        if (cascadeResult.hasRequiredUpdates()) {
            for (CascadeUpdate update : cascadeResult.getRequiredUpdates()) {
                cascadeExecutionService.executeCascadeUpdate(update);
            }
        }
        
        // Notify affected systems
        if (cascadeResult.hasAffectedSystems()) {
            for (String systemId : cascadeResult.getAffectedSystems()) {
                systemNotificationService.notifySystemUpdate(systemId, request, result);
            }
        }
    }

    private void scheduleControlActivation(TransactionControlRequest request) {
        ControlSchedule schedule = ControlSchedule.builder()
            .controlId(request.getControlId())
            .activationTime(request.getEffectiveFrom())
            .deactivationTime(request.getEffectiveTo())
            .controlData(request)
            .build();
        
        controlSchedulerService.scheduleControlActivation(schedule);
        
        // Schedule deactivation if end date is specified
        if (request.getEffectiveTo() != null) {
            controlSchedulerService.scheduleControlDeactivation(schedule);
        }
    }

    private void sendControlNotifications(TransactionControlRequest request, ControlProcessingResult result) {
        Map<String, Object> notificationData = Map.of(
            "controlId", request.getControlId(),
            "operation", request.getOperation().toString(),
            "controlType", request.getControlType().toString(),
            "scope", request.getScope().toString(),
            "targetType", request.getTargetType(),
            "targetId", request.getTargetId(),
            "reason", request.getReason(),
            "affectedTransactions", result.getAffectedTransactions(),
            "timestamp", request.getTimestamp()
        );
        
        // Send immediate notifications for critical controls
        if (CRITICAL_CONTROL_TYPES.contains(request.getControlType().toString())) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendCriticalControlAlert(notificationData);
                notificationService.sendExecutiveAlert("CRITICAL_CONTROL", notificationData);
            });
        }
        
        // Notify operations team
        CompletableFuture.runAsync(() -> {
            notificationService.sendOperationsNotification(
                "TRANSACTION_CONTROL_" + request.getOperation(),
                notificationData
            );
        });
        
        // Notify affected parties based on scope
        CompletableFuture.runAsync(() -> {
            switch (request.getScope()) {
                case CUSTOMER:
                    if (request.getTargetId() != null) {
                        notificationService.sendCustomerControlNotification(
                            request.getTargetId(), notificationData);
                    }
                    break;
                    
                case MERCHANT:
                    if (request.getTargetId() != null) {
                        notificationService.sendMerchantControlNotification(
                            request.getTargetId(), notificationData);
                    }
                    break;
                    
                case GLOBAL:
                    notificationService.sendGlobalControlNotification(notificationData);
                    break;
            }
        });
        
        // Send webhook notifications if configured
        if (request.hasWebhookUrl()) {
            CompletableFuture.runAsync(() -> {
                webhookService.sendControlWebhook(request.getWebhookUrl(), notificationData);
            });
        }
    }

    private void updateMonitoringSystems(TransactionControlRequest request, ControlProcessingResult result) {
        // Update real-time monitoring dashboards
        monitoringService.updateControlDashboard(request, result);
        
        // Update risk monitoring systems
        riskMonitoringService.updateControlMetrics(request, result);
        
        // Update compliance monitoring
        complianceMonitoringService.updateControlCompliance(request, result);
        
        // Update operational metrics
        operationalMetricsService.updateControlOperations(request, result);
    }

    private void auditControlOperation(TransactionControlRequest request, ControlProcessingResult result, 
                                     GenericKafkaEvent event) {
        auditService.auditTransactionControl(
            request.getControlId(),
            request.getOperation().toString(),
            request.getControlType().toString(),
            request.getScope().toString(),
            request.getTargetType(),
            request.getTargetId(),
            request.getReason(),
            result.getStatus().toString(),
            result.getAffectedTransactions(),
            request.getRequestedBy(),
            event.getEventId()
        );
    }

    private void recordControlMetrics(TransactionControlRequest request, ControlProcessingResult result, long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        metricsService.recordControlMetrics(
            request.getOperation().toString(),
            request.getControlType().toString(),
            request.getScope().toString(),
            processingTime,
            processingTime <= SLA_THRESHOLD_MS,
            result.getStatus().toString(),
            result.getAffectedTransactions()
        );
        
        // Record control effectiveness metrics
        metricsService.recordControlEffectiveness(
            request.getControlType().toString(),
            request.getScope().toString(),
            result.getAffectedTransactions()
        );
        
        // Record performance impact metrics
        if (result.getCascadeAnalysisResult() != null) {
            metricsService.recordControlPerformanceImpact(
                request.getControlType().toString(),
                result.getCascadeAnalysisResult().getPerformanceImpact()
            );
        }
    }

    // Error handling methods
    private void handleValidationError(GenericKafkaEvent event, ValidationException e) {
        auditService.logValidationError(event.getEventId(), e.getMessage());
        kafkaTemplate.send("transaction-control-validation-errors", event);
    }

    private void handleCriticalControlError(GenericKafkaEvent event, CriticalControlException e) {
        // Create emergency alert for critical control failures
        emergencyAlertService.createEmergencyAlert(
            "CRITICAL_CONTROL_FAILED",
            event.getPayload(),
            e.getMessage()
        );
        
        kafkaTemplate.send("transaction-control-critical-failures", event);
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;
            
            log.warn("Retrying transaction control event {} after {}ms (attempt {})", 
                    eventId, retryDelay, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            scheduledExecutor.schedule(() -> {
                kafkaTemplate.send("transaction-control-retry", event);
            }, retryDelay, TimeUnit.MILLISECONDS);
            
            acknowledgment.acknowledge();
        } else {
            log.error("Max retries exceeded for transaction control event {}, sending to DLQ", eventId);
            sendToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "transaction-control");
        
        kafkaTemplate.send("transaction-control.DLQ", event);
        
        alertingService.createDLQAlert(
            "transaction-control",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handleTransactionControlFailure(GenericKafkaEvent event, String topic, int partition,
                                              long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for transaction control processing: {}", e.getMessage());
        
        failedEventRepository.save(
            FailedEvent.builder()
                .eventId(event.getEventId())
                .topic(topic)
                .payload(event)
                .errorMessage(e.getMessage())
                .createdAt(Instant.now())
                .build()
        );
        
        alertingService.sendCriticalAlert(
            "Transaction Control Circuit Breaker Open",
            "Transaction control processing is failing. Risk management capability compromised."
        );
        
        acknowledgment.acknowledge();
    }

    // Helper extraction methods
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return new BigDecimal(value.toString());
    }

    private Integer extractInteger(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }

    private Long extractLong(Map<String, Object> map, String key, Long defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(value.toString());
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Long) return Instant.ofEpochMilli((Long) value);
        return Instant.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<String> extractList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.stream()
                .map(Object::toString)
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    // Custom exceptions
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static class CriticalControlException extends RuntimeException {
        public CriticalControlException(String message) {
            super(message);
        }
    }

    public static class ControlProcessingException extends RuntimeException {
        public ControlProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
# CRITICAL-4: Exception Handling Strategy

## Scope
**183 generic Exception catch blocks across 54 files** - this is extensive work.

## Prioritization Strategy

### Phase 1: Core Service Files (HIGH PRIORITY)
**Target:** Service layer files - these handle business logic
- DisputeResolutionService.java (7 catches)
- TransactionDisputeService.java (9 catches)
- DisputeManagementService.java (4 catches)
- DisputeAnalysisService.java (8 catches)
- WalletService.java (3 catches - already have custom exceptions)
- TransactionService.java (2 catches - already have custom exceptions)
- FraudDetectionService.java (2 catches - already have custom exceptions)
- DisputeNotificationService.java (12 catches)
- SecureFileUploadService.java (6 catches)

**Estimated:** ~50 catch blocks in core services

### Phase 2: Kafka Consumers (MEDIUM PRIORITY)
**Target:** Event processing - these can use KafkaEventProcessingException
- All *Consumer.java files (~40 files)
- All *ConsumerDlqHandler.java files (~20 files)

**Estimated:** ~110 catch blocks in Kafka consumers

### Phase 3: Configuration & Security (LOW PRIORITY)
**Target:** Infrastructure files
- Config files
- Security interceptors

**Estimated:** ~20 catch blocks

## Custom Exception Hierarchy Created

1. **DisputeServiceException** (base)
2. **DisputeNotFoundException** (404)
3. **DisputeValidationException** (400)
4. **DisputeProcessingException** (500)
5. **ExternalServiceException** (503) - enhanced existing
6. **KafkaEventProcessingException** (500)
7. **DatabaseOperationException** (500)
8. **FileOperationException** (500)
9. **NotificationException** (500)

## Replacement Rules

### Rule 1: External Service Calls
```java
// BEFORE
catch (Exception e) {
    log.error("Failed to call service", e);
    throw new RuntimeException("Service call failed", e);
}

// AFTER
catch (IOException | TimeoutException e) {
    log.error("External service unavailable", e);
    throw new ExternalServiceException("serviceName", "Service call failed", 503, e);
}
```

### Rule 2: Database Operations
```java
// BEFORE
catch (Exception e) {
    log.error("Database error", e);
}

// AFTER
catch (DataAccessException e) {
    log.error("Database operation failed", e);
    throw new DatabaseOperationException("Failed to save dispute", e);
}
```

### Rule 3: Kafka Event Processing
```java
// BEFORE
catch (Exception e) {
    log.error("Event processing failed", e);
}

// AFTER
catch (DisputeProcessingException | DatabaseOperationException e) {
    log.error("Event processing failed", e);
    throw new KafkaEventProcessingException("DISPUTE_OPENED", eventId, "Processing failed", e);
}
```

### Rule 4: Validation Failures
```java
// BEFORE
catch (Exception e) {
    throw new IllegalArgumentException("Validation failed");
}

// AFTER
catch (IllegalArgumentException | NullPointerException e) {
    throw new DisputeValidationException("Invalid dispute data: " + e.getMessage(), e);
}
```

### Rule 5: Notification Failures (Non-Critical)
```java
// BEFORE
catch (Exception e) {
    log.error("Notification failed", e);
    // Don't rethrow - notifications shouldn't block processing
}

// AFTER
catch (RestClientException | KafkaException e) {
    log.warn("Notification failed - will retry later", e);
    // Don't rethrow - notifications shouldn't block processing
}
```

## Implementation Plan

Given the scope (183 catches), we'll implement **Phase 1 only** in this session:
- Focus on core service files (~50 catches)
- These are the most critical for production stability
- Kafka consumers can be handled in a future session (they already have @CircuitBreaker protection)


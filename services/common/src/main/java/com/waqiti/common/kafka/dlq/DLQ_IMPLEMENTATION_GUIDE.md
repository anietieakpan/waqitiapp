# DLQ Handler Implementation Guide

## Overview

This guide explains how to implement Dead Letter Queue (DLQ) handlers using the new `AbstractDlqHandler` base class. This provides enterprise-grade error recovery with automatic retry, exponential backoff, metrics, alerting, and comprehensive auditing.

## Quick Start

### 1. Extend AbstractDlqHandler

```java
@Service
@Slf4j
public class YourEventDlqHandler extends AbstractDlqHandler<YourEvent> {

    @Autowired
    private YourBusinessService businessService;

    @KafkaListener(
        topics = "${kafka.topics.YourEvent.dlq:YourEvent.dlq}",
        groupId = "${kafka.consumer.group-id}-dlq"
    )
    public void listen(ConsumerRecord<String, byte[]> record) {
        handleDlqMessage(record);
    }

    @Override
    protected String getServiceName() {
        return "your-service";
    }

    @Override
    protected String getMessageType() {
        return "YourEvent";
    }

    @Override
    protected Class<YourEvent> getMessageClass() {
        return YourEvent.class;
    }

    @Override
    protected boolean validateMessage(YourEvent event, DlqMessageMetadata metadata) {
        // Validate required fields
        return event != null && event.getId() != null;
    }

    @Override
    protected RecoveryResult attemptRecovery(YourEvent event, DlqMessageMetadata metadata) {
        try {
            // Check idempotency
            if (businessService.alreadyProcessed(event.getId())) {
                return RecoveryResult.success("idempotent-skip");
            }

            // Attempt processing
            boolean success = businessService.process(event);

            if (success) {
                return RecoveryResult.success("processed");
            } else {
                return RecoveryResult.retryableFailure("processing-failed");
            }

        } catch (ServiceUnavailableException e) {
            return RecoveryResult.retryableFailure("service-unavailable");
        } catch (DataIntegrityException e) {
            return RecoveryResult.permanentFailure("data-corrupted");
        }
    }

    @Override
    protected void onPermanentFailure(YourEvent event, DlqMessageMetadata metadata) {
        // Execute compensation logic
        businessService.handleFailure(event);
    }
}
```

### 2. Features You Get Automatically

- ✅ **Automatic Retry** with exponential backoff
- ✅ **Metrics** (received, recovered, failed, retry count)
- ✅ **Alerting** (critical alerts for permanent failures)
- ✅ **Audit Logging** for compliance
- ✅ **Permanent Failure Storage** in Redis for manual review
- ✅ **Failure History** tracking
- ✅ **Circuit Breaker** integration (via Resilience4j if configured)

### 3. Configuration

Add to your `application.yml`:

```yaml
dlq:
  max-retries: 5                      # Maximum retry attempts
  initial-retry-delay-ms: 1000        # Initial retry delay (1 second)
  max-retry-delay-ms: 300000          # Maximum retry delay (5 minutes)
  enable-auto-recovery: true          # Enable automatic recovery
  permanent-failure-retention-days: 30 # How long to keep failed messages
```

## Recovery Strategies by Use Case

### 1. Payment Processing Failure

```java
@Override
protected RecoveryResult attemptRecovery(PaymentEvent event, DlqMessageMetadata metadata) {
    try {
        // Check if payment already processed (idempotency)
        if (paymentService.paymentExists(event.getPaymentId())) {
            return RecoveryResult.success("already-processed");
        }

        // Check if wallet has sufficient balance
        if (!walletService.hasSufficientBalance(event.getWalletId(), event.getAmount())) {
            // User needs to add funds - permanent failure
            return RecoveryResult.permanentFailure("insufficient-balance");
        }

        // Attempt payment
        paymentService.processPayment(event);
        return RecoveryResult.success("payment-processed");

    } catch (WalletServiceUnavailableException e) {
        // Service down - retry
        return RecoveryResult.retryableFailure("wallet-service-down");
    }
}

@Override
protected void onPermanentFailure(PaymentEvent event, DlqMessageMetadata metadata) {
    // Refund or notify user
    paymentService.createRefund(event.getPaymentId(), "DLQ_FAILURE");
    notificationService.notifyPaymentFailed(event.getUserId(), event.getAmount());
}
```

### 2. Notification Failure

```java
@Override
protected RecoveryResult attemptRecovery(NotificationEvent event, DlqMessageMetadata metadata) {
    try {
        // Check if notification already sent
        if (notificationService.wasNotificationSent(event.getNotificationId())) {
            return RecoveryResult.success("already-sent");
        }

        // Try primary channel (push notification)
        try {
            notificationService.sendPushNotification(event);
            return RecoveryResult.success("push-sent");
        } catch (PushNotificationException e) {
            // Try fallback (SMS)
            notificationService.sendSMS(event);
            return RecoveryResult.success("sms-fallback");
        }

    } catch (UserNotFoundException e) {
        // User doesn't exist - permanent failure
        return RecoveryResult.permanentFailure("user-not-found");
    } catch (Exception e) {
        // Unknown error - retry
        return RecoveryResult.retryableFailure("notification-failed");
    }
}
```

### 3. Data Sync Failure

```java
@Override
protected RecoveryResult attemptRecovery(DataSyncEvent event, DlqMessageMetadata metadata) {
    try {
        // Validate data integrity
        if (!validator.validate(event.getData())) {
            return RecoveryResult.permanentFailure("invalid-data");
        }

        // Check if data already synced
        if (syncService.isSynced(event.getSyncId())) {
            return RecoveryResult.success("already-synced");
        }

        // Attempt sync
        syncService.sync(event);
        return RecoveryResult.success("synced");

    } catch (RemoteSystemUnavailableException e) {
        // Remote system down - retry with longer delay
        return RecoveryResult.retryableFailure("remote-system-down");
    } catch (DataConflictException e) {
        // Conflict - needs manual resolution
        return RecoveryResult.permanentFailure("conflict-detected");
    }
}
```

## Advanced Usage

### Custom Retry Logic

```java
@Override
protected boolean isRetryableException(Exception e) {
    // Custom retry logic
    if (e instanceof RateLimitException) {
        return true; // Retry rate limit errors
    }
    if (e instanceof AuthenticationException) {
        return false; // Don't retry auth errors
    }
    return super.isRetryableException(e);
}
```

### Conditional Validation

```java
@Override
protected boolean validateMessage(YourEvent event, DlqMessageMetadata metadata) {
    // Basic validation
    if (event == null || event.getId() == null) {
        return false;
    }

    // Business rule validation
    if (event.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
        log.error("Invalid amount: {}", event.getAmount());
        return false;
    }

    // Time-based validation
    if (event.getTimestamp().isBefore(Instant.now().minus(Duration.ofDays(30)))) {
        log.error("Event too old, cannot process: {}", event.getTimestamp());
        return false;
    }

    return true;
}
```

## Monitoring

### Metrics Available

All metrics are automatically registered with Micrometer:

- `dlq.messages.received{service, type}` - Total messages received in DLQ
- `dlq.messages.recovered{service, type}` - Successfully recovered messages
- `dlq.messages.permanent_failure{service, type}` - Permanently failed messages
- `dlq.messages.retry{service, type}` - Retry attempts
- `dlq.processing.time{service, type}` - Processing time histogram

### Grafana Dashboard Query Examples

```promql
# Recovery rate
rate(dlq_messages_recovered_total[5m]) / rate(dlq_messages_received_total[5m])

# Permanent failure rate
rate(dlq_messages_permanent_failure_total[5m])

# Average retry count before success
increase(dlq_messages_retry_total[1h]) / increase(dlq_messages_recovered_total[1h])
```

### Alerting Rules

```yaml
# Alert when permanent failures exceed threshold
- alert: HighDLQPermanentFailures
  expr: rate(dlq_messages_permanent_failure_total[5m]) > 10
  for: 5m
  labels:
    severity: critical
  annotations:
    summary: "High rate of DLQ permanent failures"
```

## Troubleshooting

### Check Permanent Failures in Redis

```bash
redis-cli KEYS "dlq:permanent:failure:*"
redis-cli HGETALL "dlq:permanent:failure:your-message-key"
```

### Check Retry Count

```bash
redis-cli GET "dlq:retry:count:your-message-key"
```

### Check Failure History

```bash
redis-cli LRANGE "dlq:failure:history:your-message-key" 0 -1
```

## Migration from Old DLQ Handlers

### Before (Old Pattern)

```java
@Service
public class OldDlqHandler extends BaseDlqConsumer<Object> {
    @Override
    protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
        // TODO: Implement custom recovery logic
        return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
    }
}
```

### After (New Pattern)

```java
@Service
public class NewDlqHandler extends AbstractDlqHandler<YourEvent> {
    // Full implementation with automatic retry, metrics, alerting
    @Override
    protected RecoveryResult attemptRecovery(YourEvent event, DlqMessageMetadata metadata) {
        // Business logic here
        return RecoveryResult.success("recovered");
    }
}
```

## Best Practices

1. **Always Check Idempotency** - First thing in `attemptRecovery()`
2. **Validate Early** - Fail fast in `validateMessage()`
3. **Use Specific Exceptions** - Map exceptions to retry/permanent failure
4. **Implement Compensation** - Always implement `onPermanentFailure()`
5. **Log Context** - Include message ID, retry count in all logs
6. **Monitor Metrics** - Set up Grafana dashboards
7. **Test Retry Logic** - Simulate failures in integration tests
8. **Document Recovery Strategy** - Comment your recovery approach

## Complete Example - Payment DLQ Handler

See `ChargebackInitiatedEventConsumerDlqHandlerV2.java` for a complete, production-ready implementation.

## Support

For questions or issues:
- Check logs: `kubectl logs -f <pod> | grep DLQ`
- Check metrics: Grafana → DLQ Dashboard
- Check permanent failures: Redis keys `dlq:permanent:failure:*`
- Escalate: #platform-team Slack channel

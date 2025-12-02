package com.waqiti.user.kafka.dlq.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.user.kafka.dlq.DlqRecoveryContext;
import com.waqiti.user.kafka.dlq.DlqRecoveryResult;
import com.waqiti.user.kafka.dlq.entity.DlqEvent;
import com.waqiti.user.kafka.dlq.repository.DlqEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persistence service for DLQ events
 * Maintains complete audit trail of all failed messages and recovery attempts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DlqPersistenceService {

    private final DlqEventRepository dlqEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Persist DLQ event for audit trail
     *
     * @param context DLQ recovery context
     * @return UUID of persisted record
     */
    @Transactional
    public String persistDlqEvent(DlqRecoveryContext context) {
        try {
            DlqEvent dlqEvent = DlqEvent.builder()
                    .id(UUID.randomUUID())
                    .eventType(context.getEventType())
                    .businessIdentifier(context.getBusinessIdentifier())
                    .severity(context.getSeverity())
                    .recoveryStrategy(context.getRecoveryStrategy())
                    .originalTopic(context.getOriginalTopic())
                    .partition(context.getPartition())
                    .offset(context.getOffset())
                    .consumerGroup(context.getConsumerGroup())
                    .retryAttempts(context.getRetryAttempts())
                    .firstFailureTime(context.getFirstFailureTime())
                    .dlqEntryTime(context.getDlqEntryTime())
                    .originalEvent(serializeToJson(context.getOriginalEvent()))
                    .headers(serializeToJson(context.getHeaders()))
                    .failureReason(context.getFailureException() != null ?
                            context.getFailureException().getMessage() : null)
                    .failureStackTrace(context.getFailureException() != null ?
                            getStackTraceAsString(context.getFailureException()) : null)
                    .metadata(serializeToJson(context.getMetadata()))
                    .processedAt(null) // Not yet processed
                    .recoveryResult(null) // Will be updated later
                    .createdAt(LocalDateTime.now())
                    .build();

            dlqEventRepository.save(dlqEvent);

            log.info("Persisted DLQ event: id={}, type={}, businessId={}",
                    dlqEvent.getId(), dlqEvent.getEventType(), dlqEvent.getBusinessIdentifier());

            return dlqEvent.getId().toString();

        } catch (Exception e) {
            log.error("Failed to persist DLQ event for businessId: {}",
                    context.getBusinessIdentifier(), e);
            throw new RuntimeException("Failed to persist DLQ event", e);
        }
    }

    /**
     * Update recovery result after processing
     *
     * @param dlqRecordId ID of DLQ record
     * @param result Recovery result
     */
    @Transactional
    public void updateRecoveryResult(String dlqRecordId, DlqRecoveryResult result) {
        try {
            UUID id = UUID.fromString(dlqRecordId);

            DlqEvent dlqEvent = dlqEventRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("DLQ event not found: " + dlqRecordId));

            dlqEvent.setProcessedAt(LocalDateTime.now());
            dlqEvent.setRecoveryResult(serializeToJson(result));
            dlqEvent.setRecoveryStatus(result.getStatus().name());
            dlqEvent.setRequiresManualIntervention(result.isRequiresManualIntervention());
            dlqEvent.setTicketNumber(result.getTicketNumber());

            if (result.getException() != null) {
                dlqEvent.setRecoveryErrorMessage(result.getException().getMessage());
            }

            dlqEventRepository.save(dlqEvent);

            log.info("Updated DLQ recovery result: id={}, status={}, requiresManual={}",
                    dlqRecordId, result.getStatus(), result.isRequiresManualIntervention());

        } catch (Exception e) {
            log.error("Failed to update DLQ recovery result for id: {}", dlqRecordId, e);
            // Don't throw - this is not critical enough to fail the recovery
        }
    }

    /**
     * Queue event for batch processing
     *
     * @param context DLQ recovery context
     * @return Batch job ID
     */
    @Transactional
    public String queueForBatchProcessing(DlqRecoveryContext context) {
        String batchJobId = UUID.randomUUID().toString();

        try {
            DlqEvent dlqEvent = DlqEvent.builder()
                    .id(UUID.fromString(batchJobId))
                    .eventType(context.getEventType())
                    .businessIdentifier(context.getBusinessIdentifier())
                    .severity(context.getSeverity())
                    .recoveryStrategy(context.getRecoveryStrategy())
                    .originalEvent(serializeToJson(context.getOriginalEvent()))
                    .headers(serializeToJson(context.getHeaders()))
                    .metadata(serializeToJson(context.getMetadata()))
                    .recoveryStatus("QUEUED_FOR_BATCH")
                    .createdAt(LocalDateTime.now())
                    .build();

            dlqEventRepository.save(dlqEvent);

            log.info("Queued DLQ event for batch processing: batchJobId={}, type={}",
                    batchJobId, context.getEventType());

        } catch (Exception e) {
            log.error("Failed to queue DLQ event for batch processing", e);
        }

        return batchJobId;
    }

    /**
     * Serialize object to JSON string
     */
    private String serializeToJson(Object object) {
        if (object == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            log.warn("Failed to serialize object to JSON: {}", object.getClass().getName(), e);
            return object.toString();
        }
    }

    /**
     * Get stack trace as string
     */
    private String getStackTraceAsString(Exception exception) {
        StringBuilder sb = new StringBuilder();
        sb.append(exception.getClass().getName()).append(": ").append(exception.getMessage()).append("\n");

        for (StackTraceElement element : exception.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }

        if (exception.getCause() != null) {
            sb.append("Caused by: ");
            sb.append(getStackTraceAsString((Exception) exception.getCause()));
        }

        return sb.toString();
    }
}

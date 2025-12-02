package com.waqiti.payment.idempotency;

import com.waqiti.common.locking.DistributedLockService;
import com.waqiti.payment.dto.PaymentResult;
import com.waqiti.payment.dto.TransferResult;
import com.waqiti.payment.entity.IdempotencyRecord;
import com.waqiti.payment.entity.IdempotencyStatus;
import com.waqiti.payment.repository.IdempotencyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Enterprise-grade idempotency service for payment processing
 *
 * CRITICAL COMPLIANCE:
 * - Prevents duplicate payment processing (PCI DSS Requirement 6.5.3)
 * - Ensures exactly-once semantics in distributed system
 * - Provides audit trail for idempotency violations
 * - Implements distributed locking to prevent race conditions
 *
 * FEATURES:
 * - Atomic idempotency record creation with distributed locks
 * - Configurable TTL for idempotency records (default 24 hours)
 * - Support for both payment and transfer operations
 * - Automatic cleanup of expired records
 * - Comprehensive metrics and monitoring
 * - Graceful handling of concurrent duplicate requests
 *
 * USAGE:
 * 1. Check if request is duplicate before processing
 * 2. Create idempotency record at start of processing
 * 3. Update record with final result upon completion
 * 4. Return cached result for duplicate requests
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 * @since 2025-11-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentIdempotencyService {

    private final IdempotencyRepository idempotencyRepository;
    private final DistributedLockService distributedLockService;
    private final ObjectMapper objectMapper;

    private static final int IDEMPOTENCY_TTL_HOURS = 24;
    private static final int LOCK_TIMEOUT_SECONDS = 10;
    private static final String IDEMPOTENCY_LOCK_PREFIX = "payment:idempotency:";

    /**
     * Check if payment request is duplicate and return cached result if available
     *
     * ALGORITHM:
     * 1. Acquire distributed lock on idempotency key
     * 2. Query idempotency record by request ID
     * 3. If exists and not expired:
     *    a. If COMPLETED → return cached result
     *    b. If PROCESSING → throw ConcurrentRequestException
     *    c. If FAILED → allow retry
     * 4. If not exists → return empty (proceed with new request)
     *
     * @param idempotencyKey Unique identifier for request (e.g., transactionId)
     * @param requestType Type of request (PAYMENT, TRANSFER, REFUND)
     * @return Optional containing cached result if duplicate, empty otherwise
     * @throws IdempotencyViolationException if concurrent duplicate detected
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public <T> Optional<T> checkDuplicateAndGetResult(String idempotencyKey, String requestType, Class<T> resultClass) {
        log.debug("Checking idempotency for key: {}, type: {}", idempotencyKey, requestType);

        String lockKey = IDEMPOTENCY_LOCK_PREFIX + idempotencyKey;

        return distributedLockService.executeWithLock(lockKey, LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS, () -> {
            Optional<IdempotencyRecord> existingRecord = idempotencyRepository.findByIdempotencyKey(idempotencyKey);

            if (existingRecord.isEmpty()) {
                log.debug("No existing idempotency record found for key: {}", idempotencyKey);
                return Optional.empty();
            }

            IdempotencyRecord record = existingRecord.get();

            // Check if record is expired
            if (isRecordExpired(record)) {
                log.info("Idempotency record expired for key: {}, deleting", idempotencyKey);
                idempotencyRepository.delete(record);
                return Optional.empty();
            }

            // Handle based on current status
            switch (record.getStatus()) {
                case COMPLETED:
                    log.warn("DUPLICATE REQUEST DETECTED: idempotencyKey={}, originalTimestamp={}, requestType={}",
                            idempotencyKey, record.getCreatedAt(), requestType);

                    // Increment duplicate counter for monitoring
                    record.setDuplicateRequestCount(record.getDuplicateRequestCount() + 1);
                    record.setLastDuplicateRequestAt(LocalDateTime.now());
                    idempotencyRepository.save(record);

                    // Deserialize and return cached result
                    return deserializeResult(record.getResponsePayload(), resultClass);

                case PROCESSING:
                    log.error("CONCURRENT DUPLICATE REQUEST DETECTED: idempotencyKey={}, status=PROCESSING", idempotencyKey);
                    throw new IdempotencyViolationException(
                        "Concurrent request detected for idempotency key: " + idempotencyKey +
                        ". Another request is currently being processed."
                    );

                case FAILED:
                    log.info("Previous request failed for key: {}, allowing retry", idempotencyKey);
                    // Delete failed record to allow retry
                    idempotencyRepository.delete(record);
                    return Optional.empty();

                default:
                    log.error("Unknown idempotency status: {} for key: {}", record.getStatus(), idempotencyKey);
                    return Optional.empty();
            }
        });
    }

    /**
     * Create idempotency record at start of payment processing
     *
     * CRITICAL: Must be called in same transaction as payment creation
     * to ensure atomicity. Uses Propagation.MANDATORY to enforce this.
     *
     * @param idempotencyKey Unique request identifier
     * @param requestType Type of request (PAYMENT, TRANSFER, REFUND)
     * @param requestPayload Original request payload for audit
     * @param userId User initiating request
     * @return Created idempotency record
     * @throws IdempotencyViolationException if record already exists
     */
    @Transactional(propagation = Propagation.MANDATORY, isolation = Isolation.SERIALIZABLE)
    public IdempotencyRecord createIdempotencyRecord(String idempotencyKey, String requestType,
                                                     Object requestPayload, String userId) {
        log.info("Creating idempotency record: key={}, type={}, user={}", idempotencyKey, requestType, userId);

        // Double-check for existing record (defensive programming)
        Optional<IdempotencyRecord> existing = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.error("IDEMPOTENCY VIOLATION: Attempted to create duplicate record for key: {}", idempotencyKey);
            throw new IdempotencyViolationException(
                "Idempotency record already exists for key: " + idempotencyKey
            );
        }

        IdempotencyRecord record = IdempotencyRecord.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(idempotencyKey)
                .requestType(requestType)
                .status(IdempotencyStatus.PROCESSING)
                .requestPayload(serializePayload(requestPayload))
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(IDEMPOTENCY_TTL_HOURS))
                .duplicateRequestCount(0)
                .build();

        IdempotencyRecord saved = idempotencyRepository.save(record);
        log.info("Idempotency record created successfully: id={}, key={}", saved.getId(), idempotencyKey);

        return saved;
    }

    /**
     * Update idempotency record with final processing result
     *
     * CRITICAL: Must be called in same transaction as payment completion
     * to ensure atomicity of payment state and idempotency state.
     *
     * @param idempotencyKey Unique request identifier
     * @param result Payment/transfer result to cache
     * @param success Whether processing succeeded
     * @throws IdempotencyRecordNotFoundException if record doesn't exist
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateIdempotencyResult(String idempotencyKey, Object result, boolean success) {
        log.info("Updating idempotency record: key={}, success={}", idempotencyKey, success);

        IdempotencyRecord record = idempotencyRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IdempotencyRecordNotFoundException(
                    "Idempotency record not found for key: " + idempotencyKey
                ));

        record.setStatus(success ? IdempotencyStatus.COMPLETED : IdempotencyStatus.FAILED);
        record.setResponsePayload(serializePayload(result));
        record.setCompletedAt(LocalDateTime.now());
        record.setProcessingTimeMs(
            Duration.between(record.getCreatedAt(), LocalDateTime.now()).toMillis()
        );

        idempotencyRepository.save(record);
        log.info("Idempotency record updated: key={}, status={}, processingTimeMs={}",
                idempotencyKey, record.getStatus(), record.getProcessingTimeMs());
    }

    /**
     * Mark idempotency record as failed after exception
     *
     * Allows retry of failed requests while preventing duplicate successful requests.
     *
     * @param idempotencyKey Unique request identifier
     * @param errorMessage Error message from failed processing
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(String idempotencyKey, String errorMessage) {
        log.warn("Marking idempotency record as failed: key={}, error={}", idempotencyKey, errorMessage);

        Optional<IdempotencyRecord> recordOpt = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
        if (recordOpt.isEmpty()) {
            log.warn("Cannot mark as failed - record not found: {}", idempotencyKey);
            return;
        }

        IdempotencyRecord record = recordOpt.get();
        record.setStatus(IdempotencyStatus.FAILED);
        record.setErrorMessage(errorMessage);
        record.setCompletedAt(LocalDateTime.now());

        idempotencyRepository.save(record);
        log.info("Idempotency record marked as failed: key={}", idempotencyKey);
    }

    /**
     * Check if idempotency record has expired
     *
     * @param record Idempotency record to check
     * @return true if record is expired and should be deleted
     */
    private boolean isRecordExpired(IdempotencyRecord record) {
        if (record.getExpiresAt() == null) {
            // Legacy records without expiration - use creation time + TTL
            LocalDateTime expirationTime = record.getCreatedAt().plusHours(IDEMPOTENCY_TTL_HOURS);
            return LocalDateTime.now().isAfter(expirationTime);
        }
        return LocalDateTime.now().isAfter(record.getExpiresAt());
    }

    /**
     * Serialize object to JSON string for storage
     *
     * @param payload Object to serialize
     * @return JSON string representation
     */
    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize payload", e);
            return "{\"error\": \"Serialization failed\"}";
        }
    }

    /**
     * Deserialize JSON string to object
     *
     * @param json JSON string
     * @param clazz Target class
     * @return Deserialized object wrapped in Optional
     */
    private <T> Optional<T> deserializeResult(String json, Class<T> clazz) {
        try {
            T result = objectMapper.readValue(json, clazz);
            return Optional.of(result);
        } catch (Exception e) {
            log.error("Failed to deserialize result: json={}, class={}", json, clazz.getName(), e);
            return Optional.empty();
        }
    }

    /**
     * Cleanup expired idempotency records
     *
     * Should be run periodically (e.g., daily) to prevent table bloat.
     * Typically scheduled via @Scheduled cron job.
     *
     * @return Number of records deleted
     */
    @Transactional
    public int cleanupExpiredRecords() {
        log.info("Starting cleanup of expired idempotency records");

        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(IDEMPOTENCY_TTL_HOURS);
        int deletedCount = idempotencyRepository.deleteByExpiresAtBefore(cutoffTime);

        log.info("Cleanup completed: {} expired idempotency records deleted", deletedCount);
        return deletedCount;
    }

    /**
     * Get idempotency statistics for monitoring
     *
     * @return Statistics about idempotency records
     */
    @Transactional(readOnly = true)
    public IdempotencyStatistics getStatistics() {
        long totalRecords = idempotencyRepository.count();
        long processingRecords = idempotencyRepository.countByStatus(IdempotencyStatus.PROCESSING);
        long completedRecords = idempotencyRepository.countByStatus(IdempotencyStatus.COMPLETED);
        long failedRecords = idempotencyRepository.countByStatus(IdempotencyStatus.FAILED);

        return IdempotencyStatistics.builder()
                .totalRecords(totalRecords)
                .processingRecords(processingRecords)
                .completedRecords(completedRecords)
                .failedRecords(failedRecords)
                .timestamp(LocalDateTime.now())
                .build();
    }
}

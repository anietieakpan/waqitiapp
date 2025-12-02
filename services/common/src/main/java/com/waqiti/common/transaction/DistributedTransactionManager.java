package com.waqiti.common.transaction;

import com.waqiti.common.coordination.DistributedCoordinationService;
import com.waqiti.common.locking.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Distributed Transaction Manager implementing 2PC (Two-Phase Commit)
 * with compensation patterns for microservices transactions
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedTransactionManager {

    private final RedisTemplate<String, String> redisTemplate;
    private final DistributedLockService lockService;
    private final DistributedCoordinationService coordinationService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final SecureRandom secureRandom = new SecureRandom();
    
    private static final String TX_PREFIX = "dtx:";
    private static final String TX_LOCK_PREFIX = "dtx:lock:";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);
    
    /**
     * Start a new distributed transaction
     */
    public DistributedTransaction beginTransaction(String transactionId, Duration timeout) {
        String txKey = TX_PREFIX + transactionId;
        
        DistributedTransactionContext context = new DistributedTransactionContext(
            transactionId, timeout != null ? timeout : DEFAULT_TIMEOUT
        );
        
        // Initialize transaction state in Redis
        Map<String, String> txData = Map.of(
            "status", TransactionStatus.ACTIVE.name(),
            "created", String.valueOf(System.currentTimeMillis()),
            "timeout", String.valueOf(context.getTimeout().toMillis()),
            "coordinator", getNodeId()
        );
        
        redisTemplate.opsForHash().putAll(txKey, txData);
        redisTemplate.expire(txKey, context.getTimeout().multipliedBy(2));
        
        log.info("Started distributed transaction: {}", transactionId);
        
        return new DistributedTransaction(this, context);
    }
    
    /**
     * Enlist a participant in the transaction
     */
    public boolean enlistParticipant(String transactionId, TransactionParticipant participant) {
        String txKey = TX_PREFIX + transactionId;
        String participantKey = txKey + ":participants";
        
        try {
            // Check if transaction is still active
            TransactionStatus status = getTransactionStatus(transactionId);
            if (status != TransactionStatus.ACTIVE) {
                log.warn("Cannot enlist participant - transaction {} not active: {}", transactionId, status);
                return false;
            }
            
            String participantId = participant.getParticipantId();
            
            // Store participant info
            Map<String, String> participantData = Map.of(
                "id", participantId,
                "service", participant.getServiceName(),
                "endpoint", participant.getEndpoint(),
                "status", ParticipantStatus.ENLISTED.name()
            );
            
            redisTemplate.opsForHash().put(participantKey, participantId, serializeParticipant(participantData));
            
            log.info("Enlisted participant {} in transaction {}", participantId, transactionId);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to enlist participant in transaction {}", transactionId, e);
            return false;
        }
    }
    
    /**
     * Commit the distributed transaction using 2PC
     */
    public CompletableFuture<TransactionResult> commit(String transactionId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Starting commit for transaction: {}", transactionId);
                
                // Phase 1: Prepare
                boolean prepared = performPreparePhase(transactionId);
                if (!prepared) {
                    return rollback(transactionId).get();
                }
                
                // Phase 2: Commit
                return performCommitPhase(transactionId);
                
            } catch (Exception e) {
                log.error("Error during commit of transaction {}", transactionId, e);
                try {
                    return rollback(transactionId).get();
                } catch (Exception rollbackError) {
                    log.error("Error during rollback of transaction {}", transactionId, rollbackError);
                    return TransactionResult.failure("Commit and rollback both failed");
                }
            }
        });
    }
    
    /**
     * Rollback the distributed transaction
     */
    public CompletableFuture<TransactionResult> rollback(String transactionId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Starting rollback for transaction: {}", transactionId);
                
                setTransactionStatus(transactionId, TransactionStatus.ABORTING);
                
                List<String> participants = getTransactionParticipants(transactionId);
                List<CompletableFuture<Boolean>> rollbackFutures = new ArrayList<>();
                
                // Send rollback to all participants
                for (String participantId : participants) {
                    Map<String, String> participantData = getParticipantData(transactionId, participantId);
                    if (participantData != null) {
                        CompletableFuture<Boolean> future = rollbackParticipant(participantData);
                        rollbackFutures.add(future);
                    }
                }
                
                // Wait for all rollbacks
                boolean allRolledBack = rollbackFutures.stream()
                    .map(CompletableFuture::join)
                    .allMatch(result -> result);
                
                if (allRolledBack) {
                    setTransactionStatus(transactionId, TransactionStatus.ABORTED);
                    log.info("Successfully rolled back transaction: {}", transactionId);
                    return TransactionResult.success("Transaction rolled back successfully");
                } else {
                    setTransactionStatus(transactionId, TransactionStatus.FAILED);
                    log.error("Some participants failed to rollback for transaction: {}", transactionId);
                    return TransactionResult.failure("Partial rollback failure");
                }
                
            } catch (Exception e) {
                log.error("Error during rollback of transaction {}", transactionId, e);
                setTransactionStatus(transactionId, TransactionStatus.FAILED);
                return TransactionResult.failure("Rollback failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * Get transaction status
     */
    public TransactionStatus getTransactionStatus(String transactionId) {
        String txKey = TX_PREFIX + transactionId;
        String statusStr = (String) redisTemplate.opsForHash().get(txKey, "status");
        
        if (statusStr == null) {
            return TransactionStatus.UNKNOWN;
        }
        
        try {
            return TransactionStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            return TransactionStatus.UNKNOWN;
        }
    }
    
    /**
     * Set transaction status
     */
    private void setTransactionStatus(String transactionId, TransactionStatus status) {
        String txKey = TX_PREFIX + transactionId;
        redisTemplate.opsForHash().put(txKey, "status", status.name());
        redisTemplate.opsForHash().put(txKey, "updated", String.valueOf(System.currentTimeMillis()));
    }
    
    /**
     * Phase 1: Prepare phase of 2PC
     */
    private boolean performPreparePhase(String transactionId) {
        log.info("Starting prepare phase for transaction: {}", transactionId);
        
        setTransactionStatus(transactionId, TransactionStatus.PREPARING);
        
        List<String> participants = getTransactionParticipants(transactionId);
        List<CompletableFuture<Boolean>> prepareFutures = new ArrayList<>();
        
        // Send prepare to all participants
        for (String participantId : participants) {
            Map<String, String> participantData = getParticipantData(transactionId, participantId);
            if (participantData != null) {
                CompletableFuture<Boolean> future = prepareParticipant(transactionId, participantData);
                prepareFutures.add(future);
            }
        }
        
        // Wait for all prepare responses with timeout
        try {
            boolean allPrepared = prepareFutures.stream()
                .map(future -> {
                    try {
                        return future.get(30, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        log.error("Prepare phase failed for participant", e);
                        return false;
                    }
                })
                .allMatch(result -> result);
            
            if (allPrepared) {
                setTransactionStatus(transactionId, TransactionStatus.PREPARED);
                log.info("All participants prepared for transaction: {}", transactionId);
                return true;
            } else {
                setTransactionStatus(transactionId, TransactionStatus.PREPARE_FAILED);
                log.warn("Not all participants could prepare for transaction: {}", transactionId);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error in prepare phase for transaction {}", transactionId, e);
            setTransactionStatus(transactionId, TransactionStatus.PREPARE_FAILED);
            return false;
        }
    }
    
    /**
     * Phase 2: Commit phase of 2PC
     */
    private TransactionResult performCommitPhase(String transactionId) {
        log.info("Starting commit phase for transaction: {}", transactionId);
        
        setTransactionStatus(transactionId, TransactionStatus.COMMITTING);
        
        List<String> participants = getTransactionParticipants(transactionId);
        List<CompletableFuture<Boolean>> commitFutures = new ArrayList<>();
        
        // Send commit to all participants
        for (String participantId : participants) {
            Map<String, String> participantData = getParticipantData(transactionId, participantId);
            if (participantData != null) {
                CompletableFuture<Boolean> future = commitParticipant(participantData);
                commitFutures.add(future);
            }
        }
        
        // Wait for all commits
        boolean allCommitted = commitFutures.stream()
            .map(CompletableFuture::join)
            .allMatch(result -> result);
        
        if (allCommitted) {
            setTransactionStatus(transactionId, TransactionStatus.COMMITTED);
            cleanupTransaction(transactionId);
            log.info("Successfully committed transaction: {}", transactionId);
            return TransactionResult.success("Transaction committed successfully");
        } else {
            setTransactionStatus(transactionId, TransactionStatus.COMMIT_FAILED);
            log.error("Some participants failed to commit for transaction: {}", transactionId);
            return TransactionResult.failure("Partial commit failure");
        }
    }
    
    /**
     * Send prepare request to participant
     */
    private CompletableFuture<Boolean> prepareParticipant(String transactionId, Map<String, String> participantData) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String participantId = participantData.get("id");
                String service = participantData.get("service");
                
                // Use service mesh or direct HTTP call to prepare participant
                // For now, simulate the call
                boolean prepared = simulateParticipantCall(service, "prepare", transactionId, participantId);
                
                if (prepared) {
                    updateParticipantStatus(transactionId, participantId, ParticipantStatus.PREPARED);
                    log.debug("Participant {} prepared for transaction {}", participantId, transactionId);
                } else {
                    updateParticipantStatus(transactionId, participantId, ParticipantStatus.PREPARE_FAILED);
                    log.warn("Participant {} failed to prepare for transaction {}", participantId, transactionId);
                }
                
                return prepared;
                
            } catch (Exception e) {
                log.error("Error preparing participant", e);
                return false;
            }
        });
    }
    
    /**
     * Send commit request to participant
     */
    private CompletableFuture<Boolean> commitParticipant(Map<String, String> participantData) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String participantId = participantData.get("id");
                String service = participantData.get("service");
                
                // Use service mesh or direct HTTP call to commit participant
                boolean committed = simulateParticipantCall(service, "commit", null, participantId);
                
                if (committed) {
                    log.debug("Participant {} committed", participantId);
                } else {
                    log.error("Participant {} failed to commit", participantId);
                }
                
                return committed;
                
            } catch (Exception e) {
                log.error("Error committing participant", e);
                return false;
            }
        });
    }
    
    /**
     * Send rollback request to participant
     */
    private CompletableFuture<Boolean> rollbackParticipant(Map<String, String> participantData) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String participantId = participantData.get("id");
                String service = participantData.get("service");
                
                // Use service mesh or direct HTTP call to rollback participant
                boolean rolledBack = simulateParticipantCall(service, "rollback", null, participantId);
                
                if (rolledBack) {
                    log.debug("Participant {} rolled back", participantId);
                } else {
                    log.error("Participant {} failed to rollback", participantId);
                }
                
                return rolledBack;
                
            } catch (Exception e) {
                log.error("Error rolling back participant", e);
                return false;
            }
        });
    }
    
    /**
     * Get list of transaction participants
     */
    private List<String> getTransactionParticipants(String transactionId) {
        String participantKey = TX_PREFIX + transactionId + ":participants";
        Map<Object, Object> participants = redisTemplate.opsForHash().entries(participantKey);
        return new ArrayList<>(participants.keySet().stream().map(Object::toString).toList());
    }
    
    /**
     * Get participant data
     */
    private Map<String, String> getParticipantData(String transactionId, String participantId) {
        String participantKey = TX_PREFIX + transactionId + ":participants";
        String data = (String) redisTemplate.opsForHash().get(participantKey, participantId);
        
        if (data != null) {
            return deserializeParticipant(data);
        }
        
        log.error("CRITICAL: Participant data not found - transactionId: {}, participantId: {} - This can cause transaction rollback failures", transactionId, participantId);
        throw new IllegalStateException("Participant data missing for transaction: " + transactionId + ", participant: " + participantId + ". Cannot coordinate distributed transaction.");
    }
    
    /**
     * Update participant status
     */
    private void updateParticipantStatus(String transactionId, String participantId, ParticipantStatus status) {
        String participantKey = TX_PREFIX + transactionId + ":participants";
        String data = (String) redisTemplate.opsForHash().get(participantKey, participantId);
        
        if (data != null) {
            Map<String, String> participantData = deserializeParticipant(data);
            participantData.put("status", status.name());
            redisTemplate.opsForHash().put(participantKey, participantId, serializeParticipant(participantData));
        }
    }
    
    /**
     * Cleanup completed transaction
     */
    private void cleanupTransaction(String transactionId) {
        scheduler.schedule(() -> {
            String txKey = TX_PREFIX + transactionId;
            String participantKey = txKey + ":participants";
            
            redisTemplate.delete(txKey);
            redisTemplate.delete(participantKey);
            
            log.debug("Cleaned up transaction: {}", transactionId);
        }, 1, TimeUnit.HOURS);
    }
    
    /**
     * Simulate participant call - replace with actual HTTP/RPC call
     */
    private boolean simulateParticipantCall(String service, String operation, String transactionId, String participantId) {
        // In production, this would make actual HTTP calls to the service
        // using RestTemplate, WebClient, or service mesh
        
        log.debug("Simulating {} call to service {} for participant {}", operation, service, participantId);
        
        // Simulate success/failure based on some criteria
        return secureRandom.nextDouble() > 0.1; // 90% success rate for simulation
    }
    
    // Utility methods
    
    private String getNodeId() {
        // In production, get from configuration or environment
        return "coordinator-" + System.getProperty("spring.application.name", "unknown");
    }
    
    private String serializeParticipant(Map<String, String> data) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }
    
    private Map<String, String> deserializeParticipant(String data) {
        Map<String, String> result = new HashMap<>();
        if (data != null && !data.isEmpty()) {
            String[] pairs = data.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    result.put(kv[0], kv[1]);
                }
            }
        }
        return result;
    }
    
    // Shutdown cleanup
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
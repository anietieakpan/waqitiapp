package com.waqiti.common.transaction;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Distributed Transaction handle for managing 2PC transactions
 */
@Slf4j
public class DistributedTransaction implements AutoCloseable {
    
    private final DistributedTransactionManager manager;
    private final DistributedTransactionContext context;
    private final List<TransactionParticipant> participants;
    private final AtomicBoolean committed = new AtomicBoolean(false);
    private final AtomicBoolean rolledBack = new AtomicBoolean(false);
    private final Instant createdAt;
    
    public DistributedTransaction(DistributedTransactionManager manager, DistributedTransactionContext context) {
        this.manager = manager;
        this.context = context;
        this.participants = new ArrayList<>();
        this.createdAt = Instant.now();
    }
    
    /**
     * Enlist a participant in this transaction
     */
    public boolean enlist(TransactionParticipant participant) {
        if (committed.get() || rolledBack.get()) {
            log.warn("Cannot enlist participant - transaction {} already completed", context.getTransactionId());
            return false;
        }
        
        if (isExpired()) {
            log.warn("Cannot enlist participant - transaction {} has expired", context.getTransactionId());
            return false;
        }
        
        boolean enlisted = manager.enlistParticipant(context.getTransactionId(), participant);
        if (enlisted) {
            participants.add(participant);
            log.info("Enlisted participant {} in transaction {}", participant.getParticipantId(), context.getTransactionId());
        }
        
        return enlisted;
    }
    
    /**
     * Commit the transaction
     */
    public CompletableFuture<TransactionResult> commit() {
        if (rolledBack.get()) {
            return CompletableFuture.completedFuture(
                TransactionResult.failure("Transaction was already rolled back")
            );
        }
        
        if (committed.compareAndSet(false, true)) {
            log.info("Committing transaction: {}", context.getTransactionId());
            return manager.commit(context.getTransactionId());
        } else {
            return CompletableFuture.completedFuture(
                TransactionResult.failure("Transaction was already committed")
            );
        }
    }
    
    /**
     * Rollback the transaction
     */
    public CompletableFuture<TransactionResult> rollback() {
        if (committed.get()) {
            return CompletableFuture.completedFuture(
                TransactionResult.failure("Transaction was already committed")
            );
        }
        
        if (rolledBack.compareAndSet(false, true)) {
            log.info("Rolling back transaction: {}", context.getTransactionId());
            return manager.rollback(context.getTransactionId());
        } else {
            return CompletableFuture.completedFuture(
                TransactionResult.success("Transaction was already rolled back")
            );
        }
    }
    
    /**
     * Get transaction status
     */
    public TransactionStatus getStatus() {
        return manager.getTransactionStatus(context.getTransactionId());
    }
    
    /**
     * Check if transaction has expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(createdAt.plus(context.getTimeout()));
    }
    
    /**
     * Get transaction ID
     */
    public String getTransactionId() {
        return context.getTransactionId();
    }
    
    /**
     * Get timeout duration
     */
    public Duration getTimeout() {
        return context.getTimeout();
    }
    
    /**
     * Get remaining time before timeout
     */
    public Duration getRemainingTime() {
        Duration elapsed = Duration.between(createdAt, Instant.now());
        return context.getTimeout().minus(elapsed);
    }
    
    /**
     * Get list of enlisted participants
     */
    public List<TransactionParticipant> getParticipants() {
        return new ArrayList<>(participants);
    }
    
    /**
     * Check if transaction is active
     */
    public boolean isActive() {
        return !committed.get() && !rolledBack.get() && !isExpired();
    }
    
    /**
     * Add a compensation handler for saga pattern
     */
    public DistributedTransaction addCompensation(String participantId, CompensationHandler handler) {
        context.addCompensationHandler(participantId, handler);
        return this;
    }
    
    /**
     * Execute compensations (for saga pattern)
     */
    public CompletableFuture<Boolean> executeCompensations() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Executing compensations for transaction: {}", context.getTransactionId());
                
                // Execute compensations in reverse order
                List<String> participantIds = new ArrayList<>(context.getCompensationHandlers().keySet());
                participantIds.sort((a, b) -> b.compareTo(a)); // Reverse order
                
                boolean allCompensated = true;
                
                for (String participantId : participantIds) {
                    CompensationHandler handler = context.getCompensationHandlers().get(participantId);
                    if (handler != null) {
                        try {
                            boolean compensated = handler.compensate().get(30, TimeUnit.SECONDS);
                            if (!compensated) {
                                log.error("Compensation failed for participant: {}", participantId);
                                allCompensated = false;
                            } else {
                                log.debug("Successfully compensated participant: {}", participantId);
                            }
                        } catch (Exception e) {
                            log.error("Error executing compensation for participant: {}", participantId, e);
                            allCompensated = false;
                        }
                    }
                }
                
                if (allCompensated) {
                    log.info("All compensations executed successfully for transaction: {}", context.getTransactionId());
                } else {
                    log.error("Some compensations failed for transaction: {}", context.getTransactionId());
                }
                
                return allCompensated;
                
            } catch (Exception e) {
                log.error("Error executing compensations for transaction: {}", context.getTransactionId(), e);
                return false;
            }
        });
    }
    
    /**
     * Auto-close will rollback if not committed
     */
    @Override
    public void close() {
        if (!committed.get() && !rolledBack.get()) {
            log.warn("Transaction {} not explicitly committed or rolled back - auto-rollback", context.getTransactionId());
            try {
                rollback().get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Error during auto-rollback of transaction: {}", context.getTransactionId(), e);
            }
        }
    }
    
    @Override
    public String toString() {
        return String.format("DistributedTransaction{id='%s', status=%s, participants=%d, expired=%s}",
            context.getTransactionId(),
            getStatus(),
            participants.size(),
            isExpired()
        );
    }
}
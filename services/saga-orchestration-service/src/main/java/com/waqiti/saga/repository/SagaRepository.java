package com.waqiti.saga.repository;

import com.waqiti.saga.domain.Saga;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Saga persistence operations
 */
public interface SagaRepository {
    
    /**
     * Save a new saga or update existing one
     */
    Saga save(Saga saga);
    
    /**
     * Update an existing saga
     */
    Saga update(Saga saga);
    
    /**
     * Find saga by ID
     */
    Optional<Saga> findById(String sagaId);
    
    /**
     * Find all sagas by status
     */
    List<Saga> findByStatus(String status);
    
    /**
     * Find expired sagas
     */
    List<Saga> findExpiredSagas();
    
    /**
     * Delete a saga
     */
    void delete(String sagaId);
    
    /**
     * Check if saga exists
     */
    boolean exists(String sagaId);
    
    /**
     * Find sagas requiring recovery
     */
    List<Saga> findSagasForRecovery();
    
    /**
     * Create checkpoint for saga state
     */
    void createCheckpoint(Saga saga);
    
    /**
     * Recover saga from checkpoint
     */
    Optional<Saga> recoverFromCheckpoint(String sagaId);
    
    /**
     * Verify saga state integrity
     */
    boolean verifyIntegrity(String sagaId);
    
    /**
     * Acquire distributed lock for saga
     */
    boolean acquireLock(String sagaId, long timeoutSeconds);
    
    /**
     * Release distributed lock
     */
    void releaseLock(String sagaId);
}
package com.waqiti.common.domain.entities;

import com.waqiti.common.event.DomainEvent;
import jakarta.persistence.Transient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AfterDomainEventPublication;
import org.springframework.data.domain.DomainEvents;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Base Aggregate Root
 * Abstract base class for all aggregate roots in the domain model
 * Provides domain event handling capabilities
 */
@Slf4j
public abstract class BaseAggregateRoot<ID extends Serializable> extends BaseEntity<ID> {
    
    @Transient
    private final List<DomainEvent> domainEvents = new ArrayList<>();
    
    /**
     * Register a domain event
     * The event will be published when the aggregate is saved
     */
    protected void registerEvent(DomainEvent event) {
        log.debug("Registering domain event: {} for aggregate: {}", 
                event.getClass().getSimpleName(), this.getClass().getSimpleName());
        domainEvents.add(event);
    }
    
    /**
     * Clear all domain events after publication
     * Called automatically by Spring Data after events are published
     */
    @AfterDomainEventPublication
    protected void clearDomainEvents() {
        log.debug("Clearing {} domain events for aggregate: {}", 
                domainEvents.size(), this.getClass().getSimpleName());
        domainEvents.clear();
    }
    
    /**
     * Get all pending domain events
     * Called automatically by Spring Data to publish events
     */
    @DomainEvents
    protected Collection<DomainEvent> domainEvents() {
        log.debug("Publishing {} domain events for aggregate: {}", 
                domainEvents.size(), this.getClass().getSimpleName());
        return Collections.unmodifiableList(domainEvents);
    }
    
    /**
     * Get count of pending domain events
     */
    @Transient
    public int getPendingEventCount() {
        return domainEvents.size();
    }
    
    /**
     * Check if there are pending domain events
     */
    @Transient
    public boolean hasPendingEvents() {
        return !domainEvents.isEmpty();
    }
    
    /**
     * Apply business rules validation
     * Subclasses should override to implement aggregate-specific validation
     */
    public abstract void validateBusinessRules();
    
    /**
     * Check aggregate invariants
     * Called to ensure aggregate is in a valid state
     */
    protected abstract void checkInvariants();
    
    /**
     * Handle state transition
     * Validates that the transition from current state to new state is valid
     */
    protected void handleStateTransition(String fromState, String toState) {
        log.debug("State transition for {}: {} -> {}", 
                this.getClass().getSimpleName(), fromState, toState);
        
        if (!isValidStateTransition(fromState, toState)) {
            throw new IllegalStateException(
                    String.format("Invalid state transition from %s to %s", fromState, toState));
        }
    }
    
    /**
     * Check if state transition is valid
     * Subclasses should override to implement state transition rules
     */
    protected boolean isValidStateTransition(String fromState, String toState) {
        // Default implementation - subclasses should override
        return true;
    }
    
    /**
     * Validate aggregate before save
     * Calls both business rules validation and invariant checking
     */
    @Override
    public void validate() {
        super.validate();
        validateBusinessRules();
        checkInvariants();
    }
    
    /**
     * Create a snapshot of the aggregate state
     * Useful for event sourcing and auditing
     */
    public abstract AggregateSnapshot createSnapshot();
    
    /**
     * Restore aggregate state from snapshot
     * Useful for event sourcing and recovery
     */
    public abstract void restoreFromSnapshot(AggregateSnapshot snapshot);
    
    /**
     * Base class for aggregate snapshots
     */
    public abstract static class AggregateSnapshot {
        private final String aggregateId;
        private final Long version;
        private final java.time.Instant snapshotTimestamp;
        
        protected AggregateSnapshot(String aggregateId, Long version) {
            this.aggregateId = aggregateId;
            this.version = version;
            this.snapshotTimestamp = java.time.Instant.now();
        }
        
        public String getAggregateId() {
            return aggregateId;
        }
        
        public Long getVersion() {
            return version;
        }
        
        public java.time.Instant getSnapshotTimestamp() {
            return snapshotTimestamp;
        }
    }
}
package com.waqiti.transaction.domain;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Transaction Event Entity
 * 
 * Represents an event in the transaction lifecycle.
 * This provides an audit trail of all state changes and actions
 * performed on a transaction.
 */
@Entity
@Table(name = "transaction_events", indexes = {
    @Index(name = "idx_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_event_type", columnList = "eventType"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
@EntityListeners(AuditingEntityListener.class)
public class TransactionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionEventType eventType;

    @Column(length = 500)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column
    private String performedBy;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Constructors
    public TransactionEvent() {
    }

    public TransactionEvent(Transaction transaction, TransactionEventType eventType, 
                          String description, String details) {
        this.transaction = transaction;
        this.eventType = eventType;
        this.description = description;
        this.details = details;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public TransactionEventType getEventType() {
        return eventType;
    }

    public void setEventType(TransactionEventType eventType) {
        this.eventType = eventType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(String performedBy) {
        this.performedBy = performedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransactionEvent that = (TransactionEvent) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TransactionEvent{" +
               "id='" + id + '\'' +
               ", eventType=" + eventType +
               ", description='" + description + '\'' +
               ", createdAt=" + createdAt +
               '}';
    }
}
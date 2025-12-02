package com.waqiti.customer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Customer Note Entity
 *
 * Represents notes and comments associated with a customer for internal
 * tracking, audit trail, and communication purposes.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Entity
@Table(name = "customer_note", indexes = {
    @Index(name = "idx_customer_note_customer", columnList = "customer_id"),
    @Index(name = "idx_customer_note_type", columnList = "note_type"),
    @Index(name = "idx_customer_note_alert", columnList = "is_alert")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "customer")
@EqualsAndHashCode(of = "noteId")
public class CustomerNote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "note_id", unique = true, nullable = false, length = 100)
    private String noteId;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "customer_id", insertable = false, updatable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false, length = 50)
    private NoteType noteType;

    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "note", nullable = false, columnDefinition = "TEXT")
    private String note;

    @Column(name = "is_internal")
    @Builder.Default
    private Boolean isInternal = true;

    @Column(name = "is_alert")
    @Builder.Default
    private Boolean isAlert = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20)
    @Builder.Default
    private Priority priority = Priority.NORMAL;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Enums
    public enum NoteType {
        GENERAL,
        CALL,
        MEETING,
        EMAIL,
        COMPLAINT,
        FEEDBACK,
        FOLLOW_UP,
        KYC,
        AML,
        COMPLIANCE,
        RISK,
        CREDIT,
        COLLECTION,
        SUPPORT,
        SALES,
        MARKETING,
        SYSTEM,
        ALERT,
        WARNING,
        OTHER
    }

    public enum Priority {
        LOW,
        NORMAL,
        HIGH,
        URGENT,
        CRITICAL
    }

    /**
     * Check if note is internal
     *
     * @return true if internal
     */
    public boolean isInternal() {
        return isInternal != null && isInternal;
    }

    /**
     * Check if note is an alert
     *
     * @return true if alert
     */
    public boolean isAlert() {
        return isAlert != null && isAlert;
    }

    /**
     * Check if note has high priority
     *
     * @return true if priority is HIGH, URGENT, or CRITICAL
     */
    public boolean isHighPriority() {
        return priority == Priority.HIGH ||
               priority == Priority.URGENT ||
               priority == Priority.CRITICAL;
    }

    /**
     * Check if note is critical
     *
     * @return true if priority is CRITICAL
     */
    public boolean isCritical() {
        return priority == Priority.CRITICAL;
    }

    /**
     * Set as alert
     */
    public void markAsAlert() {
        this.isAlert = true;
    }

    /**
     * Remove alert status
     */
    public void clearAlert() {
        this.isAlert = false;
    }

    /**
     * Set priority
     *
     * @param priority the priority level
     */
    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    /**
     * Escalate priority
     */
    public void escalatePriority() {
        switch (this.priority) {
            case LOW:
                this.priority = Priority.NORMAL;
                break;
            case NORMAL:
                this.priority = Priority.HIGH;
                break;
            case HIGH:
                this.priority = Priority.URGENT;
                break;
            case URGENT:
                this.priority = Priority.CRITICAL;
                break;
            case CRITICAL:
                // Already at highest priority
                break;
        }
    }

    /**
     * Get note preview (first 100 characters)
     *
     * @return note preview
     */
    public String getPreview() {
        if (note == null) {
            return null;
        }
        if (note.length() <= 100) {
            return note;
        }
        return note.substring(0, 97) + "...";
    }
}

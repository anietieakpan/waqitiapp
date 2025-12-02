package com.waqiti.support.domain;

import com.waqiti.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.ToString;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "support_tickets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"messages", "attachments"})
public class Ticket extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false, unique = true)
    private String ticketNumber;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private String userEmail;
    
    private String userName;
    
    @Column(nullable = false, length = 200)
    private String subject;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketPriority priority;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketCategory category;
    
    @Enumerated(EnumType.STRING)
    private TicketSubCategory subCategory;
    
    private String assignedToAgentId;
    
    private String assignedToAgentName;
    
    private LocalDateTime assignedAt;
    
    private LocalDateTime resolvedAt;
    
    private LocalDateTime closedAt;
    
    private LocalDateTime firstResponseAt;
    
    @Column(name = "sla_breach_at")
    private LocalDateTime slaBreachAt;
    
    @Column(name = "is_escalated")
    private boolean isEscalated;
    
    private LocalDateTime escalatedAt;
    
    private String escalationReason;
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "ticket_tags", joinColumns = @JoinColumn(name = "ticket_id"))
    @Column(name = "tag")
    private Set<String> tags = new HashSet<>();
    
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<TicketMessage> messages = new ArrayList<>();
    
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TicketAttachment> attachments = new ArrayList<>();
    
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC")
    private List<TicketActivity> activities = new ArrayList<>();
    
    @OneToOne(mappedBy = "ticket", cascade = CascadeType.ALL)
    private TicketFeedback feedback;
    
    @ManyToOne
    @JoinColumn(name = "parent_ticket_id")
    private Ticket parentTicket;
    
    @OneToMany(mappedBy = "parentTicket")
    private List<Ticket> linkedTickets = new ArrayList<>();
    
    private String relatedTransactionId;
    
    private String relatedPaymentId;
    
    private String relatedUserId;
    
    @Column(name = "satisfaction_score")
    private Integer satisfactionScore;
    
    @Column(name = "resolution_time_minutes")
    private Integer resolutionTimeMinutes;
    
    @Column(name = "response_count")
    private Integer responseCount = 0;
    
    @Column(name = "reopened_count")
    private Integer reopenedCount = 0;
    
    @Column(name = "is_spam")
    private boolean isSpam;
    
    @Column(name = "is_vip")
    private boolean isVip;
    
    @Column(name = "channel")
    @Enumerated(EnumType.STRING)
    private SupportChannel channel;
    
    @Column(name = "language_code")
    private String languageCode = "en";
    
    @Column(name = "sentiment_score")
    private Double sentimentScore;

    // Soft delete support for GDPR compliance
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private String deletedBy;

    @Column(name = "deletion_reason", length = 100)
    private String deletionReason;

    @Column(name = "retention_until")
    private LocalDateTime retentionUntil;

    @PrePersist
    public void prePersist() {
        if (this.ticketNumber == null) {
            this.ticketNumber = generateTicketNumber();
        }
        if (this.status == null) {
            this.status = TicketStatus.NEW;
        }
        if (this.priority == null) {
            this.priority = TicketPriority.MEDIUM;
        }
        if (this.channel == null) {
            this.channel = SupportChannel.WEB;
        }
        calculateSlaBreachTime();
    }
    
    private String generateTicketNumber() {
        return "TKT-" + System.currentTimeMillis();
    }
    
    private void calculateSlaBreachTime() {
        LocalDateTime now = LocalDateTime.now();
        switch (this.priority) {
            case CRITICAL:
                this.slaBreachAt = now.plusHours(1);
                break;
            case HIGH:
                this.slaBreachAt = now.plusHours(4);
                break;
            case MEDIUM:
                this.slaBreachAt = now.plusHours(24);
                break;
            case LOW:
                this.slaBreachAt = now.plusHours(48);
                break;
        }
    }
    
    public void addMessage(TicketMessage message) {
        messages.add(message);
        message.setTicket(this);
        this.responseCount++;
        if (this.firstResponseAt == null && message.isAgentMessage()) {
            this.firstResponseAt = LocalDateTime.now();
        }
    }
    
    public void addActivity(TicketActivity activity) {
        activities.add(activity);
        activity.setTicket(this);
    }
    
    public void escalate(String reason) {
        this.isEscalated = true;
        this.escalatedAt = LocalDateTime.now();
        this.escalationReason = reason;
        this.priority = TicketPriority.HIGH;
    }
    
    public void resolve() {
        this.status = TicketStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
        if (this.getCreatedAt() != null) {
            this.resolutionTimeMinutes = (int) java.time.Duration.between(
                this.getCreatedAt(), this.resolvedAt).toMinutes();
        }
    }
    
    public void close() {
        this.status = TicketStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
    }
    
    public void reopen() {
        this.status = TicketStatus.OPEN;
        this.resolvedAt = null;
        this.closedAt = null;
        this.reopenedCount++;
    }
    
    public boolean isSlaBreached() {
        return LocalDateTime.now().isAfter(this.slaBreachAt);
    }

    /**
     * Soft delete this ticket (GDPR compliance).
     * Sets deletedAt timestamp and schedules permanent deletion.
     *
     * @param deletedByUserId User ID performing the deletion
     * @param reason Reason for deletion (GDPR_REQUEST, USER_REQUEST, etc.)
     * @param retentionDays Number of days before permanent deletion (default 90)
     */
    public void softDelete(String deletedByUserId, String reason, int retentionDays) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedByUserId;
        this.deletionReason = reason;
        this.retentionUntil = this.deletedAt.plusDays(retentionDays);
    }

    /**
     * Restore a soft-deleted ticket.
     */
    public void restore() {
        this.deletedAt = null;
        this.deletedBy = null;
        this.deletionReason = null;
        this.retentionUntil = null;
    }

    /**
     * Check if ticket is soft-deleted.
     */
    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    /**
     * Check if ticket is eligible for permanent deletion.
     */
    public boolean isEligibleForPermanentDeletion() {
        return this.retentionUntil != null && LocalDateTime.now().isAfter(this.retentionUntil);
    }
}


package com.waqiti.support.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "support_tickets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true, nullable = false)
    private String ticketNumber;

    @Column(nullable = false)
    private String userId;

    private String assignedAgentId;

    @Column(nullable = false)
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
    private TicketChannel channel;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<TicketMessage> messages = new ArrayList<>();

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TicketAttachment> attachments = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "ticket_tags")
    private List<String> tags = new ArrayList<>();

    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
    private LocalDateTime firstResponseAt;

    private Integer satisfactionRating;
    private String satisfactionComment;

    @Column(name = "sla_breach_at")
    private LocalDateTime slaBreachAt;

    private boolean isEscalated;
    private String escalationReason;

    @ElementCollection
    @CollectionTable(name = "ticket_metadata")
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata = new HashMap<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public enum TicketStatus {
        NEW,
        OPEN,
        IN_PROGRESS,
        PENDING_CUSTOMER,
        PENDING_INTERNAL,
        RESOLVED,
        CLOSED,
        REOPENED
    }

    public enum TicketPriority {
        LOW,
        MEDIUM,
        HIGH,
        URGENT,
        CRITICAL
    }

    public enum TicketCategory {
        ACCOUNT,
        PAYMENT,
        SECURITY,
        TECHNICAL,
        BILLING,
        COMPLIANCE,
        FEATURE_REQUEST,
        BUG_REPORT,
        OTHER
    }

    public enum TicketChannel {
        WEB,
        MOBILE_APP,
        EMAIL,
        CHAT,
        PHONE,
        SOCIAL_MEDIA,
        API
    }
}
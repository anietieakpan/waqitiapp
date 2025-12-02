package com.waqiti.support.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ticket_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Column(nullable = false)
    private String senderId;

    @Column(nullable = false)
    private String senderName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SenderType senderType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(columnDefinition = "TEXT")
    private String htmlContent;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TicketAttachment> attachments = new ArrayList<>();

    private boolean isInternal;
    private boolean isAutomated;

    @Enumerated(EnumType.STRING)
    private MessageType messageType;

    @ElementCollection
    @CollectionTable(name = "message_metadata")
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata = new HashMap<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum SenderType {
        CUSTOMER,
        AGENT,
        SYSTEM,
        BOT
    }

    public enum MessageType {
        REPLY,
        NOTE,
        SYSTEM_MESSAGE,
        ESCALATION,
        RESOLUTION,
        SATISFACTION_SURVEY
    }
}
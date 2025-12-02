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

@Entity
@Table(name = "ticket_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"ticket", "attachments"})
public class TicketMessage extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;
    
    @Column(name = "sender_id", nullable = false)
    private String senderId;
    
    @Column(name = "sender_name", nullable = false)
    private String senderName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false)
    private MessageSenderType senderType;
    
    @Column(name = "is_internal_note")
    private boolean isInternalNote;
    
    @Column(name = "is_automated")
    private boolean isAutomated;
    
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TicketAttachment> attachments = new ArrayList<>();
    
    @Column(name = "read_at")
    private LocalDateTime readAt;
    
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
    
    @Column(name = "failed_delivery_reason")
    private String failedDeliveryReason;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status")
    private MessageDeliveryStatus deliveryStatus = MessageDeliveryStatus.PENDING;
    
    @Column(name = "sentiment_score")
    private Double sentimentScore;
    
    @Column(name = "ai_suggested_response", columnDefinition = "TEXT")
    private String aiSuggestedResponse;
    
    @Column(name = "response_time_seconds")
    private Long responseTimeSeconds;
    
    @Column(name = "quoted_message_id")
    private String quotedMessageId;
    
    @ElementCollection
    @CollectionTable(name = "message_metadata", joinColumns = @JoinColumn(name = "message_id"))
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    private java.util.Map<String, String> metadata = new java.util.HashMap<>();
    
    @PrePersist
    public void prePersist() {
        this.deliveredAt = LocalDateTime.now();
        this.deliveryStatus = MessageDeliveryStatus.DELIVERED;
    }
    
    public boolean isAgentMessage() {
        return this.senderType == MessageSenderType.AGENT || 
               this.senderType == MessageSenderType.SYSTEM;
    }
    
    public boolean isCustomerMessage() {
        return this.senderType == MessageSenderType.CUSTOMER;
    }
    
    public void markAsRead() {
        this.readAt = LocalDateTime.now();
    }
    
    public void addAttachment(TicketAttachment attachment) {
        attachments.add(attachment);
        attachment.setMessage(this);
    }
}



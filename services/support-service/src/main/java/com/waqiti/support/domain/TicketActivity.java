package com.waqiti.support.domain;

//import com.waqiti.common.domain.BaseEntity;

//added by aniix - maybe this is the authoritative base entity
import com.waqiti.common.entity.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.ToString;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_activities")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = "ticket")
public class TicketActivity extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType activityType;
    
    @Column(nullable = false)
    private String performedBy;
    
    @Column(nullable = false)
    private String performedByName;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "old_value")
    private String oldValue;
    
    @Column(name = "new_value")
    private String newValue;
    
    @Column(name = "field_name")
    private String fieldName;
    
    @Column(name = "activity_metadata", columnDefinition = "TEXT")
    private String activityMetadata;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "user_agent")
    private String userAgent;
    
    @PrePersist
    public void prePersist() {
        if (this.description == null) {
            this.description = generateDescription();
        }
    }
    
    private String generateDescription() {
        switch (this.activityType) {
            case CREATED:
                return "Ticket created";
            case ASSIGNED:
                return String.format("Ticket assigned to %s", this.newValue);
            case STATUS_CHANGED:
                return String.format("Status changed from %s to %s", this.oldValue, this.newValue);
            case PRIORITY_CHANGED:
                return String.format("Priority changed from %s to %s", this.oldValue, this.newValue);
            case ESCALATED:
                return "Ticket escalated";
            case MESSAGE_ADDED:
                return "Message added";
            case ATTACHMENT_ADDED:
                return "Attachment added";
            case RESOLVED:
                return "Ticket resolved";
            case CLOSED:
                return "Ticket closed";
            case REOPENED:
                return "Ticket reopened";
            case MERGED:
                return String.format("Ticket merged with %s", this.newValue);
            case TAGGED:
                return String.format("Tag '%s' added", this.newValue);
            case UNTAGGED:
                return String.format("Tag '%s' removed", this.oldValue);
            default:
                return this.activityType.toString();
        }
    }
}


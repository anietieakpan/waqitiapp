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

@Entity
@Table(name = "ticket_feedback")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = "ticket")
public class TicketFeedback extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @OneToOne
    @JoinColumn(name = "ticket_id", nullable = false, unique = true)
    private Ticket ticket;
    
    @Column(name = "satisfaction_rating", nullable = false)
    private Integer satisfactionRating; // 1-5 scale
    
    @Column(name = "resolution_rating")
    private Integer resolutionRating; // 1-5 scale
    
    @Column(name = "response_time_rating")
    private Integer responseTimeRating; // 1-5 scale
    
    @Column(name = "agent_rating")
    private Integer agentRating; // 1-5 scale
    
    @Column(columnDefinition = "TEXT")
    private String comment;
    
    @Column(name = "would_recommend")
    private Boolean wouldRecommend;
    
    @Column(name = "feedback_submitted_at", nullable = false)
    private LocalDateTime feedbackSubmittedAt;
    
    @Column(name = "feedback_source")
    @Enumerated(EnumType.STRING)
    private FeedbackSource feedbackSource;
    
    // Specific feedback questions
    @Column(name = "issue_resolved")
    private Boolean issueResolved;
    
    @Column(name = "easy_to_contact")
    private Boolean easyToContact;
    
    @Column(name = "professional_service")
    private Boolean professionalService;
    
    @Column(name = "clear_communication")
    private Boolean clearCommunication;
    
    // Follow-up
    @Column(name = "followup_required")
    private Boolean followupRequired;
    
    @Column(name = "followup_completed")
    private Boolean followupCompleted;
    
    @Column(name = "followup_notes", columnDefinition = "TEXT")
    private String followupNotes;
    
    @PrePersist
    public void prePersist() {
        this.feedbackSubmittedAt = LocalDateTime.now();
        if (this.feedbackSource == null) {
            this.feedbackSource = FeedbackSource.EMAIL;
        }
    }
    
    public Double getAverageRating() {
        int count = 0;
        int sum = 0;
        
        if (satisfactionRating != null) {
            sum += satisfactionRating;
            count++;
        }
        if (resolutionRating != null) {
            sum += resolutionRating;
            count++;
        }
        if (responseTimeRating != null) {
            sum += responseTimeRating;
            count++;
        }
        if (agentRating != null) {
            sum += agentRating;
            count++;
        }
        
        return count > 0 ? (double) sum / count : null;
    }
    
    public boolean isPositiveFeedback() {
        Double avg = getAverageRating();
        return avg != null && avg >= 4.0;
    }
    
    public boolean requiresFollowup() {
        return Boolean.TRUE.equals(followupRequired) && !Boolean.TRUE.equals(followupCompleted);
    }
}


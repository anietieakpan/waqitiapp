package com.waqiti.user.domain;

import com.waqiti.user.security.FamilyGuardianMfaService.GuardianActionRiskLevel;
import com.waqiti.user.security.FamilyGuardianMfaService.GuardianActionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Entity representing a guardian approval request for family account actions
 */
@Entity
@Table(name = "guardian_approval_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GuardianApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, name = "approval_request_id")
    private String approvalRequestId;

    @Column(nullable = false, name = "dependent_user_id")
    private UUID dependentUserId;

    @Column(nullable = false, name = "action_id")
    private String actionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "action_type")
    private GuardianActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "risk_level")
    private GuardianActionRiskLevel riskLevel;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "guardian_approval_required_guardians", 
                    joinColumns = @JoinColumn(name = "approval_request_id"))
    @Column(name = "guardian_id")
    private Set<UUID> requiredGuardianIds = new HashSet<>();

    @Column(name = "all_guardians_must_approve", columnDefinition = "boolean default false")
    private boolean allGuardiansMustApprove = false;

    @OneToMany(mappedBy = "approvalRequest", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<GuardianApproval> approvals = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "status")
    private ApprovalStatus status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "action_details", columnDefinition = "TEXT")
    private String actionDetails; // JSON string with action specifics

    @Column(name = "context_data", columnDefinition = "TEXT")
    private String contextData; // JSON string with device/location context

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Version
    private Long version;

    // Audit fields
    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    /**
     * Adds a guardian approval to this request
     */
    public void addApproval(UUID guardianId, LocalDateTime approvedAt) {
        GuardianApproval approval = GuardianApproval.builder()
            .approvalRequest(this)
            .guardianId(guardianId)
            .approvedAt(approvedAt)
            .build();
        
        this.approvals.add(approval);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Rejects the approval request
     */
    public void reject(UUID rejectedBy, String reason) {
        this.status = ApprovalStatus.REJECTED;
        this.rejectionReason = reason;
        this.completedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = rejectedBy;
    }

    /**
     * Expires the approval request
     */
    public void expire() {
        this.status = ApprovalStatus.EXPIRED;
        this.completedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Cancels the approval request
     */
    public void cancel(UUID cancelledBy, String reason) {
        this.status = ApprovalStatus.CANCELLED;
        this.rejectionReason = reason;
        this.completedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = cancelledBy;
    }

    /**
     * Checks if the approval request is still valid (not expired)
     */
    public boolean isValid() {
        return status == ApprovalStatus.PENDING && expiresAt.isAfter(LocalDateTime.now());
    }

    /**
     * Checks if a specific guardian has already approved
     */
    public boolean hasGuardianApproved(UUID guardianId) {
        return approvals.stream()
            .anyMatch(approval -> approval.getGuardianId().equals(guardianId));
    }

    /**
     * Gets the count of approvals received
     */
    public int getApprovalCount() {
        return approvals.size();
    }

    /**
     * Checks if all required approvals are complete
     */
    public boolean areAllApprovalsComplete() {
        if (allGuardiansMustApprove) {
            return approvals.size() == requiredGuardianIds.size();
        } else {
            return approvals.size() > 0;
        }
    }

    // Setters with audit trail

    public void setStatus(ApprovalStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public void setActionDetails(String actionDetails) {
        this.actionDetails = actionDetails;
        this.updatedAt = LocalDateTime.now();
    }

    public void setContextData(String contextData) {
        this.contextData = contextData;
        this.updatedAt = LocalDateTime.now();
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Approval status enumeration
     */
    public enum ApprovalStatus {
        PENDING,    // Waiting for guardian approvals
        APPROVED,   // All required approvals received
        REJECTED,   // Rejected by guardian(s)
        EXPIRED,    // Expired without completion
        CANCELLED   // Cancelled by requestor or system
    }
}


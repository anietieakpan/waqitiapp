package com.waqiti.user.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID; /**
 * Individual guardian approval within an approval request
 */
@Entity
@Table(name = "guardian_approvals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GuardianApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_request_id", nullable = false)
    private GuardianApprovalRequest approvalRequest;

    @Column(nullable = false, name = "guardian_id")
    private UUID guardianId;

    @Column(name = "approved_at", nullable = false)
    private LocalDateTime approvedAt;

    @Column(name = "approval_method")
    private String approvalMethod; // SMS, EMAIL, etc.

    @Column(name = "device_info")
    private String deviceInfo; // JSON string with device details

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Version
    private Long version;

    /**
     * Records approval method and context
     */
    public void recordApprovalContext(String approvalMethod, String deviceInfo, 
                                    String ipAddress, String userAgent) {
        this.approvalMethod = approvalMethod;
        this.deviceInfo = deviceInfo;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }
}

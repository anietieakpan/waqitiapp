package com.waqiti.security.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "security_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class SecurityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "event_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private SecurityEventType eventType;

    @Column(name = "severity", nullable = false)
    @Enumerated(EnumType.STRING)
    private SecuritySeverity severity;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata;

    @Column(name = "resolved")
    private Boolean resolved = false;

    @Column(name = "action_taken")
    @Enumerated(EnumType.STRING)
    private SecurityAction actionTaken;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;
}
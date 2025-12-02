package com.waqiti.account.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Closure Request Entity
 *
 * Initial closure request submitted by customer or system
 */
@Entity
@Table(name = "closure_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClosureRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "request_type", length = 50)
    private String requestType;

    @Column(name = "request_reason", columnDefinition = "TEXT")
    private String requestReason;

    @Column(name = "status", length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

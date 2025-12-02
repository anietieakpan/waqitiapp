package com.waqiti.reporting.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "report_executions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ReportExecution {

    @Id
    private UUID executionId;

    @Column(nullable = false)
    private String reportType;

    @Column(nullable = false)
    private String requestedBy;

    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime startedAt;

    @LastModifiedDate
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status;

    @ElementCollection
    @CollectionTable(name = "report_execution_parameters", joinColumns = @JoinColumn(name = "execution_id"))
    @MapKeyColumn(name = "parameter_name")
    @Column(name = "parameter_value")
    private Map<String, String> parameters;

    private String outputPath;

    private Long reportSize;

    private String errorMessage;

    @Column(columnDefinition = "TEXT")
    private String executionLogs;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @PrePersist
    protected void onCreate() {
        if (executionId == null) {
            executionId = UUID.randomUUID();
        }
        if (status == null) {
            status = ExecutionStatus.PENDING;
        }
    }

    public enum ExecutionStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
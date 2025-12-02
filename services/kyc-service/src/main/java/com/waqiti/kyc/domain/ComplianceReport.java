package com.waqiti.kyc.domain;

import com.waqiti.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Entity representing a compliance report
 */
@Entity
@Table(name = "compliance_reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ComplianceReport extends BaseEntity {
    
    @Column(nullable = false)
    private String organizationId;
    
    @Column(nullable = false)
    private String reportType;
    
    @Column(nullable = false)
    private String requestedBy;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status = ReportStatus.PENDING;
    
    @Column(nullable = false)
    private String format = "PDF";
    
    private LocalDateTime startedAt;
    
    private LocalDateTime completedAt;
    
    private String filePath;
    
    private Long fileSize;
    
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(nullable = false)
    private Boolean recurring = false;
    
    private String schedule;
    
    private LocalDateTime nextRunAt;
    
    @ElementCollection
    @CollectionTable(name = "report_parameters", joinColumns = @JoinColumn(name = "report_id"))
    @MapKeyColumn(name = "param_key")
    @Column(name = "param_value")
    private Map<String, String> parameters = new HashMap<>();
    
    @Column(columnDefinition = "json")
    private String metadata;
    
    public enum ReportStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
package com.waqiti.reporting.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "report_definitions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ReportDefinition {

    @Id
    private UUID reportId;

    @Column(unique = true, nullable = false)
    private String reportCode;

    @Column(nullable = false)
    private String reportName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportType reportType;

    @Column(nullable = false)
    private String templatePath;

    @ElementCollection
    @CollectionTable(name = "report_data_sources", joinColumns = @JoinColumn(name = "report_id"))
    @Column(name = "data_source")
    private List<String> dataSources;

    @ElementCollection
    @CollectionTable(name = "report_parameters", joinColumns = @JoinColumn(name = "report_id"))
    @MapKeyColumn(name = "parameter_name")
    @Column(name = "parameter_config")
    private Map<String, String> parameters;

    @ElementCollection
    @CollectionTable(name = "report_output_formats", joinColumns = @JoinColumn(name = "report_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "output_format")
    private List<OutputFormat> supportedFormats;

    @Column(nullable = false)
    private Boolean isActive;

    @Column(nullable = false)
    private Boolean isSchedulable;

    @Column(nullable = false)
    private Boolean requiresApproval;

    @ElementCollection
    @CollectionTable(name = "report_permissions", joinColumns = @JoinColumn(name = "report_id"))
    @Column(name = "permission")
    private List<String> requiredPermissions;

    @Column(nullable = false)
    private String createdBy;

    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private String updatedBy;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @PrePersist
    protected void onCreate() {
        if (reportId == null) {
            reportId = UUID.randomUUID();
        }
        if (isActive == null) {
            isActive = true;
        }
        if (isSchedulable == null) {
            isSchedulable = false;
        }
        if (requiresApproval == null) {
            requiresApproval = false;
        }
    }

    public enum ReportCategory {
        FINANCIAL,
        REGULATORY,
        OPERATIONAL,
        RISK_MANAGEMENT,
        COMPLIANCE,
        CUSTOMER_ANALYTICS,
        BUSINESS_INTELLIGENCE
    }

    public enum ReportType {
        DASHBOARD,
        STATEMENT,
        REGULATORY_FILING,
        MIS_REPORT,
        ANALYTICS_REPORT,
        AUDIT_REPORT,
        EXCEPTION_REPORT
    }

    public enum OutputFormat {
        PDF,
        EXCEL,
        CSV,
        JSON,
        XML,
        HTML
    }
}
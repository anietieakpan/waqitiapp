package com.waqiti.infrastructure.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InfrastructureReport {
    private ReportPeriod reportPeriod;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime generatedAt;
    private HealthSummary healthSummary;
    private BackupSummary backupSummary;
    private IncidentSummary incidentSummary;
    private CapacitySummary capacitySummary;
    private AvailabilityMetrics availabilityMetrics;
    private String executiveSummary;
    private String recommendations;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class HealthSummary {
    private Integer totalChecks;
    private Integer healthyChecks;
    private Integer degradedChecks;
    private Integer unhealthyChecks;
    private Duration averageResponseTime;
    private Double uptimePercentage;
    private String worstPerformingComponent;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class BackupSummary {
    private Integer totalBackups;
    private Integer successfulBackups;
    private Integer failedBackups;
    private Double totalSizeGB;
    private Duration averageDuration;
    private Double successRate;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class IncidentSummary {
    private Integer totalIncidents;
    private Integer resolvedIncidents;
    private Integer openIncidents;
    private Duration averageResolutionTime;
    private String mostCommonIncidentType;
    private Integer criticalIncidents;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class CapacitySummary {
    private Double averageCpuUtilization;
    private Double averageMemoryUtilization;
    private Double averageDiskUtilization;
    private Double peakCpuUtilization;
    private Double peakMemoryUtilization;
    private Integer scalingEvents;
    private String recommendation;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class AvailabilityMetrics {
    private Double overallUptime;
    private Map<String, Double> serviceUptime;
    private Double downtimeMinutes;
    private Duration mttr; // Mean Time To Recovery
    private Duration mtbf; // Mean Time Between Failures
    private String slaStatus;
}

enum ReportPeriod {
    HOURLY,
    DAILY,
    WEEKLY,
    MONTHLY,
    QUARTERLY,
    YEARLY
}
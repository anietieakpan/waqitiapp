package com.waqiti.infrastructure.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "system_health_records")
public class SystemHealthRecord {
    @Id
    private String id;
    private LocalDateTime timestamp;
    private SystemStatus overallStatus;
    private Integer componentCount;
    private Integer healthyComponents;
    private Integer degradedComponents;
    private Integer unhealthyComponents;
    private Double averageResponseTimeMs;
    private String criticalComponents;
    private String alertLevel;
}
package com.waqiti.infrastructure.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "monitoring_records")
public class MonitoringRecord {
    @Id
    private String id;
    private String metricName;
    private Double value;
    private String unit;
    private LocalDateTime timestamp;
    private Map<String, String> tags;
    private String source;
    private String alertLevel;
    private String description;
}
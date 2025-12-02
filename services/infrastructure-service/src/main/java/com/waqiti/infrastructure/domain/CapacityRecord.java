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
@Document(collection = "capacity_records")
public class CapacityRecord {
    @Id
    private String id;
    private String resource;
    private Double utilization;
    private Double threshold;
    private LocalDateTime timestamp;
    private String region;
    private String zone;
    private String instanceId;
    private Double available;
    private Double total;
    private String alertLevel;
}
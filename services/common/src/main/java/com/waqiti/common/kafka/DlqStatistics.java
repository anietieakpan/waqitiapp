package com.waqiti.common.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqStatistics {
    
    private String topic;
    private int totalMessages;
    private long cachedMessages;
    private Map<String, Long> errorTypeCounts;
    private Instant timestamp;
}
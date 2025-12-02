package com.waqiti.ml.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IP Reputation data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpReputation {
    
    private String ipAddress;
    private double score;
    private List<String> categories;
    private String source;
    private boolean isMalicious;
    private LocalDateTime lastChecked;
    private LocalDateTime expiry;
}
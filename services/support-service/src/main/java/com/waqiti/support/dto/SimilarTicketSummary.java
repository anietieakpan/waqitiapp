package com.waqiti.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimilarTicketSummary {
    private Long ticketId;
    private String ticketNumber;
    private String subject;
    private String category;
    private String priority;
    private String status;
    private String resolution;
    private double similarityScore;
    private long resolutionTimeMinutes;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private String agentId;
    private String resolutionPattern;
}
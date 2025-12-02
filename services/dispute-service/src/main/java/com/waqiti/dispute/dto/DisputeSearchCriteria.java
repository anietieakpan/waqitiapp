package com.waqiti.dispute.dto;

import com.waqiti.dispute.entity.DisputeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Search criteria for dispute queries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeSearchCriteria {

    private String searchTerm;
    private DisputeStatus status;
    private String category;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String assignedTo;
    private Integer minAmount;
    private Integer maxAmount;
}

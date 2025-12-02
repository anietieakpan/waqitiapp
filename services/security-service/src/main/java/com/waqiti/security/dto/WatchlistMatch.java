package com.waqiti.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Watchlist Match DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistMatch {
    private String listName;
    private String matchedName;
    private double matchScore;
    private String reason;
}

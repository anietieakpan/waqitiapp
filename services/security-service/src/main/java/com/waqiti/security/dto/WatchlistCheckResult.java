package com.waqiti.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Watchlist Check Result DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistCheckResult {
    private boolean isMatch;
    private List<WatchlistMatch> matches;
    private String checkId;
    private LocalDateTime checkedAt;
}

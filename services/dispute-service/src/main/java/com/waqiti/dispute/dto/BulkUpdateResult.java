package com.waqiti.dispute.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkUpdateResult {

    private int totalRequested;
    private int successfulUpdates;
    private int failedUpdates;
    private List<UUID> successfulDisputeIds;
    private List<FailedUpdate> failedUpdatesList;

    private int totalProcessed; // added by aniix - from last refactoring
    private int successCount; // added by aniix - from last refactoring
    private int failureCount; // added by aniix - from last refactoring


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedUpdate {
        private UUID disputeId;
        private String reason;
    }
}


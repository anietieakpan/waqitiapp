package com.waqiti.common.kafka.dlq;

import lombok.Builder;
import lombok.Data;

/**
 * Result of DLQ message reprocessing attempt
 */
@Data
@Builder
public class DlqProcessingResult {

    /**
     * Whether reprocessing succeeded
     */
    private boolean success;

    /**
     * Error message if reprocessing failed
     */
    private String errorMessage;

    /**
     * Detailed error information for debugging
     */
    private String errorDetails;

    /**
     * Result details if reprocessing succeeded
     */
    private String resultDetails;

    /**
     * Whether this failure is retriable
     */
    private boolean retriable;

    /**
     * Suggested action for manual intervention
     */
    private String suggestedAction;

    public static DlqProcessingResult success(String resultDetails) {
        return DlqProcessingResult.builder()
                .success(true)
                .resultDetails(resultDetails)
                .build();
    }

    public static DlqProcessingResult failure(String errorMessage, boolean retriable) {
        return DlqProcessingResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .retriable(retriable)
                .build();
    }

    public static DlqProcessingResult permanentFailure(String errorMessage, String suggestedAction) {
        return DlqProcessingResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .retriable(false)
                .suggestedAction(suggestedAction)
                .build();
    }
}

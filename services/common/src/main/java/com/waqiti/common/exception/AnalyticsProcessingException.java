package com.waqiti.common.exception;

/**
 * Exception thrown when analytics data processing fails.
 *
 * Common causes:
 * - Spark job failure
 * - Invalid analytics data
 * - Data aggregation errors
 * - Report generation failure
 * - Metrics calculation errors
 *
 * This should trigger:
 * - Retry for transient errors
 * - Alert analytics team
 * - Log for investigation
 * - Fallback to cached analytics
 *
 * @author Waqiti Platform
 */
public class AnalyticsProcessingException extends RuntimeException {

    private final String analyticsType;
    private final String dataSource;
    private final String processingStage;

    /**
     * Creates exception with message
     */
    public AnalyticsProcessingException(String message) {
        super(message);
        this.analyticsType = null;
        this.dataSource = null;
        this.processingStage = null;
    }

    /**
     * Creates exception with message and cause
     */
    public AnalyticsProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.analyticsType = null;
        this.dataSource = null;
        this.processingStage = null;
    }

    /**
     * Creates exception with detailed analytics information
     */
    public AnalyticsProcessingException(String message, String analyticsType, String dataSource,
                                       String processingStage) {
        super(String.format("%s (type=%s, source=%s, stage=%s)",
            message, analyticsType, dataSource, processingStage));
        this.analyticsType = analyticsType;
        this.dataSource = dataSource;
        this.processingStage = processingStage;
    }

    /**
     * Creates exception with detailed analytics information and cause
     */
    public AnalyticsProcessingException(String message, String analyticsType, String dataSource,
                                       String processingStage, Throwable cause) {
        super(String.format("%s (type=%s, source=%s, stage=%s)",
            message, analyticsType, dataSource, processingStage), cause);
        this.analyticsType = analyticsType;
        this.dataSource = dataSource;
        this.processingStage = processingStage;
    }

    public String getAnalyticsType() {
        return analyticsType;
    }

    public String getDataSource() {
        return dataSource;
    }

    public String getProcessingStage() {
        return processingStage;
    }
}

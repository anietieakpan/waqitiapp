package com.waqiti.compliance.contracts.dto;

/**
 * Report generation status
 */
public enum ReportStatus {
    /**
     * Report generation in progress
     */
    GENERATING,

    /**
     * Report generated successfully
     */
    COMPLETED,

    /**
     * Report generation failed
     */
    FAILED,

    /**
     * Report expired and no longer available
     */
    EXPIRED,

    /**
     * Report queued for generation
     */
    QUEUED
}

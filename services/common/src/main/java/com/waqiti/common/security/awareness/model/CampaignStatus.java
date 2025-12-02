package com.waqiti.common.security.awareness.model;

/**
 * Campaign Status Enum
 *
 * Status of phishing simulation campaign.
 */
public enum CampaignStatus {
    DRAFT,          // Campaign created but not scheduled
    SCHEDULED,      // Campaign scheduled to launch
    IN_PROGRESS,    // Campaign actively running
    COMPLETED,      // Campaign finished
    CANCELLED       // Campaign cancelled
}
package com.waqiti.gdpr.domain;

/**
 * Status of Data Privacy Impact Assessment
 */
public enum DpiaStatus {
    /**
     * DPIA has been initiated but not yet started
     */
    INITIATED,

    /**
     * DPIA is in draft/preparation phase
     */
    DRAFT,

    /**
     * DPIA is under review
     */
    UNDER_REVIEW,

    /**
     * DPO consultation in progress
     */
    DPO_CONSULTATION,

    /**
     * Data subject consultation in progress
     */
    SUBJECT_CONSULTATION,

    /**
     * Supervisory authority consultation required/in progress
     */
    AUTHORITY_CONSULTATION,

    /**
     * DPIA has been completed
     */
    COMPLETED,

    /**
     * DPIA has been approved
     */
    APPROVED,

    /**
     * DPIA has been rejected and needs revision
     */
    REJECTED,

    /**
     * DPIA is scheduled for periodic review
     */
    UNDER_PERIODIC_REVIEW,

    /**
     * DPIA has been superseded by a newer version
     */
    SUPERSEDED,

    /**
     * DPIA has been archived
     */
    ARCHIVED
}

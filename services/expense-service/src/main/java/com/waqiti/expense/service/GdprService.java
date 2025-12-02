package com.waqiti.expense.service;

/**
 * GDPR compliance service for data subject rights
 */
public interface GdprService {

    /**
     * Export all user data (GDPR Right to Data Portability)
     */
    byte[] exportUserData();

    /**
     * Delete all user data (GDPR Right to Erasure)
     */
    void deleteUserData();

    /**
     * Get summary of stored user data (GDPR Right to Access)
     */
    Object getDataSummary();
}

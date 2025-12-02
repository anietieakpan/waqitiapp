package com.waqiti.transaction.service;

/**
 * Transaction notification service interface
 */
public interface TransactionNotificationService {
    
    void sendCustomerBlockNotification(Object transaction, Object block);
    void sendMerchantBlockNotification(Object transaction, Object block);
    void sendInternalBlockAlert(Object transaction, Object block);
    void sendComplianceTeamNotification(Object transaction, Object block);
    void sendEmergencyBlockAlert(Object transaction, Object blockEvent);
    void sendFraudTeamAlert(Object transaction, Object block, Object fraudAssessment);
}
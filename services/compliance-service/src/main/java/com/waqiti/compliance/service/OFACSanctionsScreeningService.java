package com.waqiti.compliance.service;

import com.waqiti.compliance.model.SanctionsScreeningResult;
import com.waqiti.compliance.model.SanctionedEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * OFAC Sanctions Screening Service Interface
 * 
 * CRITICAL COMPLIANCE: Prevents transactions with sanctioned entities
 * REGULATORY IMPACT: Avoids severe penalties for sanctions violations
 * 
 * This service performs real-time screening against OFAC and other sanctions lists
 */
public interface OFACSanctionsScreeningService {
    
    /**
     * Screen a user against sanctions lists
     * 
     * @param userId User to screen
     * @param fullName Full name of the user
     * @param dateOfBirth Date of birth
     * @param country Country of residence
     * @param nationalId National identification number
     * @return Screening result with match details
     */
    SanctionsScreeningResult screenUser(UUID userId, String fullName, String dateOfBirth, 
                                       String country, String nationalId);
    
    /**
     * Screen a transaction for sanctions violations
     * 
     * @param transactionId Transaction to screen
     * @param senderId Sender user ID
     * @param recipientId Recipient user ID
     * @param amount Transaction amount
     * @param currency Currency code
     * @param senderCountry Sender's country
     * @param recipientCountry Recipient's country
     * @return Screening result
     */
    SanctionsScreeningResult screenTransaction(UUID transactionId, UUID senderId, UUID recipientId,
                                              BigDecimal amount, String currency,
                                              String senderCountry, String recipientCountry);
    
    /**
     * Screen an entity name against sanctions lists
     * 
     * @param entityName Name to screen
     * @param entityType Type (INDIVIDUAL, ORGANIZATION, VESSEL, etc.)
     * @param country Country association
     * @return Screening result
     */
    SanctionsScreeningResult screenEntity(String entityName, String entityType, String country);
    
    /**
     * Batch screen multiple entities
     * 
     * @param entities List of entities to screen
     * @return List of screening results
     */
    List<SanctionsScreeningResult> batchScreenEntities(List<EntityScreeningRequest> entities);
    
    /**
     * Check if a country is sanctioned
     * 
     * @param countryCode ISO country code
     * @return True if country is sanctioned
     */
    boolean isCountrySanctioned(String countryCode);
    
    /**
     * Update sanctions lists from OFAC
     * 
     * @return Number of records updated
     */
    int updateSanctionsLists();
    
    /**
     * Get details of a sanctioned entity
     * 
     * @param sanctionsId Sanctions list ID
     * @return Sanctioned entity details
     */
    SanctionedEntity getSanctionedEntityDetails(String sanctionsId);
    
    /**
     * Clear a false positive match
     * 
     * @param userId User ID
     * @param screeningId Screening result ID
     * @param clearedBy Officer who cleared it
     * @param reason Reason for clearing
     */
    void clearFalsePositive(UUID userId, String screeningId, String clearedBy, String reason);
    
    /**
     * Entity screening request
     */
    class EntityScreeningRequest {
        public String entityId;
        public String entityName;
        public String entityType;
        public String country;
        public String additionalInfo;
    }
}
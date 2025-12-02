package com.waqiti.compliance.service;

import com.waqiti.common.events.SarFilingRequestEvent;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Regulatory Filing Service Interface
 * 
 * CRITICAL REGULATORY COMPLIANCE: Direct interface with regulatory authorities
 * LEGAL IMPACT: Ensures proper filing with FinCEN, OFAC, and other regulators
 * 
 * Handles direct communication with:
 * - FinCEN (Financial Crimes Enforcement Network)
 * - OFAC (Office of Foreign Assets Control)
 * - Federal Reserve
 * - State banking regulators
 * - Law enforcement agencies
 */
public interface RegulatoryFilingService {
    
    /**
     * File SAR with regulatory authority
     * 
     * @param sarId SAR ID
     * @param regulatoryBody Regulatory authority
     * @param event SAR event data
     * @param expedited Whether to use expedited filing
     * @return Filing confirmation ID
     */
    String fileSarWithRegulator(String sarId, String regulatoryBody, SarFilingRequestEvent event, 
                               boolean expedited);
    
    /**
     * Notify specific regulatory body about compliance issue
     * 
     * @param regulatoryBody Regulatory authority
     * @param userId User ID
     * @param issueType Type of compliance issue
     * @param caseId Case ID
     * @param amount Transaction amount if applicable
     */
    void notifyRegulatoryBody(String regulatoryBody, UUID userId, String issueType, 
                             String caseId, BigDecimal amount);
    
    /**
     * Notify counter-terrorism unit about terrorist financing
     * 
     * @param event SAR event with terrorist financing indicators
     */
    void notifyCounterTerrorismUnit(SarFilingRequestEvent event);
    
    /**
     * Notify OFAC compliance unit about sanctions violations
     * 
     * @param event SAR event with sanctions violations
     */
    void notifyOfacCompliance(SarFilingRequestEvent event);
    
    /**
     * Notify law enforcement about suspicious activity
     * 
     * @param userId User ID
     * @param activityType Type of suspicious activity
     * @param description Activity description
     * @param amount Amount involved
     * @param caseId Case ID
     */
    void notifyLawEnforcement(UUID userId, String activityType, String description, 
                             BigDecimal amount, String caseId);
    
    /**
     * Submit Currency Transaction Report (CTR) for transactions over $10,000
     * 
     * @param userId User ID
     * @param transactionId Transaction ID
     * @param amount Transaction amount
     * @param currency Currency
     * @param transactionType Transaction type
     * @return CTR filing ID
     */
    String submitCurrencyTransactionReport(UUID userId, UUID transactionId, BigDecimal amount,
                                          String currency, String transactionType);
    
    /**
     * File Monetary Instrument Log (MIL) for structured transactions
     * 
     * @param userId User ID
     * @param transactionIds List of related transaction IDs
     * @param totalAmount Total amount across transactions
     * @param period Time period of transactions
     * @return MIL filing ID
     */
    String fileMonetaryInstrumentLog(UUID userId, java.util.List<UUID> transactionIds, 
                                    BigDecimal totalAmount, String period);
    
    /**
     * Submit OFAC compliance report
     * 
     * @param userId User ID
     * @param sanctionsMatch Sanctions match details
     * @param actionTaken Action taken
     * @param caseId Case ID
     * @return OFAC report ID
     */
    String submitOfacComplianceReport(UUID userId, String sanctionsMatch, String actionTaken, String caseId);
}
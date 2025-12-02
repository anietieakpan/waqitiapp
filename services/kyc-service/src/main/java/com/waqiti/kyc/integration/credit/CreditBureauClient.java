package com.waqiti.kyc.integration.credit;

import com.waqiti.kyc.dto.credit.*;
import java.util.concurrent.CompletableFuture;

/**
 * Common interface for credit bureau integrations
 */
public interface CreditBureauClient {
    
    CompletableFuture<CreditReportResponse> getCreditReport(CreditReportRequest request);
    
    CompletableFuture<CreditScoreResponse> getCreditScore(CreditScoreRequest request);
    
    CompletableFuture<IdentityVerificationResponse> verifyIdentity(IdentityVerificationRequest request);
    
    CompletableFuture<FraudCheckResponse> checkFraud(FraudCheckRequest request);
    
    CompletableFuture<IncomeVerificationResponse> verifyIncome(IncomeVerificationRequest request);
}
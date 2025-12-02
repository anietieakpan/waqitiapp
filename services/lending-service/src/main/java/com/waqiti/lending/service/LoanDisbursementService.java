package com.waqiti.lending.service;

import com.waqiti.lending.domain.Loan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Loan Disbursement Service
 * Handles fund disbursement to borrowers
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoanDisbursementService {

    private final LoanService loanService;

    /**
     * Disburse loan funds
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public DisbursementResult disburseLoan(String loanId, String disbursementMethod,
                                          String destinationAccountId) {
        log.info("Processing disbursement for loan: {} via {} to account: {}",
                loanId, disbursementMethod, destinationAccountId);

        // Get loan
        Loan loan = loanService.findByLoanId(loanId);

        // Validate loan is ready for disbursement
        if (loan.getDisbursedAt() != null) {
            throw new IllegalStateException("Loan already disbursed: " + loanId);
        }

        // Calculate net disbursement amount (after fees)
        BigDecimal grossAmount = loan.getPrincipalAmount();
        BigDecimal originationFee = loan.getOriginationFee() != null ?
                loan.getOriginationFee() : BigDecimal.ZERO;
        BigDecimal netAmount = grossAmount.subtract(originationFee);

        // TODO: Integrate with actual payment processing service
        // For now, simulate successful disbursement

        // Update loan status
        loanService.disburseLoan(loanId, disbursementMethod);

        DisbursementResult result = new DisbursementResult();
        result.setLoanId(loanId);
        result.setGrossAmount(grossAmount);
        result.setOriginationFee(originationFee);
        result.setNetAmount(netAmount);
        result.setDisbursementMethod(disbursementMethod);
        result.setDestinationAccountId(destinationAccountId);
        result.setStatus("SUCCESS");
        result.setTransactionId(generateTransactionId());

        log.info("Loan disbursed successfully: {} - Net amount: {}, Transaction ID: {}",
                loanId, netAmount, result.getTransactionId());

        return result;
    }

    /**
     * Generate transaction ID
     */
    private String generateTransactionId() {
        return "TXN-" + System.currentTimeMillis();
    }

    /**
     * Disbursement Result DTO
     */
    @lombok.Data
    public static class DisbursementResult {
        private String loanId;
        private BigDecimal grossAmount;
        private BigDecimal originationFee;
        private BigDecimal netAmount;
        private String disbursementMethod;
        private String destinationAccountId;
        private String status;
        private String transactionId;
    }
}

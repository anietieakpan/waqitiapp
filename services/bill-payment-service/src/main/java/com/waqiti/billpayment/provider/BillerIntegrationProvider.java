package com.waqiti.billpayment.provider;

import com.waqiti.billpayment.domain.BillInquiryResult;
import com.waqiti.billpayment.domain.BillerConnectionResult;
import com.waqiti.billpayment.domain.PaymentSubmissionResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Provider interface for integrating with external biller systems
 * Abstracts the communication with various biller APIs
 */
public interface BillerIntegrationProvider {

    /**
     * Establish connection to a biller
     *
     * @param userId User ID establishing the connection
     * @param billerId Biller ID to connect to
     * @param accountNumber Account number with the biller
     * @param accountName Account holder name
     * @param credentials Authentication credentials for biller
     * @return Connection result with status and details
     */
    BillerConnectionResult establishConnection(
            String userId,
            UUID billerId,
            String accountNumber,
            String accountName,
            String credentials);

    /**
     * Inquire bill details from biller
     *
     * @param billerCode Biller code/identifier
     * @param accountNumber Account number
     * @param accountName Account holder name
     * @return Bill inquiry result with bill details
     */
    BillInquiryResult inquireBill(
            String billerCode,
            String accountNumber,
            String accountName);

    /**
     * Submit payment to biller
     *
     * @param billerId Biller ID
     * @param billId Bill ID
     * @param accountNumber Account number
     * @param amount Payment amount
     * @param currency Currency code
     * @param paymentReference Payment reference/tracking number
     * @return Payment submission result
     */
    PaymentSubmissionResult submitPayment(
            UUID billerId,
            UUID billId,
            String accountNumber,
            BigDecimal amount,
            String currency,
            String paymentReference);

    /**
     * Fetch bills from biller for a connection
     *
     * @param connectionId Connection ID
     * @return List of bill data from biller
     */
    List<BillData> fetchBills(UUID connectionId);

    /**
     * Check if biller supports direct payment
     *
     * @param billerId Biller ID
     * @return true if direct payment is supported
     */
    boolean supportsDirectPayment(UUID billerId);

    /**
     * Check if biller supports bill negotiation
     *
     * @param billerId Biller ID
     * @return true if negotiation is supported
     */
    boolean supportsNegotiation(UUID billerId);

    /**
     * Initiate bill negotiation with biller
     *
     * @param billerId Biller ID
     * @param billId Bill ID
     * @param proposedAmount Proposed payment amount
     * @return Negotiation result
     */
    NegotiationResult initiateBillNegotiation(
            UUID billerId,
            UUID billId,
            BigDecimal proposedAmount);

    /**
     * Verify payment status with biller
     *
     * @param billerId Biller ID
     * @param externalPaymentId External payment reference from biller
     * @return Payment status
     */
    String verifyPaymentStatus(UUID billerId, String externalPaymentId);

    /**
     * Test connection to biller
     *
     * @param billerId Biller ID
     * @return true if connection is successful
     */
    boolean testConnection(UUID billerId);

    /**
     * Data class for bill information from external biller
     */
    class BillData {
        public String externalBillId;
        public String billNumber;
        public String accountNumber;
        public BigDecimal amount;
        public BigDecimal minimumDue;
        public String currency;
        public java.time.LocalDate dueDate;
        public java.time.LocalDate issueDate;
        public String billPeriod;
        public String status;
        public String description;
    }

    /**
     * Data class for bill negotiation result
     */
    class NegotiationResult {
        public boolean success;
        public String negotiationId;
        public BigDecimal counterOffer;
        public String status;
        public String message;
    }
}

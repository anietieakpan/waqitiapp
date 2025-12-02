package com.waqiti.lending.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Document Generation Service
 *
 * Generates loan-related documents including:
 * - Loan agreements
 * - Truth in Lending Act (TILA) disclosures
 * - Payment schedules
 * - Promissory notes
 * - Disclosure statements
 *
 * This service creates document metadata and initiates document generation
 * processes. Actual document rendering would be handled by a document
 * generation engine (e.g., Jasper Reports, DocuSign, etc.)
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class DocumentGenerationService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMMM dd, yyyy");

    /**
     * Generate loan agreement document
     *
     * Creates a legally binding loan agreement between lender and borrower
     * including all loan terms and conditions
     *
     * @param loanId Loan identifier
     * @param borrowerId Borrower identifier
     * @param amount Loan amount
     * @param interestRate Annual interest rate
     * @param termMonths Loan term in months
     * @param termsAndConditions Additional terms and conditions
     * @return Document ID for the generated agreement
     */
    public String generateLoanAgreement(
            String loanId,
            UUID borrowerId,
            BigDecimal amount,
            BigDecimal interestRate,
            Integer termMonths,
            String termsAndConditions) {

        String documentId = generateDocumentId("LOAN_AGREEMENT");

        log.info("Generating loan agreement - DocumentID: {}, LoanID: {}, BorrowerID: {}, Amount: {}",
                documentId, loanId, borrowerId, amount);

        Map<String, Object> documentData = new HashMap<>();
        documentData.put("documentId", documentId);
        documentData.put("documentType", "LOAN_AGREEMENT");
        documentData.put("loanId", loanId);
        documentData.put("borrowerId", borrowerId);
        documentData.put("principalAmount", amount);
        documentData.put("interestRate", interestRate);
        documentData.put("termMonths", termMonths);
        documentData.put("termsAndConditions", termsAndConditions);
        documentData.put("generatedDate", LocalDate.now());
        documentData.put("effectiveDate", LocalDate.now());

        // TODO: Integrate with document generation engine (Jasper Reports, DocuSign, etc.)
        // For now, we create metadata and return document ID
        createDocumentMetadata(documentData);

        log.info("Loan agreement generated successfully - DocumentID: {}", documentId);
        return documentId;
    }

    /**
     * Generate Truth in Lending Act (TILA) disclosure
     *
     * Creates federally required TILA disclosure showing:
     * - APR (Annual Percentage Rate)
     * - Finance charge
     * - Amount financed
     * - Total of payments
     * - Payment schedule
     *
     * @param loanId Loan identifier
     * @param apr Annual Percentage Rate
     * @param financeCharge Total finance charge
     * @param totalOfPayments Total amount to be paid
     * @param monthlyPayment Monthly payment amount
     * @return Document ID for the TILA disclosure
     */
    public String generateTruthInLendingDisclosure(
            String loanId,
            BigDecimal apr,
            BigDecimal financeCharge,
            BigDecimal totalOfPayments,
            BigDecimal monthlyPayment) {

        String documentId = generateDocumentId("TILA_DISCLOSURE");

        log.info("Generating TILA disclosure - DocumentID: {}, LoanID: {}, APR: {}%",
                documentId, loanId, apr);

        Map<String, Object> documentData = new HashMap<>();
        documentData.put("documentId", documentId);
        documentData.put("documentType", "TILA_DISCLOSURE");
        documentData.put("loanId", loanId);
        documentData.put("annualPercentageRate", apr);
        documentData.put("financeCharge", financeCharge);
        documentData.put("amountFinanced", totalOfPayments.subtract(financeCharge));
        documentData.put("totalOfPayments", totalOfPayments);
        documentData.put("monthlyPayment", monthlyPayment);
        documentData.put("generatedDate", LocalDate.now());
        documentData.put("disclosureDate", LocalDate.now());

        // Add required TILA disclosure text
        documentData.put("disclosureText", generateTilaDisclosureText(apr, financeCharge, totalOfPayments));

        createDocumentMetadata(documentData);

        log.info("TILA disclosure generated successfully - DocumentID: {}", documentId);
        return documentId;
    }

    /**
     * Generate payment schedule document
     *
     * Creates a detailed amortization schedule showing all payments
     *
     * @param loanId Loan identifier
     * @param firstPaymentDate Date of first payment
     * @param monthlyPayment Amount of each payment
     * @param termMonths Number of payments
     * @return Document ID for the payment schedule
     */
    public String generatePaymentSchedule(
            String loanId,
            LocalDate firstPaymentDate,
            BigDecimal monthlyPayment,
            Integer termMonths) {

        String documentId = generateDocumentId("PAYMENT_SCHEDULE");

        log.info("Generating payment schedule - DocumentID: {}, LoanID: {}, FirstPayment: {}, Payments: {}",
                documentId, loanId, firstPaymentDate, termMonths);

        Map<String, Object> documentData = new HashMap<>();
        documentData.put("documentId", documentId);
        documentData.put("documentType", "PAYMENT_SCHEDULE");
        documentData.put("loanId", loanId);
        documentData.put("firstPaymentDate", firstPaymentDate);
        documentData.put("monthlyPayment", monthlyPayment);
        documentData.put("numberOfPayments", termMonths);
        documentData.put("lastPaymentDate", firstPaymentDate.plusMonths(termMonths - 1));
        documentData.put("generatedDate", LocalDate.now());

        createDocumentMetadata(documentData);

        log.info("Payment schedule generated successfully - DocumentID: {}", documentId);
        return documentId;
    }

    /**
     * Generate promissory note
     *
     * Creates a legal promissory note - borrower's promise to repay
     *
     * @param loanId Loan identifier
     * @param borrowerId Borrower identifier
     * @param amount Principal amount
     * @param interestRate Interest rate
     * @param termMonths Term in months
     * @param maturityDate Final payment date
     * @return Document ID for the promissory note
     */
    public String generatePromissoryNote(
            String loanId,
            UUID borrowerId,
            BigDecimal amount,
            BigDecimal interestRate,
            Integer termMonths,
            LocalDate maturityDate) {

        String documentId = generateDocumentId("PROMISSORY_NOTE");

        log.info("Generating promissory note - DocumentID: {}, LoanID: {}, Amount: {}",
                documentId, loanId, amount);

        Map<String, Object> documentData = new HashMap<>();
        documentData.put("documentId", documentId);
        documentData.put("documentType", "PROMISSORY_NOTE");
        documentData.put("loanId", loanId);
        documentData.put("borrowerId", borrowerId);
        documentData.put("principalAmount", amount);
        documentData.put("interestRate", interestRate);
        documentData.put("termMonths", termMonths);
        documentData.put("maturityDate", maturityDate);
        documentData.put("issueDate", LocalDate.now());
        documentData.put("generatedDate", LocalDate.now());

        createDocumentMetadata(documentData);

        log.info("Promissory note generated successfully - DocumentID: {}", documentId);
        return documentId;
    }

    /**
     * Generate loan disclosure statement
     *
     * Creates comprehensive disclosure statement with all loan terms
     *
     * @param loanId Loan identifier
     * @param loanData Map containing all loan data
     * @return Document ID for the disclosure statement
     */
    public String generateDisclosureStatement(String loanId, Map<String, Object> loanData) {
        String documentId = generateDocumentId("DISCLOSURE_STATEMENT");

        log.info("Generating disclosure statement - DocumentID: {}, LoanID: {}", documentId, loanId);

        Map<String, Object> documentData = new HashMap<>(loanData);
        documentData.put("documentId", documentId);
        documentData.put("documentType", "DISCLOSURE_STATEMENT");
        documentData.put("loanId", loanId);
        documentData.put("generatedDate", LocalDate.now());

        createDocumentMetadata(documentData);

        log.info("Disclosure statement generated successfully - DocumentID: {}", documentId);
        return documentId;
    }

    /**
     * Generate account opening disclosure
     *
     * Creates disclosure for new loan account
     *
     * @param loanId Loan identifier
     * @param accountNumber Account number
     * @param borrowerId Borrower identifier
     * @return Document ID for account opening disclosure
     */
    public String generateAccountOpeningDisclosure(
            String loanId,
            String accountNumber,
            UUID borrowerId) {

        String documentId = generateDocumentId("ACCOUNT_OPENING");

        log.info("Generating account opening disclosure - DocumentID: {}, LoanID: {}, Account: {}",
                documentId, loanId, accountNumber);

        Map<String, Object> documentData = new HashMap<>();
        documentData.put("documentId", documentId);
        documentData.put("documentType", "ACCOUNT_OPENING_DISCLOSURE");
        documentData.put("loanId", loanId);
        documentData.put("accountNumber", accountNumber);
        documentData.put("borrowerId", borrowerId);
        documentData.put("openingDate", LocalDate.now());
        documentData.put("generatedDate", LocalDate.now());

        createDocumentMetadata(documentData);

        log.info("Account opening disclosure generated successfully - DocumentID: {}", documentId);
        return documentId;
    }

    /**
     * Generate monthly statement
     *
     * Creates monthly loan statement for borrower
     *
     * @param loanId Loan identifier
     * @param statementDate Statement date
     * @param currentBalance Current balance
     * @param paymentDue Payment due
     * @param dueDate Due date
     * @return Document ID for the statement
     */
    public String generateMonthlyStatement(
            String loanId,
            LocalDate statementDate,
            BigDecimal currentBalance,
            BigDecimal paymentDue,
            LocalDate dueDate) {

        String documentId = generateDocumentId("MONTHLY_STATEMENT");

        log.info("Generating monthly statement - DocumentID: {}, LoanID: {}, Balance: {}",
                documentId, loanId, currentBalance);

        Map<String, Object> documentData = new HashMap<>();
        documentData.put("documentId", documentId);
        documentData.put("documentType", "MONTHLY_STATEMENT");
        documentData.put("loanId", loanId);
        documentData.put("statementDate", statementDate);
        documentData.put("currentBalance", currentBalance);
        documentData.put("paymentDue", paymentDue);
        documentData.put("dueDate", dueDate);
        documentData.put("generatedDate", LocalDate.now());

        createDocumentMetadata(documentData);

        log.info("Monthly statement generated successfully - DocumentID: {}", documentId);
        return documentId;
    }

    /**
     * Generate document ID with prefix
     */
    private String generateDocumentId(String documentType) {
        return documentType + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Create document metadata record
     *
     * In a production system, this would store document metadata in a database
     * and potentially trigger document rendering via message queue
     */
    private void createDocumentMetadata(Map<String, Object> documentData) {
        // TODO: Store document metadata in database
        // TODO: Publish event to document rendering queue
        // TODO: Store document template reference
        // TODO: Track document versioning

        log.debug("Document metadata created: {}", documentData.get("documentId"));
    }

    /**
     * Generate TILA disclosure text
     */
    private String generateTilaDisclosureText(
            BigDecimal apr,
            BigDecimal financeCharge,
            BigDecimal totalOfPayments) {

        return String.format(
                "FEDERAL TRUTH IN LENDING DISCLOSURE\n\n" +
                "ANNUAL PERCENTAGE RATE: %.2f%%\n" +
                "The cost of your credit as a yearly rate.\n\n" +
                "FINANCE CHARGE: $%,.2f\n" +
                "The dollar amount the credit will cost you.\n\n" +
                "TOTAL OF PAYMENTS: $%,.2f\n" +
                "The amount you will have paid after you have made all payments as scheduled.\n\n" +
                "You have the right to receive at this time an itemization of the Amount Financed.\n" +
                "[ ] I want an itemization. [ ] I do not want an itemization.\n\n" +
                "Your payment schedule will be:\n" +
                "See attached payment schedule for complete details.\n\n" +
                "Security: You are giving a security interest in the goods or property being purchased.\n\n" +
                "Late Charge: If a payment is late, you will be charged as disclosed in your loan agreement.\n\n" +
                "Prepayment: If you pay off early, you may be entitled to a refund of part of the finance charge.\n\n" +
                "See your contract documents for any additional information about nonpayment, default, " +
                "any required repayment in full before the scheduled date, and prepayment refunds and penalties.",
                apr, financeCharge, totalOfPayments
        );
    }
}

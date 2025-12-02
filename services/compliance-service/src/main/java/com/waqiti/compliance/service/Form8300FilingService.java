package com.waqiti.compliance.service;

import com.waqiti.compliance.config.InstitutionProperties;
import com.waqiti.compliance.model.Form8300Filing;
import com.waqiti.compliance.model.Form8300Document;
import com.waqiti.compliance.model.TransactionDetails;
import com.waqiti.compliance.repository.Form8300Repository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for handling IRS Form 8300 filings for cash transactions over $10,000
 *
 * LEGAL REQUIREMENT: Internal Revenue Code Section 6050I requires businesses to file
 * Form 8300 within 15 days of receiving more than $10,000 in cash in one transaction
 * or two or more related transactions.
 *
 * PENALTIES FOR NON-COMPLIANCE:
 * - Civil: $290 per form (adjusted for inflation)
 * - Criminal: Up to $250,000 fine and/or 5 years imprisonment for willful failure
 *
 * CRITICAL THRESHOLDS:
 * - Single transaction: $10,000.01 or more
 * - Related transactions: Aggregate $10,000.01 within 24 hours
 * - International wire transfers: $10,000.01 or more (includes Form 8300 + CTR requirements)
 *
 * @author Waqiti Compliance Team
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class Form8300FilingService {

    private final Form8300Repository form8300Repository;
    private final InstitutionProperties institutionProperties;
    private final IRSFilingService irsFilingService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    // Form 8300 Thresholds
    private static final BigDecimal FORM_8300_THRESHOLD = new BigDecimal("10000.00");
    private static final int FILING_DEADLINE_DAYS = 15;
    private static final int AGGREGATION_WINDOW_HOURS = 24;

    /**
     * Evaluates whether a transaction requires Form 8300 filing
     *
     * @param transactionId Unique transaction identifier
     * @param amount Transaction amount
     * @param currency Transaction currency (must be USD for Form 8300)
     * @param fromCountry Originating country
     * @param toCountry Destination country
     * @param customerId Customer identifier
     * @return true if Form 8300 filing is required
     */
    public boolean requiresForm8300Filing(
            UUID transactionId,
            BigDecimal amount,
            String currency,
            String fromCountry,
            String toCountry,
            UUID customerId) {

        // Form 8300 applies only to USD transactions
        if (!"USD".equals(currency)) {
            log.debug("Transaction {} does not require Form 8300 (currency: {})", transactionId, currency);
            return false;
        }

        // Check if transaction crosses the threshold
        if (amount.compareTo(FORM_8300_THRESHOLD) <= 0) {
            log.debug("Transaction {} does not require Form 8300 (amount {} below threshold {})",
                transactionId, amount, FORM_8300_THRESHOLD);
            return false;
        }

        // International transactions over $10K require Form 8300
        boolean isInternational = !fromCountry.equals(toCountry) ||
                                  !"US".equals(fromCountry) ||
                                  !"US".equals(toCountry);

        if (isInternational) {
            log.info("COMPLIANCE: Transaction {} requires Form 8300 filing - International transfer of ${} USD",
                transactionId, amount);
            return true;
        }

        // Domestic cash transactions over $10K also require Form 8300
        log.info("COMPLIANCE: Transaction {} requires Form 8300 filing - Cash transaction of ${} USD",
            transactionId, amount);
        return true;
    }

    /**
     * Initiates Form 8300 filing for a qualifying transaction
     *
     * @param transactionDetails Details of the transaction requiring filing
     * @return Form8300Filing entity with filing status
     */
    @Transactional
    public Form8300Filing initiateForm8300Filing(TransactionDetails transactionDetails) {
        log.info("Initiating Form 8300 filing for transaction: {}", transactionDetails.getTransactionId());

        // Validate institution configuration
        if (!institutionProperties.isFullyConfigured()) {
            throw new IllegalStateException(
                "Institution information is not fully configured. Form 8300 filing cannot proceed. " +
                "Please configure all required institution details in production environment."
            );
        }

        // Create Form 8300 filing record
        Form8300Filing filing = Form8300Filing.builder()
            .id(UUID.randomUUID())
            .transactionId(transactionDetails.getTransactionId())
            .customerId(transactionDetails.getCustomerId())
            .amount(transactionDetails.getAmount())
            .currency(transactionDetails.getCurrency())
            .transactionDate(transactionDetails.getTransactionDate())
            .fromCountry(transactionDetails.getFromCountry())
            .toCountry(transactionDetails.getToCountry())
            .filingDeadline(calculateFilingDeadline(transactionDetails.getTransactionDate()))
            .filingStatus("PENDING")
            .createdAt(LocalDateTime.now())
            .build();

        // Save filing record
        filing = form8300Repository.save(filing);

        // Audit log
        auditService.logForm8300Initiated(filing.getId(), transactionDetails);

        // Alert compliance team
        notificationService.sendComplianceAlert(
            "Form 8300 Filing Required",
            String.format("Transaction %s requires Form 8300 filing. Amount: $%s USD. Deadline: %s",
                transactionDetails.getTransactionId(),
                transactionDetails.getAmount(),
                filing.getFilingDeadline())
        );

        log.info("Form 8300 filing initiated: {} for transaction: {}. Deadline: {}",
            filing.getId(), transactionDetails.getTransactionId(), filing.getFilingDeadline());

        return filing;
    }

    /**
     * Generates Form 8300 document with all required information
     *
     * @param filing Form 8300 filing entity
     * @return Completed Form 8300 document ready for submission
     */
    @Transactional
    public Form8300Document generateForm8300Document(Form8300Filing filing) {
        log.info("Generating Form 8300 document for filing: {}", filing.getId());

        Form8300Document document = new Form8300Document();

        // Part I - Identity of Individual From Whom Cash Was Received
        document.setCustomerName(filing.getCustomerName());
        document.setCustomerTIN(filing.getCustomerTIN());  // Tax Identification Number
        document.setCustomerAddress(filing.getCustomerAddress());
        document.setCustomerDOB(filing.getCustomerDOB());
        document.setCustomerOccupation(filing.getCustomerOccupation());

        // Part II - Person on Whose Behalf Transaction Was Conducted (if applicable)
        if (filing.getBeneficiaryName() != null) {
            document.setBeneficiaryName(filing.getBeneficiaryName());
            document.setBeneficiaryTIN(filing.getBeneficiaryTIN());
            document.setBeneficiaryAddress(filing.getBeneficiaryAddress());
        }

        // Part III - Description of Transaction and Method of Payment
        document.setTransactionDate(filing.getTransactionDate());
        document.setTransactionAmount(filing.getAmount());
        document.setTransactionType(determineTransactionType(filing));
        document.setTransactionDescription(buildTransactionDescription(filing));
        document.setPaymentMethod("WIRE_TRANSFER");

        // Part IV - Business Reporting This Transaction
        document.setBusinessName(institutionProperties.getName());
        document.setBusinessEIN(institutionProperties.getEin());
        document.setBusinessAddress(institutionProperties.getAddress().getFormattedAddress());
        document.setBusinessNature("Money Services Business - International Wire Transfers");

        // Contact information
        document.setContactName(institutionProperties.getContact().getComplianceOfficerName());
        document.setContactTitle(institutionProperties.getContact().getComplianceOfficerTitle());
        document.setContactPhone(institutionProperties.getContact().getComplianceOfficerPhone());
        document.setContactEmail(institutionProperties.getContact().getComplianceOfficerEmail());

        // Set document metadata
        document.setFilingId(filing.getId());
        document.setGeneratedAt(LocalDateTime.now());
        document.setFilingDeadline(filing.getFilingDeadline());

        log.info("Form 8300 document generated successfully for filing: {}", filing.getId());

        return document;
    }

    /**
     * Submits Form 8300 to IRS via BSA E-Filing System
     *
     * @param filing Form 8300 filing entity
     * @return true if submission successful
     */
    @Transactional
    public boolean submitForm8300ToIRS(Form8300Filing filing) {
        log.info("Submitting Form 8300 to IRS: {}", filing.getId());

        try {
            // Generate document
            Form8300Document document = generateForm8300Document(filing);

            // Validate document completeness
            validateForm8300Document(document);

            // Submit to IRS via BSA E-Filing System
            String confirmationNumber = irsFilingService.submitForm8300(document);

            // Update filing status
            filing.setFilingStatus("SUBMITTED");
            filing.setSubmittedAt(LocalDateTime.now());
            filing.setConfirmationNumber(confirmationNumber);
            form8300Repository.save(filing);

            // Audit log
            auditService.logForm8300Submitted(filing.getId(), confirmationNumber);

            // Notify compliance team
            notificationService.sendComplianceAlert(
                "Form 8300 Filed Successfully",
                String.format("Form 8300 filed for transaction %s. Confirmation: %s",
                    filing.getTransactionId(), confirmationNumber)
            );

            log.info("Form 8300 submitted successfully: {}. Confirmation: {}", filing.getId(), confirmationNumber);
            return true;

        } catch (Exception e) {
            log.error("Failed to submit Form 8300: {}", filing.getId(), e);

            // Update filing status to error
            filing.setFilingStatus("ERROR");
            filing.setErrorMessage(e.getMessage());
            form8300Repository.save(filing);

            // Alert compliance team of failure
            notificationService.sendComplianceAlert(
                "Form 8300 Filing Failed",
                String.format("Failed to file Form 8300 for transaction %s. Error: %s. " +
                    "Manual intervention required immediately.",
                    filing.getTransactionId(), e.getMessage())
            );

            throw new RuntimeException("Form 8300 filing failed", e);
        }
    }

    /**
     * Calculates the filing deadline (15 days after transaction)
     */
    private LocalDate calculateFilingDeadline(LocalDateTime transactionDate) {
        return transactionDate.toLocalDate().plusDays(FILING_DEADLINE_DAYS);
    }

    /**
     * Determines the transaction type for Form 8300
     */
    private String determineTransactionType(Form8300Filing filing) {
        if (!filing.getFromCountry().equals(filing.getToCountry())) {
            return "INTERNATIONAL_WIRE_TRANSFER";
        }
        return "DOMESTIC_WIRE_TRANSFER";
    }

    /**
     * Builds detailed transaction description
     */
    private String buildTransactionDescription(Form8300Filing filing) {
        return String.format(
            "International wire transfer from %s to %s in the amount of $%s USD. " +
            "Transaction ID: %s. Date: %s.",
            filing.getFromCountry(),
            filing.getToCountry(),
            filing.getAmount(),
            filing.getTransactionId(),
            filing.getTransactionDate()
        );
    }

    /**
     * Validates that Form 8300 document has all required fields
     */
    private void validateForm8300Document(Form8300Document document) {
        if (document.getCustomerName() == null || document.getCustomerName().isEmpty()) {
            throw new IllegalArgumentException("Customer name is required for Form 8300");
        }
        if (document.getCustomerTIN() == null || document.getCustomerTIN().isEmpty()) {
            throw new IllegalArgumentException("Customer TIN is required for Form 8300");
        }
        if (document.getBusinessEIN() == null || document.getBusinessEIN().isEmpty()) {
            throw new IllegalArgumentException("Business EIN is required for Form 8300");
        }
        if (document.getTransactionAmount().compareTo(FORM_8300_THRESHOLD) <= 0) {
            throw new IllegalArgumentException("Transaction amount must exceed $10,000 for Form 8300");
        }

        log.debug("Form 8300 document validation passed for filing: {}", document.getFilingId());
    }

    /**
     * Retrieves all pending Form 8300 filings approaching deadline
     *
     * @param daysBeforeDeadline Number of days before deadline to consider (e.g., 3 days)
     * @return List of filings approaching deadline
     */
    public java.util.List<Form8300Filing> getPendingFilingsNearDeadline(int daysBeforeDeadline) {
        LocalDate cutoffDate = LocalDate.now().plusDays(daysBeforeDeadline);
        return form8300Repository.findByFilingStatusAndFilingDeadlineBefore("PENDING", cutoffDate);
    }
}

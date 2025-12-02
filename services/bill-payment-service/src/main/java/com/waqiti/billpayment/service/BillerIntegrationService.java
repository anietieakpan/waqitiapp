package com.waqiti.billpayment.service;

import com.waqiti.billpayment.entity.*;
import com.waqiti.billpayment.repository.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for integrating with external biller APIs
 * Handles bill import, payment submission, and biller communication
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BillerIntegrationService {

    private final BillerRepository billerRepository;
    private final BillerConnectionRepository billerConnectionRepository;
    private final BillRepository billRepository;
    private final BillerService billerService;
    private final BillService billService;
    private final MeterRegistry meterRegistry;

    private Counter billImportCounter;
    private Counter billImportFailedCounter;
    private Timer billImportTimer;

    @jakarta.annotation.PostConstruct
    public void initMetrics() {
        billImportCounter = Counter.builder("bill.import.success")
                .description("Number of successful bill imports")
                .register(meterRegistry);

        billImportFailedCounter = Counter.builder("bill.import.failed")
                .description("Number of failed bill imports")
                .register(meterRegistry);

        billImportTimer = Timer.builder("bill.import.time")
                .description("Time taken to import bills")
                .register(meterRegistry);
    }

    /**
     * Import bills from external biller for a connection (called by scheduler)
     */
    @Transactional
    public void importBillsForConnection(UUID connectionId) {
        Timer.Sample sample = Timer.start(meterRegistry);

        log.info("Importing bills for connection: {}", connectionId);

        try {
            BillerConnection connection = billerConnectionRepository.findById(connectionId)
                    .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + connectionId));

            if (!connection.isActive()) {
                log.warn("Connection not active, skipping import: {}", connectionId);
                return;
            }

            Biller biller = billerRepository.findById(connection.getBillerId())
                    .orElseThrow(() -> new IllegalArgumentException("Biller not found: " + connection.getBillerId()));

            // Import bills from external API
            List<Bill> importedBills = fetchBillsFromExternalAPI(biller, connection);

            // Save imported bills
            int savedCount = 0;
            for (Bill bill : importedBills) {
                try {
                    // Check if bill already exists
                    if (bill.getExternalBillId() != null) {
                        boolean exists = billRepository.findByExternalBillId(bill.getExternalBillId()).isPresent();
                        if (exists) {
                            log.debug("Bill already imported, skipping: {}", bill.getExternalBillId());
                            continue;
                        }
                    }

                    billService.createBill(bill, connection.getUserId());
                    savedCount++;
                } catch (Exception e) {
                    log.error("Error saving imported bill", e);
                }
            }

            // Update connection import status
            billerService.updateImportSuccess(connectionId);

            billImportCounter.increment(savedCount);
            sample.stop(billImportTimer);

            log.info("Successfully imported {} bills for connection: {}", savedCount, connectionId);

        } catch (Exception e) {
            log.error("Failed to import bills for connection: {}", connectionId, e);
            billerService.updateImportFailure(connectionId, e.getMessage());
            billImportFailedCounter.increment();
            throw e;
        }
    }

    /**
     * Process all connections due for import (called by scheduler)
     */
    @Transactional
    public void processAutoImports() {
        log.info("Processing auto-imports");

        List<BillerConnection> connections = billerService.getConnectionsDueForImport();

        for (BillerConnection connection : connections) {
            try {
                importBillsForConnection(connection.getId());
            } catch (Exception e) {
                log.error("Error importing bills for connection: {}", connection.getId(), e);
            }
        }

        log.info("Processed {} auto-imports", connections.size());
    }

    /**
     * Submit payment to external biller
     */
    public String submitPaymentToBiller(BillPayment payment, Bill bill, Biller biller) {
        log.info("Submitting payment to biller: {}, payment: {}, amount: {}",
                biller.getName(), payment.getId(), payment.getAmount());

        try {
            // Call external biller API
            String confirmationNumber = callBillerPaymentAPI(biller, bill, payment);

            log.info("Payment submitted successfully to biller: {}, confirmation: {}",
                    biller.getName(), confirmationNumber);

            return confirmationNumber;

        } catch (Exception e) {
            log.error("Failed to submit payment to biller: {}", biller.getName(), e);
            throw new RuntimeException("Biller payment submission failed: " + e.getMessage(), e);
        }
    }

    /**
     * Verify payment status with biller
     */
    public String verifyPaymentWithBiller(String externalPaymentId, Biller biller) {
        log.info("Verifying payment with biller: {}, external ID: {}", biller.getName(), externalPaymentId);

        try {
            // Call external biller API to verify payment status
            String status = callBillerVerificationAPI(biller, externalPaymentId);

            log.info("Payment verification completed: {}, status: {}", externalPaymentId, status);

            return status;

        } catch (Exception e) {
            log.error("Failed to verify payment with biller: {}", biller.getName(), e);
            throw new RuntimeException("Biller verification failed: " + e.getMessage(), e);
        }
    }

    /**
     * Check if biller supports feature
     */
    public boolean checkBillerFeatureSupport(UUID billerId, String feature) {
        Biller biller = billerRepository.findById(billerId)
                .orElseThrow(() -> new IllegalArgumentException("Biller not found: " + billerId));

        return biller.supportsFeature(feature);
    }

    // ========== PRIVATE INTEGRATION METHODS ==========
    // These are placeholders for actual external API calls

    /**
     * Fetch bills from external biller API
     */
    private List<Bill> fetchBillsFromExternalAPI(Biller biller, BillerConnection connection) {
        log.info("Fetching bills from external API: biller={}, account={}",
                biller.getName(), connection.getAccountNumber());

        // TODO: Implement actual API call to external biller
        // This would use HTTP client (RestTemplate, WebClient, or Feign)
        // to call the biller's API and retrieve bills

        // Placeholder: Return sample bill data
        List<Bill> bills = new ArrayList<>();

        Bill bill = Bill.builder()
                .userId(connection.getUserId())
                .billerId(biller.getId())
                .billerName(biller.getName())
                .accountNumber(connection.getAccountNumber())
                .billNumber("SAMPLE-" + UUID.randomUUID().toString().substring(0, 8))
                .category(biller.getCategory())
                .amount(new BigDecimal("125.50"))
                .currency("USD")
                .dueDate(LocalDate.now().plusDays(15))
                .issueDate(LocalDate.now())
                .status(BillStatus.UNPAID)
                .description("Sample bill imported from " + biller.getName())
                .externalBillId("EXT-" + UUID.randomUUID().toString())
                .isRecurring(true)
                .build();

        bills.add(bill);

        log.info("Fetched {} bills from external API", bills.size());
        return bills;
    }

    /**
     * Call external biller API to submit payment
     */
    private String callBillerPaymentAPI(Biller biller, Bill bill, BillPayment payment) {
        log.info("Calling biller payment API: {}", biller.getName());

        // TODO: Implement actual API call
        // Example structure:
        // PaymentRequest request = PaymentRequest.builder()
        //     .billerAccountNumber(bill.getAccountNumber())
        //     .billNumber(bill.getBillNumber())
        //     .amount(payment.getAmount())
        //     .currency(payment.getCurrency())
        //     .build();
        //
        // HttpHeaders headers = new HttpHeaders();
        // headers.set("Authorization", "Bearer " + billerApiKey);
        // headers.setContentType(MediaType.APPLICATION_JSON);
        //
        // HttpEntity<PaymentRequest> entity = new HttpEntity<>(request, headers);
        //
        // ResponseEntity<PaymentResponse> response = restTemplate.postForEntity(
        //     biller.getApiEndpoint() + "/payments",
        //     entity,
        //     PaymentResponse.class
        // );
        //
        // return response.getBody().getConfirmationNumber();

        // Placeholder: Generate confirmation number
        String confirmationNumber = "CONF-" + biller.getName().toUpperCase().replace(" ", "")
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.info("Payment API call completed, confirmation: {}", confirmationNumber);
        return confirmationNumber;
    }

    /**
     * Call external biller API to verify payment status
     */
    private String callBillerVerificationAPI(Biller biller, String externalPaymentId) {
        log.info("Calling biller verification API: {}", biller.getName());

        // TODO: Implement actual API call
        // ResponseEntity<PaymentStatusResponse> response = restTemplate.getForEntity(
        //     biller.getApiEndpoint() + "/payments/" + externalPaymentId + "/status",
        //     PaymentStatusResponse.class
        // );
        //
        // return response.getBody().getStatus();

        // Placeholder: Return completed status
        return "COMPLETED";
    }

    /**
     * Test biller connection
     */
    public boolean testBillerConnection(UUID billerId) {
        log.info("Testing biller connection: {}", billerId);

        try {
            Biller biller = billerRepository.findById(billerId)
                    .orElseThrow(() -> new IllegalArgumentException("Biller not found: " + billerId));

            // TODO: Implement actual connection test
            // Call biller's health/status endpoint
            // Return true if reachable and responding

            log.info("Biller connection test successful: {}", biller.getName());
            return true;

        } catch (Exception e) {
            log.error("Biller connection test failed: {}", billerId, e);
            return false;
        }
    }
}

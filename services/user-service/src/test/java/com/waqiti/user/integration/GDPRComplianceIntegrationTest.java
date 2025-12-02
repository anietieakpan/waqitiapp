package com.waqiti.user.integration;

import com.waqiti.user.dto.*;
import com.waqiti.user.entity.GDPRRequest;
import com.waqiti.user.entity.ConsentRecord;
import com.waqiti.user.repository.GDPRRequestRepository;
import com.waqiti.user.repository.ConsentRecordRepository;
import com.waqiti.user.service.GDPRService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * GDPR Compliance Integration Tests
 *
 * COMPLIANCE VERIFICATION:
 * - GDPR Article 15 (Right to Access)
 * - GDPR Article 17 (Right to Erasure)
 * - GDPR Article 20 (Right to Data Portability)
 * - GDPR Article 21 (Right to Object)
 * - GDPR Article 7 (Consent)
 *
 * SCENARIOS TESTED:
 * 1. Data export request and generation
 * 2. Data erasure request and execution
 * 3. Consent management
 * 4. Data access history
 * 5. 30-day processing timeline
 * 6. Data portability formats (JSON, CSV, PDF)
 * 7. Consent withdrawal
 * 8. Automated erasure after 30 days
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
@DisplayName("GDPR Compliance Integration Tests")
class GDPRComplianceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GDPRService gdprService;

    @Autowired
    private GDPRRequestRepository gdprRequestRepository;

    @Autowired
    private ConsentRecordRepository consentRecordRepository;

    @Test
    @DisplayName("GDPR Article 15: Request data export in JSON format")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testDataExportRequest_JSON() throws Exception {
        // ARRANGE
        UUID userId = createTestUser("John Doe", "john@example.com");
        createTestData(userId); // Create transactions, payments, etc.

        DataExportRequest request = DataExportRequest.builder()
                .format("JSON")
                .includeTransactions(true)
                .includePayments(true)
                .includeDocuments(true)
                .build();

        // ACT & ASSERT
        mockMvc.perform(post("/api/v1/gdpr/export-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.estimatedCompletionTime").exists())
                .andExpect(jsonPath("$.format").value("JSON"));

        // Verify request was stored
        var requests = gdprRequestRepository.findByUserIdAndType(userId, "EXPORT");
        assertThat(requests).isNotEmpty();
        assertThat(requests.get(0).getStatus()).isEqualTo("PENDING");

        // Wait for background processing
        await()
                .atMost(30, SECONDS)
                .until(() -> {
                    var req = gdprRequestRepository.findById(requests.get(0).getId());
                    return req.isPresent() && "COMPLETED".equals(req.get().getStatus());
                });

        // Verify export file was generated
        var completedRequest = gdprRequestRepository.findById(requests.get(0).getId()).get();
        assertThat(completedRequest.getExportFileUrl()).isNotNull();
        assertThat(completedRequest.getExportFileUrl()).contains(".json");
        assertThat(completedRequest.getCompletedAt()).isNotNull();
        assertThat(completedRequest.getCompletedAt()).isAfter(completedRequest.getRequestedAt());
    }

    @Test
    @DisplayName("GDPR Article 15: Data export includes all user data categories")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testDataExport_Completeness() throws Exception {
        // ARRANGE
        UUID userId = createTestUser("Jane Smith", "jane@example.com");
        createComprehensiveTestData(userId);

        // Request export
        var exportRequest = gdprService.requestDataExport(userId, "JSON");

        // Wait for completion
        await().atMost(30, SECONDS).until(() ->
                gdprService.getRequestStatus(exportRequest.getRequestId()).equals("COMPLETED")
        );

        // ACT - Download export file
        var exportData = gdprService.getExportData(exportRequest.getRequestId());

        // ASSERT - Verify all data categories present
        assertThat(exportData).isNotNull();
        assertThat(exportData).containsKey("user");
        assertThat(exportData).containsKey("accounts");
        assertThat(exportData).containsKey("transactions");
        assertThat(exportData).containsKey("payments");
        assertThat(exportData).containsKey("paymentMethods");
        assertThat(exportData).containsKey("savedRecipients");
        assertThat(exportData).containsKey("preferences");
        assertThat(exportData).containsKey("documents");
        assertThat(exportData).containsKey("auditLogs");
        assertThat(exportData).containsKey("consents");

        // Verify transaction data
        var transactions = (java.util.List<?>) exportData.get("transactions");
        assertThat(transactions).isNotEmpty();
    }

    @Test
    @DisplayName("GDPR Article 17: Request data erasure")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testDataErasureRequest() throws Exception {
        // ARRANGE
        UUID userId = createTestUser("Bob Wilson", "bob@example.com");
        createTestData(userId);

        DataErasureRequest request = DataErasureRequest.builder()
                .reason("No longer using service")
                .confirmationText("DELETE")
                .build();

        // ACT & ASSERT
        mockMvc.perform(post("/api/v1/gdpr/erasure-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.processingDeadline").exists())
                .andExpect(jsonPath("$.estimatedCompletionDays").value(30));

        // Verify request was stored
        var requests = gdprRequestRepository.findByUserIdAndType(userId, "ERASURE");
        assertThat(requests).isNotEmpty();
        assertThat(requests.get(0).getStatus()).isEqualTo("PENDING");
        assertThat(requests.get(0).getReason()).isEqualTo("No longer using service");

        // Verify account marked for deletion
        var user = userService.getUser(userId);
        assertThat(user.getAccountStatus()).isEqualTo("DELETION_PENDING");
        assertThat(user.getDeletionScheduledAt()).isNotNull();
    }

    @Test
    @DisplayName("GDPR Article 17: Data erasure completes within 30 days")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testDataErasure_30DayCompliance() throws Exception {
        // ARRANGE
        UUID userId = createTestUser("Charlie Davis", "charlie@example.com");
        var erasureRequest = gdprService.requestDataErasure(userId, "Account closure");

        // Simulate 30 days passing and scheduler running
        gdprService.processScheduledErasures();

        // ASSERT
        var request = gdprRequestRepository.findById(erasureRequest.getRequestId()).get();
        assertThat(request.getStatus()).isIn("COMPLETED", "PROCESSING");

        // Verify data was actually erased
        await().atMost(30, SECONDS).until(() -> {
            var req = gdprRequestRepository.findById(erasureRequest.getRequestId());
            return req.isPresent() && "COMPLETED".equals(req.get().getStatus());
        });

        // Verify user data deleted
        var userExists = userService.userExists(userId);
        assertThat(userExists).isFalse();

        // Verify transactions anonymized
        var transactions = transactionService.getTransactionsByUserId(userId);
        assertThat(transactions).isEmpty();
    }

    @Test
    @DisplayName("GDPR Article 7: Grant consent")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testGrantConsent() throws Exception {
        // ARRANGE
        UUID userId = getCurrentUserId();

        ConsentRequest request = ConsentRequest.builder()
                .consentId("marketing-communications")
                .granted(true)
                .build();

        // ACT & ASSERT
        mockMvc.perform(post("/api/v1/gdpr/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentId").value("marketing-communications"))
                .andExpect(jsonPath("$.granted").value(true))
                .andExpect(jsonPath("$.grantedAt").exists());

        // Verify consent record created
        var consents = consentRecordRepository.findByUserId(userId);
        assertThat(consents).isNotEmpty();
        assertThat(consents.stream()
                .anyMatch(c -> c.getConsentId().equals("marketing-communications") && c.isGranted()))
                .isTrue();
    }

    @Test
    @DisplayName("GDPR Article 7: Withdraw consent")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testWithdrawConsent() throws Exception {
        // ARRANGE
        UUID userId = getCurrentUserId();

        // First grant consent
        gdprService.grantConsent(userId, "analytics", true);

        // ACT - Withdraw consent
        ConsentRequest request = ConsentRequest.builder()
                .consentId("analytics")
                .granted(false)
                .build();

        mockMvc.perform(post("/api/v1/gdpr/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granted").value(false))
                .andExpect(jsonPath("$.withdrawnAt").exists());

        // Verify consent withdrawn
        var consent = consentRecordRepository.findByUserIdAndConsentId(userId, "analytics");
        assertThat(consent).isPresent();
        assertThat(consent.get().isGranted()).isFalse();
        assertThat(consent.get().getWithdrawnAt()).isNotNull();

        // Verify analytics processing stopped
        var analyticsEnabled = gdprService.isConsentGranted(userId, "analytics");
        assertThat(analyticsEnabled).isFalse();
    }

    @Test
    @DisplayName("GDPR Article 15: Get consent history")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testGetConsentHistory() throws Exception {
        // ARRANGE
        UUID userId = getCurrentUserId();

        // Create consent history
        gdprService.grantConsent(userId, "marketing", true);
        Thread.sleep(1000);
        gdprService.grantConsent(userId, "analytics", true);
        Thread.sleep(1000);
        gdprService.grantConsent(userId, "analytics", false); // Withdraw

        // ACT & ASSERT
        mockMvc.perform(get("/api/v1/gdpr/consent-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consents").isArray())
                .andExpect(jsonPath("$.consents.length()").value(3))
                .andExpect(jsonPath("$.consents[0].consentId").exists())
                .andExpect(jsonPath("$.consents[0].granted").exists())
                .andExpect(jsonPath("$.consents[0].timestamp").exists());
    }

    @Test
    @DisplayName("GDPR Article 20: Data portability - Multiple formats")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testDataPortability_MultipleFormats() throws Exception {
        // ARRANGE
        UUID userId = createTestUser("Emma Brown", "emma@example.com");
        createTestData(userId);

        // ACT & ASSERT - Request in different formats
        String[] formats = {"JSON", "CSV", "PDF"};

        for (String format : formats) {
            DataExportRequest request = DataExportRequest.builder()
                    .format(format)
                    .includeTransactions(true)
                    .build();

            mockMvc.perform(post("/api/v1/gdpr/export-request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.format").value(format));
        }

        // Verify all requests created
        var requests = gdprRequestRepository.findByUserIdAndType(userId, "EXPORT");
        assertThat(requests).hasSize(3);
        assertThat(requests.stream()
                .map(GDPRRequest::getFormat)
                .toList())
                .containsExactlyInAnyOrder("JSON", "CSV", "PDF");
    }

    @Test
    @DisplayName("GDPR: Cannot request erasure with pending transactions")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testDataErasure_BlockedByPendingTransactions() throws Exception {
        // ARRANGE
        UUID userId = createTestUser("Frank Miller", "frank@example.com");
        createPendingTransaction(userId); // Create pending transaction

        DataErasureRequest request = DataErasureRequest.builder()
                .reason("Close account")
                .confirmationText("DELETE")
                .build();

        // ACT & ASSERT
        mockMvc.perform(post("/api/v1/gdpr/erasure-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Cannot delete account with pending transactions"))
                .andExpect(jsonPath("$.pendingTransactionCount").value(1));
    }

    @Test
    @DisplayName("GDPR: Required consents cannot be withdrawn")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testWithdrawConsent_RequiredConsent() throws Exception {
        // ARRANGE - Essential service consent
        ConsentRequest request = ConsentRequest.builder()
                .consentId("essential-services")
                .granted(false)
                .build();

        // ACT & ASSERT
        mockMvc.perform(post("/api/v1/gdpr/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cannot withdraw required consent"))
                .andExpect(jsonPath("$.consentId").value("essential-services"));
    }

    @Test
    @DisplayName("GDPR: Data access audit trail")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testDataAccessAuditTrail() throws Exception {
        // ARRANGE
        UUID userId = getCurrentUserId();

        // Simulate various data access events
        gdprService.logDataAccess(userId, "PROFILE_VIEW", "User viewed own profile");
        gdprService.logDataAccess(userId, "TRANSACTION_EXPORT", "User exported transactions");
        gdprService.logDataAccess(userId, "ADMIN_REVIEW", "Admin reviewed account");

        // ACT & ASSERT
        mockMvc.perform(get("/api/v1/gdpr/access-log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessLogs").isArray())
                .andExpect(jsonPath("$.accessLogs.length()").value(3))
                .andExpect(jsonPath("$.accessLogs[0].accessType").exists())
                .andExpect(jsonPath("$.accessLogs[0].timestamp").exists())
                .andExpect(jsonPath("$.accessLogs[0].purpose").exists());
    }

    // Helper methods

    private void createTestData(UUID userId) {
        // Create sample transactions, payments, etc.
        createTestTransaction(userId, new java.math.BigDecimal("100.00"));
        createTestPayment(userId, new java.math.BigDecimal("50.00"));
    }

    private void createComprehensiveTestData(UUID userId) {
        createTestData(userId);
        // Add more comprehensive data
        createTestPaymentMethod(userId);
        createTestDocument(userId);
        createTestPreferences(userId);
    }

    private void createPendingTransaction(UUID userId) {
        // Create a transaction in PENDING status
        transactionService.createTransaction(userId, new java.math.BigDecimal("25.00"), "PENDING");
    }
}

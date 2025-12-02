package com.waqiti.fraud.sanctions.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.fraud.sanctions.dto.SanctionsScreeningRequest;
import com.waqiti.fraud.sanctions.dto.SanctionsScreeningResult;
import com.waqiti.fraud.sanctions.entity.SanctionsCheckRecord;
import com.waqiti.fraud.sanctions.entity.SanctionsCheckRecord.*;
import com.waqiti.fraud.sanctions.repository.SanctionsCheckRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Sanctions Screening API endpoints.
 *
 * Tests cover:
 * - End-to-end screening workflows
 * - REST API contract validation
 * - Security and authorization
 * - Database persistence
 * - Error handling
 *
 * Uses Testcontainers for PostgreSQL database.
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("integration-test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:tc:postgresql:15:///sanctions_test",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "keycloak.enabled=false", // Disable Keycloak for integration tests
    "ofac.api.base-url=http://mock-ofac-api.test",
    "resilience4j.circuitbreaker.instances.ofac-api.sliding-window-size=10",
    "resilience4j.retry.instances.ofac-api.max-attempts=1" // Disable retries for faster tests
})
@DisplayName("Sanctions Screening Integration Tests")
class SanctionsScreeningIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("sanctions_test")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SanctionsCheckRepository sanctionsCheckRepository;

    private UUID testUserId;
    private UUID testTransactionId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testTransactionId = UUID.randomUUID();
        sanctionsCheckRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        sanctionsCheckRepository.deleteAll();
    }

    // =============================================================================
    // User Screening Endpoint Tests
    // =============================================================================

    @Nested
    @DisplayName("POST /api/v1/sanctions/screen/user")
    class ScreenUserEndpointTests {

        @Test
        @DisplayName("Should screen user successfully with authentication")
        @WithMockUser(authorities = {"SCOPE_sanctions:screen"})
        void shouldScreenUserSuccessfully() throws Exception {
            // Given
            SanctionsScreeningRequest request = SanctionsScreeningRequest.builder()
                .userId(testUserId)
                .entityType(EntityType.USER)
                .fullName("John Smith")
                .dateOfBirth(LocalDate.of(1985, 5, 20))
                .nationality("USA")
                .checkSource(CheckSource.REGISTRATION)
                .build();

            // When
            MvcResult result = mockMvc.perform(post("/api/v1/sanctions/screen/user")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkId").exists())
                .andExpect(jsonPath("$.matchFound").isBoolean())
                .andExpect(jsonPath("$.checkStatus").exists())
                .andReturn();

            // Then
            String responseBody = result.getResponse().getContentAsString();
            SanctionsScreeningResult response = objectMapper.readValue(responseBody, SanctionsScreeningResult.class);

            assertThat(response).isNotNull();
            assertThat(response.getCheckId()).isNotNull();
            assertThat(response.getCheckStatus()).isIn(
                CheckStatus.COMPLETED, CheckStatus.MANUAL_REVIEW);

            // Verify database persistence
            List<SanctionsCheckRecord> records = sanctionsCheckRepository.findAll();
            assertThat(records).isNotEmpty();
            assertThat(records.get(0).getUserId()).isEqualTo(testUserId);
        }

        @Test
        @DisplayName("Should reject request without authentication")
        void shouldRejectUnauthenticatedRequest() throws Exception {
            // Given
            SanctionsScreeningRequest request = SanctionsScreeningRequest.builder()
                .userId(testUserId)
                .entityType(EntityType.USER)
                .fullName("John Smith")
                .checkSource(CheckSource.REGISTRATION)
                .build();

            // When / Then
            mockMvc.perform(post("/api/v1/sanctions/screen/user")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should reject invalid request data")
        @WithMockUser(authorities = {"SCOPE_sanctions:screen"})
        void shouldRejectInvalidRequestData() throws Exception {
            // Given - missing required fields
            String invalidRequest = "{\"userId\":\"" + testUserId + "\"}";

            // When / Then
            mockMvc.perform(post("/api/v1/sanctions/screen/user")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidRequest))
                .andExpect(status().isBadRequest());
        }
    }

    // =============================================================================
    // Transaction Screening Endpoint Tests
    // =============================================================================

    @Nested
    @DisplayName("POST /api/v1/sanctions/screen/transaction")
    class ScreenTransactionEndpointTests {

        @Test
        @DisplayName("Should screen transaction parties successfully")
        @WithMockUser(authorities = {"SCOPE_sanctions:screen"})
        void shouldScreenTransactionSuccessfully() throws Exception {
            // Given
            SanctionsScreeningRequest request = SanctionsScreeningRequest.builder()
                .transactionId(testTransactionId)
                .entityType(EntityType.TRANSACTION_PARTY)
                .fullName("Beneficiary Name")
                .transactionAmount(new BigDecimal("75000.00"))
                .transactionCurrency("USD")
                .checkSource(CheckSource.TRANSACTION)
                .build();

            // When
            mockMvc.perform(post("/api/v1/sanctions/screen/transaction")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkId").exists());
        }
    }

    // =============================================================================
    // Manual Review Endpoint Tests
    // =============================================================================

    @Nested
    @DisplayName("POST /api/v1/sanctions/resolve/{checkId}")
    class ResolveCheckEndpointTests {

        @Test
        @DisplayName("Should resolve check as CLEARED with compliance officer role")
        @WithMockUser(roles = {"COMPLIANCE_OFFICER"})
        void shouldResolveCheckAsCleared() throws Exception {
            // Given - create a check requiring manual review
            SanctionsCheckRecord check = SanctionsCheckRecord.builder()
                .userId(testUserId)
                .checkedName("John Doe")
                .entityType(EntityType.USER)
                .checkSource(CheckSource.REGISTRATION)
                .matchFound(true)
                .matchScore(new BigDecimal("85.50"))
                .riskLevel(RiskLevel.HIGH)
                .checkStatus(CheckStatus.MANUAL_REVIEW)
                .requiresManualReview(true)
                .build();

            SanctionsCheckRecord savedCheck = sanctionsCheckRepository.save(check);
            UUID reviewerId = UUID.randomUUID();

            // When
            mockMvc.perform(post("/api/v1/sanctions/resolve/" + savedCheck.getId())
                    .param("resolution", "CLEARED")
                    .param("reviewNotes", "False positive - different person")
                    .param("reviewedBy", reviewerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value("CLEARED"))
                .andExpect(jsonPath("$.cleared").value(true))
                .andExpect(jsonPath("$.blocked").value(false));

            // Then - verify database update
            SanctionsCheckRecord updatedCheck = sanctionsCheckRepository.findById(savedCheck.getId()).orElseThrow();
            assertThat(updatedCheck.getResolution()).isEqualTo(Resolution.CLEARED);
            assertThat(updatedCheck.getCleared()).isTrue();
            assertThat(updatedCheck.getReviewedBy()).isEqualTo(reviewerId);
        }

        @Test
        @DisplayName("Should resolve check as BLOCKED")
        @WithMockUser(roles = {"COMPLIANCE_OFFICER"})
        void shouldResolveCheckAsBlocked() throws Exception {
            // Given
            SanctionsCheckRecord check = SanctionsCheckRecord.builder()
                .userId(testUserId)
                .checkedName("Sanctioned Person")
                .entityType(EntityType.USER)
                .checkSource(CheckSource.KYC)
                .matchFound(true)
                .matchScore(new BigDecimal("98.75"))
                .riskLevel(RiskLevel.CRITICAL)
                .checkStatus(CheckStatus.MANUAL_REVIEW)
                .requiresManualReview(true)
                .build();

            SanctionsCheckRecord savedCheck = sanctionsCheckRepository.save(check);
            UUID reviewerId = UUID.randomUUID();

            // When
            mockMvc.perform(post("/api/v1/sanctions/resolve/" + savedCheck.getId())
                    .param("resolution", "BLOCKED")
                    .param("reviewNotes", "Confirmed sanctions match")
                    .param("reviewedBy", reviewerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value("BLOCKED"))
                .andExpect(jsonPath("$.blocked").value(true))
                .andExpect(jsonPath("$.cleared").value(false));

            // Then
            SanctionsCheckRecord updatedCheck = sanctionsCheckRepository.findById(savedCheck.getId()).orElseThrow();
            assertThat(updatedCheck.getResolution()).isEqualTo(Resolution.BLOCKED);
            assertThat(updatedCheck.getBlocked()).isTrue();
        }

        @Test
        @DisplayName("Should reject resolution without compliance officer role")
        @WithMockUser(authorities = {"SCOPE_sanctions:read"})
        void shouldRejectResolutionWithoutRole() throws Exception {
            // Given
            UUID checkId = UUID.randomUUID();

            // When / Then
            mockMvc.perform(post("/api/v1/sanctions/resolve/" + checkId)
                    .param("resolution", "CLEARED")
                    .param("reviewNotes", "Notes")
                    .param("reviewedBy", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
        }
    }

    // =============================================================================
    // Query Endpoint Tests
    // =============================================================================

    @Nested
    @DisplayName("GET /api/v1/sanctions/checks/{userId}")
    class GetUserHistoryEndpointTests {

        @Test
        @DisplayName("Should retrieve user screening history")
        @WithMockUser(authorities = {"SCOPE_sanctions:read"})
        void shouldRetrieveUserHistory() throws Exception {
            // Given - create multiple checks for user
            SanctionsCheckRecord check1 = createTestCheck(testUserId, CheckStatus.COMPLETED, RiskLevel.LOW);
            SanctionsCheckRecord check2 = createTestCheck(testUserId, CheckStatus.MANUAL_REVIEW, RiskLevel.HIGH);

            sanctionsCheckRepository.saveAll(List.of(check1, check2));

            // When
            mockMvc.perform(get("/api/v1/sanctions/checks/" + testUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/sanctions/pending-review")
    class GetPendingReviewsEndpointTests {

        @Test
        @DisplayName("Should retrieve pending manual reviews")
        @WithMockUser(roles = {"COMPLIANCE_OFFICER"})
        void shouldRetrievePendingReviews() throws Exception {
            // Given
            SanctionsCheckRecord pendingCheck = SanctionsCheckRecord.builder()
                .userId(testUserId)
                .checkedName("Pending Review Name")
                .entityType(EntityType.USER)
                .checkSource(CheckSource.KYC)
                .matchFound(true)
                .riskLevel(RiskLevel.HIGH)
                .checkStatus(CheckStatus.MANUAL_REVIEW)
                .requiresManualReview(true)
                .build();

            sanctionsCheckRepository.save(pendingCheck);

            // When
            mockMvc.perform(get("/api/v1/sanctions/pending-review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].checkStatus").value("MANUAL_REVIEW"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/sanctions/blocked-status/{entityId}")
    class GetBlockedStatusEndpointTests {

        @Test
        @DisplayName("Should return blocked status for entity")
        @WithMockUser(authorities = {"SCOPE_sanctions:read"})
        void shouldReturnBlockedStatus() throws Exception {
            // Given - create blocked check
            SanctionsCheckRecord blockedCheck = SanctionsCheckRecord.builder()
                .userId(testUserId)
                .checkedName("Blocked User")
                .entityType(EntityType.USER)
                .checkSource(CheckSource.REGISTRATION)
                .matchFound(true)
                .riskLevel(RiskLevel.CRITICAL)
                .checkStatus(CheckStatus.COMPLETED)
                .resolution(Resolution.BLOCKED)
                .blocked(true)
                .build();

            sanctionsCheckRepository.save(blockedCheck);

            // When
            mockMvc.perform(get("/api/v1/sanctions/blocked-status/" + testUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityId").value(testUserId.toString()))
                .andExpect(jsonPath("$.blocked").value(true));
        }
    }

    // =============================================================================
    // Admin Endpoint Tests
    // =============================================================================

    @Nested
    @DisplayName("GET /api/v1/sanctions/health")
    class HealthCheckEndpointTests {

        @Test
        @DisplayName("Should return health status")
        void shouldReturnHealthStatus() throws Exception {
            // When
            mockMvc.perform(get("/api/v1/sanctions/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
        }
    }

    // =============================================================================
    // Helper Methods
    // =============================================================================

    private SanctionsCheckRecord createTestCheck(UUID userId, CheckStatus status, RiskLevel riskLevel) {
        return SanctionsCheckRecord.builder()
            .userId(userId)
            .checkedName("Test Name")
            .entityType(EntityType.USER)
            .checkSource(CheckSource.REGISTRATION)
            .matchFound(status == CheckStatus.MANUAL_REVIEW)
            .riskLevel(riskLevel)
            .checkStatus(status)
            .requiresManualReview(status == CheckStatus.MANUAL_REVIEW)
            .build();
    }
}

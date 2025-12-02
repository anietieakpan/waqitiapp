package com.waqiti.dispute.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.dispute.dto.CreateDisputeRequest;
import com.waqiti.dispute.entity.Dispute;
import com.waqiti.dispute.entity.DisputeStatus;
import com.waqiti.dispute.repository.DisputeRepository;
import com.waqiti.dispute.service.DisputeResolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for DisputeController
 *
 * Tests end-to-end REST API functionality with security
 *
 * @author Waqiti Production Team
 * @version 2.0.0-PRODUCTION
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("DisputeController Integration Tests")
class DisputeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DisputeRepository disputeRepository;

    @Autowired
    private DisputeResolutionService resolutionService;

    private CreateDisputeRequest testRequest;

    @BeforeEach
    void setUp() {
        // Clean up before each test
        disputeRepository.deleteAll();

        // Setup test request
        testRequest = CreateDisputeRequest.builder()
                .transactionId("txn-integration-test-123")
                .initiatorId("user-test-456")
                .disputeType("UNAUTHORIZED_TRANSACTION")
                .amount(new BigDecimal("150.00"))
                .currency("USD")
                .description("Integration test - unauthorized charge")
                .build();
    }

    @Test
    @DisplayName("Should create dispute successfully with valid request")
    @WithMockUser(username = "user-test-456", roles = {"USER"})
    void testCreateDispute_Success() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disputeId").exists())
                .andExpect(jsonPath("$.transactionId").value("txn-integration-test-123"))
                .andExpect(jsonPath("$.userId").value("user-test-456"))
                .andExpect(jsonPath("$.disputeAmount").value(150.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @DisplayName("Should return 400 when transaction ID is missing")
    @WithMockUser(username = "user-test-456", roles = {"USER"})
    void testCreateDispute_MissingTransactionId_BadRequest() throws Exception {
        // Given
        testRequest.setTransactionId(null);

        // When & Then
        mockMvc.perform(post("/api/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("Should return 400 when amount is negative")
    @WithMockUser(username = "user-test-456", roles = {"USER"})
    void testCreateDispute_NegativeAmount_BadRequest() throws Exception {
        // Given
        testRequest.setAmount(new BigDecimal("-50.00"));

        // When & Then
        mockMvc.perform(post("/api/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 401 when not authenticated")
    void testCreateDispute_NotAuthenticated_Unauthorized() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should get dispute by ID successfully")
    @WithMockUser(username = "user-test-456", roles = {"USER"})
    void testGetDispute_Success() throws Exception {
        // Given - Create a dispute first
        Dispute dispute = Dispute.builder()
                .transactionId("txn-123")
                .userId("user-test-456")
                .merchantId("merchant-789")
                .disputeAmount(new BigDecimal("100.00"))
                .currency("USD")
                .status(DisputeStatus.OPEN)
                .build();
        dispute = disputeRepository.save(dispute);

        // When & Then
        mockMvc.perform(get("/api/disputes/" + dispute.getDisputeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disputeId").value(dispute.getDisputeId()))
                .andExpect(jsonPath("$.transactionId").value("txn-123"))
                .andExpect(jsonPath("$.userId").value("user-test-456"))
                .andExpect(jsonPath("$.disputeAmount").value(100.00));
    }

    @Test
    @DisplayName("Should return 404 when dispute not found")
    @WithMockUser(username = "user-test-456", roles = {"USER"})
    void testGetDispute_NotFound() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/disputes/non-existent-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 403 when accessing another user's dispute")
    @WithMockUser(username = "different-user", roles = {"USER"})
    void testGetDispute_AccessDenied() throws Exception {
        // Given - Create a dispute for a different user
        Dispute dispute = Dispute.builder()
                .transactionId("txn-123")
                .userId("user-test-456")
                .merchantId("merchant-789")
                .disputeAmount(new BigDecimal("100.00"))
                .currency("USD")
                .status(DisputeStatus.OPEN)
                .build();
        dispute = disputeRepository.save(dispute);

        // When & Then
        mockMvc.perform(get("/api/disputes/" + dispute.getDisputeId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should get user disputes with pagination")
    @WithMockUser(username = "user-test-456", roles = {"USER"})
    void testGetUserDisputes_Success() throws Exception {
        // Given - Create multiple disputes
        for (int i = 0; i < 3; i++) {
            Dispute dispute = Dispute.builder()
                    .transactionId("txn-" + i)
                    .userId("user-test-456")
                    .merchantId("merchant-789")
                    .disputeAmount(new BigDecimal("100.00"))
                    .currency("USD")
                    .status(DisputeStatus.OPEN)
                    .build();
            disputeRepository.save(dispute);
        }

        // When & Then
        mockMvc.perform(get("/api/disputes")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("Should update dispute status successfully")
    @WithMockUser(username = "admin-user", roles = {"ADMIN"})
    void testUpdateDisputeStatus_Success() throws Exception {
        // Given
        Dispute dispute = Dispute.builder()
                .transactionId("txn-123")
                .userId("user-test-456")
                .merchantId("merchant-789")
                .disputeAmount(new BigDecimal("100.00"))
                .currency("USD")
                .status(DisputeStatus.OPEN)
                .build();
        dispute = disputeRepository.save(dispute);

        String updateRequest = """
                {
                    "disputeId": "%s",
                    "newStatus": "UNDER_REVIEW",
                    "reason": "Escalating for review",
                    "updatedBy": "admin-user"
                }
                """.formatted(dispute.getDisputeId());

        // When & Then
        mockMvc.perform(put("/api/disputes/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
    }

    @Test
    @DisplayName("Should search disputes with filters")
    @WithMockUser(username = "admin-user", roles = {"ADMIN"})
    void testSearchDisputes_Success() throws Exception {
        // Given
        Dispute dispute1 = Dispute.builder()
                .transactionId("txn-search-1")
                .userId("user-test-456")
                .merchantId("merchant-789")
                .disputeAmount(new BigDecimal("100.00"))
                .currency("USD")
                .status(DisputeStatus.OPEN)
                .build();
        disputeRepository.save(dispute1);

        Dispute dispute2 = Dispute.builder()
                .transactionId("txn-search-2")
                .userId("user-test-456")
                .merchantId("merchant-789")
                .disputeAmount(new BigDecimal("200.00"))
                .currency("USD")
                .status(DisputeStatus.RESOLVED)
                .build();
        disputeRepository.save(dispute2);

        String searchCriteria = """
                {
                    "status": "OPEN",
                    "minAmount": 50,
                    "maxAmount": 150
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/disputes/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(searchCriteria)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"));
    }

    @Test
    @DisplayName("Should return dispute statistics")
    @WithMockUser(username = "admin-user", roles = {"ADMIN"})
    void testGetDisputeStatistics_Success() throws Exception {
        // Given
        Dispute dispute1 = Dispute.builder()
                .transactionId("txn-stat-1")
                .userId("user-test-456")
                .merchantId("merchant-789")
                .disputeAmount(new BigDecimal("100.00"))
                .currency("USD")
                .status(DisputeStatus.OPEN)
                .build();
        disputeRepository.save(dispute1);

        Dispute dispute2 = Dispute.builder()
                .transactionId("txn-stat-2")
                .userId("user-test-456")
                .merchantId("merchant-789")
                .disputeAmount(new BigDecimal("200.00"))
                .currency("USD")
                .status(DisputeStatus.RESOLVED)
                .build();
        disputeRepository.save(dispute2);

        // When & Then
        mockMvc.perform(get("/api/disputes/statistics")
                        .param("userId", "user-test-456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDisputes").value(2))
                .andExpect(jsonPath("$.openDisputes").value(1))
                .andExpect(jsonPath("$.resolvedDisputes").value(1));
    }
}

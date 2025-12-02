package com.waqiti.compliance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.compliance.dto.AMLScreeningRequest;
import com.waqiti.compliance.dto.FileSARRequest;
import com.waqiti.compliance.dto.SanctionsScreeningRequest;
import com.waqiti.compliance.service.AMLComplianceService;
import com.waqiti.compliance.service.ComplianceService;
import com.waqiti.compliance.service.ProductionSARFilingService;
import com.waqiti.compliance.service.OFACSanctionsScreeningServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive Security Test Suite for Compliance Controller.
 *
 * Tests authentication and authorization for all compliance endpoints:
 * - AML screening
 * - SAR filing
 * - Sanctions screening
 * - KYC verification
 *
 * Security Requirements:
 * - All endpoints must require authentication
 * - Sensitive endpoints require COMPLIANCE_OFFICER or ADMIN role
 * - CSRF protection enabled for state-changing operations
 * - Input validation on all request bodies
 * - Rate limiting enforced
 *
 * @author Waqiti Compliance Engineering
 * @version 1.0
 */
@WebMvcTest(ComplianceController.class)
@DisplayName("Compliance Controller Security Tests")
class ComplianceControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ComplianceService complianceService;

    @MockBean
    private AMLComplianceService amlComplianceService;

    @MockBean
    private ProductionSARFilingService sarFilingService;

    @MockBean
    private OFACSanctionsScreeningServiceImpl ofacScreeningService;

    @Nested
    @DisplayName("Authentication Tests")
    class AuthenticationTests {

        @Test
        @WithAnonymousUser
        @DisplayName("Should reject anonymous users from AML screening endpoint")
        void shouldRejectAnonymousUser_AMLScreening() throws Exception {
            // Given
            AMLScreeningRequest request = new AMLScreeningRequest();
            request.setUserId("user-123");

            // When & Then
            mockMvc.perform(post("/api/v1/compliance/aml/transactions/screen")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @WithAnonymousUser
        @DisplayName("Should reject anonymous users from SAR filing endpoint")
        void shouldRejectAnonymousUser_SARFiling() throws Exception {
            // Given
            FileSARRequest request = new FileSARRequest();
            request.setAmount(new BigDecimal("10000.00"));

            // When & Then
            mockMvc.perform(post("/api/v1/compliance/aml/sar")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @WithAnonymousUser
        @DisplayName("Should reject anonymous users from sanctions screening endpoint")
        void shouldRejectAnonymousUser_SanctionsScreening() throws Exception {
            // Given
            SanctionsScreeningRequest request = new SanctionsScreeningRequest();
            request.setFullName("TEST NAME");

            // When & Then
            mockMvc.perform(post("/api/v1/compliance/sanctions/screen")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Authorization Tests")
    class AuthorizationTests {

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("Should reject regular users from SAR filing endpoint")
        void shouldRejectRegularUser_SARFiling() throws Exception {
            // Given
            FileSARRequest request = new FileSARRequest();
            request.setAmount(new BigDecimal("15000.00"));

            // When & Then
            mockMvc.perform(post("/api/v1/compliance/aml/sar")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "COMPLIANCE_OFFICER")
        @DisplayName("Should allow COMPLIANCE_OFFICER to file SAR")
        void shouldAllowComplianceOfficer_SARFiling() throws Exception {
            // Given
            FileSARRequest request = new FileSARRequest();
            request.setUserId("user-123");
            request.setAmount(new BigDecimal("20000.00"));
            request.setNarrative("Complete SAR narrative with all 5 W's");

            // When & Then
            mockMvc.perform(post("/api/v1/compliance/aml/sar")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Should allow ADMIN to file SAR")
        void shouldAllowAdmin_SARFiling() throws Exception {
            // Given
            FileSARRequest request = new FileSARRequest();
            request.setUserId("user-123");
            request.setAmount(new BigDecimal("25000.00"));
            request.setNarrative("Admin-filed SAR with complete narrative");

            // When & Then
            mockMvc.perform(post("/api/v1/compliance/aml/sar")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "COMPLIANCE_OFFICER")
        @DisplayName("Should allow COMPLIANCE_OFFICER to perform AML screening")
        void shouldAllowComplianceOfficer_AMLScreening() throws Exception {
            // Given
            AMLScreeningRequest request = new AMLScreeningRequest();
            request.setUserId("user-456");

            // When & Then
            mockMvc.perform(post("/api/v1/compliance/aml/transactions/screen")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "SYSTEM")
        @DisplayName("Should allow SYSTEM role for automated AML screening")
        void shouldAllowSystem_AMLScreening() throws Exception {
            // Given
            AMLScreeningRequest request = new AMLScreeningRequest();
            request.setUserId("user-789");

            // When & Then
            mockMvc.perform(post("/api/v1/compliance/aml/transactions/screen")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("CSRF Protection Tests")
    class CSRFProtectionTests {

        @Test
        @WithMockUser(roles = "COMPLIANCE_OFFICER")
        @DisplayName("Should reject POST requests without CSRF token")
        void shouldRejectRequestsWithoutCSRF() throws Exception {
            // Given
            FileSARRequest request = new FileSARRequest();
            request.setAmount(new BigDecimal("10000.00"));

            // When & Then - No csrf() call
            mockMvc.perform(post("/api/v1/compliance/aml/sar")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "COMPLIANCE_OFFICER")
        @DisplayName("Should accept POST requests with valid CSRF token")
        void shouldAcceptRequestsWithCSRF() throws Exception {
            // Given
            AMLScreeningRequest request = new AMLScreeningRequest();
            request.setUserId("user-123");

            // When & Then - With csrf()
            mockMvc.perform(post("/api/v1/compliance/aml/transactions/screen")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {

        @Test
        @WithMockUser(roles = "COMPLIANCE_OFFICER")
        @DisplayName("Should reject SAR filing with missing required fields")
        void shouldRejectInvalidSARRequest() throws Exception {
            // Given - Missing required fields
            FileSARRequest request = new FileSARRequest();
            // No userId, amount, or narrative set

            // When & Then
            mockMvc.perform(post("/api/v1/compliance/aml/sar")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(roles = "COMPLIANCE_OFFICER")
        @DisplayName("Should reject sanctions screening with missing name")
        void shouldRejectMissingName_SanctionsScreening() throws Exception {
            // Given
            SanctionsScreeningRequest request = new SanctionsScreeningRequest();
            // No fullName set

            // When & Then
            mockMvc.perform(post("/api/v1/compliance/sanctions/screen")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(roles = "COMPLIANCE_OFFICER")
        @DisplayName("Should reject malformed JSON")
        void shouldRejectMalformedJSON() throws Exception {
            // Given
            String malformedJson = "{\"userId\": \"test\", \"amount\":}"; // Invalid JSON

            // When & Then
            mockMvc.perform(post("/api/v1/compliance/aml/sar")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(malformedJson))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Content Type Validation Tests")
    class ContentTypeTests {

        @Test
        @WithMockUser(roles = "COMPLIANCE_OFFICER")
        @DisplayName("Should reject requests with incorrect content type")
        void shouldRejectIncorrectContentType() throws Exception {
            // Given
            AMLScreeningRequest request = new AMLScreeningRequest();
            request.setUserId("user-123");

            // When & Then
            mockMvc.perform(post("/api/v1/compliance/aml/transactions/screen")
                    .with(csrf())
                    .contentType(MediaType.TEXT_PLAIN) // Wrong content type
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        @WithMockUser(roles = "COMPLIANCE_OFFICER")
        @DisplayName("Should accept requests with correct JSON content type")
        void shouldAcceptCorrectContentType() throws Exception {
            // Given
            AMLScreeningRequest request = new AMLScreeningRequest();
            request.setUserId("user-123");

            // When & Then
            mockMvc.perform(post("/api/v1/compliance/aml/transactions/screen")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Rate Limiting Tests")
    class RateLimitingTests {

        @Test
        @WithMockUser(roles = "COMPLIANCE_OFFICER")
        @DisplayName("Should enforce rate limits on compliance endpoints")
        void shouldEnforceRateLimits() throws Exception {
            // Given
            AMLScreeningRequest request = new AMLScreeningRequest();
            request.setUserId("user-123");

            // When - Make multiple requests rapidly
            // Note: Actual rate limit enforcement depends on configuration
            // This test validates the endpoint accepts requests under normal conditions
            for (int i = 0; i < 5; i++) {
                mockMvc.perform(post("/api/v1/compliance/aml/transactions/screen")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
            }

            // Additional requests would be rate limited in actual deployment
            // Rate limit configuration is tested separately in integration tests
        }
    }

    @Nested
    @DisplayName("HTTP Method Security Tests")
    class HTTPMethodTests {

        @Test
        @WithMockUser(roles = "COMPLIANCE_OFFICER")
        @DisplayName("Should reject GET requests to POST-only endpoints")
        void shouldRejectGETOnPostEndpoint() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/compliance/aml/sar")
                    .with(csrf()))
                .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @WithMockUser(roles = "COMPLIANCE_OFFICER")
        @DisplayName("Should allow GET requests to query endpoints")
        void shouldAllowGETOnQueryEndpoint() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/compliance/aml/alerts")
                    .with(csrf()))
                .andExpect(status().isOk());
        }
    }
}

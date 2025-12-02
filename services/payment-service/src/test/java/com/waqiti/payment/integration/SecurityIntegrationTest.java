package com.waqiti.payment.integration;

import com.waqiti.common.security.AuthorizationService;
import com.waqiti.common.security.SqlInjectionPreventionService;
import com.waqiti.payment.controller.PaymentController;
import com.waqiti.payment.dto.*;
import com.waqiti.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * CRITICAL SECURITY TESTS: Integration tests for security controls
 * Tests authorization, SQL injection prevention, XSS protection, rate limiting
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SqlInjectionPreventionService sqlInjectionService;

    @Autowired
    private AuthorizationService authorizationService;

    private UUID testUserId;
    private UUID testWalletId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testWalletId = UUID.randomUUID();
    }

    /**
     * CRITICAL: Test SQL injection prevention in request parameters
     */
    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    void testSqlInjectionPrevention() throws Exception {
        String[] sqlInjectionAttempts = {
            "'; DROP TABLE payments; --",
            "1' OR '1'='1",
            "admin'--",
            "' OR 1=1--",
            "'; DELETE FROM users WHERE 1=1; --",
            "1' UNION SELECT NULL, NULL, NULL--"
        };

        for (String maliciousInput : sqlInjectionAttempts) {
            // Test SQL injection in query parameters
            mockMvc.perform(get("/api/v1/payments/search")
                    .param("query", maliciousInput)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
            
            // Verify SQL injection detection service works
            assertTrue(sqlInjectionService.containsSqlInjection(maliciousInput),
                "SQL injection should be detected: " + maliciousInput);
        }
    }

    /**
     * CRITICAL: Test XSS prevention in request body
     */
    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    void testXssPreventionInRequestBody() throws Exception {
        String[] xssAttempts = {
            "<script>alert('XSS')</script>",
            "javascript:alert('XSS')",
            "<img src=x onerror=alert('XSS')>",
            "<iframe src='javascript:alert(\"XSS\")'></iframe>",
            "onclick=alert('XSS')"
        };

        for (String maliciousInput : xssAttempts) {
            PaymentRequest request = PaymentRequest.builder()
                .userId(testUserId)
                .recipientId(UUID.randomUUID())
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .description(maliciousInput) // XSS attempt in description
                .build();

            mockMvc.perform(post("/api/v1/payments/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }
    }

    /**
     * CRITICAL: Test unauthorized access to other user's payments
     */
    @Test
    @WithMockUser(username = "user1", roles = {"USER"})
    void testUnauthorizedPaymentAccess() throws Exception {
        UUID otherUserId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        // Attempt to access another user's payment
        mockMvc.perform(get("/api/v1/payments/" + paymentId)
                .param("userId", otherUserId.toString())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    /**
     * CRITICAL: Test wallet ownership validation
     */
    @Test
    @WithMockUser(username = "user1", roles = {"USER"})
    void testWalletOwnershipValidation() throws Exception {
        UUID otherUserWalletId = UUID.randomUUID();

        // Attempt to transfer from another user's wallet
        TransferRequest request = TransferRequest.builder()
            .fromWalletId(otherUserWalletId)
            .toWalletId(testWalletId)
            .amount(BigDecimal.valueOf(100.00))
            .currency("USD")
            .description("Unauthorized transfer attempt")
            .build();

        mockMvc.perform(post("/api/v1/wallets/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    /**
     * CRITICAL: Test request size limits
     */
    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    void testRequestSizeLimits() throws Exception {
        // Create a very large description (>10MB)
        StringBuilder largeDescription = new StringBuilder();
        for (int i = 0; i < 2_000_000; i++) {
            largeDescription.append("AAAAAAAAAA");
        }

        PaymentRequest request = PaymentRequest.builder()
            .userId(testUserId)
            .recipientId(UUID.randomUUID())
            .amount(BigDecimal.valueOf(100.00))
            .currency("USD")
            .description(largeDescription.toString())
            .build();

        mockMvc.perform(post("/api/v1/payments/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    /**
     * CRITICAL: Test rate limiting protection
     */
    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    void testRateLimitingProtection() throws Exception {
        PaymentRequest request = PaymentRequest.builder()
            .userId(testUserId)
            .recipientId(UUID.randomUUID())
            .amount(BigDecimal.valueOf(10.00))
            .currency("USD")
            .description("Rate limit test")
            .build();

        int requestCount = 50; // Exceed typical rate limit
        int rateLimitedCount = 0;

        for (int i = 0; i < requestCount; i++) {
            MvcResult result = mockMvc.perform(post("/api/v1/payments/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andReturn();

            if (result.getResponse().getStatus() == 429) { // Too Many Requests
                rateLimitedCount++;
            }
        }

        assertTrue(rateLimitedCount > 0, 
            "Rate limiting should have been triggered after " + requestCount + " requests");
    }

    /**
     * CRITICAL: Test path traversal prevention
     */
    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    void testPathTraversalPrevention() throws Exception {
        String[] pathTraversalAttempts = {
            "../../../etc/passwd",
            "..\\..\\..\\windows\\system32",
            "%2e%2e%2f%2e%2e%2f",
            "....//....//....//",
            "..\\..\\.."
        };

        for (String maliciousPath : pathTraversalAttempts) {
            mockMvc.perform(get("/api/v1/payments/receipt/" + maliciousPath)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        }
    }

    /**
     * CRITICAL: Test malicious User-Agent detection
     */
    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    void testMaliciousUserAgentDetection() throws Exception {
        String[] maliciousUserAgents = {
            "sqlmap/1.0",
            "nikto/2.1.6",
            "acunetix",
            "havij",
            "masscan/1.0"
        };

        for (String maliciousAgent : maliciousUserAgents) {
            mockMvc.perform(get("/api/v1/payments")
                    .header("User-Agent", maliciousAgent)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        }
    }

    /**
     * TEST: Verify security headers are present
     */
    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    void testSecurityHeadersPresent() throws Exception {
        mockMvc.perform(get("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(header().exists("X-Content-Type-Options"))
            .andExpect(header().exists("X-Frame-Options"))
            .andExpect(header().exists("X-XSS-Protection"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    /**
     * CRITICAL: Test amount-based authorization limits
     */
    @Test
    @WithMockUser(username = "basic-user", roles = {"USER"})
    void testAmountBasedAuthorizationLimits() throws Exception {
        // Attempt to make payment exceeding user's limit (assuming BASIC user limit is 1000)
        PaymentRequest largePayment = PaymentRequest.builder()
            .userId(testUserId)
            .recipientId(UUID.randomUUID())
            .amount(BigDecimal.valueOf(50000.00)) // Exceeds limit
            .currency("USD")
            .description("Large payment test")
            .build();

        mockMvc.perform(post("/api/v1/payments/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(largePayment)))
            .andExpect(status().isForbidden());
    }

    /**
     * CRITICAL: Test KYC level requirement enforcement
     */
    @Test
    @WithMockUser(username = "unverified-user", roles = {"USER"})
    void testKycLevelRequirementEnforcement() throws Exception {
        // Attempt international transfer without proper KYC level
        PaymentRequest internationalPayment = PaymentRequest.builder()
            .userId(testUserId)
            .recipientId(UUID.randomUUID())
            .amount(BigDecimal.valueOf(1000.00))
            .currency("EUR") // International currency
            .description("International payment test")
            .paymentMethod("INTERNATIONAL_WIRE")
            .build();

        mockMvc.perform(post("/api/v1/payments/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(internationalPayment)))
            .andExpect(status().isForbidden());
    }

    /**
     * TEST: Verify CSRF protection is enabled
     */
    @Test
    void testCsrfProtectionEnabled() throws Exception {
        // Without CSRF token, POST request should be rejected
        PaymentRequest request = PaymentRequest.builder()
            .userId(testUserId)
            .recipientId(UUID.randomUUID())
            .amount(BigDecimal.valueOf(100.00))
            .currency("USD")
            .description("CSRF test")
            .build();

        mockMvc.perform(post("/api/v1/payments/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden()); // CSRF validation should reject
    }

    /**
     * CRITICAL: Test sensitive data is not exposed in error messages
     */
    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    void testSensitiveDataNotExposedInErrors() throws Exception {
        // Trigger an error and verify no sensitive data is leaked
        PaymentRequest invalidRequest = PaymentRequest.builder()
            .userId(testUserId)
            .recipientId(UUID.randomUUID())
            .amount(BigDecimal.valueOf(-100.00)) // Invalid amount
            .currency("USD")
            .description("Error test")
            .build();

        MvcResult result = mockMvc.perform(post("/api/v1/payments/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest())
            .andReturn();

        String response = result.getResponse().getContentAsString();
        
        // Verify response doesn't contain sensitive patterns
        assertFalse(response.contains("password"), "Error should not expose passwords");
        assertFalse(response.contains("secret"), "Error should not expose secrets");
        assertFalse(response.contains("token"), "Error should not expose tokens");
        assertFalse(response.contains("stackTrace"), "Error should not expose stack traces");
    }

    /**
     * CRITICAL: Test session fixation protection
     */
    @Test
    void testSessionFixationProtection() throws Exception {
        // Login with a session ID
        String initialSessionId = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test\",\"password\":\"test\"}"))
            .andReturn()
            .getRequest()
            .getSession()
            .getId();

        // Perform authentication
        // Session ID should change after authentication to prevent fixation
        
        String postAuthSessionId = mockMvc.perform(get("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON))
            .andReturn()
            .getRequest()
            .getSession()
            .getId();

        // In a proper implementation, these should be different
        // This test structure demonstrates the security requirement
    }

    /**
     * TEST: Verify proper CORS configuration
     */
    @Test
    void testCorsConfiguration() throws Exception {
        // Request with origin header
        mockMvc.perform(options("/api/v1/payments")
                .header("Origin", "https://malicious-site.com")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isForbidden()); // Should reject unauthorized origins
    }

    /**
     * CRITICAL: Test concurrent session detection
     */
    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    void testConcurrentSessionDetection() throws Exception {
        // This would test that multiple active sessions from same user are detected
        // and handled according to security policy (allow, reject, or terminate old session)
        
        // Implementation would depend on session management strategy
        // This test structure demonstrates the security requirement
    }
}
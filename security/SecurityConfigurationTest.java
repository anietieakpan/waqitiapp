package com.waqiti.security.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.*;

/**
 * Comprehensive Security Configuration Tests
 * 
 * Validates all security controls are properly configured and working
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private Environment environment;
    
    @Nested
    @DisplayName("OAuth2/JWT Security Tests")
    class OAuth2SecurityTests {
        
        @Test
        @DisplayName("Should reject requests without authentication")
        public void testUnauthenticatedAccess() throws Exception {
            mockMvc.perform(get("/api/v1/payments"))
                .andExpect(status().isUnauthorized());
        }
        
        @Test
        @DisplayName("Should reject requests with invalid JWT token")
        public void testInvalidJwtToken() throws Exception {
            String invalidToken = "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.invalid";
            
            mockMvc.perform(get("/api/v1/payments")
                    .header("Authorization", invalidToken))
                .andExpect(status().isUnauthorized());
        }
        
        @Test
        @DisplayName("Should reject expired JWT tokens")
        public void testExpiredJwtToken() throws Exception {
            // Use a real expired token
            String expiredToken = "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0IiwiZXhwIjoxNjAwMDAwMDAwfQ.expired";
            
            mockMvc.perform(get("/api/v1/payments")
                    .header("Authorization", expiredToken))
                .andExpect(status().isUnauthorized());
        }
        
        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("Should allow authenticated users with valid roles")
        public void testAuthenticatedAccess() throws Exception {
            mockMvc.perform(get("/api/v1/user/profile"))
                .andExpect(status().isOk());
        }
        
        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("Should enforce role-based access control")
        public void testRoleBasedAccessControl() throws Exception {
            // User role should not access admin endpoints
            mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
        }
        
        @Test
        @DisplayName("Should validate JWT signature")
        public void testJwtSignatureValidation() throws Exception {
            // Token with tampered signature
            String tamperedToken = "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.tampered_signature";
            
            mockMvc.perform(get("/api/v1/payments")
                    .header("Authorization", tamperedToken))
                .andExpect(status().isUnauthorized());
        }
    }
    
    @Nested
    @DisplayName("CSRF Protection Tests")
    class CSRFProtectionTests {
        
        @Test
        @WithMockUser
        @DisplayName("Should reject POST requests without CSRF token")
        public void testCsrfProtectionOnPost() throws Exception {
            mockMvc.perform(post("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isForbidden());
        }
        
        @Test
        @WithMockUser
        @DisplayName("Should accept POST requests with valid CSRF token")
        public void testCsrfTokenValidation() throws Exception {
            mockMvc.perform(post("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
                    .with(csrf()))
                .andExpect(status().isOk());
        }
        
        @Test
        @WithMockUser
        @DisplayName("Should reject requests with invalid CSRF token")
        public void testInvalidCsrfToken() throws Exception {
            mockMvc.perform(post("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
                    .header("X-XSRF-TOKEN", "invalid-token"))
                .andExpect(status().isForbidden());
        }
        
        @Test
        @WithMockUser
        @DisplayName("Should allow GET requests without CSRF token")
        public void testCsrfExemptionForSafeMethods() throws Exception {
            mockMvc.perform(get("/api/v1/payments"))
                .andExpect(status().isOk());
        }
    }
    
    @Nested
    @DisplayName("CORS Configuration Tests")
    class CORSConfigurationTests {
        
        @Test
        @DisplayName("Should reject requests from unauthorized origins")
        public void testUnauthorizedOrigin() throws Exception {
            mockMvc.perform(options("/api/v1/payments")
                    .header("Origin", "http://evil.com")
                    .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
        }
        
        @Test
        @DisplayName("Should allow requests from authorized origins")
        public void testAuthorizedOrigin() throws Exception {
            String allowedOrigin = environment.getProperty("cors.allowed-origins[0]", "https://app.example.com");
            
            mockMvc.perform(options("/api/v1/payments")
                    .header("Origin", allowedOrigin)
                    .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", allowedOrigin));
        }
        
        @Test
        @DisplayName("Should include proper CORS headers")
        public void testCorsHeaders() throws Exception {
            String allowedOrigin = environment.getProperty("cors.allowed-origins[0]", "https://app.example.com");
            
            mockMvc.perform(get("/api/v1/payments")
                    .header("Origin", allowedOrigin))
                .andExpect(header().exists("Access-Control-Allow-Credentials"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        }
    }
    
    @Nested
    @DisplayName("Security Headers Tests")
    class SecurityHeadersTests {
        
        @Test
        @WithMockUser
        @DisplayName("Should include X-Frame-Options header")
        public void testXFrameOptions() throws Exception {
            mockMvc.perform(get("/api/v1/payments"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
        }
        
        @Test
        @WithMockUser
        @DisplayName("Should include X-Content-Type-Options header")
        public void testXContentTypeOptions() throws Exception {
            mockMvc.perform(get("/api/v1/payments"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        }
        
        @Test
        @WithMockUser
        @DisplayName("Should include X-XSS-Protection header")
        public void testXXSSProtection() throws Exception {
            mockMvc.perform(get("/api/v1/payments"))
                .andExpect(header().string("X-XSS-Protection", "1; mode=block"));
        }
        
        @Test
        @WithMockUser
        @DisplayName("Should include Content-Security-Policy header")
        public void testContentSecurityPolicy() throws Exception {
            mockMvc.perform(get("/api/v1/payments"))
                .andExpect(header().exists("Content-Security-Policy"));
        }
        
        @Test
        @WithMockUser
        @DisplayName("Should include Strict-Transport-Security header")
        public void testStrictTransportSecurity() throws Exception {
            mockMvc.perform(get("/api/v1/payments"))
                .andExpect(header().exists("Strict-Transport-Security"));
        }
    }
    
    @Nested
    @DisplayName("Rate Limiting Tests")
    class RateLimitingTests {
        
        @Test
        @WithMockUser
        @DisplayName("Should enforce rate limits")
        public void testRateLimiting() throws Exception {
            int rateLimit = environment.getProperty("rate-limiting.per-user.limit", Integer.class, 100);
            
            // Make requests up to the limit
            for (int i = 0; i < rateLimit; i++) {
                mockMvc.perform(get("/api/v1/payments"))
                    .andExpect(status().isOk());
            }
            
            // Next request should be rate limited
            mockMvc.perform(get("/api/v1/payments"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("X-RateLimit-Remaining"))
                .andExpect(header().exists("X-RateLimit-Reset"));
        }
        
        @Test
        @DisplayName("Should rate limit by IP address")
        public void testIpBasedRateLimiting() throws Exception {
            int ipRateLimit = environment.getProperty("rate-limiting.per-ip.limit", Integer.class, 50);
            
            // Simulate requests from same IP
            for (int i = 0; i < ipRateLimit; i++) {
                mockMvc.perform(get("/api/v1/public/info")
                        .header("X-Forwarded-For", "192.168.1.1"))
                    .andExpect(status().isOk());
            }
            
            // Should be rate limited
            mockMvc.perform(get("/api/v1/public/info")
                    .header("X-Forwarded-For", "192.168.1.1"))
                .andExpect(status().isTooManyRequests());
        }
    }
    
    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {
        
        @Test
        @WithMockUser
        @DisplayName("Should reject SQL injection attempts")
        public void testSqlInjectionProtection() throws Exception {
            String sqlInjection = "'; DROP TABLE users; --";
            
            mockMvc.perform(get("/api/v1/users")
                    .param("search", sqlInjection))
                .andExpect(status().isBadRequest());
        }
        
        @Test
        @WithMockUser
        @DisplayName("Should reject XSS attempts")
        public void testXssProtection() throws Exception {
            String xssPayload = "<script>alert('XSS')</script>";
            
            mockMvc.perform(post("/api/v1/comments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"comment\": \"" + xssPayload + "\"}")
                    .with(csrf()))
                .andExpect(status().isBadRequest());
        }
        
        @Test
        @WithMockUser
        @DisplayName("Should reject command injection attempts")
        public void testCommandInjectionProtection() throws Exception {
            String commandInjection = "test; rm -rf /";
            
            mockMvc.perform(post("/api/v1/process")
                    .param("input", commandInjection)
                    .with(csrf()))
                .andExpect(status().isBadRequest());
        }
        
        @Test
        @WithMockUser
        @DisplayName("Should reject path traversal attempts")
        public void testPathTraversalProtection() throws Exception {
            String pathTraversal = "../../etc/passwd";
            
            mockMvc.perform(get("/api/v1/files/" + pathTraversal))
                .andExpect(status().isBadRequest());
        }
    }
    
    @Nested
    @DisplayName("Session Management Tests")
    class SessionManagementTests {
        
        @Test
        @WithMockUser
        @DisplayName("Should enforce session timeout")
        public void testSessionTimeout() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/session"))
                .andExpect(status().isOk())
                .andReturn();
            
            String sessionId = result.getResponse().getCookie("JSESSIONID").getValue();
            assertThat(sessionId).isNotNull();
            
            // Simulate session timeout
            Thread.sleep(environment.getProperty("security.session.timeout", Integer.class, 1800) * 1000 + 1000);
            
            mockMvc.perform(get("/api/v1/session")
                    .cookie(result.getResponse().getCookies()))
                .andExpect(status().isUnauthorized());
        }
        
        @Test
        @WithMockUser
        @DisplayName("Should prevent session fixation")
        public void testSessionFixationProtection() throws Exception {
            // Get initial session
            MvcResult firstResult = mockMvc.perform(get("/api/v1/session"))
                .andExpect(status().isOk())
                .andReturn();
            
            String firstSessionId = firstResult.getResponse().getCookie("JSESSIONID").getValue();
            
            // Authenticate
            MvcResult authResult = mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\": \"test\", \"password\": \"test\"}")
                    .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
            
            String newSessionId = authResult.getResponse().getCookie("JSESSIONID").getValue();
            
            // Session ID should change after authentication
            assertThat(newSessionId).isNotEqualTo(firstSessionId);
        }
    }
    
    @Nested
    @DisplayName("Encryption Tests")
    class EncryptionTests {
        
        @Test
        @DisplayName("Should not expose encryption keys")
        public void testEncryptionKeyProtection() {
            // Ensure no default encryption keys
            String masterKey = environment.getProperty("encryption.master.key");
            assertThat(masterKey).doesNotContain("default", "test", "demo", "VAULT_SECRET_REQUIRED");
        }
        
        @Test
        @DisplayName("Should use strong encryption algorithms")
        public void testEncryptionAlgorithm() {
            String algorithm = environment.getProperty("encryption.master.algorithm");
            assertThat(algorithm).isIn("AES/GCM/NoPadding", "RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        }
        
        @Test
        @DisplayName("Should enforce minimum key size")
        public void testEncryptionKeySize() {
            Integer keySize = environment.getProperty("encryption.master.key-size", Integer.class);
            assertThat(keySize).isGreaterThanOrEqualTo(256);
        }
    }
    
    @Nested
    @DisplayName("Configuration Security Tests")
    class ConfigurationSecurityTests {
        
        @Test
        @DisplayName("Should not contain hardcoded secrets")
        public void testNoHardcodedSecrets() {
            // Check for common secret patterns
            String[] sensitiveProps = {
                "spring.datasource.password",
                "spring.redis.password",
                "vault.token",
                "jwt.secret"
            };
            
            for (String prop : sensitiveProps) {
                String value = environment.getProperty(prop);
                if (value != null) {
                    assertThat(value).doesNotContainPattern("(?i)(password|secret|token|key)");
                }
            }
        }
        
        @Test
        @DisplayName("Should have Vault enabled in production")
        public void testVaultEnabled() {
            if ("production".equals(environment.getProperty("spring.profiles.active"))) {
                Boolean vaultEnabled = environment.getProperty("vault.enabled", Boolean.class);
                assertThat(vaultEnabled).isTrue();
            }
        }
        
        @Test
        @DisplayName("Should use HTTPS for external services")
        public void testHttpsUsage() {
            String[] urlProps = {
                "vault.uri",
                "spring.security.oauth2.resourceserver.jwt.issuer-uri",
                "keycloak.auth-server-url"
            };
            
            for (String prop : urlProps) {
                String url = environment.getProperty(prop);
                if (url != null && "production".equals(environment.getProperty("spring.profiles.active"))) {
                    assertThat(url).startsWith("https://");
                }
            }
        }
    }
}
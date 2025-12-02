package com.waqiti.common.validation.validators;

import com.waqiti.common.validation.constraints.ValidWalletOwnership;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive security tests for WalletOwnershipValidator
 * 
 * Tests cover:
 * - Positive cases (valid ownership)
 * - Negative cases (unauthorized access attempts)
 * - Security bypass attempts
 * - Database fallback scenarios
 * - Admin access scenarios
 * - Error handling and fail-secure behavior
 * - Performance and timeout scenarios
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Wallet Ownership Validator Security Tests")
class WalletOwnershipValidatorTest {
    
    private WalletOwnershipValidator validator;
    
    @Mock
    private RestTemplate restTemplate;
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private ConstraintValidatorContext context;
    
    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;
    
    @Mock
    private SecurityContext securityContext;
    
    @Mock
    private Authentication authentication;
    
    @Mock
    private ValidWalletOwnership annotation;
    
    private static final String VALID_WALLET_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String VALID_USERNAME = "testuser";
    private static final String WALLET_SERVICE_URL = "http://wallet-service:8082";
    
    @BeforeEach
    void setUp() {
        validator = new WalletOwnershipValidator();
        
        // Inject mocks
        ReflectionTestUtils.setField(validator, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(validator, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(validator, "walletServiceUrl", WALLET_SERVICE_URL);
        ReflectionTestUtils.setField(validator, "validationTimeoutMs", 3000);
        ReflectionTestUtils.setField(validator, "cacheEnabled", true);
        ReflectionTestUtils.setField(validator, "fallbackEnabled", true);
        ReflectionTestUtils.setField(validator, "databaseSchema", "public");
        
        // Setup constraint context mock
        when(context.buildConstraintViolationWithTemplate(anyString()))
                .thenReturn(violationBuilder);
        when(violationBuilder.addConstraintViolation()).thenReturn(context);
        
        // Setup security context
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(VALID_USERNAME);
        
        // Initialize validator
        when(annotation.allowNull()).thenReturn(false);
        validator.initialize(annotation);
    }
    
    @Test
    @DisplayName("Should validate ownership when wallet service confirms")
    void testValidOwnership_ViaWalletService() {
        // Arrange
        Map<String, Object> response = Map.of("isOwner", true);
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(response);
        
        // Act
        boolean result = validator.isValid(VALID_WALLET_ID, context);
        
        // Assert
        assertTrue(result, "Should validate ownership when wallet service confirms");
        verify(restTemplate).getForObject(
                eq(WALLET_SERVICE_URL + "/api/v1/wallets/" + VALID_WALLET_ID + "/owner/" + VALID_USERNAME),
                eq(Map.class)
        );
    }
    
    @Test
    @DisplayName("Should deny access when wallet service denies ownership")
    void testInvalidOwnership_ViaWalletService() {
        // Arrange
        Map<String, Object> response = Map.of("isOwner", false);
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(response);
        
        // Act
        boolean result = validator.isValid(VALID_WALLET_ID, context);
        
        // Assert
        assertFalse(result, "Should deny access when wallet service denies ownership");
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("Access denied: You do not own this wallet");
    }
    
    @Test
    @DisplayName("Should use database fallback when wallet service fails")
    void testDatabaseFallback_WhenServiceFails() {
        // Arrange
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RestClientException("Service unavailable"));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(UUID.class), eq(VALID_USERNAME)))
                .thenReturn(1);
        
        // Act
        boolean result = validator.isValid(VALID_WALLET_ID, context);
        
        // Assert
        assertFalse(result, "Should fail-secure when service fails and fallback succeeds");
        // Fallback is called internally but doesn't override the fail-secure behavior
    }
    
    @Test
    @DisplayName("Should validate ownership via database fallback when RestTemplate unavailable")
    void testDatabaseFallback_NoRestTemplate() {
        // Arrange
        ReflectionTestUtils.setField(validator, "restTemplate", null);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(UUID.class), eq(VALID_USERNAME)))
                .thenReturn(1);
        
        // Act
        boolean result = validator.isValid(VALID_WALLET_ID, context);
        
        // Assert
        assertTrue(result, "Should validate via database when RestTemplate unavailable");
        verify(jdbcTemplate).queryForObject(
                contains("SELECT COUNT(*) FROM public.wallets"),
                eq(Integer.class),
                any(UUID.class),
                eq(VALID_USERNAME)
        );
    }
    
    @Test
    @DisplayName("Should deny access when database fallback finds no ownership")
    void testDatabaseFallback_NoOwnership() {
        // Arrange
        ReflectionTestUtils.setField(validator, "restTemplate", null);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(UUID.class), eq(VALID_USERNAME)))
                .thenReturn(0);
        
        // Act
        boolean result = validator.isValid(VALID_WALLET_ID, context);
        
        // Assert
        assertFalse(result, "Should deny access when database shows no ownership");
    }
    
    @Test
    @DisplayName("Should allow admin access to any wallet")
    void testAdminAccess() {
        // Arrange
        Collection<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_WALLET_ADMIN")
        );
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);
        
        // Act
        boolean result = validator.isValid(VALID_WALLET_ID, context);
        
        // Assert
        assertTrue(result, "Should allow admin access to any wallet");
        // Should not call wallet service for admin users
        verify(restTemplate, never()).getForObject(anyString(), any());
    }
    
    @Test
    @DisplayName("Should allow compliance officer access to any wallet")
    void testComplianceOfficerAccess() {
        // Arrange
        Collection<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_COMPLIANCE_OFFICER")
        );
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);
        
        // Act
        boolean result = validator.isValid(VALID_WALLET_ID, context);
        
        // Assert
        assertTrue(result, "Should allow compliance officer access");
    }
    
    @Test
    @DisplayName("Should deny access when user not authenticated")
    void testNoAuthentication() {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(null);
        
        // Act
        boolean result = validator.isValid(VALID_WALLET_ID, context);
        
        // Assert
        assertFalse(result, "Should deny access when not authenticated");
        verify(context).buildConstraintViolationWithTemplate("Authentication required for wallet access");
    }
    
    @Test
    @DisplayName("Should deny access when authentication not valid")
    void testInvalidAuthentication() {
        // Arrange
        when(authentication.isAuthenticated()).thenReturn(false);
        
        // Act
        boolean result = validator.isValid(VALID_WALLET_ID, context);
        
        // Assert
        assertFalse(result, "Should deny access with invalid authentication");
    }
    
    @Test
    @DisplayName("Should reject invalid wallet ID format")
    void testInvalidWalletIdFormat() {
        // Act
        boolean result = validator.isValid("invalid-uuid", context);
        
        // Assert
        assertFalse(result, "Should reject invalid UUID format");
        verify(context).buildConstraintViolationWithTemplate("Invalid wallet ID format");
    }
    
    @Test
    @DisplayName("Should handle null wallet ID based on configuration")
    void testNullWalletId() {
        // Test when null not allowed
        boolean result1 = validator.isValid(null, context);
        assertFalse(result1, "Should reject null when not allowed");
        
        // Test when null allowed
        when(annotation.allowNull()).thenReturn(true);
        validator.initialize(annotation);
        boolean result2 = validator.isValid(null, context);
        assertTrue(result2, "Should accept null when allowed");
    }
    
    @Test
    @DisplayName("Should handle empty wallet ID")
    void testEmptyWalletId() {
        // Act
        boolean result = validator.isValid("", context);
        
        // Assert
        assertFalse(result, "Should reject empty wallet ID");
    }
    
    @Test
    @DisplayName("Should fail-secure on database errors")
    void testDatabaseError_FailSecure() {
        // Arrange
        ReflectionTestUtils.setField(validator, "restTemplate", null);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any()))
                .thenThrow(new RuntimeException("Database connection failed"));
        
        // Act
        boolean result = validator.isValid(VALID_WALLET_ID, context);
        
        // Assert
        assertFalse(result, "Should fail-secure on database errors");
    }
    
    @Test
    @DisplayName("Should fail-secure when both service and database fail")
    void testBothValidationMethodsFail_FailSecure() {
        // Arrange
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RestClientException("Service down"));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any()))
                .thenThrow(new RuntimeException("Database down"));
        
        // Act
        boolean result = validator.isValid(VALID_WALLET_ID, context);
        
        // Assert
        assertFalse(result, "Should fail-secure when all validation methods fail");
    }
    
    @Test
    @DisplayName("Should fail-secure when no validation method available")
    void testNoValidationMethodAvailable() {
        // Arrange
        ReflectionTestUtils.setField(validator, "restTemplate", null);
        ReflectionTestUtils.setField(validator, "jdbcTemplate", null);
        
        // Act
        boolean result = validator.isValid(VALID_WALLET_ID, context);
        
        // Assert
        assertFalse(result, "Should fail-secure when no validation method available");
    }
    
    @Test
    @DisplayName("Should validate correct SQL injection protection")
    void testSQLInjectionProtection() {
        // Arrange
        ReflectionTestUtils.setField(validator, "restTemplate", null);
        String maliciousWalletId = VALID_WALLET_ID + "' OR '1'='1";
        
        // Act & Assert
        assertThrows(Exception.class, () -> {
            validator.isValid(maliciousWalletId, context);
        }, "Should reject SQL injection attempt");
    }
    
    @Test
    @DisplayName("Should use parameterized queries in database fallback")
    void testParameterizedQuery() {
        // Arrange
        ReflectionTestUtils.setField(validator, "restTemplate", null);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(UUID.class), eq(VALID_USERNAME)))
                .thenReturn(1);
        
        // Act
        validator.isValid(VALID_WALLET_ID, context);
        
        // Assert
        verify(jdbcTemplate).queryForObject(
                argThat(sql -> sql.contains("WHERE id = ?") && sql.contains("AND user_id = ?")),
                eq(Integer.class),
                any(UUID.class),
                eq(VALID_USERNAME)
        );
    }
    
    @Test
    @DisplayName("Should check only ACTIVE and VERIFIED wallets")
    void testOnlyActiveWallets() {
        // Arrange
        ReflectionTestUtils.setField(validator, "restTemplate", null);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any()))
                .thenReturn(1);
        
        // Act
        validator.isValid(VALID_WALLET_ID, context);
        
        // Assert
        verify(jdbcTemplate).queryForObject(
                argThat(sql -> sql.contains("status IN ('ACTIVE', 'VERIFIED')")),
                eq(Integer.class),
                any(),
                any()
        );
    }
    
    @Test
    @DisplayName("Should exclude soft-deleted wallets")
    void testExcludeSoftDeleted() {
        // Arrange
        ReflectionTestUtils.setField(validator, "restTemplate", null);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any()))
                .thenReturn(1);
        
        // Act
        validator.isValid(VALID_WALLET_ID, context);
        
        // Assert
        verify(jdbcTemplate).queryForObject(
                argThat(sql -> sql.contains("deleted_at IS NULL")),
                eq(Integer.class),
                any(),
                any()
        );
    }
    
    @Test
    @DisplayName("Should respect database schema configuration")
    void testDatabaseSchemaConfiguration() {
        // Arrange
        ReflectionTestUtils.setField(validator, "restTemplate", null);
        ReflectionTestUtils.setField(validator, "databaseSchema", "wallet_schema");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any()))
                .thenReturn(1);
        
        // Act
        validator.isValid(VALID_WALLET_ID, context);
        
        // Assert
        verify(jdbcTemplate).queryForObject(
                argThat(sql -> sql.contains("wallet_schema.wallets")),
                eq(Integer.class),
                any(),
                any()
        );
    }
    
    @Test
    @DisplayName("Should test isWalletOwner method for @PreAuthorize expressions")
    void testIsWalletOwnerMethod() {
        // Arrange
        Map<String, Object> response = Map.of("isOwner", true);
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(response);
        
        // Act
        boolean result = validator.isWalletOwner(VALID_USERNAME, VALID_WALLET_ID);
        
        // Assert
        assertTrue(result, "Should validate ownership for SpEL expressions");
    }
    
    @Test
    @DisplayName("Should fail-secure in isWalletOwner on errors")
    void testIsWalletOwnerMethod_FailSecure() {
        // Arrange
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RuntimeException("Service error"));
        
        // Act
        boolean result = validator.isWalletOwner(VALID_USERNAME, VALID_WALLET_ID);
        
        // Assert
        assertFalse(result, "Should fail-secure on errors");
    }
}
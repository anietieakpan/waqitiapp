package com.waqiti.user.security;

import com.waqiti.user.service.KeycloakAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Security Tests for OwnershipValidator
 *
 * PURPOSE: Validates IDOR prevention mechanisms
 *
 * CRITICAL TEST SCENARIOS:
 * 1. User CAN access their own resources
 * 2. User CANNOT access other users' resources
 * 3. Admin CAN access any user's resources
 * 4. Unauthenticated requests are REJECTED
 * 5. Invalid user IDs are REJECTED
 * 6. Bean failures default to DENY (fail-secure)
 *
 * @author Waqiti Security Team
 * @since 2025-11-08 (CRITICAL-002 Fix)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OwnershipValidator Security Tests")
class OwnershipValidatorTest {

    @Mock
    private KeycloakAuthService keycloakAuthService;

    @InjectMocks
    private OwnershipValidator ownershipValidator;

    private UUID authenticatedUserId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        authenticatedUserId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POSITIVE TEST CASES - Access Should Be Granted
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("✅ User CAN access their own data")
    void testUserCanAccessOwnData() {
        // Given: User is authenticated and requesting their own data
        when(keycloakAuthService.getCurrentUserId()).thenReturn(authenticatedUserId);
        when(keycloakAuthService.isAdmin()).thenReturn(false);

        // When: Validating ownership of own resource
        // Then: Should not throw exception
        assertDoesNotThrow(() ->
                ownershipValidator.validateUserOwnership(authenticatedUserId, "testOperation")
        );

        verify(keycloakAuthService, times(1)).getCurrentUserId();
        verify(keycloakAuthService, times(1)).isAdmin();
    }

    @Test
    @DisplayName("✅ Admin CAN access any user's data")
    void testAdminCanAccessAnyUserData() {
        // Given: User is an admin
        when(keycloakAuthService.getCurrentUserId()).thenReturn(authenticatedUserId);
        when(keycloakAuthService.isAdmin()).thenReturn(true);

        // When: Admin accessing another user's data
        // Then: Should not throw exception
        assertDoesNotThrow(() ->
                ownershipValidator.validateUserOwnership(otherUserId, "testAdminOperation")
        );

        verify(keycloakAuthService, times(1)).getCurrentUserId();
        verify(keycloakAuthService, times(1)).isAdmin();
    }

    @Test
    @DisplayName("✅ isAuthorized returns true for own data")
    void testIsAuthorizedForOwnData() {
        // Given
        when(keycloakAuthService.getCurrentUserId()).thenReturn(authenticatedUserId);
        when(keycloakAuthService.isAdmin()).thenReturn(false);

        // When
        boolean result = ownershipValidator.isAuthorized(authenticatedUserId);

        // Then
        assertTrue(result, "User should be authorized to access their own data");
    }

    @Test
    @DisplayName("✅ isAuthorized returns true for admin accessing any data")
    void testIsAuthorizedForAdmin() {
        // Given
        when(keycloakAuthService.getCurrentUserId()).thenReturn(authenticatedUserId);
        when(keycloakAuthService.isAdmin()).thenReturn(true);

        // When
        boolean result = ownershipValidator.isAuthorized(otherUserId);

        // Then
        assertTrue(result, "Admin should be authorized to access any user's data");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NEGATIVE TEST CASES - Access Should Be Denied (IDOR Prevention)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("❌ User CANNOT access other user's data (IDOR Prevention)")
    void testUserCannotAccessOtherUserData() {
        // Given: Regular user (not admin) trying to access another user's data
        when(keycloakAuthService.getCurrentUserId()).thenReturn(authenticatedUserId);
        when(keycloakAuthService.isAdmin()).thenReturn(false);

        // When: Attempting to access another user's resource
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> ownershipValidator.validateUserOwnership(otherUserId, "testIDORAttempt"),
                "Should throw exception for unauthorized access"
        );

        // Then: Should return 403 Forbidden
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode(),
                "Should return 403 Forbidden for IDOR attempt");
        assertTrue(exception.getReason().contains("Access denied"),
                "Error message should indicate access denial");

        verify(keycloakAuthService, times(1)).getCurrentUserId();
        verify(keycloakAuthService, times(1)).isAdmin();
    }

    @Test
    @DisplayName("❌ Unauthenticated request is REJECTED")
    void testUnauthenticatedRequestRejected() {
        // Given: No authentication (getCurrentUserId throws exception)
        when(keycloakAuthService.getCurrentUserId())
                .thenThrow(new RuntimeException("No authentication"));

        // When: Attempting to access resource without authentication
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> ownershipValidator.validateUserOwnership(otherUserId, "testUnauthenticated"),
                "Should throw exception for unauthenticated access"
        );

        // Then: Should return 401 Unauthorized
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode(),
                "Should return 401 Unauthorized for unauthenticated request");
        assertTrue(exception.getReason().contains("Authentication required"),
                "Error message should indicate authentication required");

        verify(keycloakAuthService, times(1)).getCurrentUserId();
        verify(keycloakAuthService, never()).isAdmin();
    }

    @Test
    @DisplayName("❌ Invalid user ID format is REJECTED")
    void testInvalidUserIdRejected() {
        // When: Attempting to validate with invalid UUID string
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> ownershipValidator.validateUserOwnership("invalid-uuid", "testInvalidId"),
                "Should throw exception for invalid user ID"
        );

        // Then: Should return 400 Bad Request
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode(),
                "Should return 400 Bad Request for invalid user ID");
        assertTrue(exception.getReason().contains("Invalid user ID format"),
                "Error message should indicate invalid format");

        verifyNoInteractions(keycloakAuthService);
    }

    @Test
    @DisplayName("❌ isAuthorized returns false for unauthorized access")
    void testIsAuthorizedReturnsFalseForUnauthorized() {
        // Given
        when(keycloakAuthService.getCurrentUserId()).thenReturn(authenticatedUserId);
        when(keycloakAuthService.isAdmin()).thenReturn(false);

        // When
        boolean result = ownershipValidator.isAuthorized(otherUserId);

        // Then
        assertFalse(result, "User should NOT be authorized to access other user's data");
    }

    @Test
    @DisplayName("❌ isAuthorized returns false on authentication failure (Fail Secure)")
    void testIsAuthorizedReturnsFalseOnError() {
        // Given: Authentication service throws exception
        when(keycloakAuthService.getCurrentUserId())
                .thenThrow(new RuntimeException("Auth failure"));

        // When
        boolean result = ownershipValidator.isAuthorized(otherUserId);

        // Then: Should default to DENY (fail-secure)
        assertFalse(result, "Should return false (deny access) on authentication failure");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ADMIN VALIDATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("✅ Admin role check PASSES for admin")
    void testRequireAdminPassesForAdmin() {
        // Given
        when(keycloakAuthService.isAdmin()).thenReturn(true);
        when(keycloakAuthService.getCurrentUserId()).thenReturn(authenticatedUserId);

        // When/Then: Should not throw exception
        assertDoesNotThrow(() ->
                ownershipValidator.requireAdmin("testAdminOperation")
        );
    }

    @Test
    @DisplayName("❌ Admin role check FAILS for non-admin")
    void testRequireAdminFailsForNonAdmin() {
        // Given
        when(keycloakAuthService.isAdmin()).thenReturn(false);
        when(keycloakAuthService.getCurrentUserId()).thenReturn(authenticatedUserId);

        // When
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> ownershipValidator.requireAdmin("testAdminRequired"),
                "Should throw exception for non-admin"
        );

        // Then
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Admin access required"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ROLE VALIDATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("✅ Role check PASSES when user has required role")
    void testRequireRolePassesWhenRolePresent() {
        // Given
        when(keycloakAuthService.hasRole("ACCOUNTANT")).thenReturn(true);
        when(keycloakAuthService.getCurrentUserId()).thenReturn(authenticatedUserId);

        // When/Then
        assertDoesNotThrow(() ->
                ownershipValidator.requireRole("ACCOUNTANT", "testRoleOperation")
        );
    }

    @Test
    @DisplayName("❌ Role check FAILS when user lacks required role")
    void testRequireRoleFailsWhenRoleMissing() {
        // Given
        when(keycloakAuthService.hasRole("ACCOUNTANT")).thenReturn(false);
        when(keycloakAuthService.getCurrentUserId()).thenReturn(authenticatedUserId);

        // When
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> ownershipValidator.requireRole("ACCOUNTANT", "testRoleRequired"),
                "Should throw exception when role missing"
        );

        // Then
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Required role: ACCOUNTANT"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONVENIENCE METHOD TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("✅ getCurrentUserId returns authenticated user ID")
    void testGetCurrentUserId() {
        // Given
        when(keycloakAuthService.getCurrentUserId()).thenReturn(authenticatedUserId);

        // When
        UUID result = ownershipValidator.getCurrentUserId();

        // Then
        assertEquals(authenticatedUserId, result);
    }

    @Test
    @DisplayName("❌ getCurrentUserId throws when not authenticated")
    void testGetCurrentUserIdThrowsWhenUnauthenticated() {
        // Given
        when(keycloakAuthService.getCurrentUserId())
                .thenThrow(new RuntimeException("Not authenticated"));

        // When/Then
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> ownershipValidator.getCurrentUserId()
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    @DisplayName("✅ isAdmin returns true for admin")
    void testIsAdminReturnsTrueForAdmin() {
        // Given
        when(keycloakAuthService.isAdmin()).thenReturn(true);

        // When
        boolean result = ownershipValidator.isAdmin();

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("❌ isAdmin returns false on error (Fail Secure)")
    void testIsAdminReturnsFalseOnError() {
        // Given
        when(keycloakAuthService.isAdmin())
                .thenThrow(new RuntimeException("Auth error"));

        // When
        boolean result = ownershipValidator.isAdmin();

        // Then: Should default to false (fail-secure)
        assertFalse(result, "Should return false on error (fail-secure)");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FAIL-SECURE BEHAVIOR TESTS (Most Critical for Security)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("🔒 FAIL-SECURE: Bean failure defaults to DENY")
    void testFailSecureBehaviorOnBeanFailure() {
        // Given: Keycloak service completely fails
        when(keycloakAuthService.getCurrentUserId())
                .thenThrow(new RuntimeException("Service unavailable"));

        // When: Attempting to validate ownership
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> ownershipValidator.validateUserOwnership(otherUserId, "testFailSecure")
        );

        // Then: Should default to DENY (401 Unauthorized)
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode(),
                "Should fail-secure to UNAUTHORIZED when bean fails");
    }

    @Test
    @DisplayName("🔒 FAIL-SECURE: isAuthorized defaults to FALSE on failure")
    void testIsAuthorizedFailsSecure() {
        // Given
        when(keycloakAuthService.getCurrentUserId())
                .thenThrow(new RuntimeException("Service failure"));

        // When
        boolean result = ownershipValidator.isAuthorized(authenticatedUserId);

        // Then: Should default to false (DENY)
        assertFalse(result, "Should fail-secure to false (deny access)");
    }

    @Test
    @DisplayName("🔒 FAIL-SECURE: isAdmin defaults to FALSE on failure")
    void testIsAdminFailsSecure() {
        // Given
        when(keycloakAuthService.isAdmin())
                .thenThrow(new RuntimeException("Service failure"));

        // When
        boolean result = ownershipValidator.isAdmin();

        // Then: Should default to false (DENY admin access)
        assertFalse(result, "Should fail-secure to false (deny admin access)");
    }
}

package com.waqiti.payment.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for RBACService
 *
 * @author Waqiti Security Team
 * @version 3.0.0
 * @since 2025-10-11
 */
@ExtendWith(MockitoExtension.class)
class RBACServiceTest {

    @InjectMocks
    private RBACService rbacService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test-user");
    }

    @Test
    void testIsAdmin_WithSuperAdminRole_ReturnsTrue() {
        // Arrange
        Collection<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")
        );
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        // Act
        boolean result = rbacService.isAdmin();

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void testIsAdmin_WithAdminRole_ReturnsTrue() {
        // Arrange
        Collection<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_ADMIN")
        );
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        // Act
        boolean result = rbacService.isAdmin();

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void testIsAdmin_WithUserRole_ReturnsFalse() {
        // Arrange
        Collection<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_USER")
        );
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        // Act
        boolean result = rbacService.isAdmin();

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void testCanPerformTellerOperations_WithTellerRole_ReturnsTrue() {
        // Arrange
        Collection<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_TELLER")
        );
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        // Act
        boolean result = rbacService.canPerformTellerOperations();

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void testCanPerformTellerOperations_WithBranchManagerRole_ReturnsTrue() {
        // Arrange
        Collection<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER")
        );
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        // Act
        boolean result = rbacService.canPerformTellerOperations();

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void testCanPerformTellerOperations_WithSuperAdminRole_ReturnsTrue() {
        // Arrange
        Collection<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")
        );
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        // Act
        boolean result = rbacService.canPerformTellerOperations();

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void testCanPerformTellerOperations_WithUserRole_ReturnsFalse() {
        // Arrange
        Collection<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_USER")
        );
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        // Act
        boolean result = rbacService.canPerformTellerOperations();

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void testCanPerformPayrollOperations_WithPayrollAdminRole_ReturnsTrue() {
        // Arrange
        Collection<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_PAYROLL_ADMIN")
        );
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        // Act
        boolean result = rbacService.canPerformPayrollOperations();

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void testCanPerformPayrollOperations_WithUserRole_ReturnsFalse() {
        // Arrange
        Collection<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_USER")
        );
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        // Act
        boolean result = rbacService.canPerformPayrollOperations();

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void testCanAccessComplianceData_WithComplianceOfficerRole_ReturnsTrue() {
        // Arrange
        Collection<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_COMPLIANCE_OFFICER")
        );
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        // Act
        boolean result = rbacService.canAccessComplianceData();

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void testCanAccessComplianceData_WithUserRole_ReturnsFalse() {
        // Arrange
        Collection<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_USER")
        );
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        // Act
        boolean result = rbacService.canAccessComplianceData();

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void testIsMerchant_WithMerchantRole_ReturnsTrue() {
        // Arrange
        Collection<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_MERCHANT")
        );
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        // Act
        boolean result = rbacService.isMerchant();

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void testIsMerchant_WithUserRole_ReturnsFalse() {
        // Arrange
        Collection<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_USER")
        );
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        // Act
        boolean result = rbacService.isMerchant();

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void testCanAccessCrossAccount_WithAdminRole_ReturnsTrue() {
        // Arrange
        Collection<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_ADMIN")
        );
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        // Act
        boolean result = rbacService.canAccessCrossAccount();

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void testCanAccessCrossAccount_WithUserRole_ReturnsFalse() {
        // Arrange
        Collection<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_USER")
        );
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        // Act
        boolean result = rbacService.canAccessCrossAccount();

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void testGetCurrentUserRoles_ReturnsCorrectRoles() {
        // Arrange
        Collection<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_USER"),
            new SimpleGrantedAuthority("ROLE_MERCHANT")
        );
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        // Act
        Set<String> roles = rbacService.getCurrentUserRoles();

        // Assert
        assertThat(roles).containsExactlyInAnyOrder("ROLE_USER", "ROLE_MERCHANT");
    }

    @Test
    void testGetCurrentUserRoles_NoAuthentication_ReturnsEmptySet() {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(null);

        // Act
        Set<String> roles = rbacService.getCurrentUserRoles();

        // Assert
        assertThat(roles).isEmpty();
    }

    @Test
    void testGetCurrentUserRoles_NotAuthenticated_ReturnsEmptySet() {
        // Arrange
        when(authentication.isAuthenticated()).thenReturn(false);

        // Act
        Set<String> roles = rbacService.getCurrentUserRoles();

        // Assert
        assertThat(roles).isEmpty();
    }

    @Test
    void testRoleHierarchy_SuperAdminCanPerformAllOperations() {
        // Arrange
        Collection<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")
        );
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        // Act & Assert
        assertThat(rbacService.isAdmin()).isTrue();
        assertThat(rbacService.canPerformTellerOperations()).isTrue();
        assertThat(rbacService.canPerformPayrollOperations()).isTrue();
        assertThat(rbacService.canAccessComplianceData()).isTrue();
        assertThat(rbacService.canAccessCrossAccount()).isTrue();
    }

    @Test
    void testMultipleRoles_UserHasMultiplePermissions() {
        // Arrange
        Collection<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_USER"),
            new SimpleGrantedAuthority("ROLE_MERCHANT"),
            new SimpleGrantedAuthority("ROLE_TELLER")
        );
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        // Act & Assert
        assertThat(rbacService.isAdmin()).isFalse();
        assertThat(rbacService.isMerchant()).isTrue();
        assertThat(rbacService.canPerformTellerOperations()).isTrue();
    }

    @Test
    void testLogAuthorizationCheck_AccessGranted() {
        // Arrange
        Collection<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_ADMIN")
        );
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        // Act
        rbacService.logAuthorizationCheck("CREATE_PAYMENT", "payment-123", true);

        // Assert - verify no exceptions thrown
        verify(authentication, atLeastOnce()).getName();
    }

    @Test
    void testLogAuthorizationCheck_AccessDenied() {
        // Arrange
        Collection<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_USER")
        );
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        // Act
        rbacService.logAuthorizationCheck("ADMIN_OPERATION", "resource-456", false);

        // Assert - verify no exceptions thrown
        verify(authentication, atLeastOnce()).getName();
    }

    @Test
    void testAuthorizationCheck_NoAuthentication_ReturnsFalse() {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(null);

        // Act
        boolean result = rbacService.isAdmin();

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void testAuthorizationCheck_NotAuthenticated_ReturnsFalse() {
        // Arrange
        when(authentication.isAuthenticated()).thenReturn(false);

        // Act
        boolean result = rbacService.isAdmin();

        // Assert
        assertThat(result).isFalse();
    }
}

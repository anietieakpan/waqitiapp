package com.waqiti.auth.service;

import com.waqiti.auth.domain.User;
import com.waqiti.auth.dto.LoginRequest;
import com.waqiti.auth.dto.LoginResponse;
import com.waqiti.auth.repository.UserRepository;
import com.waqiti.auth.repository.RefreshTokenRepository;
import com.waqiti.auth.repository.AuthAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthenticationService.
 *
 * Tests cover:
 * - Successful authentication
 * - Failed authentication scenarios
 * - Account locking
 * - Token generation
 * - Audit logging
 *
 * @author Waqiti Platform Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationService Unit Tests")
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuthAuditLogRepository auditLogRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthenticationService authenticationService;

    private User testUser;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .id(UUID.randomUUID())
            .username("testuser")
            .email("test@example.com")
            .passwordHash("$2a$10$hashed_password")
            .accountStatus(User.AccountStatus.ACTIVE)
            .emailVerified(true)
            .failedLoginAttempts(0)
            .build();

        loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password123");
    }

    @Test
    @DisplayName("Should authenticate user successfully")
    void shouldAuthenticateSuccessfully() {
        // Given
        when(userRepository.findByUsernameAndDeletedFalse("testuser"))
            .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", "$2a$10$hashed_password"))
            .thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(any(User.class)))
            .thenReturn("access_token");
        when(jwtTokenProvider.generateRefreshToken(any(User.class)))
            .thenReturn("refresh_token");

        // When
        LoginResponse response = authenticationService.authenticate(loginRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access_token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh_token");
        assertThat(response.getUserId()).isEqualTo(testUser.getId());

        // Verify user updated
        verify(userRepository).save(argThat(user ->
            user.getLastLoginAt() != null &&
            user.getFailedLoginAttempts() == 0
        ));

        // Verify audit log created
        verify(auditLogRepository).save(any());
    }

    @Test
    @DisplayName("Should fail authentication with wrong password")
    void shouldFailWithWrongPassword() {
        // Given
        when(userRepository.findByUsernameAndDeletedFalse("testuser"))
            .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrong_password", "$2a$10$hashed_password"))
            .thenReturn(false);

        loginRequest.setPassword("wrong_password");

        // When & Then
        assertThatThrownBy(() -> authenticationService.authenticate(loginRequest))
            .isInstanceOf(BadCredentialsException.class)
            .hasMessageContaining("Invalid credentials");

        // Verify failed attempts incremented
        verify(userRepository).save(argThat(user ->
            user.getFailedLoginAttempts() == 1
        ));

        // Verify audit log created for failure
        verify(auditLogRepository).save(any());
    }

    @Test
    @DisplayName("Should lock account after 5 failed attempts")
    void shouldLockAccountAfter5FailedAttempts() {
        // Given
        testUser.setFailedLoginAttempts(4); // 4 previous failures
        when(userRepository.findByUsernameAndDeletedFalse("testuser"))
            .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrong_password", "$2a$10$hashed_password"))
            .thenReturn(false);

        loginRequest.setPassword("wrong_password");

        // When & Then
        assertThatThrownBy(() -> authenticationService.authenticate(loginRequest))
            .isInstanceOf(BadCredentialsException.class);

        // Verify account locked
        verify(userRepository).save(argThat(user ->
            user.getFailedLoginAttempts() == 5 &&
            user.getAccountStatus() == User.AccountStatus.LOCKED &&
            user.getAccountLockedUntil() != null
        ));
    }

    @Test
    @DisplayName("Should fail authentication for locked account")
    void shouldFailForLockedAccount() {
        // Given
        testUser.lockAccount(30); // Locked for 30 minutes
        when(userRepository.findByUsernameAndDeletedFalse("testuser"))
            .thenReturn(Optional.of(testUser));

        // When & Then
        assertThatThrownBy(() -> authenticationService.authenticate(loginRequest))
            .isInstanceOf(AccountLockedException.class)
            .hasMessageContaining("Account is locked");

        // Verify no password check attempted
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("Should fail authentication for inactive account")
    void shouldFailForInactiveAccount() {
        // Given
        testUser.setAccountStatus(User.AccountStatus.SUSPENDED);
        when(userRepository.findByUsernameAndDeletedFalse("testuser"))
            .thenReturn(Optional.of(testUser));

        // When & Then
        assertThatThrownBy(() -> authenticationService.authenticate(loginRequest))
            .isInstanceOf(AccountDisabledException.class)
            .hasMessageContaining("Account is not active");
    }

    @Test
    @DisplayName("Should fail authentication for unverified email")
    void shouldFailForUnverifiedEmail() {
        // Given
        testUser.setEmailVerified(false);
        when(userRepository.findByUsernameAndDeletedFalse("testuser"))
            .thenReturn(Optional.of(testUser));

        // When & Then
        assertThatThrownBy(() -> authenticationService.authenticate(loginRequest))
            .isInstanceOf(EmailNotVerifiedException.class)
            .hasMessageContaining("Email not verified");
    }

    @Test
    @DisplayName("Should fail authentication for non-existent user")
    void shouldFailForNonExistentUser() {
        // Given
        when(userRepository.findByUsernameAndDeletedFalse("testuser"))
            .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> authenticationService.authenticate(loginRequest))
            .isInstanceOf(BadCredentialsException.class)
            .hasMessageContaining("Invalid credentials");

        // Verify audit log created
        verify(auditLogRepository).save(any());
    }

    @Test
    @DisplayName("Should refresh access token successfully")
    void shouldRefreshAccessToken() {
        // Given
        String refreshToken = "valid_refresh_token";
        when(jwtTokenProvider.validateRefreshToken(refreshToken))
            .thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(refreshToken))
            .thenReturn(testUser.getId());
        when(userRepository.findById(testUser.getId()))
            .thenReturn(Optional.of(testUser));
        when(jwtTokenProvider.generateAccessToken(testUser))
            .thenReturn("new_access_token");

        // When
        String newAccessToken = authenticationService.refreshAccessToken(refreshToken);

        // Then
        assertThat(newAccessToken).isEqualTo("new_access_token");
    }

    @Test
    @DisplayName("Should fail to refresh with invalid token")
    void shouldFailToRefreshWithInvalidToken() {
        // Given
        String invalidToken = "invalid_token";
        when(jwtTokenProvider.validateRefreshToken(invalidToken))
            .thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> authenticationService.refreshAccessToken(invalidToken))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("Invalid refresh token");
    }

    @Test
    @DisplayName("Should invalidate token on logout")
    void shouldInvalidateTokenOnLogout() {
        // Given
        String accessToken = "valid_access_token";

        // When
        authenticationService.invalidateToken(accessToken);

        // Then
        // Verify token added to blacklist (implementation specific)
        // This would typically check a Redis cache or database table
        verify(tokenBlacklistService).addToBlacklist(accessToken);
    }

    @Test
    @DisplayName("Should unlock account automatically after timeout")
    void shouldUnlockAccountAfterTimeout() {
        // Given
        testUser.setAccountLockedUntil(LocalDateTime.now().minusMinutes(1)); // Lock expired
        testUser.setAccountStatus(User.AccountStatus.LOCKED);
        when(userRepository.findByUsernameAndDeletedFalse("testuser"))
            .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", "$2a$10$hashed_password"))
            .thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(any(User.class)))
            .thenReturn("access_token");
        when(jwtTokenProvider.generateRefreshToken(any(User.class)))
            .thenReturn("refresh_token");

        // When
        LoginResponse response = authenticationService.authenticate(loginRequest);

        // Then
        assertThat(response).isNotNull();
        verify(userRepository).save(argThat(user ->
            user.isAccountNonLocked() // Should be unlocked now
        ));
    }

    @Test
    @DisplayName("Should reset failed attempts on successful login")
    void shouldResetFailedAttemptsOnSuccess() {
        // Given
        testUser.setFailedLoginAttempts(3);
        when(userRepository.findByUsernameAndDeletedFalse("testuser"))
            .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", "$2a$10$hashed_password"))
            .thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(any(User.class)))
            .thenReturn("access_token");
        when(jwtTokenProvider.generateRefreshToken(any(User.class)))
            .thenReturn("refresh_token");

        // When
        authenticationService.authenticate(loginRequest);

        // Then
        verify(userRepository).save(argThat(user ->
            user.getFailedLoginAttempts() == 0 &&
            user.getAccountLockedUntil() == null
        ));
    }

    // Custom exceptions for test clarity
    static class AccountLockedException extends RuntimeException {
        public AccountLockedException(String message) {
            super(message);
        }
    }

    static class AccountDisabledException extends RuntimeException {
        public AccountDisabledException(String message) {
            super(message);
        }
    }

    static class EmailNotVerifiedException extends RuntimeException {
        public EmailNotVerifiedException(String message) {
            super(message);
        }
    }

    static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }
}

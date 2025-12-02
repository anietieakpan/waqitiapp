package com.waqiti.user.service;

import com.waqiti.user.domain.User;
import com.waqiti.user.domain.VerificationToken;
import com.waqiti.user.domain.VerificationType;
import com.waqiti.user.dto.PasswordResetRequest;
import com.waqiti.user.exception.InvalidVerificationTokenException;
import com.waqiti.user.repository.UserRepository;
import com.waqiti.user.repository.VerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for CRITICAL-001 fix: Password Reset TOCTOU Race Condition
 *
 * This test validates that the pessimistic locking implementation prevents
 * concurrent token reuse attacks. It simulates real-world attack scenarios
 * where multiple threads attempt to use the same password reset token simultaneously.
 *
 * Test Scenarios:
 * 1. Concurrent token reuse attack (1000 threads)
 * 2. Sequential token use (should fail on second attempt)
 * 3. Expired token handling
 * 4. Already used token handling
 * 5. Performance under concurrent load
 *
 * Expected Behavior:
 * - Exactly ONE thread succeeds in resetting password
 * - All other threads receive InvalidVerificationTokenException
 * - Token is marked as used atomically
 * - No race condition window exists
 *
 * Attack Resistance Validation:
 * - 1000 concurrent requests with same token
 * - Only first request succeeds
 * - Remaining 999 requests fail with "already used" error
 * - User password is changed exactly once
 *
 * @author Waqiti Security Team
 * @since 1.0.0
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Password Reset Concurrency Integration Test - CRITICAL-001 Fix Validation")
class PasswordResetConcurrencyIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private String validToken;
    private static final String NEW_PASSWORD = "NewSecurePassword123!";
    private static final int CONCURRENT_THREADS = 1000;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up any existing test data
        tokenRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user
        testUser = User.builder()
                .id(UUID.randomUUID())
                .username("test.user@example.com")
                .email("test.user@example.com")
                .password(passwordEncoder.encode("OldPassword123!"))
                .firstName("Test")
                .lastName("User")
                .enabled(true)
                .accountNonLocked(true)
                .build();
        testUser = userRepository.save(testUser);

        // Create valid password reset token
        VerificationToken token = VerificationToken.builder()
                .id(UUID.randomUUID())
                .token(UUID.randomUUID().toString())
                .userId(testUser.getId())
                .type(VerificationType.PASSWORD_RESET)
                .expiryDate(LocalDateTime.now().plusHours(24))
                .used(false)
                .build();
        token = tokenRepository.save(token);
        validToken = token.getToken();
    }

    @Test
    @DisplayName("CRITICAL-001: Prevent concurrent token reuse - 1000 thread attack simulation")
    void testConcurrentPasswordResetPreventsTokenReuse() throws InterruptedException {
        // Given: 1000 concurrent requests with same token
        int threadCount = CONCURRENT_THREADS;
        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<String> failureReasons = new CopyOnWriteArrayList<>();

        // When: All threads attempt password reset simultaneously
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();

                    // Attempt password reset
                    PasswordResetRequest request = PasswordResetRequest.builder()
                            .token(validToken)
                            .newPassword(NEW_PASSWORD)
                            .build();

                    userService.resetPassword(request);
                    successCount.incrementAndGet();

                } catch (InvalidVerificationTokenException e) {
                    failureCount.incrementAndGet();
                    failureReasons.add(e.getMessage());
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    failureReasons.add("Unexpected error: " + e.getMessage());
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start all threads at once
        startLatch.countDown();

        // Wait for all threads to complete (max 30 seconds)
        boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: Exactly ONE request should succeed
        assertThat(completed)
                .as("All threads should complete within 30 seconds")
                .isTrue();

        assertThat(successCount.get())
                .as("Exactly ONE thread should successfully reset password")
                .isEqualTo(1);

        assertThat(failureCount.get())
                .as("Remaining %d threads should fail with token validation error", threadCount - 1)
                .isEqualTo(threadCount - 1);

        // Verify token is marked as used
        VerificationToken usedToken = tokenRepository.findByToken(validToken)
                .orElseThrow(() -> new AssertionError("Token should still exist"));

        assertThat(usedToken.isUsed())
                .as("Token should be marked as used")
                .isTrue();

        // Verify user password was changed exactly once
        User updatedUser = userRepository.findById(testUser.getId())
                .orElseThrow(() -> new AssertionError("User should still exist"));

        assertThat(passwordEncoder.matches(NEW_PASSWORD, updatedUser.getPassword()))
                .as("User password should be updated to new password")
                .isTrue();

        // Verify failure reasons
        assertThat(failureReasons)
                .as("All failures should be due to token validation")
                .allMatch(reason -> reason.contains("expired") || reason.contains("already used"));
    }

    @Test
    @DisplayName("Sequential password reset - second attempt should fail")
    void testSequentialPasswordResetFailsOnSecondAttempt() {
        // Given: Valid password reset request
        PasswordResetRequest firstRequest = PasswordResetRequest.builder()
                .token(validToken)
                .newPassword(NEW_PASSWORD)
                .build();

        // When: First request succeeds
        boolean firstResult = userService.resetPassword(firstRequest);
        assertThat(firstResult).isTrue();

        // Then: Second request with same token should fail
        PasswordResetRequest secondRequest = PasswordResetRequest.builder()
                .token(validToken)
                .newPassword("AnotherPassword456!")
                .build();

        assertThatThrownBy(() -> userService.resetPassword(secondRequest))
                .isInstanceOf(InvalidVerificationTokenException.class)
                .hasMessageContaining("already used");

        // Verify password was changed only once (to first request's password)
        User updatedUser = userRepository.findById(testUser.getId())
                .orElseThrow(() -> new AssertionError("User should exist"));

        assertThat(passwordEncoder.matches(NEW_PASSWORD, updatedUser.getPassword()))
                .as("Password should match first request's password")
                .isTrue();

        assertThat(passwordEncoder.matches("AnotherPassword456!", updatedUser.getPassword()))
                .as("Password should NOT match second request's password")
                .isFalse();
    }

    @Test
    @DisplayName("Expired token should be rejected")
    void testExpiredTokenIsRejected() {
        // Given: Expired token
        VerificationToken expiredToken = VerificationToken.builder()
                .id(UUID.randomUUID())
                .token(UUID.randomUUID().toString())
                .userId(testUser.getId())
                .type(VerificationType.PASSWORD_RESET)
                .expiryDate(LocalDateTime.now().minusHours(1)) // Expired 1 hour ago
                .used(false)
                .build();
        expiredToken = tokenRepository.save(expiredToken);

        // When/Then: Password reset should fail
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token(expiredToken.getToken())
                .newPassword(NEW_PASSWORD)
                .build();

        assertThatThrownBy(() -> userService.resetPassword(request))
                .isInstanceOf(InvalidVerificationTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("Already used token should be rejected")
    void testAlreadyUsedTokenIsRejected() {
        // Given: Already used token
        VerificationToken usedToken = VerificationToken.builder()
                .id(UUID.randomUUID())
                .token(UUID.randomUUID().toString())
                .userId(testUser.getId())
                .type(VerificationType.PASSWORD_RESET)
                .expiryDate(LocalDateTime.now().plusHours(24))
                .used(true) // Already used
                .build();
        usedToken = tokenRepository.save(usedToken);

        // When/Then: Password reset should fail
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token(usedToken.getToken())
                .newPassword(NEW_PASSWORD)
                .build();

        assertThatThrownBy(() -> userService.resetPassword(request))
                .isInstanceOf(InvalidVerificationTokenException.class)
                .hasMessageContaining("already used");
    }

    @Test
    @DisplayName("Performance test - concurrent requests should complete within acceptable time")
    void testPerformanceUnderConcurrentLoad() throws InterruptedException {
        // Given: 100 concurrent requests (smaller than attack simulation for performance testing)
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);

        long startTime = System.currentTimeMillis();

        // When: All threads attempt password reset
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    PasswordResetRequest request = PasswordResetRequest.builder()
                            .token(validToken)
                            .newPassword(NEW_PASSWORD)
                            .build();
                    userService.resetPassword(request);
                } catch (Exception ignored) {
                    // Expected failures
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = completionLatch.await(10, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;

        executorService.shutdown();

        // Then: All requests should complete within 10 seconds
        assertThat(completed)
                .as("100 concurrent requests should complete within 10 seconds")
                .isTrue();

        assertThat(duration)
                .as("Performance should be acceptable (< 10 seconds for 100 concurrent requests)")
                .isLessThan(10000);

        System.out.printf("Performance: 100 concurrent password reset requests completed in %d ms%n", duration);
    }

    @Test
    @DisplayName("Wrong token type should be rejected")
    void testWrongTokenTypeIsRejected() {
        // Given: Email verification token (not password reset)
        VerificationToken emailToken = VerificationToken.builder()
                .id(UUID.randomUUID())
                .token(UUID.randomUUID().toString())
                .userId(testUser.getId())
                .type(VerificationType.EMAIL) // Wrong type
                .expiryDate(LocalDateTime.now().plusHours(24))
                .used(false)
                .build();
        emailToken = tokenRepository.save(emailToken);

        // When/Then: Password reset should fail
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token(emailToken.getToken())
                .newPassword(NEW_PASSWORD)
                .build();

        assertThatThrownBy(() -> userService.resetPassword(request))
                .isInstanceOf(InvalidVerificationTokenException.class)
                .hasMessageContaining("not a password reset token");
    }

    @Test
    @DisplayName("Database isolation - verify SERIALIZABLE prevents phantom reads")
    void testDatabaseIsolationLevel() throws InterruptedException {
        // This test verifies that SERIALIZABLE isolation level is working correctly
        // by attempting to create a scenario where phantom reads could occur

        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);

        // Thread 1: Read and update token
        Thread thread1 = new Thread(() -> {
            try {
                PasswordResetRequest request = PasswordResetRequest.builder()
                        .token(validToken)
                        .newPassword(NEW_PASSWORD)
                        .build();
                userService.resetPassword(request);
                successCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                latch.countDown();
            }
        });

        // Thread 2: Concurrent read and update attempt
        Thread thread2 = new Thread(() -> {
            try {
                // Small delay to ensure thread1 starts first
                Thread.sleep(10);
                PasswordResetRequest request = PasswordResetRequest.builder()
                        .token(validToken)
                        .newPassword("DifferentPassword789!")
                        .build();
                userService.resetPassword(request);
                successCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                latch.countDown();
            }
        });

        thread1.start();
        thread2.start();

        latch.await(5, TimeUnit.SECONDS);

        // Exactly one should succeed
        assertThat(successCount.get())
                .as("SERIALIZABLE isolation should prevent both threads from succeeding")
                .isEqualTo(1);
    }
}

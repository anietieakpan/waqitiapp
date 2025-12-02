package com.waqiti.payment.security;

import com.waqiti.common.audit.ComprehensiveAuditService;
import com.waqiti.payment.dto.PaymentRequest;
import com.waqiti.payment.exception.MfaRequiredException;
import com.waqiti.payment.exception.MfaVerificationFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for High-Value Transaction MFA Service
 * Tests PCI DSS and security compliance for high-value payments
 */
@ExtendWith(MockitoExtension.class)
class HighValueTransactionMfaServiceTest {

    @Mock
    private ComprehensiveAuditService auditService;

    @InjectMocks
    private HighValueTransactionMfaService mfaService;

    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("5000");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mfaService, "highValueThreshold", HIGH_VALUE_THRESHOLD);
        ReflectionTestUtils.setField(mfaService, "mfaRequired", true);
    }

    @Test
    void testRequiresMfa_HighValueTransaction() {
        // Given
        PaymentRequest request = createPaymentRequest(new BigDecimal("10000"));

        // When
        boolean requiresMfa = mfaService.requiresMfa(request);

        // Then
        assertTrue(requiresMfa, "High-value transaction should require MFA");
    }

    @Test
    void testRequiresMfa_LowValueTransaction() {
        // Given
        PaymentRequest request = createPaymentRequest(new BigDecimal("100"));

        // When
        boolean requiresMfa = mfaService.requiresMfa(request);

        // Then
        assertFalse(requiresMfa, "Low-value transaction should not require MFA");
    }

    @Test
    void testRequiresMfa_ExactThreshold() {
        // Given
        PaymentRequest request = createPaymentRequest(HIGH_VALUE_THRESHOLD);

        // When
        boolean requiresMfa = mfaService.requiresMfa(request);

        // Then
        assertTrue(requiresMfa, "Transaction at exact threshold should require MFA");
    }

    @Test
    void testVerifyMfa_Success() {
        // Given
        String userId = "user-123";
        String mfaCode = "123456";
        PaymentRequest request = createPaymentRequest(new BigDecimal("10000"));

        // When
        boolean verified = mfaService.verifyMfa(userId, mfaCode, request);

        // Then
        assertTrue(verified, "Valid MFA code should verify successfully");
        verify(auditService, times(1)).auditSecurityEvent(
            eq("MFA_VERIFICATION_SUCCESS"),
            eq(userId),
            anyString(),
            any()
        );
    }

    @Test
    void testVerifyMfa_InvalidCode() {
        // Given
        String userId = "user-123";
        String invalidCode = "000000";
        PaymentRequest request = createPaymentRequest(new BigDecimal("10000"));

        // When/Then
        assertThrows(MfaVerificationFailedException.class, 
            () -> mfaService.verifyMfa(userId, invalidCode, request));
        
        verify(auditService, times(1)).auditSecurityEvent(
            eq("MFA_VERIFICATION_FAILED"),
            eq(userId),
            anyString(),
            any()
        );
    }

    @Test
    void testVerifyMfa_ExpiredCode() {
        // Given
        String userId = "user-123";
        String expiredCode = "expired";
        PaymentRequest request = createPaymentRequest(new BigDecimal("10000"));

        // When/Then
        assertThrows(MfaVerificationFailedException.class,
            () -> mfaService.verifyMfa(userId, expiredCode, request));
    }

    @Test
    void testGenerateMfaChallenge() {
        // Given
        String userId = "user-123";
        PaymentRequest request = createPaymentRequest(new BigDecimal("10000"));

        // When
        String challengeId = mfaService.generateMfaChallenge(userId, request);

        // Then
        assertNotNull(challengeId, "Challenge ID should be generated");
        verify(auditService, times(1)).auditSecurityEvent(
            eq("MFA_CHALLENGE_GENERATED"),
            eq(userId),
            anyString(),
            any()
        );
    }

    @Test
    void testEnforceMfa_WhenRequired() {
        // Given
        String userId = "user-123";
        PaymentRequest request = createPaymentRequest(new BigDecimal("10000"));
        request.setMfaCode(null);

        // When/Then
        assertThrows(MfaRequiredException.class,
            () -> mfaService.enforceMfa(userId, request));
    }

    @Test
    void testEnforceMfa_WhenProvided() {
        // Given
        String userId = "user-123";
        PaymentRequest request = createPaymentRequest(new BigDecimal("10000"));
        request.setMfaCode("123456");

        // When
        assertDoesNotThrow(() -> mfaService.enforceMfa(userId, request));

        // Then
        verify(auditService, times(1)).auditSecurityEvent(
            eq("MFA_VERIFICATION_SUCCESS"),
            eq(userId),
            anyString(),
            any()
        );
    }

    @Test
    void testEnforceMfa_LowValueNoMfaRequired() {
        // Given
        String userId = "user-123";
        PaymentRequest request = createPaymentRequest(new BigDecimal("100"));
        request.setMfaCode(null);

        // When/Then
        assertDoesNotThrow(() -> mfaService.enforceMfa(userId, request));
        
        // No MFA verification should occur
        verify(auditService, never()).auditSecurityEvent(
            eq("MFA_VERIFICATION_SUCCESS"),
            anyString(),
            anyString(),
            any()
        );
    }

    @Test
    void testMfaRateLimiting() {
        // Given
        String userId = "user-123";
        PaymentRequest request = createPaymentRequest(new BigDecimal("10000"));

        // When - Attempt multiple failed verifications
        for (int i = 0; i < 5; i++) {
            try {
                mfaService.verifyMfa(userId, "wrong-code", request);
            } catch (MfaVerificationFailedException e) {
                // Expected
            }
        }

        // Then - 6th attempt should be rate limited
        assertThrows(MfaVerificationFailedException.class,
            () -> mfaService.verifyMfa(userId, "123456", request));
    }

    @Test
    void testInternationalTransactionMfa() {
        // Given
        String userId = "user-123";
        PaymentRequest request = createPaymentRequest(new BigDecimal("10000"));
        request.setCurrency("EUR");
        request.setInternational(true);

        // When
        boolean requiresMfa = mfaService.requiresMfa(request);

        // Then
        assertTrue(requiresMfa, "International high-value transaction should require MFA");
    }

    @Test
    void testCryptoTransactionMfa() {
        // Given
        String userId = "user-123";
        PaymentRequest request = createPaymentRequest(new BigDecimal("10000"));
        request.setPaymentMethod("CRYPTO");

        // When
        boolean requiresMfa = mfaService.requiresMfa(request);

        // Then
        assertTrue(requiresMfa, "Crypto transaction should require MFA");
    }

    // Helper methods

    private PaymentRequest createPaymentRequest(BigDecimal amount) {
        return PaymentRequest.builder()
            .paymentId(UUID.randomUUID().toString())
            .amount(amount)
            .currency("USD")
            .senderId(UUID.randomUUID().toString())
            .recipientId(UUID.randomUUID().toString())
            .paymentMethod("CARD")
            .international(false)
            .timestamp(LocalDateTime.now())
            .build();
    }
}
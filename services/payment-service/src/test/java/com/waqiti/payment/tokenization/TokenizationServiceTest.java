package com.waqiti.payment.tokenization;

import com.waqiti.common.exception.TokenizationException;
import com.waqiti.payment.tokenization.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for TokenizationService
 *
 * @author Waqiti Security Team
 * @version 3.0.0
 * @since 2025-10-11
 */
@ExtendWith(MockitoExtension.class)
class TokenizationServiceTest {

    @Mock
    private VaultTokenizationClient vaultClient;

    @Mock
    private TokenizationAuditService auditService;

    @Mock
    private TokenizationMetricsService metricsService;

    @InjectMocks
    private TokenizationService tokenizationService;

    private TokenizationRequest validRequest;
    private String testCardNumber;
    private String testUserId;

    @BeforeEach
    void setUp() {
        testCardNumber = "4532123456789012";
        testUserId = UUID.randomUUID().toString();

        Map<String, String> metadata = new HashMap<>();
        metadata.put("dataType", "CREDIT_CARD");

        validRequest = TokenizationRequest.builder()
            .sensitiveData(testCardNumber)
            .dataType("CREDIT_CARD")
            .userId(testUserId)
            .ipAddress("192.168.1.1")
            .metadata(metadata)
            .build();

        // Mock encryption key
        SecretKey mockKey = new SecretKeySpec(new byte[32], "AES");
        when(vaultClient.getEncryptionKey(anyString())).thenReturn(mockKey);
    }

    @Test
    void testTokenizeValidCreditCard_Success() {
        // Arrange
        doNothing().when(vaultClient).storeToken(anyString(), anyString(), any(), anyLong());

        // Act
        TokenizationResponse response = tokenizationService.tokenize(validRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getToken()).isNotNull();
        assertThat(response.getTokenType()).isEqualTo("CREDIT_CARD");
        assertThat(response.getExpiresAt()).isGreaterThan(System.currentTimeMillis());

        verify(vaultClient, times(1)).storeToken(anyString(), anyString(), any(), anyLong());
        verify(auditService, times(1)).logTokenization(
            eq(testUserId),
            eq("CREDIT_CARD"),
            anyString(),
            eq("192.168.1.1"),
            eq(true),
            anyString()
        );
        verify(metricsService, times(1)).recordTokenization(
            eq("CREDIT_CARD"),
            anyLong(),
            eq(true)
        );
    }

    @Test
    void testTokenizeAccountNumber_Success() {
        // Arrange
        TokenizationRequest accountRequest = TokenizationRequest.builder()
            .sensitiveData("123456789012")
            .dataType("ACCOUNT_NUMBER")
            .userId(testUserId)
            .build();

        doNothing().when(vaultClient).storeToken(anyString(), anyString(), any(), anyLong());

        // Act
        TokenizationResponse response = tokenizationService.tokenize(accountRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getToken()).isNotNull();
        assertThat(response.getTokenType()).isEqualTo("ACCOUNT_NUMBER");

        verify(vaultClient, times(1)).storeToken(anyString(), anyString(), any(), anyLong());
    }

    @Test
    void testTokenizeInvalidCardNumber_ThrowsException() {
        // Arrange
        TokenizationRequest invalidRequest = TokenizationRequest.builder()
            .sensitiveData("invalid")
            .dataType("CREDIT_CARD")
            .userId(testUserId)
            .build();

        // Act & Assert
        assertThatThrownBy(() -> tokenizationService.tokenize(invalidRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid credit card number format");

        verify(auditService, times(1)).logTokenization(
            eq(testUserId),
            eq("CREDIT_CARD"),
            isNull(),
            isNull(),
            eq(false),
            anyString()
        );
    }

    @Test
    void testTokenizeNullSensitiveData_ThrowsException() {
        // Arrange
        TokenizationRequest nullRequest = TokenizationRequest.builder()
            .sensitiveData(null)
            .dataType("CREDIT_CARD")
            .userId(testUserId)
            .build();

        // Act & Assert
        assertThatThrownBy(() -> tokenizationService.tokenize(nullRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Sensitive data cannot be null or empty");
    }

    @Test
    void testDetokenizeValidToken_Success() {
        // Arrange
        String token = "tok_123456789";
        String encryptedData = "encrypted_data_here";

        Map<String, String> metadata = new HashMap<>();
        metadata.put("dataType", "CREDIT_CARD");

        when(vaultClient.retrieveToken(token)).thenReturn(encryptedData);
        when(vaultClient.getTokenMetadata(token)).thenReturn(metadata);

        DetokenizationRequest request = DetokenizationRequest.builder()
            .token(token)
            .userId(testUserId)
            .ipAddress("192.168.1.1")
            .reason("PAYMENT_PROCESSING")
            .build();

        // Act
        DetokenizationResponse response = tokenizationService.detokenize(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getSensitiveData()).isNotNull();
        assertThat(response.getMetadata()).isEqualTo(metadata);

        verify(vaultClient, times(1)).retrieveToken(token);
        verify(auditService, times(1)).logDetokenization(
            eq(testUserId),
            eq(token),
            eq("192.168.1.1"),
            eq("PAYMENT_PROCESSING"),
            eq(true),
            anyString()
        );
    }

    @Test
    void testDetokenizeNonExistentToken_ThrowsException() {
        // Arrange
        String token = "tok_nonexistent";
        when(vaultClient.retrieveToken(token)).thenReturn(null);

        DetokenizationRequest request = DetokenizationRequest.builder()
            .token(token)
            .userId(testUserId)
            .reason("PAYMENT_PROCESSING")
            .build();

        // Act & Assert
        assertThatThrownBy(() -> tokenizationService.detokenize(request))
            .isInstanceOf(TokenizationException.class)
            .hasMessageContaining("Token not found or expired");

        verify(auditService, times(1)).logDetokenization(
            eq(testUserId),
            eq(token),
            isNull(),
            eq("PAYMENT_PROCESSING"),
            eq(false),
            anyString()
        );
    }

    @Test
    void testRotateToken_Success() {
        // Arrange
        String oldToken = "tok_old_123";
        String encryptedData = "encrypted_data";

        Map<String, String> metadata = new HashMap<>();
        metadata.put("dataType", "CREDIT_CARD");
        metadata.put("userId", testUserId);

        when(vaultClient.retrieveToken(oldToken)).thenReturn(encryptedData);
        when(vaultClient.getTokenMetadata(oldToken)).thenReturn(metadata);
        doNothing().when(vaultClient).deleteToken(oldToken);
        doNothing().when(vaultClient).storeToken(anyString(), anyString(), any(), anyLong());

        // Act
        String newToken = tokenizationService.rotateToken(oldToken, testUserId);

        // Assert
        assertThat(newToken).isNotNull();
        assertThat(newToken).isNotEqualTo(oldToken);

        verify(vaultClient, times(1)).retrieveToken(oldToken);
        verify(vaultClient, times(1)).deleteToken(oldToken);
        verify(vaultClient, times(1)).storeToken(anyString(), anyString(), any(), anyLong());
        verify(auditService, times(1)).logTokenRotation(eq(testUserId), eq(oldToken), anyString());
    }

    @Test
    void testDeleteToken_Success() {
        // Arrange
        String token = "tok_to_delete";
        doNothing().when(vaultClient).deleteToken(token);

        // Act
        tokenizationService.deleteToken(token, testUserId);

        // Assert
        verify(vaultClient, times(1)).deleteToken(token);
        verify(auditService, times(1)).logTokenDeletion(
            eq(testUserId),
            eq(token),
            eq(true),
            anyString()
        );
    }

    @Test
    void testVerifyTokenOwnership_ValidOwner_ReturnsTrue() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String token = "tok_123456";

        Map<String, String> metadata = new HashMap<>();
        metadata.put("userId", userId.toString());
        metadata.put("expires_at", String.valueOf(System.currentTimeMillis() + 1000000));

        when(vaultClient.getTokenMetadata(token)).thenReturn(metadata);

        // Act
        boolean result = tokenizationService.verifyTokenOwnership(userId, token);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void testVerifyTokenOwnership_InvalidOwner_ReturnsFalse() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID differentUserId = UUID.randomUUID();
        String token = "tok_123456";

        Map<String, String> metadata = new HashMap<>();
        metadata.put("userId", differentUserId.toString());
        metadata.put("expires_at", String.valueOf(System.currentTimeMillis() + 1000000));

        when(vaultClient.getTokenMetadata(token)).thenReturn(metadata);

        // Act
        boolean result = tokenizationService.verifyTokenOwnership(userId, token);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void testVerifyTokenOwnership_ExpiredToken_ReturnsFalse() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String token = "tok_expired";

        Map<String, String> metadata = new HashMap<>();
        metadata.put("userId", userId.toString());
        metadata.put("expires_at", String.valueOf(System.currentTimeMillis() - 1000)); // Expired

        when(vaultClient.getTokenMetadata(token)).thenReturn(metadata);

        // Act
        boolean result = tokenizationService.verifyTokenOwnership(userId, token);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void testVerifyTokenOwnership_NonExistentToken_ReturnsFalse() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String token = "tok_nonexistent";

        when(vaultClient.getTokenMetadata(token)).thenReturn(new HashMap<>());

        // Act
        boolean result = tokenizationService.verifyTokenOwnership(userId, token);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void testFormatPreservingTokenization_CreditCard() {
        // Arrange
        TokenizationRequest request = TokenizationRequest.builder()
            .sensitiveData("4532123456789012")
            .dataType("CREDIT_CARD")
            .userId(testUserId)
            .build();

        doNothing().when(vaultClient).storeToken(anyString(), anyString(), any(), anyLong());

        // Act
        TokenizationResponse response = tokenizationService.tokenize(request);

        // Assert
        assertThat(response.getToken()).matches("\\d{16}"); // Format preserved (16 digits)
        assertThat(response.getFormatPreserved()).isTrue();
    }

    @Test
    void testCircuitBreakerActivation_TokenizationFailure() {
        // Arrange
        when(vaultClient.getEncryptionKey(anyString())).thenThrow(new RuntimeException("Vault unavailable"));

        // Act & Assert
        assertThatThrownBy(() -> tokenizationService.tokenize(validRequest))
            .isInstanceOf(TokenizationException.class);

        verify(metricsService, times(1)).recordTokenization(
            eq("CREDIT_CARD"),
            anyLong(),
            eq(false)
        );
    }

    @Test
    void testAuditLogging_AllOperations() {
        // Test tokenization audit
        doNothing().when(vaultClient).storeToken(anyString(), anyString(), any(), anyLong());
        tokenizationService.tokenize(validRequest);

        verify(auditService, times(1)).logTokenization(
            anyString(), anyString(), anyString(), anyString(), eq(true), anyString());

        // Test detokenization audit
        when(vaultClient.retrieveToken(anyString())).thenReturn("encrypted");
        when(vaultClient.getTokenMetadata(anyString())).thenReturn(new HashMap<>());

        DetokenizationRequest detokenRequest = DetokenizationRequest.builder()
            .token("tok_123")
            .userId(testUserId)
            .reason("TEST")
            .build();

        try {
            tokenizationService.detokenize(detokenRequest);
        } catch (Exception e) {
            // Expected due to null return
        }

        verify(auditService, atLeastOnce()).logDetokenization(
            anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyString());
    }

    @Test
    void testMetricsRecording_AllOperations() {
        // Test tokenization metrics
        doNothing().when(vaultClient).storeToken(anyString(), anyString(), any(), anyLong());
        tokenizationService.tokenize(validRequest);

        verify(metricsService, times(1)).recordTokenization(
            eq("CREDIT_CARD"), anyLong(), eq(true));

        // Test detokenization metrics
        when(vaultClient.retrieveToken(anyString())).thenReturn("encrypted");
        when(vaultClient.getTokenMetadata(anyString())).thenReturn(new HashMap<>());

        DetokenizationRequest detokenRequest = DetokenizationRequest.builder()
            .token("tok_123")
            .userId(testUserId)
            .reason("TEST")
            .build();

        try {
            tokenizationService.detokenize(detokenRequest);
        } catch (Exception e) {
            // Expected
        }

        verify(metricsService, atLeastOnce()).recordDetokenization(anyLong(), anyBoolean());
    }
}

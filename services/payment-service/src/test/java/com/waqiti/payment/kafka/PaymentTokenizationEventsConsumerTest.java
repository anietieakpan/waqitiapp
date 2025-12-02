package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentTokenizationEvent;
import com.waqiti.common.events.TokenLifecycleEvent;
import com.waqiti.common.events.PCIComplianceEvent;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.domain.PaymentToken;
import com.waqiti.payment.domain.TokenStatus;
import com.waqiti.payment.domain.TokenType;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.repository.PaymentTokenRepository;
import com.waqiti.payment.service.TokenizationService;
import com.waqiti.payment.service.TokenVaultService;
import com.waqiti.payment.service.PCIComplianceService;
import com.waqiti.payment.service.TokenSecurityService;
import com.waqiti.payment.metrics.TokenizationMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.encryption.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///waqiti_test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "tokenization.default.expiry.days=365",
        "tokenization.network.expiry.days=1095",
        "tokenization.temp.expiry.minutes=15",
        "tokenization.max.failed.attempts=3",
        "tokenization.rotation.window.days=90"
})
@DisplayName("Payment Tokenization Events Consumer Tests")
class PaymentTokenizationEventsConsumerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("waqiti_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private PaymentTokenizationEventsConsumer tokenizationEventsConsumer;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentTokenRepository tokenRepository;

    @MockBean
    private TokenizationService tokenizationService;

    @MockBean
    private TokenVaultService vaultService;

    @MockBean
    private PCIComplianceService pciComplianceService;

    @MockBean
    private TokenSecurityService tokenSecurityService;

    @MockBean
    private TokenizationMetricsService metricsService;

    @MockBean
    private AuditService auditService;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private SecurityContext securityContext;

    @MockBean
    private EncryptionService encryptionService;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private Acknowledgment acknowledgment;

    private String testPaymentId;
    private Payment testPayment;
    private String testCustomerId;
    private String testMerchantId;
    private Map<String, Object> testCardData;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        tokenRepository.deleteAll();

        testPaymentId = UUID.randomUUID().toString();
        testCustomerId = UUID.randomUUID().toString();
        testMerchantId = UUID.randomUUID().toString();

        testPayment = createTestPayment();
        testPayment = paymentRepository.save(testPayment);

        testCardData = Map.of(
            "cardNumber", "4111111111111111",
            "expiryMonth", "12",
            "expiryYear", "2025",
            "cvv", "123",
            "cardholderName", "John Doe"
        );

        // Mock default behaviors
        when(pciComplianceService.isTokenizationCompliant(any())).thenReturn(true);
        when(tokenSecurityService.checkTokenizationRateLimit(any())).thenReturn(true);
        when(tokenSecurityService.generateSecureRandomToken(anyInt())).thenReturn("1234567890abcdef");
        when(tokenSecurityService.hasDetokenizationPermission(any(), any())).thenReturn(true);
        when(vaultService.storeCardData(any(), any(), any())).thenReturn("vault-ref-12345");
        when(vaultService.retrieveCardData(any(), any(), any())).thenReturn(testCardData);
        when(kafkaTemplate.send(anyString(), any())).thenReturn(mock(CompletableFuture.class));
        when(tokenizationService.provisionNetworkToken(any(), any(), any(), any()))
            .thenReturn("network-token-12345");
        when(tokenRepository.hasActiveRecurringPayments(any())).thenReturn(false);
    }

    @Nested
    @DisplayName("Create Token Tests")
    class CreateTokenTests {

        @Test
        @Transactional
        @DisplayName("Should create standard payment token successfully")
        void shouldCreateStandardPaymentTokenSuccessfully() {
            PaymentTokenizationEvent event = createTokenizationEvent("CREATE_TOKEN");
            event.setTokenType("STANDARD");
            event.setCardData(testCardData);
            event.setLastFourDigits("1111");
            event.setCardBrand("VISA");
            event.setExpiryMonth("12");
            event.setExpiryYear("2025");

            tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment);

            List<PaymentToken> tokens = tokenRepository.findAll();
            assertThat(tokens).hasSize(1);
            
            PaymentToken token = tokens.get(0);
            assertThat(token.getTokenValue()).startsWith("tok_");
            assertThat(token.getTokenType()).isEqualTo(TokenType.STANDARD);
            assertThat(token.getCustomerId()).isEqualTo(testCustomerId);
            assertThat(token.getMerchantId()).isEqualTo(testMerchantId);
            assertThat(token.getPaymentId()).isEqualTo(testPaymentId);
            assertThat(token.getStatus()).isEqualTo(TokenStatus.ACTIVE);
            assertThat(token.getLastFourDigits()).isEqualTo("1111");
            assertThat(token.getCardBrand()).isEqualTo("VISA");
            assertThat(token.getExpiryMonth()).isEqualTo("12");
            assertThat(token.getExpiryYear()).isEqualTo("2025");
            assertThat(token.getVaultReference()).isEqualTo("vault-ref-12345");
            assertThat(token.getSecurityLevel()).isEqualTo("STANDARD");
            assertThat(token.getUsageCount()).isEqualTo(0);
            assertThat(token.getMaxUsageCount()).isEqualTo(-1); // Unlimited
            assertThat(token.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(360));

            // Verify payment updated
            Payment updatedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updatedPayment.getTokenId()).isEqualTo(token.getId());
            assertThat(updatedPayment.getTokenized()).isTrue();

            verify(vaultService).storeCardData(eq(testCardData), eq(token.getId()), anyString());
            verify(metricsService).recordTokenCreated(TokenType.STANDARD, testCustomerId);
            verify(kafkaTemplate).send(eq("token-lifecycle-events"), any(TokenLifecycleEvent.class));
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should create network token with extended expiry")
        void shouldCreateNetworkTokenWithExtendedExpiry() {
            PaymentTokenizationEvent event = createTokenizationEvent("CREATE_TOKEN");
            event.setTokenType("NETWORK_TOKEN");
            event.setCardData(testCardData);
            event.setNetworkProvider("APPLE_PAY");
            event.setSecurityLevel("HIGH");

            tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment);

            List<PaymentToken> tokens = tokenRepository.findAll();
            assertThat(tokens).hasSize(1);
            
            PaymentToken token = tokens.get(0);
            assertThat(token.getTokenValue()).startsWith("ntok_");
            assertThat(token.getTokenType()).isEqualTo(TokenType.NETWORK_TOKEN);
            assertThat(token.getSecurityLevel()).isEqualTo("HIGH");
            assertThat(token.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(1090)); // ~3 years
            assertThat(token.getMaxUsageCount()).isEqualTo(-1); // Unlimited

            verify(metricsService).recordTokenCreated(TokenType.NETWORK_TOKEN, testCustomerId);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should create temporary token with short expiry")
        void shouldCreateTemporaryTokenWithShortExpiry() {
            PaymentTokenizationEvent event = createTokenizationEvent("CREATE_TOKEN");
            event.setTokenType("TEMPORARY");
            event.setCardData(testCardData);

            tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment);

            List<PaymentToken> tokens = tokenRepository.findAll();
            assertThat(tokens).hasSize(1);
            
            PaymentToken token = tokens.get(0);
            assertThat(token.getTokenValue()).startsWith("tmp_");
            assertThat(token.getTokenType()).isEqualTo(TokenType.TEMPORARY);
            assertThat(token.getSecurityLevel()).isEqualTo("MEDIUM");
            assertThat(token.getExpiresAt()).isBefore(LocalDateTime.now().plusMinutes(20)); // 15 min expiry
            assertThat(token.getMaxUsageCount()).isEqualTo(10);

            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should create single-use token with usage limit")
        void shouldCreateSingleUseTokenWithUsageLimit() {
            PaymentTokenizationEvent event = createTokenizationEvent("CREATE_TOKEN");
            event.setTokenType("SINGLE_USE");
            event.setCardData(testCardData);

            tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment);

            List<PaymentToken> tokens = tokenRepository.findAll();
            assertThat(tokens).hasSize(1);
            
            PaymentToken token = tokens.get(0);
            assertThat(token.getTokenType()).isEqualTo(TokenType.SINGLE_USE);
            assertThat(token.getMaxUsageCount()).isEqualTo(1);
            assertThat(token.getExpiresAt()).isBefore(LocalDateTime.now().plusDays(2)); // 1 day expiry

            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should reject token creation without card data")
        void shouldRejectTokenCreationWithoutCardData() {
            PaymentTokenizationEvent event = createTokenizationEvent("CREATE_TOKEN");
            event.setTokenType("STANDARD");
            // No card data set

            assertThatCode(() -> 
                tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment)
            ).doesNotThrowAnyException();

            List<PaymentToken> tokens = tokenRepository.findAll();
            assertThat(tokens).isEmpty();

            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should reject token creation when rate limit exceeded")
        void shouldRejectTokenCreationWhenRateLimitExceeded() {
            when(tokenSecurityService.checkTokenizationRateLimit(testCustomerId)).thenReturn(false);

            PaymentTokenizationEvent event = createTokenizationEvent("CREATE_TOKEN");
            event.setTokenType("STANDARD");
            event.setCardData(testCardData);

            assertThatCode(() -> 
                tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment)
            ).doesNotThrowAnyException();

            List<PaymentToken> tokens = tokenRepository.findAll();
            assertThat(tokens).isEmpty();

            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Detokenization Tests")
    class DetokenizationTests {

        @Test
        @Transactional
        @DisplayName("Should detokenize token successfully")
        void shouldDetokenizeTokenSuccessfully() {
            PaymentToken token = createTestToken(TokenType.STANDARD, TokenStatus.ACTIVE);
            token = tokenRepository.save(token);

            PaymentTokenizationEvent event = createTokenizationEvent("DETOKENIZE");
            event.setTokenId(token.getId());
            event.setDetokenizationPurpose("PAYMENT_PROCESSING");
            event.setRequesterId("payment-service");

            tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment);

            PaymentToken updatedToken = tokenRepository.findById(token.getId()).orElseThrow();
            assertThat(updatedToken.getUsageCount()).isEqualTo(1);
            assertThat(updatedToken.getLastUsedAt()).isNotNull();
            assertThat(updatedToken.getLastDetokenizedAt()).isNotNull();

            verify(vaultService).retrieveCardData(eq("vault-ref-12345"), eq("PAYMENT_PROCESSING"), anyString());
            verify(tokenSecurityService).hasDetokenizationPermission("payment-service", token.getId());
            verify(metricsService).recordDetokenization(TokenType.STANDARD, "PAYMENT_PROCESSING");
            verify(kafkaTemplate).send(eq("detokenization-result-events"), any(Map.class));
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should reject detokenization of inactive token")
        void shouldRejectDetokenizationOfInactiveToken() {
            PaymentToken token = createTestToken(TokenType.STANDARD, TokenStatus.REVOKED);
            token = tokenRepository.save(token);

            PaymentTokenizationEvent event = createTokenizationEvent("DETOKENIZE");
            event.setTokenId(token.getId());
            event.setDetokenizationPurpose("PAYMENT_PROCESSING");

            assertThatCode(() -> 
                tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment)
            ).doesNotThrowAnyException();

            // Token usage should not be updated
            PaymentToken unchangedToken = tokenRepository.findById(token.getId()).orElseThrow();
            assertThat(unchangedToken.getUsageCount()).isEqualTo(0);
            assertThat(unchangedToken.getLastDetokenizedAt()).isNull();

            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should reject detokenization of expired token")
        void shouldRejectDetokenizationOfExpiredToken() {
            PaymentToken token = createTestToken(TokenType.STANDARD, TokenStatus.ACTIVE);
            token.setExpiresAt(LocalDateTime.now().minusDays(1)); // Expired
            token = tokenRepository.save(token);

            PaymentTokenizationEvent event = createTokenizationEvent("DETOKENIZE");
            event.setTokenId(token.getId());

            assertThatCode(() -> 
                tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment)
            ).doesNotThrowAnyException();

            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should reject detokenization when usage limit exceeded")
        void shouldRejectDetokenizationWhenUsageLimitExceeded() {
            PaymentToken token = createTestToken(TokenType.SINGLE_USE, TokenStatus.ACTIVE);
            token.setUsageCount(1); // Already used once, max is 1
            token = tokenRepository.save(token);

            PaymentTokenizationEvent event = createTokenizationEvent("DETOKENIZE");
            event.setTokenId(token.getId());

            assertThatCode(() -> 
                tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment)
            ).doesNotThrowAnyException();

            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should suspend token after max failed detokenization attempts")
        void shouldSuspendTokenAfterMaxFailedDetokenizationAttempts() {
            PaymentToken token = createTestToken(TokenType.STANDARD, TokenStatus.ACTIVE);
            token.setFailedDetokenizationAttempts(2); // Already 2 failures
            token = tokenRepository.save(token);

            when(tokenSecurityService.hasDetokenizationPermission(any(), any())).thenReturn(false);

            PaymentTokenizationEvent event = createTokenizationEvent("DETOKENIZE");
            event.setTokenId(token.getId());
            event.setRequesterId("unauthorized-service");

            assertThatCode(() -> 
                tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment)
            ).doesNotThrowAnyException();

            PaymentToken updatedToken = tokenRepository.findById(token.getId()).orElseThrow();
            assertThat(updatedToken.getStatus()).isEqualTo(TokenStatus.SUSPENDED);
            assertThat(updatedToken.getFailedDetokenizationAttempts()).isEqualTo(3);
            assertThat(updatedToken.getSuspendedAt()).isNotNull();
            assertThat(updatedToken.getSuspensionReason()).isEqualTo("Too many failed detokenization attempts");

            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should schedule token rotation for single-use token after use")
        void shouldScheduleTokenRotationForSingleUseTokenAfterUse() {
            PaymentToken token = createTestToken(TokenType.SINGLE_USE, TokenStatus.ACTIVE);
            token.setUsageCount(0); // Not yet used
            token = tokenRepository.save(token);

            PaymentTokenizationEvent event = createTokenizationEvent("DETOKENIZE");
            event.setTokenId(token.getId());
            event.setDetokenizationPurpose("PAYMENT_PROCESSING");

            tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment);

            PaymentToken updatedToken = tokenRepository.findById(token.getId()).orElseThrow();
            assertThat(updatedToken.getUsageCount()).isEqualTo(1);

            // Should schedule rotation (verified through Kafka send)
            verify(kafkaTemplate, atLeastOnce()).send(anyString(), any());
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Token Update Tests")
    class TokenUpdateTests {

        @Test
        @Transactional
        @DisplayName("Should update token expiry information")
        void shouldUpdateTokenExpiryInformation() {
            PaymentToken token = createTestToken(TokenType.STANDARD, TokenStatus.ACTIVE);
            token = tokenRepository.save(token);

            PaymentTokenizationEvent event = createTokenizationEvent("UPDATE_TOKEN");
            event.setTokenId(token.getId());
            event.setExpiryMonth("06");
            event.setExpiryYear("2026");

            tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment);

            PaymentToken updatedToken = tokenRepository.findById(token.getId()).orElseThrow();
            assertThat(updatedToken.getExpiryMonth()).isEqualTo("06");
            assertThat(updatedToken.getExpiryYear()).isEqualTo("2026");
            assertThat(updatedToken.getUpdatedAt()).isNotNull();

            verify(metricsService).recordTokenUpdated(TokenType.STANDARD);
            verify(kafkaTemplate).send(eq("token-lifecycle-events"), any(TokenLifecycleEvent.class));
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should update token status and publish status change")
        void shouldUpdateTokenStatusAndPublishStatusChange() {
            PaymentToken token = createTestToken(TokenType.STANDARD, TokenStatus.ACTIVE);
            token = tokenRepository.save(token);

            PaymentTokenizationEvent event = createTokenizationEvent("UPDATE_TOKEN");
            event.setTokenId(token.getId());
            event.setNewStatus("SUSPENDED");

            tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment);

            PaymentToken updatedToken = tokenRepository.findById(token.getId()).orElseThrow();
            assertThat(updatedToken.getStatus()).isEqualTo(TokenStatus.SUSPENDED);

            verify(kafkaTemplate).send(eq("token-status-change-events"), any(Map.class));
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should update token security level")
        void shouldUpdateTokenSecurityLevel() {
            PaymentToken token = createTestToken(TokenType.STANDARD, TokenStatus.ACTIVE);
            token.setSecurityLevel("STANDARD");
            token = tokenRepository.save(token);

            PaymentTokenizationEvent event = createTokenizationEvent("UPDATE_TOKEN");
            event.setTokenId(token.getId());
            event.setNewSecurityLevel("HIGH");

            tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment);

            PaymentToken updatedToken = tokenRepository.findById(token.getId()).orElseThrow();
            assertThat(updatedToken.getSecurityLevel()).isEqualTo("HIGH");

            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should handle update with no changes")
        void shouldHandleUpdateWithNoChanges() {
            PaymentToken token = createTestToken(TokenType.STANDARD, TokenStatus.ACTIVE);
            token = tokenRepository.save(token);

            PaymentTokenizationEvent event = createTokenizationEvent("UPDATE_TOKEN");
            event.setTokenId(token.getId());
            // No update fields set

            tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment);

            PaymentToken unchangedToken = tokenRepository.findById(token.getId()).orElseThrow();
            assertThat(unchangedToken.getUpdatedAt()).isNull();

            verify(metricsService, never()).recordTokenUpdated(any());
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Token Revocation Tests")
    class TokenRevocationTests {

        @Test
        @Transactional
        @DisplayName("Should revoke token and remove from vault")
        void shouldRevokeTokenAndRemoveFromVault() {
            PaymentToken token = createTestToken(TokenType.STANDARD, TokenStatus.ACTIVE);
            token.setNetworkTokenId("network-token-123");
            token = tokenRepository.save(token);

            PaymentTokenizationEvent event = createTokenizationEvent("REVOKE_TOKEN");
            event.setTokenId(token.getId());
            event.setRevocationReason("SECURITY_BREACH");
            event.setRequesterId("security-service");
            event.setRemoveFromVault(true);
            event.setNotifyCustomer(true);

            tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment);

            PaymentToken revokedToken = tokenRepository.findById(token.getId()).orElseThrow();
            assertThat(revokedToken.getStatus()).isEqualTo(TokenStatus.REVOKED);
            assertThat(revokedToken.getRevokedAt()).isNotNull();
            assertThat(revokedToken.getRevocationReason()).isEqualTo("SECURITY_BREACH");
            assertThat(revokedToken.getRevokedBy()).isEqualTo("security-service");
            assertThat(revokedToken.getVaultReference()).isNull(); // Removed from vault

            verify(vaultService).removeCardData("vault-ref-12345", anyString());
            verify(tokenizationService).invalidateNetworkToken("network-token-123", anyString());
            verify(notificationService).sendCustomerNotification(
                eq(testCustomerId), 
                eq("Payment Method Removed"), 
                anyString(), 
                eq(NotificationService.Priority.MEDIUM)
            );
            verify(metricsService).recordTokenRevoked(TokenType.STANDARD, "SECURITY_BREACH");
            verify(kafkaTemplate).send(eq("token-lifecycle-events"), any(TokenLifecycleEvent.class));
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should revoke token without removing from vault")
        void shouldRevokeTokenWithoutRemovingFromVault() {
            PaymentToken token = createTestToken(TokenType.STANDARD, TokenStatus.ACTIVE);
            token = tokenRepository.save(token);

            PaymentTokenizationEvent event = createTokenizationEvent("REVOKE_TOKEN");
            event.setTokenId(token.getId());
            event.setRevocationReason("CUSTOMER_REQUEST");
            event.setRemoveFromVault(false);
            event.setNotifyCustomer(false);

            tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment);

            PaymentToken revokedToken = tokenRepository.findById(token.getId()).orElseThrow();
            assertThat(revokedToken.getStatus()).isEqualTo(TokenStatus.REVOKED);
            assertThat(revokedToken.getVaultReference()).isEqualTo("vault-ref-12345"); // Not removed

            verify(vaultService, never()).removeCardData(anyString(), anyString());
            verify(notificationService, never()).sendCustomerNotification(anyString(), anyString(), anyString(), any());
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Token Rotation Tests")
    class TokenRotationTests {

        @Test
        @Transactional
        @DisplayName("Should rotate token successfully")
        void shouldRotateTokenSuccessfully() {
            PaymentToken oldToken = createTestToken(TokenType.STANDARD, TokenStatus.ACTIVE);
            oldToken = tokenRepository.save(oldToken);

            // Create a payment using the old token
            testPayment.setTokenId(oldToken.getId());
            paymentRepository.save(testPayment);

            PaymentTokenizationEvent event = createTokenizationEvent("ROTATE_TOKEN");
            event.setTokenId(oldToken.getId());
            event.setRotationReason("SCHEDULED_ROTATION");

            tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment);

            // Verify old token updated
            PaymentToken updatedOldToken = tokenRepository.findById(oldToken.getId()).orElseThrow();
            assertThat(updatedOldToken.getStatus()).isEqualTo(TokenStatus.ROTATED);
            assertThat(updatedOldToken.getRotatedAt()).isNotNull();
            assertThat(updatedOldToken.getReplacementTokenId()).isNotNull();

            // Verify new token created
            List<PaymentToken> allTokens = tokenRepository.findAll();
            assertThat(allTokens).hasSize(2);
            
            PaymentToken newToken = allTokens.stream()
                .filter(t -> !t.getId().equals(oldToken.getId()))
                .findFirst().orElseThrow();
            
            assertThat(newToken.getStatus()).isEqualTo(TokenStatus.ACTIVE);
            assertThat(newToken.getTokenType()).isEqualTo(oldToken.getTokenType());
            assertThat(newToken.getCustomerId()).isEqualTo(oldToken.getCustomerId());
            assertThat(newToken.getVaultReference()).isEqualTo(oldToken.getVaultReference());
            assertThat(newToken.getPredecessorTokenId()).isEqualTo(oldToken.getId());
            assertThat(newToken.getUsageCount()).isEqualTo(0);

            // Verify payment updated with new token
            Payment updatedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updatedPayment.getTokenId()).isEqualTo(newToken.getId());

            verify(metricsService).recordTokenRotated(TokenType.STANDARD);
            verify(kafkaTemplate, times(2)).send(eq("token-lifecycle-events"), any(TokenLifecycleEvent.class));
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Network Token Tests")
    class NetworkTokenTests {

        @Test
        @Transactional
        @DisplayName("Should provision network token successfully")
        void shouldProvisionNetworkTokenSuccessfully() {
            PaymentToken token = createTestToken(TokenType.STANDARD, TokenStatus.ACTIVE);
            token = tokenRepository.save(token);

            Map<String, Object> deviceInfo = Map.of(
                "deviceId", "device-123",
                "deviceType", "iPhone",
                "osVersion", "iOS 15.0"
            );

            PaymentTokenizationEvent event = createTokenizationEvent("PROVISION_NETWORK_TOKEN");
            event.setTokenId(token.getId());
            event.setNetworkProvider("APPLE_PAY");
            event.setDeviceInfo(deviceInfo);

            tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment);

            PaymentToken updatedToken = tokenRepository.findById(token.getId()).orElseThrow();
            assertThat(updatedToken.getNetworkTokenId()).isEqualTo("network-token-12345");
            assertThat(updatedToken.getNetworkProvider()).isEqualTo("APPLE_PAY");
            assertThat(updatedToken.getNetworkTokenStatus()).isEqualTo("ACTIVE");
            assertThat(updatedToken.getNetworkTokenProvisionedAt()).isNotNull();

            verify(tokenizationService).provisionNetworkToken(
                eq(token.getId()), 
                eq("APPLE_PAY"), 
                eq(deviceInfo), 
                anyString()
            );
            verify(metricsService).recordNetworkTokenProvisioned("APPLE_PAY");
            verify(kafkaTemplate).send(eq("network-token-events"), any(Map.class));
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should handle network token provisioning failure")
        void shouldHandleNetworkTokenProvisioningFailure() {
            PaymentToken token = createTestToken(TokenType.STANDARD, TokenStatus.ACTIVE);
            token = tokenRepository.save(token);

            when(tokenizationService.provisionNetworkToken(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Network provider unavailable"));

            PaymentTokenizationEvent event = createTokenizationEvent("PROVISION_NETWORK_TOKEN");
            event.setTokenId(token.getId());
            event.setNetworkProvider("GOOGLE_PAY");

            assertThatCode(() -> 
                tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment)
            ).doesNotThrowAnyException();

            // Token should remain unchanged
            PaymentToken unchangedToken = tokenRepository.findById(token.getId()).orElseThrow();
            assertThat(unchangedToken.getNetworkTokenId()).isNull();

            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Token Synchronization Tests")
    class TokenSynchronizationTests {

        @Test
        @Transactional
        @DisplayName("Should sync token across platforms")
        void shouldSyncTokenAcrossPlatforms() {
            PaymentToken token = createTestToken(TokenType.STANDARD, TokenStatus.ACTIVE);
            token = tokenRepository.save(token);

            Map<String, Object> syncData = Map.of(
                "platformVersion", "2.1",
                "syncType", "FULL"
            );

            PaymentTokenizationEvent event = createTokenizationEvent("SYNC_TOKEN");
            event.setTokenId(token.getId());
            event.setSyncPlatform("MOBILE_APP");
            event.setSyncData(syncData);

            tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment);

            PaymentToken updatedToken = tokenRepository.findById(token.getId()).orElseThrow();
            assertThat(updatedToken.getLastSyncAt()).isNotNull();
            assertThat(updatedToken.getSyncPlatforms()).contains("MOBILE_APP");

            verify(tokenizationService).syncTokenAcrossPlatforms(
                eq(token.getId()), 
                eq("MOBILE_APP"), 
                eq(syncData), 
                anyString()
            );
            verify(metricsService).recordTokenSynced("MOBILE_APP");
            verify(kafkaTemplate).send(eq("token-lifecycle-events"), any(TokenLifecycleEvent.class));
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should handle token sync with multiple platforms")
        void shouldHandleTokenSyncWithMultiplePlatforms() {
            PaymentToken token = createTestToken(TokenType.STANDARD, TokenStatus.ACTIVE);
            token.setSyncPlatforms("WEB_APP,MOBILE_APP");
            token = tokenRepository.save(token);

            PaymentTokenizationEvent event = createTokenizationEvent("SYNC_TOKEN");
            event.setTokenId(token.getId());
            event.setSyncPlatform("DESKTOP_APP");

            tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment);

            PaymentToken updatedToken = tokenRepository.findById(token.getId()).orElseThrow();
            assertThat(updatedToken.getSyncPlatforms()).contains("WEB_APP");
            assertThat(updatedToken.getSyncPlatforms()).contains("MOBILE_APP");
            assertThat(updatedToken.getSyncPlatforms()).contains("DESKTOP_APP");

            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Token Validation Tests")
    class TokenValidationTests {

        @Test
        @Transactional
        @DisplayName("Should validate active token successfully")
        void shouldValidateActiveTokenSuccessfully() {
            PaymentToken token = createTestToken(TokenType.STANDARD, TokenStatus.ACTIVE);
            token.setExpiresAt(LocalDateTime.now().plusDays(30)); // Valid expiry
            token.setUsageCount(5);
            token.setMaxUsageCount(100); // Within usage limit
            token = tokenRepository.save(token);

            PaymentTokenizationEvent event = createTokenizationEvent("VALIDATE_TOKEN");
            event.setTokenId(token.getId());

            tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment);

            PaymentToken updatedToken = tokenRepository.findById(token.getId()).orElseThrow();
            assertThat(updatedToken.getLastValidatedAt()).isNotNull();
            assertThat(updatedToken.getValidationFailureReason()).isNull();

            verify(metricsService).recordTokenValidation(TokenType.STANDARD, true);
            verify(kafkaTemplate).send(eq("token-validation-events"), any(Map.class));
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should validate expired token as invalid")
        void shouldValidateExpiredTokenAsInvalid() {
            PaymentToken token = createTestToken(TokenType.STANDARD, TokenStatus.ACTIVE);
            token.setExpiresAt(LocalDateTime.now().minusDays(1)); // Expired
            token = tokenRepository.save(token);

            PaymentTokenizationEvent event = createTokenizationEvent("VALIDATE_TOKEN");
            event.setTokenId(token.getId());

            tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment);

            PaymentToken updatedToken = tokenRepository.findById(token.getId()).orElseThrow();
            assertThat(updatedToken.getValidationFailureReason()).isEqualTo("Token has expired");

            verify(metricsService).recordTokenValidation(TokenType.STANDARD, false);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should validate token with usage limit exceeded as invalid")
        void shouldValidateTokenWithUsageLimitExceededAsInvalid() {
            PaymentToken token = createTestToken(TokenType.SINGLE_USE, TokenStatus.ACTIVE);
            token.setUsageCount(1);
            token.setMaxUsageCount(1); // Usage limit exceeded
            token = tokenRepository.save(token);

            PaymentTokenizationEvent event = createTokenizationEvent("VALIDATE_TOKEN");
            event.setTokenId(token.getId());

            tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment);

            PaymentToken updatedToken = tokenRepository.findById(token.getId()).orElseThrow();
            assertThat(updatedToken.getValidationFailureReason()).isEqualTo("Token usage limit exceeded");

            verify(metricsService).recordTokenValidation(TokenType.SINGLE_USE, false);
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Token Expiration Tests")
    class TokenExpirationTests {

        @Test
        @Transactional
        @DisplayName("Should expire token and schedule cleanup")
        void shouldExpireTokenAndScheduleCleanup() {
            PaymentToken token = createTestToken(TokenType.STANDARD, TokenStatus.ACTIVE);
            token = tokenRepository.save(token);

            PaymentTokenizationEvent event = createTokenizationEvent("EXPIRE_TOKEN");
            event.setTokenId(token.getId());
            event.setCleanupAfterExpiration(true);

            tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment);

            PaymentToken expiredToken = tokenRepository.findById(token.getId()).orElseThrow();
            assertThat(expiredToken.getStatus()).isEqualTo(TokenStatus.EXPIRED);
            assertThat(expiredToken.getExpiredAt()).isNotNull();

            verify(metricsService).recordTokenExpired(TokenType.STANDARD);
            verify(kafkaTemplate).send(eq("token-lifecycle-events"), any(TokenLifecycleEvent.class));
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("PCI Compliance Tests")
    class PCIComplianceTests {

        @Test
        @Transactional
        @DisplayName("Should handle PCI compliance violation")
        void shouldHandlePCIComplianceViolation() {
            when(pciComplianceService.isTokenizationCompliant(any())).thenReturn(false);

            PaymentTokenizationEvent event = createTokenizationEvent("CREATE_TOKEN");
            event.setTokenType("STANDARD");
            event.setCardData(testCardData);

            tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment);

            // No token should be created
            List<PaymentToken> tokens = tokenRepository.findAll();
            assertThat(tokens).isEmpty();

            verify(kafkaTemplate).send(eq("pci-compliance-events"), any(PCIComplianceEvent.class));
            verify(notificationService).sendComplianceAlert(
                eq("PCI Compliance Violation"), 
                anyString(), 
                eq(NotificationService.Priority.CRITICAL)
            );
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @Transactional
        @DisplayName("Should handle invalid tokenization event")
        void shouldHandleInvalidTokenizationEvent() {
            PaymentTokenizationEvent invalidEvent = PaymentTokenizationEvent.builder()
                .tokenAction(null) // Invalid - null action
                .timestamp(Instant.now())
                .build();

            assertThatCode(() -> 
                tokenizationEventsConsumer.handlePaymentTokenizationEvent(invalidEvent, 0, 0L, "topic", acknowledgment)
            ).doesNotThrowAnyException();

            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should handle token not found error")
        void shouldHandleTokenNotFoundError() {
            PaymentTokenizationEvent event = createTokenizationEvent("DETOKENIZE");
            event.setTokenId("non-existent-token-id");

            assertThatCode(() -> 
                tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment)
            ).doesNotThrowAnyException();

            verify(kafkaTemplate).send(eq("payment-tokenization-events-dlq"), any(Map.class));
            verify(notificationService).sendOperationalAlert(
                eq("Tokenization Event Processing Failed"), 
                anyString(), 
                eq(NotificationService.Priority.HIGH)
            );
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should handle unknown action gracefully")
        void shouldHandleUnknownActionGracefully() {
            PaymentTokenizationEvent event = createTokenizationEvent("UNKNOWN_ACTION");

            assertThatCode(() -> 
                tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment)
            ).doesNotThrowAnyException();

            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Audit and Metrics Tests")
    class AuditAndMetricsTests {

        @Test
        @Transactional
        @DisplayName("Should audit all tokenization operations")
        void shouldAuditAllTokenizationOperations() {
            PaymentTokenizationEvent event = createTokenizationEvent("CREATE_TOKEN");
            event.setTokenType("STANDARD");
            event.setCardData(testCardData);

            tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment);

            verify(auditService).logFinancialEvent(
                eq("TOKENIZATION_EVENT_PROCESSED"),
                eq(testPaymentId),
                any(Map.class)
            );
        }

        @Test
        @Transactional
        @DisplayName("Should record tokenization metrics")
        void shouldRecordTokenizationMetrics() {
            PaymentTokenizationEvent event = createTokenizationEvent("CREATE_TOKEN");
            event.setTokenType("NETWORK_TOKEN");
            event.setCardData(testCardData);

            tokenizationEventsConsumer.handlePaymentTokenizationEvent(event, 0, 0L, "topic", acknowledgment);

            verify(metricsService).recordTokenCreated(TokenType.NETWORK_TOKEN, testCustomerId);
        }
    }

    /**
     * Helper methods
     */
    private Payment createTestPayment() {
        return Payment.builder()
            .id(testPaymentId)
            .customerId(testCustomerId)
            .merchantId(testMerchantId)
            .amount(new BigDecimal("100.00"))
            .currency("USD")
            .status(PaymentStatus.PENDING)
            .paymentMethod("CARD")
            .createdAt(LocalDateTime.now())
            .build();
    }

    private PaymentTokenizationEvent createTokenizationEvent(String action) {
        return PaymentTokenizationEvent.builder()
            .paymentId(testPaymentId)
            .customerId(testCustomerId)
            .merchantId(testMerchantId)
            .tokenAction(action)
            .timestamp(Instant.now())
            .build();
    }

    private PaymentToken createTestToken(TokenType tokenType, TokenStatus status) {
        return PaymentToken.builder()
            .id(UUID.randomUUID().toString())
            .tokenValue("tok_1234567890abcdef")
            .tokenType(tokenType)
            .customerId(testCustomerId)
            .merchantId(testMerchantId)
            .paymentId(testPaymentId)
            .status(status)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusDays(365))
            .lastFourDigits("1111")
            .cardBrand("VISA")
            .expiryMonth("12")
            .expiryYear("2025")
            .vaultReference("vault-ref-12345")
            .securityLevel("STANDARD")
            .usageCount(0)
            .maxUsageCount(tokenType == TokenType.SINGLE_USE ? 1 : 
                          tokenType == TokenType.TEMPORARY ? 10 : -1)
            .build();
    }
}
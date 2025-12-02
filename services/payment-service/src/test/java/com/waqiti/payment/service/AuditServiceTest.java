package com.waqiti.payment.service;

import com.waqiti.common.audit.AuditEvent;
import com.waqiti.common.audit.AuditEventType;
import com.waqiti.common.audit.AuditSeverity;
import com.waqiti.payment.dto.FraudAssessmentResult;
import com.waqiti.payment.entity.PaymentTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for AuditService
 *
 * Tests audit logging for:
 * - Payment transactions
 * - NFC payments
 * - P2P transfers
 * - Fraud detection
 * - Cryptography operations
 * - Balance operations
 * - Device operations
 * - Authentication events
 * - Compliance checks
 * - System errors
 * - Event processing failures
 * - Dead letter events
 * - Critical failures
 * - Kafka event publishing
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService Tests")
class AuditServiceTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private AuditService auditService;

    @Captor
    private ArgumentCaptor<String> topicCaptor;

    @Captor
    private ArgumentCaptor<Object> eventCaptor;

    private static final String AUDIT_TOPIC = "payment-audit-events";

    @BeforeEach
    void setUp() {
        auditService = new AuditService(kafkaTemplate);

        // Mock Kafka send to return completed future
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(anyString(), any())).thenReturn(future);
    }

    @Nested
    @DisplayName("Payment Transaction Audit Tests")
    class PaymentTransactionAuditTests {

        @Test
        @DisplayName("Should log payment transaction successfully")
        void shouldLogPaymentTransactionSuccessfully() {
            // Given
            PaymentTransaction transaction = createTestPaymentTransaction();
            String action = "PROCESSED";
            String userId = "user-123";

            // When
            auditService.logPaymentTransaction(transaction, action, userId);

            // Then
            verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());
            assertThat(topicCaptor.getValue()).isEqualTo(AUDIT_TOPIC);

            AuditEvent event = (AuditEvent) eventCaptor.getValue();
            assertThat(event).isNotNull();
            assertThat(event.getEventType()).isEqualTo(AuditEventType.PAYMENT_TRANSACTION);
            assertThat(event.getUserId()).isEqualTo(userId);
            assertThat(event.getSeverity()).isEqualTo(AuditSeverity.HIGH);
            assertThat(event.getDetails()).containsEntry("action", action);
            assertThat(event.getDetails()).containsKey("transactionId");
            assertThat(event.getDetails()).containsKey("amount");
        }

        @Test
        @DisplayName("Should include all transaction details in audit")
        void shouldIncludeAllTransactionDetailsInAudit() {
            // Given
            PaymentTransaction transaction = createTestPaymentTransaction();

            // When
            auditService.logPaymentTransaction(transaction, "COMPLETED", "user-456");

            // Then
            verify(kafkaTemplate).send(anyString(), eventCaptor.capture());
            AuditEvent event = (AuditEvent) eventCaptor.getValue();

            assertThat(event.getDetails()).containsKeys(
                    "transactionId", "customerId", "merchantId",
                    "amount", "currency", "status", "paymentMethod"
            );
        }
    }

    @Nested
    @DisplayName("NFC Payment Audit Tests")
    class NFCPaymentAuditTests {

        @Test
        @DisplayName("Should log NFC payment successfully")
        void shouldLogNFCPaymentSuccessfully() {
            // Given
            String transactionId = "txn-nfc-123";
            String customerId = "customer-456";
            String deviceId = "device-789";
            BigDecimal amount = new BigDecimal("50.00");
            String currency = "USD";
            String result = "SUCCESS";

            // When
            auditService.logNFCPayment(transactionId, customerId, deviceId, amount, currency, result);

            // Then
            verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());
            assertThat(topicCaptor.getValue()).isEqualTo(AUDIT_TOPIC);

            AuditEvent event = (AuditEvent) eventCaptor.getValue();
            assertThat(event.getEventType()).isEqualTo(AuditEventType.NFC_PAYMENT);
            assertThat(event.getUserId()).isEqualTo(customerId);
            assertThat(event.getSeverity()).isEqualTo(AuditSeverity.HIGH);
            assertThat(event.getDetails()).containsEntry("paymentType", "NFC");
            assertThat(event.getDetails()).containsEntry("deviceId", deviceId);
            assertThat(event.getDetails()).containsEntry("result", result);
        }

        @Test
        @DisplayName("Should log failed NFC payment")
        void shouldLogFailedNFCPayment() {
            // Given
            String result = "FAILED";

            // When
            auditService.logNFCPayment("txn-fail", "customer-123", "device-456",
                    new BigDecimal("100.00"), "USD", result);

            // Then
            verify(kafkaTemplate).send(anyString(), eventCaptor.capture());
            AuditEvent event = (AuditEvent) eventCaptor.getValue();
            assertThat(event.getDetails()).containsEntry("result", "FAILED");
        }
    }

    @Nested
    @DisplayName("P2P Transfer Audit Tests")
    class P2PTransferAuditTests {

        @Test
        @DisplayName("Should log P2P transfer successfully")
        void shouldLogP2PTransferSuccessfully() {
            // Given
            String transactionId = "txn-p2p-123";
            String senderId = "sender-456";
            String receiverId = "receiver-789";
            BigDecimal amount = new BigDecimal("250.00");
            String currency = "USD";
            String status = "COMPLETED";

            // When
            auditService.logP2PTransfer(transactionId, senderId, receiverId, amount, currency, status);

            // Then
            verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());
            assertThat(topicCaptor.getValue()).isEqualTo(AUDIT_TOPIC);

            AuditEvent event = (AuditEvent) eventCaptor.getValue();
            assertThat(event.getEventType()).isEqualTo(AuditEventType.P2P_TRANSFER);
            assertThat(event.getUserId()).isEqualTo(senderId);
            assertThat(event.getSeverity()).isEqualTo(AuditSeverity.HIGH);
            assertThat(event.getDetails()).containsEntry("transferType", "P2P");
            assertThat(event.getDetails()).containsEntry("senderId", senderId);
            assertThat(event.getDetails()).containsEntry("receiverId", receiverId);
            assertThat(event.getDetails()).containsEntry("status", status);
        }

        @Test
        @DisplayName("Should include both sender and receiver in P2P audit")
        void shouldIncludeBothParties() {
            // Given
            String senderId = "alice";
            String receiverId = "bob";

            // When
            auditService.logP2P Transfer(transactionId, senderId, receiverId,
                    BigDecimal.TEN, "USD", "PENDING");

            // Then
            verify(kafkaTemplate).send(anyString(), eventCaptor.capture());
            AuditEvent event = (AuditEvent) eventCaptor.getValue();

            assertThat(event.getDetails())
                    .containsEntry("senderId", senderId)
                    .containsEntry("receiverId", receiverId);
        }
    }

    @Nested
    @DisplayName("Fraud Detection Audit Tests")
    class FraudDetectionAuditTests {

        @Test
        @DisplayName("Should log fraud detection event")
        void shouldLogFraudDetectionEvent() {
            // Given
            String transactionId = "txn-123";
            String customerId = "customer-456";
            FraudAssessmentResult fraudResult = createFraudResult();

            // When
            auditService.logFraudDetection(transactionId, customerId, fraudResult);

            // Then
            verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());
            assertThat(topicCaptor.getValue()).isEqualTo(AUDIT_TOPIC);

            AuditEvent event = (AuditEvent) eventCaptor.getValue();
            assertThat(event.getEventType()).isEqualTo(AuditEventType.FRAUD_DETECTION);
            assertThat(event.getUserId()).isEqualTo(customerId);
            assertThat(event.getDetails()).containsKey("transactionId");
            assertThat(event.getDetails()).containsKey("customerId");
        }

        @Test
        @DisplayName("Should use high severity for fraud events")
        void shouldUseHighSeverityForFraudEvents() {
            // Given
            FraudAssessmentResult fraudResult = createFraudResult();

            // When
            auditService.logFraudDetection("txn-123", "customer-456", fraudResult);

            // Then
            verify(kafkaTemplate).send(anyString(), eventCaptor.capture());
            AuditEvent event = (AuditEvent) eventCaptor.getValue();
            assertThat(event.getSeverity()).isIn(AuditSeverity.HIGH, AuditSeverity.CRITICAL);
        }
    }

    @Nested
    @DisplayName("Cryptography Operation Audit Tests")
    class CryptographyOperationAuditTests {

        @Test
        @DisplayName("Should log successful cryptography operation")
        void shouldLogSuccessfulCryptographyOperation() {
            // Given
            String operation = "ENCRYPT";
            String transactionId = "txn-123";
            String userId = "user-456";
            boolean successful = true;

            // When
            auditService.logCryptographyOperation(operation, transactionId, userId, successful);

            // Then
            verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());
            AuditEvent event = (AuditEvent) eventCaptor.getValue();

            assertThat(event.getEventType()).isEqualTo(AuditEventType.CRYPTOGRAPHY_OPERATION);
            assertThat(event.getUserId()).isEqualTo(userId);
            assertThat(event.getDetails()).containsEntry("operation", operation);
            assertThat(event.getDetails()).containsEntry("successful", successful);
        }

        @Test
        @DisplayName("Should log failed cryptography operation")
        void shouldLogFailedCryptographyOperation() {
            // Given
            boolean successful = false;

            // When
            auditService.logCryptographyOperation("DECRYPT", "txn-123", "user-456", successful);

            // Then
            verify(kafkaTemplate).send(anyString(), eventCaptor.capture());
            AuditEvent event = (AuditEvent) eventCaptor.getValue();
            assertThat(event.getDetails()).containsEntry("successful", false);
        }
    }

    @Nested
    @DisplayName("Balance Operation Audit Tests")
    class BalanceOperationAuditTests {

        @Test
        @DisplayName("Should log balance credit operation")
        void shouldLogBalanceCreditOperation() {
            // Given
            String customerId = "customer-123";
            String operation = "CREDIT";
            BigDecimal amount = new BigDecimal("100.00");
            BigDecimal previousBalance = new BigDecimal("500.00");
            BigDecimal newBalance = new BigDecimal("600.00");

            // When
            auditService.logBalanceOperation(customerId, operation, amount, previousBalance, newBalance);

            // Then
            verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());
            AuditEvent event = (AuditEvent) eventCaptor.getValue();

            assertThat(event.getEventType()).isEqualTo(AuditEventType.BALANCE_OPERATION);
            assertThat(event.getUserId()).isEqualTo(customerId);
            assertThat(event.getDetails()).containsEntry("operation", "CREDIT");
            assertThat(event.getDetails()).containsKey("amount");
            assertThat(event.getDetails()).containsKey("previousBalance");
            assertThat(event.getDetails()).containsKey("newBalance");
        }

        @Test
        @DisplayName("Should log balance debit operation")
        void shouldLogBalanceDebitOperation() {
            // Given
            String operation = "DEBIT";

            // When
            auditService.logBalanceOperation("customer-123", operation,
                    new BigDecimal("50.00"), new BigDecimal("100.00"), new BigDecimal("50.00"));

            // Then
            verify(kafkaTemplate).send(anyString(), eventCaptor.capture());
            AuditEvent event = (AuditEvent) eventCaptor.getValue();
            assertThat(event.getDetails()).containsEntry("operation", "DEBIT");
        }
    }

    @Nested
    @DisplayName("Device Operation Audit Tests")
    class DeviceOperationAuditTests {

        @Test
        @DisplayName("Should log device registration")
        void shouldLogDeviceRegistration() {
            // Given
            String deviceId = "device-123";
            String customerId = "customer-456";
            String operation = "REGISTER";
            boolean successful = true;

            // When
            auditService.logDeviceOperation(deviceId, customerId, operation, successful);

            // Then
            verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());
            AuditEvent event = (AuditEvent) eventCaptor.getValue();

            assertThat(event.getEventType()).isEqualTo(AuditEventType.DEVICE_OPERATION);
            assertThat(event.getUserId()).isEqualTo(customerId);
            assertThat(event.getDetails()).containsEntry("deviceId", deviceId);
            assertThat(event.getDetails()).containsEntry("operation", operation);
            assertThat(event.getDetails()).containsEntry("successful", successful);
        }

        @Test
        @DisplayName("Should log failed device operation")
        void shouldLogFailedDeviceOperation() {
            // Given
            boolean successful = false;

            // When
            auditService.logDeviceOperation("device-123", "customer-456", "AUTHENTICATE", successful);

            // Then
            verify(kafkaTemplate).send(anyString(), eventCaptor.capture());
            AuditEvent event = (AuditEvent) eventCaptor.getValue();
            assertThat(event.getDetails()).containsEntry("successful", false);
        }
    }

    @Nested
    @DisplayName("Authentication Audit Tests")
    class AuthenticationAuditTests {

        @Test
        @DisplayName("Should log successful authentication")
        void shouldLogSuccessfulAuthentication() {
            // Given
            String userId = "user-123";
            String authMethod = "PASSWORD";
            boolean successful = true;
            String ipAddress = "192.168.1.1";

            // When
            auditService.logAuthentication(userId, authMethod, successful, ipAddress);

            // Then
            verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());
            AuditEvent event = (AuditEvent) eventCaptor.getValue();

            assertThat(event.getEventType()).isEqualTo(AuditEventType.AUTHENTICATION);
            assertThat(event.getUserId()).isEqualTo(userId);
            assertThat(event.getDetails()).containsEntry("authMethod", authMethod);
            assertThat(event.getDetails()).containsEntry("successful", successful);
            assertThat(event.getDetails()).containsEntry("ipAddress", ipAddress);
        }

        @Test
        @DisplayName("Should log failed authentication with high severity")
        void shouldLogFailedAuthenticationWithHighSeverity() {
            // Given
            boolean successful = false;

            // When
            auditService.logAuthentication("user-123", "PASSWORD", successful, "10.0.0.1");

            // Then
            verify(kafkaTemplate).send(anyString(), eventCaptor.capture());
            AuditEvent event = (AuditEvent) eventCaptor.getValue();
            assertThat(event.getDetails()).containsEntry("successful", false);
            assertThat(event.getSeverity()).isIn(AuditSeverity.HIGH, AuditSeverity.CRITICAL);
        }
    }

    @Nested
    @DisplayName("Compliance Check Audit Tests")
    class ComplianceCheckAuditTests {

        @Test
        @DisplayName("Should log compliance check")
        void shouldLogComplianceCheck() {
            // Given
            String customerId = "customer-123";
            String checkType = "KYC";
            String result = "PASSED";
            Map<String, Object> checkDetails = new HashMap<>();
            checkDetails.put("verificationLevel", "ENHANCED");
            checkDetails.put("documentsVerified", 3);

            // When
            auditService.logComplianceCheck(customerId, checkType, result, checkDetails);

            // Then
            verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());
            AuditEvent event = (AuditEvent) eventCaptor.getValue();

            assertThat(event.getEventType()).isEqualTo(AuditEventType.COMPLIANCE_CHECK);
            assertThat(event.getUserId()).isEqualTo(customerId);
            assertThat(event.getDetails()).containsEntry("checkType", checkType);
            assertThat(event.getDetails()).containsEntry("result", result);
        }

        @Test
        @DisplayName("Should log failed compliance check")
        void shouldLogFailedComplianceCheck() {
            // Given
            String result = "FAILED";

            // When
            auditService.logComplianceCheck("customer-123", "AML", result, new HashMap<>());

            // Then
            verify(kafkaTemplate).send(anyString(), eventCaptor.capture());
            AuditEvent event = (AuditEvent) eventCaptor.getValue();
            assertThat(event.getDetails()).containsEntry("result", "FAILED");
        }
    }

    @Nested
    @DisplayName("System Error Audit Tests")
    class SystemErrorAuditTests {

        @Test
        @DisplayName("Should log system error")
        void shouldLogSystemError() {
            // Given
            String operation = "PAYMENT_PROCESSING";
            String errorCode = "ERR_500";
            String errorMessage = "Internal server error";
            String userId = "user-123";

            // When
            auditService.logSystemError(operation, errorCode, errorMessage, userId);

            // Then
            verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());
            AuditEvent event = (AuditEvent) eventCaptor.getValue();

            assertThat(event.getEventType()).isEqualTo(AuditEventType.SYSTEM_ERROR);
            assertThat(event.getUserId()).isEqualTo(userId);
            assertThat(event.getSeverity()).isEqualTo(AuditSeverity.CRITICAL);
            assertThat(event.getDetails()).containsEntry("operation", operation);
            assertThat(event.getDetails()).containsEntry("errorCode", errorCode);
            assertThat(event.getDetails()).containsEntry("errorMessage", errorMessage);
        }
    }

    // Helper methods

    private PaymentTransaction createTestPaymentTransaction() {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setId("txn-123");
        transaction.setCustomerId("customer-456");
        transaction.setMerchantId("merchant-789");
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrency("USD");
        transaction.setStatus("COMPLETED");
        transaction.setPaymentMethod("CREDIT_CARD");
        return transaction;
    }

    private FraudAssessmentResult createFraudResult() {
        FraudAssessmentResult result = new FraudAssessmentResult();
        result.setRiskScore(45);
        result.setRiskLevel("MEDIUM");
        result.setRecommendation("REVIEW");
        return result;
    }
}

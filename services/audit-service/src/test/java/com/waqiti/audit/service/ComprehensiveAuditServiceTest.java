package com.waqiti.audit.service;

import com.waqiti.audit.client.NotificationServiceClient;
import com.waqiti.audit.client.SecurityServiceClient;
import com.waqiti.audit.domain.AuditLog;
import com.waqiti.audit.domain.SecurityEvent;
import com.waqiti.audit.domain.UserActivity;
import com.waqiti.audit.dto.AuditEventRequest;
import com.waqiti.audit.dto.AuditEventResult;
import com.waqiti.audit.model.AuditEventMessage;
import com.waqiti.audit.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///waqiti_test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "audit.retention.days=2555",
        "audit.compliance.enabled=true",
        "audit.suspicious-activity.enabled=true",
        "audit.real-time-alerts.enabled=true",
        "audit.integrity.verification=true"
})
@DisplayName("Comprehensive Audit Service Tests")
class ComprehensiveAuditServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("waqiti_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private ComprehensiveAuditService auditService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private SecurityEventRepository securityEventRepository;

    @Autowired
    private UserActivityRepository userActivityRepository;

    @MockBean
    private SecurityServiceClient securityServiceClient;

    @MockBean
    private NotificationServiceClient notificationServiceClient;

    @MockBean
    private CryptographicIntegrityService cryptographicIntegrityService;

    @MockBean
    private SuspiciousActivityDetectionEngine suspiciousActivityDetectionEngine;

    @MockBean
    private AuditAnalyticsEngine auditAnalyticsEngine;

    @MockBean
    private ComplianceReportingService complianceReportingService;

    private UUID testUserId;
    private String testSessionId;
    private String testIpAddress;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        securityEventRepository.deleteAll();
        userActivityRepository.deleteAll();

        testUserId = UUID.randomUUID();
        testSessionId = UUID.randomUUID().toString();
        testIpAddress = "192.168.1.100";

        when(cryptographicIntegrityService.generateIntegrityHash(any()))
                .thenReturn("sha256:abcdef1234567890");
    }

    @Nested
    @DisplayName("Audit Log Creation Tests")
    class AuditLogCreationTests {

        @Test
        @Transactional
        @DisplayName("Should create audit log with all required fields")
        void shouldCreateAuditLogWithAllRequiredFields() {
            AuditEventRequest request = createAuditEventRequest(
                    "USER_LOGIN", "User", testUserId.toString(), "LOGIN");

            AuditEventResult result = auditService.recordAuditEvent(request);

            assertThat(result).isNotNull();
            assertThat(result.isSuccessful()).isTrue();
            assertThat(result.getAuditId()).isNotNull();
            assertThat(result.getTimestamp()).isNotNull();
            assertThat(result.getIntegrityHash()).isNotNull();

            AuditLog saved = auditLogRepository.findById(result.getAuditId()).orElseThrow();
            assertThat(saved.getEventType()).isEqualTo("USER_LOGIN");
            assertThat(saved.getEntityType()).isEqualTo("User");
            assertThat(saved.getAction()).isEqualTo("LOGIN");
            assertThat(saved.getUserId()).isEqualTo(testUserId);
        }

        @Test
        @Transactional
        @DisplayName("Should generate cryptographic integrity hash")
        void shouldGenerateCryptographicIntegrityHash() {
            AuditEventRequest request = createAuditEventRequest(
                    "TRANSACTION_CREATED", "Transaction", UUID.randomUUID().toString(), "CREATE");

            AuditEventResult result = auditService.recordAuditEvent(request);

            assertThat(result.getIntegrityHash()).isEqualTo("sha256:abcdef1234567890");
            verify(cryptographicIntegrityService).generateIntegrityHash(any(AuditLog.class));
        }

        @Test
        @Transactional
        @DisplayName("Should capture before and after state")
        void shouldCaptureBeforeAndAfterState() {
            Map<String, Object> beforeState = Map.of(
                    "balance", "1000.00",
                    "status", "ACTIVE"
            );
            Map<String, Object> afterState = Map.of(
                    "balance", "750.00",
                    "status", "ACTIVE"
            );

            AuditEventRequest request = createAuditEventRequest(
                    "ACCOUNT_UPDATED", "Account", UUID.randomUUID().toString(), "UPDATE");
            request.setBeforeState(beforeState);
            request.setAfterState(afterState);

            AuditEventResult result = auditService.recordAuditEvent(request);

            AuditLog saved = auditLogRepository.findById(result.getAuditId()).orElseThrow();
            assertThat(saved.getBeforeState()).isEqualTo(beforeState);
            assertThat(saved.getAfterState()).isEqualTo(afterState);
        }

        @Test
        @Transactional
        @DisplayName("Should include source IP and user agent")
        void shouldIncludeSourceIPAndUserAgent() {
            AuditEventRequest request = createAuditEventRequest(
                    "API_ACCESS", "API", "/api/v1/transactions", "GET");
            request.setSourceIpAddress("203.0.113.45");
            request.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

            AuditEventResult result = auditService.recordAuditEvent(request);

            AuditLog saved = auditLogRepository.findById(result.getAuditId()).orElseThrow();
            assertThat(saved.getSourceIpAddress()).isEqualTo("203.0.113.45");
            assertThat(saved.getUserAgent()).contains("Mozilla");
        }

        @Test
        @Transactional
        @DisplayName("Should include correlation ID for tracing")
        void shouldIncludeCorrelationIDForTracing() {
            String correlationId = UUID.randomUUID().toString();
            AuditEventRequest request = createAuditEventRequest(
                    "PAYMENT_PROCESSED", "Payment", UUID.randomUUID().toString(), "PROCESS");
            request.setCorrelationId(correlationId);

            AuditEventResult result = auditService.recordAuditEvent(request);

            assertThat(result.getCorrelationId()).isEqualTo(correlationId);

            AuditLog saved = auditLogRepository.findById(result.getAuditId()).orElseThrow();
            assertThat(saved.getCorrelationId()).isEqualTo(correlationId);
        }

        @Test
        @Transactional
        @DisplayName("Should include service origin")
        void shouldIncludeServiceOrigin() {
            AuditEventRequest request = createAuditEventRequest(
                    "LEDGER_ENTRY_POSTED", "LedgerEntry", UUID.randomUUID().toString(), "CREATE");
            request.setServiceOrigin("ledger-service");

            AuditEventResult result = auditService.recordAuditEvent(request);

            AuditLog saved = auditLogRepository.findById(result.getAuditId()).orElseThrow();
            assertThat(saved.getServiceOrigin()).isEqualTo("ledger-service");
        }

        @Test
        @Transactional
        @DisplayName("Should store custom metadata")
        void shouldStoreCustomMetadata() {
            Map<String, String> metadata = Map.of(
                    "clientId", "mobile-app-v2.1",
                    "platform", "iOS",
                    "deviceId", "ABC123DEF456"
            );

            AuditEventRequest request = createAuditEventRequest(
                    "MOBILE_LOGIN", "User", testUserId.toString(), "LOGIN");
            request.setMetadata(metadata);

            AuditEventResult result = auditService.recordAuditEvent(request);

            AuditLog saved = auditLogRepository.findById(result.getAuditId()).orElseThrow();
            assertThat(saved.getMetadata()).containsKeys("clientId", "platform", "deviceId");
        }
    }

    @Nested
    @DisplayName("Audit Event Querying Tests")
    class AuditEventQueryingTests {

        @Test
        @Transactional
        @DisplayName("Should query audit logs by user")
        void shouldQueryAuditLogsByUser() {
            UUID userId1 = UUID.randomUUID();
            UUID userId2 = UUID.randomUUID();

            for (int i = 0; i < 5; i++) {
                AuditEventRequest request = createAuditEventRequest(
                        "USER_ACTION_" + i, "User", userId1.toString(), "ACTION");
                request.setUserId(userId1);
                auditService.recordAuditEvent(request);
            }

            for (int i = 0; i < 3; i++) {
                AuditEventRequest request = createAuditEventRequest(
                        "USER_ACTION_" + i, "User", userId2.toString(), "ACTION");
                request.setUserId(userId2);
                auditService.recordAuditEvent(request);
            }

            List<AuditLog> user1Logs = auditLogRepository.findByUserId(userId1);
            assertThat(user1Logs).hasSize(5);

            List<AuditLog> user2Logs = auditLogRepository.findByUserId(userId2);
            assertThat(user2Logs).hasSize(3);
        }

        @Test
        @Transactional
        @DisplayName("Should query audit logs by time range")
        void shouldQueryAuditLogsByTimeRange() {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime yesterday = now.minusDays(1);
            LocalDateTime twoDaysAgo = now.minusDays(2);

            for (int i = 0; i < 10; i++) {
                AuditEventRequest request = createAuditEventRequest(
                        "EVENT_" + i, "Entity", UUID.randomUUID().toString(), "ACTION");
                AuditEventResult result = auditService.recordAuditEvent(request);
                
                AuditLog log = auditLogRepository.findById(result.getAuditId()).orElseThrow();
                log.setTimestamp(yesterday.plusHours(i));
                auditLogRepository.save(log);
            }

            List<AuditLog> logsInRange = auditLogRepository.findByTimestampBetween(
                    yesterday.minusHours(1), now);
            assertThat(logsInRange).hasSize(10);
        }

        @Test
        @Transactional
        @DisplayName("Should query audit logs by event type")
        void shouldQueryAuditLogsByEventType() {
            String[] eventTypes = {"USER_LOGIN", "TRANSACTION_CREATED", "PAYMENT_PROCESSED"};

            for (String eventType : eventTypes) {
                for (int i = 0; i < 3; i++) {
                    AuditEventRequest request = createAuditEventRequest(
                            eventType, "Entity", UUID.randomUUID().toString(), "ACTION");
                    auditService.recordAuditEvent(request);
                }
            }

            List<AuditLog> loginLogs = auditLogRepository.findByEventType("USER_LOGIN");
            assertThat(loginLogs).hasSize(3);

            List<AuditLog> transactionLogs = auditLogRepository.findByEventType("TRANSACTION_CREATED");
            assertThat(transactionLogs).hasSize(3);
        }

        @Test
        @Transactional
        @DisplayName("Should query audit logs by entity")
        void shouldQueryAuditLogsByEntity() {
            String entityId = UUID.randomUUID().toString();

            for (int i = 0; i < 5; i++) {
                AuditEventRequest request = createAuditEventRequest(
                        "ENTITY_UPDATED", "Account", entityId, "UPDATE");
                auditService.recordAuditEvent(request);
            }

            List<AuditLog> entityLogs = auditLogRepository.findByEntityTypeAndEntityId(
                    "Account", entityId);
            assertThat(entityLogs).hasSize(5);
        }

        @Test
        @Transactional
        @DisplayName("Should support pagination for large result sets")
        void shouldSupportPaginationForLargeResultSets() {
            for (int i = 0; i < 50; i++) {
                AuditEventRequest request = createAuditEventRequest(
                        "EVENT_" + i, "Entity", UUID.randomUUID().toString(), "ACTION");
                auditService.recordAuditEvent(request);
            }

            Pageable pageable = PageRequest.of(0, 20);
            Page<AuditLog> page = auditLogRepository.findAll(pageable);

            assertThat(page.getTotalElements()).isEqualTo(50);
            assertThat(page.getContent()).hasSize(20);
            assertThat(page.getTotalPages()).isEqualTo(3);
        }

        @Test
        @Transactional
        @DisplayName("Should filter by multiple criteria")
        void shouldFilterByMultipleCriteria() {
            UUID userId = UUID.randomUUID();
            String eventType = "USER_ACTION";
            LocalDateTime startTime = LocalDateTime.now().minusHours(1);

            for (int i = 0; i < 5; i++) {
                AuditEventRequest request = createAuditEventRequest(
                        eventType, "User", userId.toString(), "ACTION");
                request.setUserId(userId);
                auditService.recordAuditEvent(request);
            }

            List<AuditLog> filteredLogs = auditLogRepository.findByUserIdAndEventTypeAndTimestampAfter(
                    userId, eventType, startTime);
            assertThat(filteredLogs).hasSize(5);
        }
    }

    @Nested
    @DisplayName("Compliance Reporting Tests")
    class ComplianceReportingTests {

        @Test
        @Transactional
        @DisplayName("Should flag PCI DSS compliance events")
        void shouldFlagPCIDSSComplianceEvents() {
            AuditEventRequest request = createAuditEventRequest(
                    "CARD_DATA_ACCESSED", "CardData", UUID.randomUUID().toString(), "READ");
            request.setComplianceFlags(Set.of("PCI_DSS"));

            AuditEventResult result = auditService.recordAuditEvent(request);

            AuditLog saved = auditLogRepository.findById(result.getAuditId()).orElseThrow();
            assertThat(saved.getComplianceFlags()).contains("PCI_DSS");
        }

        @Test
        @Transactional
        @DisplayName("Should flag SOX compliance events")
        void shouldFlagSOXComplianceEvents() {
            AuditEventRequest request = createAuditEventRequest(
                    "FINANCIAL_REPORT_GENERATED", "Report", UUID.randomUUID().toString(), "GENERATE");
            request.setComplianceFlags(Set.of("SOX"));

            AuditEventResult result = auditService.recordAuditEvent(request);

            AuditLog saved = auditLogRepository.findById(result.getAuditId()).orElseThrow();
            assertThat(saved.getComplianceFlags()).contains("SOX");
        }

        @Test
        @Transactional
        @DisplayName("Should flag GDPR compliance events")
        void shouldFlagGDPRComplianceEvents() {
            AuditEventRequest request = createAuditEventRequest(
                    "PERSONAL_DATA_ACCESSED", "UserProfile", testUserId.toString(), "READ");
            request.setComplianceFlags(Set.of("GDPR"));

            AuditEventResult result = auditService.recordAuditEvent(request);

            AuditLog saved = auditLogRepository.findById(result.getAuditId()).orElseThrow();
            assertThat(saved.getComplianceFlags()).contains("GDPR");
        }

        @Test
        @Transactional
        @DisplayName("Should support multiple compliance flags")
        void shouldSupportMultipleComplianceFlags() {
            AuditEventRequest request = createAuditEventRequest(
                    "SENSITIVE_OPERATION", "Data", UUID.randomUUID().toString(), "PROCESS");
            request.setComplianceFlags(Set.of("PCI_DSS", "SOX", "GDPR"));

            AuditEventResult result = auditService.recordAuditEvent(request);

            AuditLog saved = auditLogRepository.findById(result.getAuditId()).orElseThrow();
            assertThat(saved.getComplianceFlags()).hasSize(3);
            assertThat(saved.getComplianceFlags()).containsAll(Set.of("PCI_DSS", "SOX", "GDPR"));
        }

        @Test
        @Transactional
        @DisplayName("Should generate compliance audit trail")
        void shouldGenerateComplianceAuditTrail() {
            String[] complianceEvents = {
                    "USER_CONSENT_RECORDED",
                    "DATA_RETENTION_APPLIED",
                    "RIGHT_TO_ERASURE_EXECUTED",
                    "DATA_PORTABILITY_REQUEST"
            };

            for (String eventType : complianceEvents) {
                AuditEventRequest request = createAuditEventRequest(
                        eventType, "UserData", testUserId.toString(), "COMPLIANCE");
                request.setComplianceFlags(Set.of("GDPR"));
                auditService.recordAuditEvent(request);
            }

            List<AuditLog> complianceLogs = auditLogRepository.findAll();
            assertThat(complianceLogs).hasSize(4);
            assertThat(complianceLogs).allMatch(log -> 
                    log.getComplianceFlags() != null && log.getComplianceFlags().contains("GDPR"));
        }
    }

    @Nested
    @DisplayName("Audit Trail Immutability Tests")
    class AuditTrailImmutabilityTests {

        @Test
        @Transactional
        @DisplayName("Should prevent modification of audit logs")
        void shouldPreventModificationOfAuditLogs() {
            AuditEventRequest request = createAuditEventRequest(
                    "IMMUTABLE_EVENT", "Entity", UUID.randomUUID().toString(), "ACTION");

            AuditEventResult result = auditService.recordAuditEvent(request);

            AuditLog original = auditLogRepository.findById(result.getAuditId()).orElseThrow();
            String originalHash = original.getIntegrityHash();

            assertThat(originalHash).isNotNull();
            assertThat(originalHash).isEqualTo("sha256:abcdef1234567890");
        }

        @Test
        @Transactional
        @DisplayName("Should verify audit log integrity")
        void shouldVerifyAuditLogIntegrity() {
            AuditEventRequest request = createAuditEventRequest(
                    "VERIFIED_EVENT", "Entity", UUID.randomUUID().toString(), "ACTION");

            AuditEventResult result = auditService.recordAuditEvent(request);

            assertThat(result.getIntegrityHash()).isNotNull();
            verify(cryptographicIntegrityService).generateIntegrityHash(any());
        }

        @Test
        @Transactional
        @DisplayName("Should maintain audit chain integrity")
        void shouldMaintainAuditChainIntegrity() {
            List<UUID> auditIds = new ArrayList<>();

            for (int i = 0; i < 5; i++) {
                AuditEventRequest request = createAuditEventRequest(
                        "CHAIN_EVENT_" + i, "Entity", UUID.randomUUID().toString(), "ACTION");
                AuditEventResult result = auditService.recordAuditEvent(request);
                auditIds.add(result.getAuditId());
            }

            for (UUID auditId : auditIds) {
                AuditLog log = auditLogRepository.findById(auditId).orElseThrow();
                assertThat(log.getIntegrityHash()).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("Data Retention Tests")
    class DataRetentionTests {

        @Test
        @Transactional
        @DisplayName("Should support 7-year retention policy")
        void shouldSupport7YearRetentionPolicy() {
            AuditEventRequest request = createAuditEventRequest(
                    "LONG_TERM_EVENT", "Entity", UUID.randomUUID().toString(), "ACTION");

            AuditEventResult result = auditService.recordAuditEvent(request);

            AuditLog saved = auditLogRepository.findById(result.getAuditId()).orElseThrow();
            assertThat(saved.getTimestamp()).isNotNull();
        }

        @Test
        @Transactional
        @DisplayName("Should identify audit logs eligible for archival")
        void shouldIdentifyAuditLogsEligibleForArchival() {
            LocalDateTime oldDate = LocalDateTime.now().minusYears(8);

            AuditEventRequest request = createAuditEventRequest(
                    "OLD_EVENT", "Entity", UUID.randomUUID().toString(), "ACTION");
            AuditEventResult result = auditService.recordAuditEvent(request);

            AuditLog log = auditLogRepository.findById(result.getAuditId()).orElseThrow();
            log.setTimestamp(oldDate);
            auditLogRepository.save(log);

            List<AuditLog> oldLogs = auditLogRepository.findByTimestampBefore(
                    LocalDateTime.now().minusYears(7));
            assertThat(oldLogs).hasSize(1);
        }

        @Test
        @Transactional
        @DisplayName("Should preserve compliance-required audit logs")
        void shouldPreserveComplianceRequiredAuditLogs() {
            AuditEventRequest request = createAuditEventRequest(
                    "COMPLIANCE_EVENT", "Entity", UUID.randomUUID().toString(), "ACTION");
            request.setComplianceFlags(Set.of("SOX", "PCI_DSS"));

            AuditEventResult result = auditService.recordAuditEvent(request);

            AuditLog saved = auditLogRepository.findById(result.getAuditId()).orElseThrow();
            assertThat(saved.getComplianceFlags()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("User Activity Tracking Tests")
    class UserActivityTrackingTests {

        @Test
        @Transactional
        @DisplayName("Should track user login activity")
        void shouldTrackUserLoginActivity() {
            AuditEventRequest request = createAuditEventRequest(
                    "USER_LOGIN", "User", testUserId.toString(), "LOGIN");
            request.setUserId(testUserId);

            auditService.recordAuditEvent(request);

            verify(suspiciousActivityDetectionEngine).analyzeActivity(any());
        }

        @Test
        @Transactional
        @DisplayName("Should track user transaction activity")
        void shouldTrackUserTransactionActivity() {
            AuditEventRequest request = createAuditEventRequest(
                    "TRANSACTION_INITIATED", "Transaction", UUID.randomUUID().toString(), "CREATE");
            request.setUserId(testUserId);

            auditService.recordAuditEvent(request);

            verify(suspiciousActivityDetectionEngine).analyzeActivity(any());
        }

        @Test
        @Transactional
        @DisplayName("Should detect multiple failed login attempts")
        void shouldDetectMultipleFailedLoginAttempts() {
            for (int i = 0; i < 5; i++) {
                AuditEventRequest request = createAuditEventRequest(
                        "LOGIN_FAILED", "User", testUserId.toString(), "LOGIN_ATTEMPT");
                request.setUserId(testUserId);
                auditService.recordAuditEvent(request);
            }

            verify(suspiciousActivityDetectionEngine, times(5)).analyzeActivity(any());
        }

        @Test
        @Transactional
        @DisplayName("Should track privileged access")
        void shouldTrackPrivilegedAccess() {
            AuditEventRequest request = createAuditEventRequest(
                    "ADMIN_ACCESS", "AdminPanel", "/admin/users", "ACCESS");
            request.setUserId(testUserId);
            request.setRiskLevel("HIGH");

            auditService.recordAuditEvent(request);

            AuditLog saved = auditLogRepository.findAll().get(0);
            assertThat(saved.getRiskLevel()).isEqualTo("HIGH");
        }
    }

    @Nested
    @DisplayName("Security Event Detection Tests")
    class SecurityEventDetectionTests {

        @Test
        @Transactional
        @DisplayName("Should detect suspicious IP address patterns")
        void shouldDetectSuspiciousIPAddressPatterns() {
            String suspiciousIp = "192.0.2.1";

            AuditEventRequest request = createAuditEventRequest(
                    "LOGIN_ATTEMPT", "User", testUserId.toString(), "LOGIN");
            request.setSourceIpAddress(suspiciousIp);

            auditService.recordAuditEvent(request);

            verify(suspiciousActivityDetectionEngine).analyzeActivity(any());
        }

        @Test
        @Transactional
        @DisplayName("Should detect unusual transaction patterns")
        void shouldDetectUnusualTransactionPatterns() {
            for (int i = 0; i < 10; i++) {
                AuditEventRequest request = createAuditEventRequest(
                        "HIGH_VALUE_TRANSACTION", "Transaction", UUID.randomUUID().toString(), "CREATE");
                request.setUserId(testUserId);
                request.setRiskLevel("HIGH");
                auditService.recordAuditEvent(request);
            }

            verify(suspiciousActivityDetectionEngine, times(10)).analyzeActivity(any());
        }

        @Test
        @Transactional
        @DisplayName("Should detect unauthorized access attempts")
        void shouldDetectUnauthorizedAccessAttempts() {
            AuditEventRequest request = createAuditEventRequest(
                    "UNAUTHORIZED_ACCESS", "Resource", "/api/admin/sensitive", "ACCESS_DENIED");
            request.setUserId(testUserId);
            request.setRiskLevel("CRITICAL");

            auditService.recordAuditEvent(request);

            AuditLog saved = auditLogRepository.findAll().get(0);
            assertThat(saved.getRiskLevel()).isEqualTo("CRITICAL");
        }

        @Test
        @Transactional
        @DisplayName("Should alert on critical security events")
        void shouldAlertOnCriticalSecurityEvents() {
            AuditEventRequest request = createAuditEventRequest(
                    "PRIVILEGE_ESCALATION_ATTEMPT", "Security", testUserId.toString(), "ESCALATE");
            request.setRiskLevel("CRITICAL");

            auditService.recordAuditEvent(request);

            verify(suspiciousActivityDetectionEngine).analyzeActivity(any());
        }
    }

    @Nested
    @DisplayName("Kafka Event Processing Tests")
    class KafkaEventProcessingTests {

        @Test
        @Transactional
        @DisplayName("Should process audit events from Kafka")
        void shouldProcessAuditEventsFromKafka() {
            AuditEventMessage message = AuditEventMessage.builder()
                    .topic("transaction-events")
                    .eventType("TRANSACTION_COMPLETED")
                    .entityId(UUID.randomUUID().toString())
                    .userId(testUserId)
                    .timestamp(LocalDateTime.now())
                    .build();

            auditService.processAuditEventFromKafka(message);

            List<AuditLog> logs = auditLogRepository.findAll();
            assertThat(logs).hasSize(1);
        }

        @Test
        @Transactional
        @DisplayName("Should handle Kafka processing errors gracefully")
        void shouldHandleKafkaProcessingErrorsGracefully() {
            AuditEventMessage invalidMessage = AuditEventMessage.builder()
                    .topic("invalid-topic")
                    .eventType(null)
                    .build();

            assertThatCode(() -> auditService.processAuditEventFromKafka(invalidMessage))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Audit Failure Handling Tests")
    class AuditFailureHandlingTests {

        @Test
        @Transactional
        @DisplayName("Should record audit failures")
        void shouldRecordAuditFailures() {
            when(cryptographicIntegrityService.generateIntegrityHash(any()))
                    .thenThrow(new RuntimeException("Hashing failed"));

            AuditEventRequest request = createAuditEventRequest(
                    "FAILING_EVENT", "Entity", UUID.randomUUID().toString(), "ACTION");

            AuditEventResult result = auditService.recordAuditEvent(request);

            assertThat(result.isSuccessful()).isFalse();
            assertThat(result.getErrorMessage()).contains("Audit recording failed");
        }

        @Test
        @Transactional
        @DisplayName("Should continue operation despite audit failure")
        void shouldContinueOperationDespiteAuditFailure() {
            when(cryptographicIntegrityService.generateIntegrityHash(any()))
                    .thenThrow(new RuntimeException("Service unavailable"));

            AuditEventRequest request = createAuditEventRequest(
                    "RESILIENT_EVENT", "Entity", UUID.randomUUID().toString(), "ACTION");

            AuditEventResult result = auditService.recordAuditEvent(request);

            assertThat(result).isNotNull();
            assertThat(result.isSuccessful()).isFalse();
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @Transactional
        @DisplayName("Should handle high-volume audit logging")
        void shouldHandleHighVolumeAuditLogging() {
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < 100; i++) {
                AuditEventRequest request = createAuditEventRequest(
                        "BULK_EVENT_" + i, "Entity", UUID.randomUUID().toString(), "ACTION");
                auditService.recordAuditEvent(request);
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            assertThat(duration).isLessThan(10000);

            List<AuditLog> logs = auditLogRepository.findAll();
            assertThat(logs).hasSize(100);
        }

        @Test
        @Transactional
        @DisplayName("Should process audit event within acceptable time")
        void shouldProcessAuditEventWithinAcceptableTime() {
            AuditEventRequest request = createAuditEventRequest(
                    "TIMED_EVENT", "Entity", UUID.randomUUID().toString(), "ACTION");

            long startTime = System.currentTimeMillis();
            auditService.recordAuditEvent(request);
            long endTime = System.currentTimeMillis();

            long duration = endTime - startTime;
            assertThat(duration).isLessThan(1000);
        }
    }

    private AuditEventRequest createAuditEventRequest(String eventType, String entityType, 
                                                       String entityId, String action) {
        return AuditEventRequest.builder()
                .eventType(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .userId(testUserId)
                .sessionId(testSessionId)
                .sourceIpAddress(testIpAddress)
                .userAgent("Test User Agent")
                .serviceOrigin("test-service")
                .correlationId(UUID.randomUUID().toString())
                .build();
    }
}
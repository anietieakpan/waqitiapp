package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentLinkEvent;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentLink;
import com.waqiti.payment.domain.PaymentLinkStatus;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.repository.PaymentLinkRepository;
import com.waqiti.payment.service.PaymentLinkService;
import com.waqiti.payment.service.PaymentCollectionService;
import com.waqiti.payment.metrics.PaymentLinkMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.security.SecurityContext;
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
import java.math.BigDecimal;
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
        "payment.link.default.expiry.days=30",
        "payment.link.quick.expiry.hours=24",
        "payment.link.invoice.expiry.days=90"
})
@DisplayName("Payment Link Events Consumer Tests")
class PaymentLinkEventsConsumerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("waqiti_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private PaymentLinkEventsConsumer linkEventsConsumer;

    @Autowired
    private PaymentLinkRepository linkRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockBean
    private PaymentLinkService linkService;

    @MockBean
    private PaymentCollectionService collectionService;

    @MockBean
    private PaymentLinkMetricsService metricsService;

    @MockBean
    private AuditService auditService;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private SecurityContext securityContext;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private Acknowledgment acknowledgment;

    private String testLinkId;
    private String testCustomerId;
    private String testMerchantId;

    @BeforeEach
    void setUp() {
        linkRepository.deleteAll();
        paymentRepository.deleteAll();

        testLinkId = UUID.randomUUID().toString();
        testCustomerId = UUID.randomUUID().toString();
        testMerchantId = UUID.randomUUID().toString();

        // Mock default behaviors
        when(linkService.generateLinkUrl(any())).thenReturn("https://pay.example.com/link/" + testLinkId);
        when(kafkaTemplate.send(anyString(), any())).thenReturn(mock(CompletableFuture.class));
    }

    @Nested
    @DisplayName("Create Link Tests")
    class CreateLinkTests {

        @Test
        @Transactional
        @DisplayName("Should create payment link successfully")
        void shouldCreatePaymentLinkSuccessfully() {
            PaymentLinkEvent event = createLinkEvent("CREATE_LINK");
            event.setAmount(new BigDecimal("250.00"));
            event.setCurrency("USD");
            event.setDescription("Invoice #INV-2024-001");
            event.setMaxUsageCount(1);
            event.setAllowPartialPayment(false);

            linkEventsConsumer.handlePaymentLinkEvent(event, 0, 0L, acknowledgment);

            List<PaymentLink> links = linkRepository.findAll();
            assertThat(links).hasSize(1);
            
            PaymentLink link = links.get(0);
            assertThat(link.getMerchantId()).isEqualTo(testMerchantId);
            assertThat(link.getCustomerId()).isEqualTo(testCustomerId);
            assertThat(link.getAmount()).isEqualTo(new BigDecimal("250.00"));
            assertThat(link.getCurrency()).isEqualTo("USD");
            assertThat(link.getDescription()).isEqualTo("Invoice #INV-2024-001");
            assertThat(link.getStatus()).isEqualTo(PaymentLinkStatus.ACTIVE);
            assertThat(link.getLinkUrl()).isEqualTo("https://pay.example.com/link/" + testLinkId);
            assertThat(link.getAccessCount()).isEqualTo(0);
            assertThat(link.getMaxUsageCount()).isEqualTo(1);
            assertThat(link.isAllowPartialPayment()).isFalse();
            assertThat(link.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(29));
            assertThat(link.getCreatedAt()).isNotNull();

            verify(linkService).generateLinkUrl(event);
            verify(notificationService).sendCustomerNotification(
                eq(testCustomerId),
                eq("Payment Link Ready"),
                anyString(),
                eq(NotificationService.Priority.MEDIUM)
            );
            verify(metricsService).recordLinkCreated(testMerchantId, new BigDecimal("250.00"));
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should create payment link with partial payment enabled")
        void shouldCreatePaymentLinkWithPartialPaymentEnabled() {
            PaymentLinkEvent event = createLinkEvent("CREATE_LINK");
            event.setAmount(new BigDecimal("1000.00"));
            event.setDescription("Flexible payment link");
            event.setMaxUsageCount(10); // Multiple usage allowed
            event.setAllowPartialPayment(true);

            linkEventsConsumer.handlePaymentLinkEvent(event, 0, 0L, acknowledgment);

            List<PaymentLink> links = linkRepository.findAll();
            assertThat(links).hasSize(1);
            
            PaymentLink link = links.get(0);
            assertThat(link.getAmount()).isEqualTo(new BigDecimal("1000.00"));
            assertThat(link.getMaxUsageCount()).isEqualTo(10);
            assertThat(link.isAllowPartialPayment()).isTrue();

            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should create payment link with default max usage count")
        void shouldCreatePaymentLinkWithDefaultMaxUsageCount() {
            PaymentLinkEvent event = createLinkEvent("CREATE_LINK");
            event.setAmount(new BigDecimal("100.00"));
            // No max usage count specified

            linkEventsConsumer.handlePaymentLinkEvent(event, 0, 0L, acknowledgment);

            List<PaymentLink> links = linkRepository.findAll();
            assertThat(links).hasSize(1);
            
            PaymentLink link = links.get(0);
            assertThat(link.getMaxUsageCount()).isEqualTo(1); // Default value

            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should create payment link without customer notification if no customer ID")
        void shouldCreatePaymentLinkWithoutCustomerNotificationIfNoCustomerId() {
            PaymentLinkEvent event = createLinkEvent("CREATE_LINK");
            event.setCustomerId(null); // No customer ID
            event.setAmount(new BigDecimal("50.00"));

            linkEventsConsumer.handlePaymentLinkEvent(event, 0, 0L, acknowledgment);

            List<PaymentLink> links = linkRepository.findAll();
            assertThat(links).hasSize(1);
            
            PaymentLink link = links.get(0);
            assertThat(link.getCustomerId()).isNull();

            verify(notificationService, never()).sendCustomerNotification(anyString(), anyString(), anyString(), any());
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Link Access Tests")
    class LinkAccessTests {

        @Test
        @Transactional
        @DisplayName("Should track link access and update analytics")
        void shouldTrackLinkAccessAndUpdateAnalytics() {
            PaymentLink link = createTestLink();
            link.setAccessCount(0);
            link = linkRepository.save(link);

            PaymentLinkEvent event = createLinkEvent("LINK_ACCESSED");
            event.setLinkId(link.getId());
            event.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            linkEventsConsumer.handlePaymentLinkEvent(event, 0, 0L, acknowledgment);

            PaymentLink updatedLink = linkRepository.findById(link.getId()).orElseThrow();
            assertThat(updatedLink.getAccessCount()).isEqualTo(1);
            assertThat(updatedLink.getLastAccessedAt()).isNotNull();

            verify(metricsService).recordLinkAccessed(
                eq(testMerchantId), 
                eq("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            );
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should increment access count on multiple accesses")
        void shouldIncrementAccessCountOnMultipleAccesses() {
            PaymentLink link = createTestLink();
            link.setAccessCount(5); // Already accessed 5 times
            link = linkRepository.save(link);

            PaymentLinkEvent event = createLinkEvent("LINK_ACCESSED");
            event.setLinkId(link.getId());

            linkEventsConsumer.handlePaymentLinkEvent(event, 0, 0L, acknowledgment);

            PaymentLink updatedLink = linkRepository.findById(link.getId()).orElseThrow();
            assertThat(updatedLink.getAccessCount()).isEqualTo(6);

            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Payment Collection Tests")
    class PaymentCollectionTests {

        @Test
        @Transactional
        @DisplayName("Should collect full payment and complete link")
        void shouldCollectFullPaymentAndCompleteLink() {
            PaymentLink link = createTestLink();
            link.setAmount(new BigDecimal("100.00"));
            link = linkRepository.save(link);

            PaymentLinkEvent event = createLinkEvent("PAYMENT_COLLECTED");
            event.setLinkId(link.getId());
            event.setCollectedAmount(new BigDecimal("100.00")); // Full amount

            linkEventsConsumer.handlePaymentLinkEvent(event, 0, 0L, acknowledgment);

            // Verify payment created
            List<Payment> payments = paymentRepository.findAll();
            assertThat(payments).hasSize(1);
            
            Payment payment = payments.get(0);
            assertThat(payment.getAmount()).isEqualTo(new BigDecimal("100.00"));
            assertThat(payment.getCurrency()).isEqualTo(link.getCurrency());
            assertThat(payment.getMerchantId()).isEqualTo(link.getMerchantId());
            assertThat(payment.getCustomerId()).isEqualTo(link.getCustomerId());
            assertThat(payment.getPaymentLinkId()).isEqualTo(link.getId());
            assertThat(payment.getDescription()).contains("Payment via link");

            // Verify link updated
            PaymentLink updatedLink = linkRepository.findById(link.getId()).orElseThrow();
            assertThat(updatedLink.getCollectedAmount()).isEqualTo(new BigDecimal("100.00"));
            assertThat(updatedLink.getStatus()).isEqualTo(PaymentLinkStatus.COMPLETED);
            assertThat(updatedLink.getCompletedAt()).isNotNull();
            assertThat(updatedLink.getLastPaymentAt()).isNotNull();

            verify(notificationService).sendCustomerNotification(
                eq(testCustomerId),
                eq("Payment Confirmation"),
                anyString(),
                eq(NotificationService.Priority.HIGH)
            );
            verify(metricsService).recordPaymentCollected(testMerchantId, new BigDecimal("100.00"));
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should collect partial payment and keep link active")
        void shouldCollectPartialPaymentAndKeepLinkActive() {
            PaymentLink link = createTestLink();
            link.setAmount(new BigDecimal("500.00"));
            link.setAllowPartialPayment(true);
            link = linkRepository.save(link);

            PaymentLinkEvent event = createLinkEvent("PAYMENT_COLLECTED");
            event.setLinkId(link.getId());
            event.setCollectedAmount(new BigDecimal("200.00")); // Partial amount

            linkEventsConsumer.handlePaymentLinkEvent(event, 0, 0L, acknowledgment);

            // Verify payment created
            List<Payment> payments = paymentRepository.findAll();
            assertThat(payments).hasSize(1);
            
            Payment payment = payments.get(0);
            assertThat(payment.getAmount()).isEqualTo(new BigDecimal("200.00"));

            // Verify link remains active
            PaymentLink updatedLink = linkRepository.findById(link.getId()).orElseThrow();
            assertThat(updatedLink.getCollectedAmount()).isEqualTo(new BigDecimal("200.00"));
            assertThat(updatedLink.getStatus()).isEqualTo(PaymentLinkStatus.ACTIVE); // Still active
            assertThat(updatedLink.getCompletedAt()).isNull();
            assertThat(updatedLink.getLastPaymentAt()).isNotNull();

            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should collect multiple partial payments and complete when total reached")
        void shouldCollectMultiplePartialPaymentsAndCompleteWhenTotalReached() {
            PaymentLink link = createTestLink();
            link.setAmount(new BigDecimal("300.00"));
            link.setAllowPartialPayment(true);
            link.setCollectedAmount(new BigDecimal("150.00")); // Already collected $150
            link = linkRepository.save(link);

            PaymentLinkEvent event = createLinkEvent("PAYMENT_COLLECTED");
            event.setLinkId(link.getId());
            event.setCollectedAmount(new BigDecimal("150.00")); // Final $150

            linkEventsConsumer.handlePaymentLinkEvent(event, 0, 0L, acknowledgment);

            PaymentLink updatedLink = linkRepository.findById(link.getId()).orElseThrow();
            assertThat(updatedLink.getCollectedAmount()).isEqualTo(new BigDecimal("300.00")); // Total
            assertThat(updatedLink.getStatus()).isEqualTo(PaymentLinkStatus.COMPLETED);
            assertThat(updatedLink.getCompletedAt()).isNotNull();

            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should complete link when collected amount exceeds required amount")
        void shouldCompleteLinkWhenCollectedAmountExceedsRequiredAmount() {
            PaymentLink link = createTestLink();
            link.setAmount(new BigDecimal("100.00"));
            link = linkRepository.save(link);

            PaymentLinkEvent event = createLinkEvent("PAYMENT_COLLECTED");
            event.setLinkId(link.getId());
            event.setCollectedAmount(new BigDecimal("120.00")); // Overpayment

            linkEventsConsumer.handlePaymentLinkEvent(event, 0, 0L, acknowledgment);

            PaymentLink updatedLink = linkRepository.findById(link.getId()).orElseThrow();
            assertThat(updatedLink.getCollectedAmount()).isEqualTo(new BigDecimal("120.00"));
            assertThat(updatedLink.getStatus()).isEqualTo(PaymentLinkStatus.COMPLETED); // Completed due to overpayment

            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Partial Payment Tests")
    class PartialPaymentTests {

        @Test
        @Transactional
        @DisplayName("Should process partial payment when allowed")
        void shouldProcessPartialPaymentWhenAllowed() {
            PaymentLink link = createTestLink();
            link.setAmount(new BigDecimal("800.00"));
            link.setAllowPartialPayment(true);
            link = linkRepository.save(link);

            PaymentLinkEvent event = createLinkEvent("PARTIAL_PAYMENT");
            event.setLinkId(link.getId());
            event.setCollectedAmount(new BigDecimal("300.00"));

            linkEventsConsumer.handlePaymentLinkEvent(event, 0, 0L, acknowledgment);

            // Verify payment created and link updated
            List<Payment> payments = paymentRepository.findAll();
            assertThat(payments).hasSize(1);

            PaymentLink updatedLink = linkRepository.findById(link.getId()).orElseThrow();
            assertThat(updatedLink.getCollectedAmount()).isEqualTo(new BigDecimal("300.00"));
            assertThat(updatedLink.getStatus()).isEqualTo(PaymentLinkStatus.ACTIVE);

            // Verify partial payment notification sent
            verify(notificationService).sendCustomerNotification(
                eq(testCustomerId),
                eq("Partial Payment Received"),
                contains("Partial payment of $300.00 received. Remaining: $500.00"),
                eq(NotificationService.Priority.MEDIUM)
            );

            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should reject partial payment when not allowed")
        void shouldRejectPartialPaymentWhenNotAllowed() {
            PaymentLink link = createTestLink();
            link.setAmount(new BigDecimal("500.00"));
            link.setAllowPartialPayment(false); // Partial payments not allowed
            link = linkRepository.save(link);

            PaymentLinkEvent event = createLinkEvent("PARTIAL_PAYMENT");
            event.setLinkId(link.getId());
            event.setCollectedAmount(new BigDecimal("200.00"));

            linkEventsConsumer.handlePaymentLinkEvent(event, 0, 0L, acknowledgment);

            // Verify no payment created
            List<Payment> payments = paymentRepository.findAll();
            assertThat(payments).isEmpty();

            // Verify link unchanged
            PaymentLink unchangedLink = linkRepository.findById(link.getId()).orElseThrow();
            assertThat(unchangedLink.getCollectedAmount()).isNull();
            assertThat(unchangedLink.getStatus()).isEqualTo(PaymentLinkStatus.ACTIVE);

            verify(notificationService, never()).sendCustomerNotification(anyString(), anyString(), anyString(), any());
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should calculate correct remaining amount in partial payment notification")
        void shouldCalculateCorrectRemainingAmountInPartialPaymentNotification() {
            PaymentLink link = createTestLink();
            link.setAmount(new BigDecimal("1000.00"));
            link.setAllowPartialPayment(true);
            link.setCollectedAmount(new BigDecimal("400.00")); // Already collected
            link = linkRepository.save(link);

            PaymentLinkEvent event = createLinkEvent("PARTIAL_PAYMENT");
            event.setLinkId(link.getId());
            event.setCollectedAmount(new BigDecimal("250.00"));

            linkEventsConsumer.handlePaymentLinkEvent(event, 0, 0L, acknowledgment);

            // Verify notification shows correct remaining amount
            verify(notificationService).sendCustomerNotification(
                eq(testCustomerId),
                eq("Partial Payment Received"),
                contains("Partial payment of $250.00 received. Remaining: $350.00"), // $1000 - $650 = $350
                eq(NotificationService.Priority.MEDIUM)
            );

            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Link Expiration Tests")
    class LinkExpirationTests {

        @Test
        @Transactional
        @DisplayName("Should expire payment link")
        void shouldExpirePaymentLink() {
            PaymentLink link = createTestLink();
            link.setStatus(PaymentLinkStatus.ACTIVE);
            link = linkRepository.save(link);

            PaymentLinkEvent event = createLinkEvent("LINK_EXPIRED");
            event.setLinkId(link.getId());

            linkEventsConsumer.handlePaymentLinkEvent(event, 0, 0L, acknowledgment);

            PaymentLink expiredLink = linkRepository.findById(link.getId()).orElseThrow();
            assertThat(expiredLink.getStatus()).isEqualTo(PaymentLinkStatus.EXPIRED);
            assertThat(expiredLink.getExpiredAt()).isNotNull();

            verify(notificationService).sendMerchantNotification(
                eq(testMerchantId),
                eq("Payment Link Expired"),
                anyString(),
                eq(NotificationService.Priority.LOW)
            );
            verify(metricsService).recordLinkExpired(testMerchantId);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should not send merchant notification if no merchant ID")
        void shouldNotSendMerchantNotificationIfNoMerchantId() {
            PaymentLink link = createTestLink();
            link.setMerchantId(null);
            link = linkRepository.save(link);

            PaymentLinkEvent event = createLinkEvent("LINK_EXPIRED");
            event.setLinkId(link.getId());

            linkEventsConsumer.handlePaymentLinkEvent(event, 0, 0L, acknowledgment);

            PaymentLink expiredLink = linkRepository.findById(link.getId()).orElseThrow();
            assertThat(expiredLink.getStatus()).isEqualTo(PaymentLinkStatus.EXPIRED);

            verify(notificationService, never()).sendMerchantNotification(anyString(), anyString(), anyString(), any());
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Link Deactivation Tests")
    class LinkDeactivationTests {

        @Test
        @Transactional
        @DisplayName("Should deactivate payment link with reason")
        void shouldDeactivatePaymentLinkWithReason() {
            PaymentLink link = createTestLink();
            link.setStatus(PaymentLinkStatus.ACTIVE);
            link = linkRepository.save(link);

            PaymentLinkEvent event = createLinkEvent("LINK_DEACTIVATED");
            event.setLinkId(link.getId());
            event.setDeactivationReason("Merchant request - invoice cancelled");

            linkEventsConsumer.handlePaymentLinkEvent(event, 0, 0L, acknowledgment);

            PaymentLink deactivatedLink = linkRepository.findById(link.getId()).orElseThrow();
            assertThat(deactivatedLink.getStatus()).isEqualTo(PaymentLinkStatus.DEACTIVATED);
            assertThat(deactivatedLink.getDeactivatedAt()).isNotNull();
            assertThat(deactivatedLink.getDeactivationReason()).isEqualTo("Merchant request - invoice cancelled");

            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should deactivate payment link without reason")
        void shouldDeactivatePaymentLinkWithoutReason() {
            PaymentLink link = createTestLink();
            link = linkRepository.save(link);

            PaymentLinkEvent event = createLinkEvent("LINK_DEACTIVATED");
            event.setLinkId(link.getId());
            // No deactivation reason set

            linkEventsConsumer.handlePaymentLinkEvent(event, 0, 0L, acknowledgment);

            PaymentLink deactivatedLink = linkRepository.findById(link.getId()).orElseThrow();
            assertThat(deactivatedLink.getStatus()).isEqualTo(PaymentLinkStatus.DEACTIVATED);
            assertThat(deactivatedLink.getDeactivationReason()).isNull();

            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @Transactional
        @DisplayName("Should handle invalid payment link event")
        void shouldHandleInvalidPaymentLinkEvent() {
            PaymentLinkEvent invalidEvent = PaymentLinkEvent.builder()
                .linkId(null) // Invalid - null link ID
                .action("CREATE_LINK")
                .timestamp(Instant.now())
                .build();

            assertThatCode(() -> 
                linkEventsConsumer.handlePaymentLinkEvent(invalidEvent, 0, 0L, acknowledgment)
            ).doesNotThrowAnyException();

            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should handle payment link not found error")
        void shouldHandlePaymentLinkNotFoundError() {
            PaymentLinkEvent event = createLinkEvent("LINK_ACCESSED");
            event.setLinkId("non-existent-link-id");

            assertThatCode(() -> 
                linkEventsConsumer.handlePaymentLinkEvent(event, 0, 0L, acknowledgment)
            ).doesNotThrowAnyException();

            verify(kafkaTemplate).send(eq("payment-link-events-dlq"), any(Map.class));
            verify(notificationService).sendOperationalAlert(
                eq("Payment Link Event Processing Failed"), 
                anyString(), 
                eq(NotificationService.Priority.HIGH)
            );
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should handle unknown action gracefully")
        void shouldHandleUnknownActionGracefully() {
            PaymentLinkEvent event = createLinkEvent("UNKNOWN_ACTION");

            assertThatCode(() -> 
                linkEventsConsumer.handlePaymentLinkEvent(event, 0, 0L, acknowledgment)
            ).doesNotThrowAnyException();

            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Audit and Metrics Tests")
    class AuditAndMetricsTests {

        @Test
        @Transactional
        @DisplayName("Should audit all payment link operations")
        void shouldAuditAllPaymentLinkOperations() {
            PaymentLinkEvent event = createLinkEvent("CREATE_LINK");
            event.setAmount(new BigDecimal("150.00"));

            linkEventsConsumer.handlePaymentLinkEvent(event, 0, 0L, acknowledgment);

            verify(auditService).logFinancialEvent(
                eq("PAYMENT_LINK_EVENT_PROCESSED"),
                eq(testLinkId),
                any(Map.class)
            );
        }

        @Test
        @Transactional
        @DisplayName("Should record payment link metrics")
        void shouldRecordPaymentLinkMetrics() {
            PaymentLinkEvent event = createLinkEvent("CREATE_LINK");
            event.setAmount(new BigDecimal("300.00"));

            linkEventsConsumer.handlePaymentLinkEvent(event, 0, 0L, acknowledgment);

            verify(metricsService).recordLinkCreated(testMerchantId, new BigDecimal("300.00"));
        }
    }

    @Nested
    @DisplayName("Scheduled Tasks Tests")
    class ScheduledTasksTests {

        @Test
        @Transactional
        @DisplayName("Should check for expired links and publish expiration events")
        void shouldCheckForExpiredLinksAndPublishExpirationEvents() {
            // Create expired link
            PaymentLink expiredLink = createTestLink();
            expiredLink.setStatus(PaymentLinkStatus.ACTIVE);
            expiredLink.setExpiresAt(LocalDateTime.now().minusDays(1)); // Expired yesterday
            linkRepository.save(expiredLink);

            // Create active link
            PaymentLink activeLink = createTestLink();
            activeLink.setStatus(PaymentLinkStatus.ACTIVE);
            activeLink.setExpiresAt(LocalDateTime.now().plusDays(10)); // Not expired
            linkRepository.save(activeLink);

            // Mock repository to return expired links
            when(linkRepository.findExpiredActiveLinks(any())).thenReturn(List.of(expiredLink));

            linkEventsConsumer.checkExpiredLinks();

            // Verify expiration event published for expired link only
            verify(kafkaTemplate).send(eq("payment-link-events"), any(PaymentLinkEvent.class));
        }

        @Test
        @Transactional
        @DisplayName("Should handle expired links check with no expired links")
        void shouldHandleExpiredLinksCheckWithNoExpiredLinks() {
            when(linkRepository.findExpiredActiveLinks(any())).thenReturn(Collections.emptyList());

            assertThatCode(() -> linkEventsConsumer.checkExpiredLinks()).doesNotThrowAnyException();

            verify(kafkaTemplate, never()).send(anyString(), any());
        }

        @Test
        @Transactional
        @DisplayName("Should handle expired links check with processing error")
        void shouldHandleExpiredLinksCheckWithProcessingError() {
            PaymentLink expiredLink = createTestLink();
            expiredLink.setExpiresAt(LocalDateTime.now().minusDays(1));
            
            when(linkRepository.findExpiredActiveLinks(any())).thenReturn(List.of(expiredLink));
            when(kafkaTemplate.send(anyString(), any())).thenThrow(new RuntimeException("Kafka error"));

            assertThatCode(() -> linkEventsConsumer.checkExpiredLinks()).doesNotThrowAnyException();

            verify(kafkaTemplate).send(eq("payment-link-events"), any(PaymentLinkEvent.class));
        }
    }

    @Nested
    @DisplayName("Customer Notification Tests")
    class CustomerNotificationTests {

        @Test
        @Transactional
        @DisplayName("Should send payment confirmation without customer notification if no customer ID")
        void shouldSendPaymentConfirmationWithoutCustomerNotificationIfNoCustomerId() {
            PaymentLink link = createTestLink();
            link.setCustomerId(null); // No customer ID
            link = linkRepository.save(link);

            PaymentLinkEvent event = createLinkEvent("PAYMENT_COLLECTED");
            event.setLinkId(link.getId());
            event.setCollectedAmount(link.getAmount());

            linkEventsConsumer.handlePaymentLinkEvent(event, 0, 0L, acknowledgment);

            // Payment should still be created
            List<Payment> payments = paymentRepository.findAll();
            assertThat(payments).hasSize(1);

            // But no customer notification should be sent
            verify(notificationService, never()).sendCustomerNotification(anyString(), anyString(), anyString(), any());
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should send partial payment notification without customer notification if no customer ID")
        void shouldSendPartialPaymentNotificationWithoutCustomerNotificationIfNoCustomerId() {
            PaymentLink link = createTestLink();
            link.setCustomerId(null);
            link.setAllowPartialPayment(true);
            link = linkRepository.save(link);

            PaymentLinkEvent event = createLinkEvent("PARTIAL_PAYMENT");
            event.setLinkId(link.getId());
            event.setCollectedAmount(new BigDecimal("50.00"));

            linkEventsConsumer.handlePaymentLinkEvent(event, 0, 0L, acknowledgment);

            verify(notificationService, never()).sendCustomerNotification(anyString(), anyString(), anyString(), any());
            verify(acknowledgment).acknowledge();
        }
    }

    /**
     * Helper methods
     */
    private PaymentLinkEvent createLinkEvent(String action) {
        return PaymentLinkEvent.builder()
            .linkId(testLinkId)
            .merchantId(testMerchantId)
            .customerId(testCustomerId)
            .action(action)
            .timestamp(Instant.now())
            .build();
    }

    private PaymentLink createTestLink() {
        return PaymentLink.builder()
            .id(UUID.randomUUID().toString())
            .merchantId(testMerchantId)
            .customerId(testCustomerId)
            .amount(new BigDecimal("200.00"))
            .currency("USD")
            .description("Test payment link")
            .status(PaymentLinkStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusDays(30))
            .linkUrl("https://pay.example.com/link/test-link")
            .accessCount(0)
            .maxUsageCount(1)
            .allowPartialPayment(false)
            .build();
    }
}
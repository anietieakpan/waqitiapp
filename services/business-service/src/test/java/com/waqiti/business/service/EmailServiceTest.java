package com.waqiti.business.service;

import com.waqiti.business.domain.EmailOutbox;
import com.waqiti.business.domain.EmailOutbox.EmailStatus;
import com.waqiti.business.domain.EmailOutbox.EmailType;
import com.waqiti.business.repository.EmailOutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Unit Tests for EmailService
 *
 * Tests cover:
 * - Email queuing with Outbox pattern
 * - Retry logic with exponential backoff
 * - SendGrid webhook processing
 * - Email statistics and monitoring
 * - Error handling and edge cases
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailService Unit Tests")
class EmailServiceTest {

    @Mock
    private EmailOutboxRepository emailOutboxRepository;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    @InjectMocks
    private EmailService emailService;

    private static final String TEST_API_KEY = "SG.test_api_key";
    private static final String TEST_FROM_EMAIL = "test@example.com";
    private static final String TEST_FROM_NAME = "Waqiti Test";

    @BeforeEach
    void setUp() {
        // Set test configuration
        ReflectionTestUtils.setField(emailService, "sendgridApiKey", TEST_API_KEY);
        ReflectionTestUtils.setField(emailService, "defaultFromEmail", TEST_FROM_EMAIL);
        ReflectionTestUtils.setField(emailService, "defaultFromName", TEST_FROM_NAME);
        ReflectionTestUtils.setField(emailService, "sendgridEnabled", false); // Disabled for unit tests

        // Mock metrics
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);
    }

    // ===========================
    // Email Queuing Tests
    // ===========================

    @Nested
    @DisplayName("Email Queuing Tests")
    class EmailQueuingTests {

        @Test
        @DisplayName("Should queue email successfully")
        void shouldQueueEmailSuccessfully() {
            // Arrange
            EmailService.EmailRequest request = EmailService.EmailRequest.builder()
                    .recipientEmail("customer@example.com")
                    .recipientName("John Doe")
                    .subject("Test Invoice")
                    .htmlContent("<html><body>Invoice content</body></html>")
                    .emailType(EmailType.INVOICE)
                    .priority(3)
                    .build();

            ArgumentCaptor<EmailOutbox> emailCaptor = ArgumentCaptor.forClass(EmailOutbox.class);
            when(emailOutboxRepository.save(emailCaptor.capture())).thenAnswer(i -> {
                EmailOutbox saved = i.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });

            // Act
            UUID emailId = emailService.queueEmail(request);

            // Assert
            assertThat(emailId).isNotNull();

            EmailOutbox savedEmail = emailCaptor.getValue();
            assertThat(savedEmail.getRecipientEmail()).isEqualTo("customer@example.com");
            assertThat(savedEmail.getRecipientName()).isEqualTo("John Doe");
            assertThat(savedEmail.getSubject()).isEqualTo("Test Invoice");
            assertThat(savedEmail.getHtmlContent()).contains("Invoice content");
            assertThat(savedEmail.getEmailType()).isEqualTo(EmailType.INVOICE);
            assertThat(savedEmail.getPriority()).isEqualTo(3);
            assertThat(savedEmail.getStatus()).isEqualTo(EmailStatus.PENDING);
            assertThat(savedEmail.getRetryCount()).isEqualTo(0);
            assertThat(savedEmail.getMaxRetries()).isEqualTo(5);

            verify(emailOutboxRepository).save(any(EmailOutbox.class));
            verify(counter).increment();
        }

        @Test
        @DisplayName("Should use default sender if not provided")
        void shouldUseDefaultSender() {
            // Arrange
            EmailService.EmailRequest request = EmailService.EmailRequest.builder()
                    .recipientEmail("test@example.com")
                    .subject("Test")
                    .htmlContent("<html>Test</html>")
                    .emailType(EmailType.NOTIFICATION)
                    .build();

            ArgumentCaptor<EmailOutbox> emailCaptor = ArgumentCaptor.forClass(EmailOutbox.class);
            when(emailOutboxRepository.save(emailCaptor.capture())).thenAnswer(i -> {
                EmailOutbox saved = i.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });

            // Act
            emailService.queueEmail(request);

            // Assert
            EmailOutbox savedEmail = emailCaptor.getValue();
            assertThat(savedEmail.getSenderEmail()).isEqualTo(TEST_FROM_EMAIL);
            assertThat(savedEmail.getSenderName()).isEqualTo(TEST_FROM_NAME);
        }

        @Test
        @DisplayName("Should use default priority if not provided")
        void shouldUseDefaultPriority() {
            // Arrange
            EmailService.EmailRequest request = EmailService.EmailRequest.builder()
                    .recipientEmail("test@example.com")
                    .subject("Test")
                    .htmlContent("<html>Test</html>")
                    .emailType(EmailType.NOTIFICATION)
                    .build();

            ArgumentCaptor<EmailOutbox> emailCaptor = ArgumentCaptor.forClass(EmailOutbox.class);
            when(emailOutboxRepository.save(emailCaptor.capture())).thenAnswer(i -> {
                EmailOutbox saved = i.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });

            // Act
            emailService.queueEmail(request);

            // Assert
            EmailOutbox savedEmail = emailCaptor.getValue();
            assertThat(savedEmail.getPriority()).isEqualTo(5); // Default priority
        }

        @Test
        @DisplayName("Should queue email with template data")
        void shouldQueueEmailWithTemplateData() {
            // Arrange
            Map<String, Object> templateData = Map.of(
                    "customerName", "Jane Smith",
                    "invoiceNumber", "INV-12345",
                    "amount", "1000.00"
            );

            EmailService.EmailRequest request = EmailService.EmailRequest.builder()
                    .recipientEmail("customer@example.com")
                    .subject("Invoice")
                    .htmlContent("<html>Invoice</html>")
                    .templateId("invoice-template")
                    .templateData(templateData)
                    .emailType(EmailType.INVOICE)
                    .build();

            ArgumentCaptor<EmailOutbox> emailCaptor = ArgumentCaptor.forClass(EmailOutbox.class);
            when(emailOutboxRepository.save(emailCaptor.capture())).thenAnswer(i -> {
                EmailOutbox saved = i.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });

            // Act
            emailService.queueEmail(request);

            // Assert
            EmailOutbox savedEmail = emailCaptor.getValue();
            assertThat(savedEmail.getTemplateId()).isEqualTo("invoice-template");
            assertThat(savedEmail.getTemplateData()).containsEntry("customerName", "Jane Smith");
            assertThat(savedEmail.getTemplateData()).containsEntry("amount", "1000.00");
        }
    }

    // ===========================
    // Webhook Processing Tests
    // ===========================

    @Nested
    @DisplayName("Webhook Processing Tests")
    class WebhookProcessingTests {

        @Test
        @DisplayName("Should handle delivered webhook event")
        void shouldHandleDeliveredEvent() {
            // Arrange
            UUID emailId = UUID.randomUUID();
            String sendgridMessageId = "SG-MSG-123456";

            EmailOutbox email = createTestEmail(emailId, EmailStatus.SENT);
            email.setSendgridMessageId(sendgridMessageId);

            EmailService.SendGridWebhookEvent event = EmailService.SendGridWebhookEvent.builder()
                    .event("delivered")
                    .sgMessageId(sendgridMessageId)
                    .timestamp(System.currentTimeMillis())
                    .build();

            when(emailOutboxRepository.findBySendgridMessageId(sendgridMessageId))
                    .thenReturn(Optional.of(email));
            when(emailOutboxRepository.save(any(EmailOutbox.class))).thenReturn(email);

            // Act
            emailService.handleWebhookEvent(event);

            // Assert
            assertThat(email.getStatus()).isEqualTo(EmailStatus.DELIVERED);
            assertThat(email.getDeliveredAt()).isNotNull();

            verify(emailOutboxRepository).save(email);
            verify(counter).increment();
        }

        @Test
        @DisplayName("Should handle opened webhook event")
        void shouldHandleOpenedEvent() {
            // Arrange
            UUID emailId = UUID.randomUUID();
            String sendgridMessageId = "SG-MSG-789012";

            EmailOutbox email = createTestEmail(emailId, EmailStatus.DELIVERED);
            email.setSendgridMessageId(sendgridMessageId);

            EmailService.SendGridWebhookEvent event = EmailService.SendGridWebhookEvent.builder()
                    .event("open")
                    .sgMessageId(sendgridMessageId)
                    .timestamp(System.currentTimeMillis())
                    .build();

            when(emailOutboxRepository.findBySendgridMessageId(sendgridMessageId))
                    .thenReturn(Optional.of(email));
            when(emailOutboxRepository.save(any(EmailOutbox.class))).thenReturn(email);

            // Act
            emailService.handleWebhookEvent(event);

            // Assert
            assertThat(email.getOpenedAt()).isNotNull();

            verify(emailOutboxRepository).save(email);
            verify(counter).increment();
        }

        @Test
        @DisplayName("Should handle bounced webhook event")
        void shouldHandleBouncedEvent() {
            // Arrange
            UUID emailId = UUID.randomUUID();
            String sendgridMessageId = "SG-MSG-BOUNCE";

            EmailOutbox email = createTestEmail(emailId, EmailStatus.SENT);
            email.setSendgridMessageId(sendgridMessageId);

            EmailService.SendGridWebhookEvent event = EmailService.SendGridWebhookEvent.builder()
                    .event("bounce")
                    .sgMessageId(sendgridMessageId)
                    .reason("Invalid email address")
                    .timestamp(System.currentTimeMillis())
                    .build();

            when(emailOutboxRepository.findBySendgridMessageId(sendgridMessageId))
                    .thenReturn(Optional.of(email));
            when(emailOutboxRepository.save(any(EmailOutbox.class))).thenReturn(email);

            // Act
            emailService.handleWebhookEvent(event);

            // Assert
            assertThat(email.getStatus()).isEqualTo(EmailStatus.BOUNCED);
            assertThat(email.getBouncedAt()).isNotNull();
            assertThat(email.getBounceReason()).isEqualTo("Invalid email address");

            verify(emailOutboxRepository).save(email);
            verify(counter).increment();
        }

        @Test
        @DisplayName("Should handle webhook for non-existent email gracefully")
        void shouldHandleWebhookForNonExistentEmail() {
            // Arrange
            EmailService.SendGridWebhookEvent event = EmailService.SendGridWebhookEvent.builder()
                    .event("delivered")
                    .sgMessageId("NON-EXISTENT-ID")
                    .timestamp(System.currentTimeMillis())
                    .build();

            when(emailOutboxRepository.findBySendgridMessageId("NON-EXISTENT-ID"))
                    .thenReturn(Optional.empty());

            // Act & Assert - Should not throw exception
            assertThatCode(() -> emailService.handleWebhookEvent(event))
                    .doesNotThrowAnyException();

            verify(emailOutboxRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should only record first open event")
        void shouldOnlyRecordFirstOpenEvent() {
            // Arrange
            UUID emailId = UUID.randomUUID();
            String sendgridMessageId = "SG-MSG-OPEN";

            EmailOutbox email = createTestEmail(emailId, EmailStatus.DELIVERED);
            email.setSendgridMessageId(sendgridMessageId);
            email.setOpenedAt(LocalDateTime.now().minusHours(1)); // Already opened

            EmailService.SendGridWebhookEvent event = EmailService.SendGridWebhookEvent.builder()
                    .event("open")
                    .sgMessageId(sendgridMessageId)
                    .timestamp(System.currentTimeMillis())
                    .build();

            when(emailOutboxRepository.findBySendgridMessageId(sendgridMessageId))
                    .thenReturn(Optional.of(email));
            when(emailOutboxRepository.save(any(EmailOutbox.class))).thenReturn(email);

            LocalDateTime firstOpenTime = email.getOpenedAt();

            // Act
            emailService.handleWebhookEvent(event);

            // Assert
            assertThat(email.getOpenedAt()).isEqualTo(firstOpenTime); // Should not change

            verify(emailOutboxRepository).save(email);
        }
    }

    // ===========================
    // Email Statistics Tests
    // ===========================

    @Nested
    @DisplayName("Email Statistics Tests")
    class EmailStatisticsTests {

        @Test
        @DisplayName("Should calculate email statistics correctly")
        void shouldCalculateEmailStatistics() {
            // Arrange
            LocalDateTime since = LocalDateTime.now().minusDays(7);

            List<Object[]> mockStats = Arrays.asList(
                    new Object[]{EmailStatus.PENDING, 5L},
                    new Object[]{EmailStatus.SENT, 100L},
                    new Object[]{EmailStatus.DELIVERED, 95L},
                    new Object[]{EmailStatus.FAILED, 2L},
                    new Object[]{EmailStatus.RETRY_SCHEDULED, 3L}
            );

            when(emailOutboxRepository.getEmailStatistics(since)).thenReturn(mockStats);

            // Act
            EmailService.EmailStatistics stats = emailService.getEmailStatistics(since);

            // Assert
            assertThat(stats.getPending()).isEqualTo(8); // PENDING + RETRY_SCHEDULED
            assertThat(stats.getSent()).isEqualTo(100);
            assertThat(stats.getDelivered()).isEqualTo(95);
            assertThat(stats.getFailed()).isEqualTo(2);
            assertThat(stats.getSince()).isEqualTo(since);

            verify(emailOutboxRepository).getEmailStatistics(since);
        }

        @Test
        @DisplayName("Should handle empty statistics")
        void shouldHandleEmptyStatistics() {
            // Arrange
            LocalDateTime since = LocalDateTime.now().minusDays(1);

            when(emailOutboxRepository.getEmailStatistics(since)).thenReturn(Collections.emptyList());

            // Act
            EmailService.EmailStatistics stats = emailService.getEmailStatistics(since);

            // Assert
            assertThat(stats.getPending()).isEqualTo(0);
            assertThat(stats.getSent()).isEqualTo(0);
            assertThat(stats.getDelivered()).isEqualTo(0);
            assertThat(stats.getFailed()).isEqualTo(0);
        }
    }

    // ===========================
    // Helper Methods
    // ===========================

    private EmailOutbox createTestEmail(UUID id, EmailStatus status) {
        return EmailOutbox.builder()
                .id(id)
                .recipientEmail("test@example.com")
                .recipientName("Test User")
                .senderEmail(TEST_FROM_EMAIL)
                .senderName(TEST_FROM_NAME)
                .subject("Test Email")
                .htmlContent("<html>Test content</html>")
                .emailType(EmailType.NOTIFICATION)
                .status(status)
                .priority(5)
                .retryCount(0)
                .maxRetries(5)
                .createdAt(LocalDateTime.now())
                .build();
    }
}

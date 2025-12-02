package com.waqiti.merchant.kafka;

import com.waqiti.common.events.MerchantSettlementFailedEvent;
import com.waqiti.merchant.domain.MerchantAccount;
import com.waqiti.merchant.domain.SettlementEntry;
import com.waqiti.merchant.domain.SettlementStatus;
import com.waqiti.merchant.domain.SettlementFailureRecord;
import com.waqiti.merchant.repository.MerchantAccountRepository;
import com.waqiti.merchant.repository.SettlementRepository;
import com.waqiti.merchant.repository.SettlementFailureRepository;
import com.waqiti.merchant.service.MerchantNotificationService;
import com.waqiti.merchant.service.SettlementRetryService;
import com.waqiti.merchant.service.ComplianceService;
import com.waqiti.merchant.service.RiskAssessmentService;
import com.waqiti.merchant.service.SupportTicketService;
import com.waqiti.merchant.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantSettlementFailedConsumerTest {

    @Mock
    private MerchantAccountRepository merchantAccountRepository;
    
    @Mock
    private SettlementRepository settlementRepository;
    
    @Mock
    private SettlementFailureRepository settlementFailureRepository;
    
    @Mock
    private MerchantNotificationService merchantNotificationService;
    
    @Mock
    private SettlementRetryService settlementRetryService;
    
    @Mock
    private ComplianceService complianceService;
    
    @Mock
    private RiskAssessmentService riskAssessmentService;
    
    @Mock
    private SupportTicketService supportTicketService;
    
    @Mock
    private AuditService auditService;
    
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @InjectMocks
    private MerchantSettlementFailedConsumer consumer;
    
    private MerchantSettlementFailedEvent testEvent;
    private MerchantAccount testMerchant;
    private SettlementEntry testSettlement;
    
    @BeforeEach
    void setUp() {
        testEvent = MerchantSettlementFailedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .timestamp(Instant.now())
            .settlementId("settlement-123")
            .merchantId("merchant-456")
            .merchantName("Test Merchant Inc")
            .grossAmount(new BigDecimal("10000.00"))
            .netAmount(new BigDecimal("9500.00"))
            .totalFees(new BigDecimal("400.00"))
            .totalTax(new BigDecimal("100.00"))
            .currency("USD")
            .transactionCount(50)
            .settlementPeriod("2025-01-01 to 2025-01-31")
            .failureReason("Bank account details invalid")
            .failureCategory("BANK_TRANSFER")
            .failureCode("INVALID_ACCOUNT")
            .bankTransferId("bank-transfer-789")
            .bankErrorCode("ERR_INVALID_ACCT")
            .bankErrorMessage("Account number format invalid")
            .retryAttempt(0)
            .maxRetryAttempts(3)
            .retryable(false)
            .complianceViolation(false)
            .amlViolation(false)
            .sanctionsViolation(false)
            .kycNonCompliant(false)
            .riskScore(25)
            .highRisk(false)
            .impactLevel("MEDIUM")
            .requiresImmediateAction(false)
            .correlationId("corr-123")
            .build();
            
        testMerchant = new MerchantAccount();
        testMerchant.setId("merchant-456");
        testMerchant.setName("Test Merchant Inc");
        testMerchant.setEmail("merchant@test.com");
        testMerchant.setPhone("+1234567890");
        testMerchant.setSettlementFailureCount(0);
        testMerchant.setSettlementSuspended(false);
        testMerchant.setBankAccountVerified(true);
        
        testSettlement = new SettlementEntry();
        testSettlement.setSettlementId("settlement-123");
        testSettlement.setMerchantId("merchant-456");
        testSettlement.setGrossAmount(new BigDecimal("10000.00"));
        testSettlement.setStatus(SettlementStatus.PENDING);
    }
    
    @Test
    void shouldProcessSettlementFailureSuccessfully() {
        when(settlementFailureRepository.existsByEventId(anyString())).thenReturn(false);
        when(settlementFailureRepository.save(any(SettlementFailureRecord.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(merchantAccountRepository.findById("merchant-456"))
            .thenReturn(Optional.of(testMerchant));
        when(settlementRepository.findBySettlementIdAndMerchantId("settlement-123", "merchant-456"))
            .thenReturn(Optional.of(testSettlement));
        when(supportTicketService.createTicket(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn("ticket-999");
        when(riskAssessmentService.calculateMerchantRiskScore(anyString()))
            .thenReturn(30);
        
        consumer.handleMerchantSettlementFailed(testEvent);
        
        ArgumentCaptor<SettlementFailureRecord> failureCaptor = ArgumentCaptor.forClass(SettlementFailureRecord.class);
        verify(settlementFailureRepository, atLeastOnce()).save(failureCaptor.capture());
        
        SettlementFailureRecord failure = failureCaptor.getValue();
        assertThat(failure.getSettlementId()).isEqualTo("settlement-123");
        assertThat(failure.getMerchantId()).isEqualTo("merchant-456");
        assertThat(failure.getFailureCategory()).isEqualTo("BANK_TRANSFER");
        assertThat(failure.isRetryable()).isFalse();
        
        verify(merchantAccountRepository).save(any(MerchantAccount.class));
        verify(supportTicketService).createTicket(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }
    
    @Test
    void shouldHandleComplianceViolation() {
        testEvent.setComplianceViolation(true);
        testEvent.setComplianceFlag("AML_ALERT");
        testEvent.setAmlViolation(true);
        
        when(settlementFailureRepository.existsByEventId(anyString())).thenReturn(false);
        when(settlementFailureRepository.save(any(SettlementFailureRecord.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(merchantAccountRepository.findById("merchant-456"))
            .thenReturn(Optional.of(testMerchant));
        when(complianceService.createComplianceCase(
            anyString(), anyString(), anyString(), anyString()))
            .thenReturn("case-555");
        when(riskAssessmentService.calculateMerchantRiskScore(anyString()))
            .thenReturn(75);
        
        consumer.handleMerchantSettlementFailed(testEvent);
        
        verify(complianceService).createComplianceCase(
            eq("merchant-456"), eq("settlement-123"), eq("AML_ALERT"), anyString());
        verify(complianceService).generateSAR(
            eq(testMerchant), eq("settlement-123"), anyString());
        verify(complianceService).notifyComplianceTeam(
            eq("merchant-456"), eq("settlement-123"), eq("AML_ALERT"), anyString());
        
        ArgumentCaptor<MerchantAccount> merchantCaptor = ArgumentCaptor.forClass(MerchantAccount.class);
        verify(merchantAccountRepository).save(merchantCaptor.capture());
        
        MerchantAccount updatedMerchant = merchantCaptor.getValue();
        assertThat(updatedMerchant.isSettlementSuspended()).isTrue();
        assertThat(updatedMerchant.getSuspensionReason()).contains("Bank account details invalid");
    }
    
    @Test
    void shouldScheduleRetryForRetryableFailures() {
        testEvent.setFailureCategory("SYSTEM_ERROR");
        testEvent.setFailureCode("TEMPORARY_ERROR");
        testEvent.setRetryable(true);
        testEvent.setRetryAttempt(1);
        
        when(settlementFailureRepository.existsByEventId(anyString())).thenReturn(false);
        when(settlementFailureRepository.save(any(SettlementFailureRecord.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(merchantAccountRepository.findById("merchant-456"))
            .thenReturn(Optional.of(testMerchant));
        when(riskAssessmentService.calculateMerchantRiskScore(anyString()))
            .thenReturn(25);
        
        consumer.handleMerchantSettlementFailed(testEvent);
        
        verify(settlementRetryService).scheduleRetry(
            eq("settlement-123"), eq("merchant-456"), any(LocalDateTime.class), eq(2), eq(3));
        
        ArgumentCaptor<SettlementFailureRecord> failureCaptor = ArgumentCaptor.forClass(SettlementFailureRecord.class);
        verify(settlementFailureRepository, atLeastOnce()).save(failureCaptor.capture());
        
        SettlementFailureRecord failure = failureCaptor.getValue();
        assertThat(failure.isRetryScheduled()).isTrue();
        assertThat(failure.getNextRetryAt()).isNotNull();
    }
    
    @Test
    void shouldNotRetryComplianceViolations() {
        testEvent.setComplianceViolation(true);
        testEvent.setRetryable(true);
        
        when(settlementFailureRepository.existsByEventId(anyString())).thenReturn(false);
        when(settlementFailureRepository.save(any(SettlementFailureRecord.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(merchantAccountRepository.findById("merchant-456"))
            .thenReturn(Optional.of(testMerchant));
        when(complianceService.createComplianceCase(anyString(), anyString(), anyString(), anyString()))
            .thenReturn("case-111");
        when(riskAssessmentService.calculateMerchantRiskScore(anyString()))
            .thenReturn(80);
        
        consumer.handleMerchantSettlementFailed(testEvent);
        
        verify(settlementRetryService, never()).scheduleRetry(
            anyString(), anyString(), any(LocalDateTime.class), anyInt(), anyInt());
    }
    
    @Test
    void shouldSuspendMerchantAfterExcessiveFailures() {
        testMerchant.setSettlementFailureCount(4);
        
        when(settlementFailureRepository.existsByEventId(anyString())).thenReturn(false);
        when(settlementFailureRepository.save(any(SettlementFailureRecord.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(merchantAccountRepository.findById("merchant-456"))
            .thenReturn(Optional.of(testMerchant));
        when(riskAssessmentService.calculateMerchantRiskScore(anyString()))
            .thenReturn(60);
        
        consumer.handleMerchantSettlementFailed(testEvent);
        
        ArgumentCaptor<MerchantAccount> merchantCaptor = ArgumentCaptor.forClass(MerchantAccount.class);
        verify(merchantAccountRepository).save(merchantCaptor.capture());
        
        MerchantAccount updatedMerchant = merchantCaptor.getValue();
        assertThat(updatedMerchant.getSettlementFailureCount()).isEqualTo(5);
        assertThat(updatedMerchant.isSettlementSuspended()).isTrue();
        assertThat(updatedMerchant.getSuspensionReason()).contains("Excessive settlement failures: 5");
    }
    
    @Test
    void shouldMarkBankAccountAsUnverifiedForBankTransferFailures() {
        when(settlementFailureRepository.existsByEventId(anyString())).thenReturn(false);
        when(settlementFailureRepository.save(any(SettlementFailureRecord.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(merchantAccountRepository.findById("merchant-456"))
            .thenReturn(Optional.of(testMerchant));
        when(riskAssessmentService.calculateMerchantRiskScore(anyString()))
            .thenReturn(35);
        
        consumer.handleMerchantSettlementFailed(testEvent);
        
        ArgumentCaptor<MerchantAccount> merchantCaptor = ArgumentCaptor.forClass(MerchantAccount.class);
        verify(merchantAccountRepository).save(merchantCaptor.capture());
        
        MerchantAccount updatedMerchant = merchantCaptor.getValue();
        assertThat(updatedMerchant.isBankAccountVerified()).isFalse();
        assertThat(updatedMerchant.getBankAccountIssue()).isEqualTo("Account number format invalid");
    }
    
    @Test
    void shouldSendEmailNotificationToMerchant() {
        when(settlementFailureRepository.existsByEventId(anyString())).thenReturn(false);
        when(settlementFailureRepository.save(any(SettlementFailureRecord.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(merchantAccountRepository.findById("merchant-456"))
            .thenReturn(Optional.of(testMerchant));
        when(riskAssessmentService.calculateMerchantRiskScore(anyString()))
            .thenReturn(25);
        
        consumer.handleMerchantSettlementFailed(testEvent);
        
        verify(merchantNotificationService).sendSettlementFailureEmail(
            eq("merchant@test.com"), eq("settlement-123"),
            eq(new BigDecimal("10000.00")), eq("USD"), anyString(),
            eq(false), isNull(), eq("NORMAL"));
    }
    
    @Test
    void shouldSendSMSForCriticalFailures() {
        testEvent.setImpactLevel("CRITICAL");
        
        when(settlementFailureRepository.existsByEventId(anyString())).thenReturn(false);
        when(settlementFailureRepository.save(any(SettlementFailureRecord.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(merchantAccountRepository.findById("merchant-456"))
            .thenReturn(Optional.of(testMerchant));
        when(riskAssessmentService.calculateMerchantRiskScore(anyString()))
            .thenReturn(25);
        
        consumer.handleMerchantSettlementFailed(testEvent);
        
        verify(merchantNotificationService).sendSettlementFailureSMS(
            eq("+1234567890"), eq("settlement-123"), anyString());
    }
    
    @Test
    void shouldCreateSupportTicketForNonRetryableFailures() {
        when(settlementFailureRepository.existsByEventId(anyString())).thenReturn(false);
        when(settlementFailureRepository.save(any(SettlementFailureRecord.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(merchantAccountRepository.findById("merchant-456"))
            .thenReturn(Optional.of(testMerchant));
        when(supportTicketService.createTicket(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn("ticket-777");
        when(riskAssessmentService.calculateMerchantRiskScore(anyString()))
            .thenReturn(25);
        
        consumer.handleMerchantSettlementFailed(testEvent);
        
        verify(supportTicketService).createTicket(
            contains("Settlement Failure"), contains("Merchant settlement failed"),
            eq("MEDIUM"), eq("SETTLEMENTS"), eq("merchant-456"), eq("settlement-123"));
        
        ArgumentCaptor<SettlementFailureRecord> failureCaptor = ArgumentCaptor.forClass(SettlementFailureRecord.class);
        verify(settlementFailureRepository, atLeastOnce()).save(failureCaptor.capture());
        
        SettlementFailureRecord failure = failureCaptor.getValue();
        assertThat(failure.getSupportTicketId()).isEqualTo("ticket-777");
    }
    
    @Test
    void shouldUpdateSettlementEntryStatus() {
        when(settlementFailureRepository.existsByEventId(anyString())).thenReturn(false);
        when(settlementFailureRepository.save(any(SettlementFailureRecord.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(merchantAccountRepository.findById("merchant-456"))
            .thenReturn(Optional.of(testMerchant));
        when(settlementRepository.findBySettlementIdAndMerchantId("settlement-123", "merchant-456"))
            .thenReturn(Optional.of(testSettlement));
        when(riskAssessmentService.calculateMerchantRiskScore(anyString()))
            .thenReturn(25);
        
        consumer.handleMerchantSettlementFailed(testEvent);
        
        ArgumentCaptor<SettlementEntry> settlementCaptor = ArgumentCaptor.forClass(SettlementEntry.class);
        verify(settlementRepository).save(settlementCaptor.capture());
        
        SettlementEntry updatedSettlement = settlementCaptor.getValue();
        assertThat(updatedSettlement.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(updatedSettlement.getFailureReason()).isEqualTo("Bank account details invalid");
        assertThat(updatedSettlement.getFailureCategory()).isEqualTo("BANK_TRANSFER");
        assertThat(updatedSettlement.getBankErrorCode()).isEqualTo("ERR_INVALID_ACCT");
        assertThat(updatedSettlement.isRetryable()).isFalse();
        assertThat(updatedSettlement.getFailedAt()).isNotNull();
    }
    
    @Test
    void shouldUpdateMerchantRiskScore() {
        when(settlementFailureRepository.existsByEventId(anyString())).thenReturn(false);
        when(settlementFailureRepository.save(any(SettlementFailureRecord.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(merchantAccountRepository.findById("merchant-456"))
            .thenReturn(Optional.of(testMerchant));
        when(riskAssessmentService.calculateMerchantRiskScore("merchant-456"))
            .thenReturn(45);
        
        consumer.handleMerchantSettlementFailed(testEvent);
        
        verify(riskAssessmentService).recordSettlementFailure(
            eq("merchant-456"), eq("settlement-123"),
            eq("BANK_TRANSFER"), eq(new BigDecimal("10000.00")));
        verify(riskAssessmentService).calculateMerchantRiskScore("merchant-456");
        
        ArgumentCaptor<MerchantAccount> merchantCaptor = ArgumentCaptor.forClass(MerchantAccount.class);
        verify(merchantAccountRepository).save(merchantCaptor.capture());
        
        MerchantAccount updatedMerchant = merchantCaptor.getValue();
        assertThat(updatedMerchant.getRiskScore()).isEqualTo(45);
    }
    
    @Test
    void shouldPublishAnalyticsEvent() {
        when(settlementFailureRepository.existsByEventId(anyString())).thenReturn(false);
        when(settlementFailureRepository.save(any(SettlementFailureRecord.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(merchantAccountRepository.findById("merchant-456"))
            .thenReturn(Optional.of(testMerchant));
        when(riskAssessmentService.calculateMerchantRiskScore(anyString()))
            .thenReturn(25);
        
        consumer.handleMerchantSettlementFailed(testEvent);
        
        verify(kafkaTemplate).send(eq("analytics.settlement.failure"), anyMap());
    }
    
    @Test
    void shouldCreateAuditTrail() {
        when(settlementFailureRepository.existsByEventId(anyString())).thenReturn(false);
        when(settlementFailureRepository.save(any(SettlementFailureRecord.class)))
            .thenAnswer(inv -> {
                SettlementFailureRecord record = inv.getArgument(0);
                record.setId(999L);
                return record;
            });
        when(merchantAccountRepository.findById("merchant-456"))
            .thenReturn(Optional.of(testMerchant));
        when(riskAssessmentService.calculateMerchantRiskScore(anyString()))
            .thenReturn(25);
        
        consumer.handleMerchantSettlementFailed(testEvent);
        
        verify(auditService).logSettlementFailure(
            eq("settlement-123"), eq("merchant-456"), eq("Test Merchant Inc"),
            eq(new BigDecimal("10000.00")), eq("USD"), anyString(),
            eq("BANK_TRANSFER"), eq(false), eq(999L), eq("corr-123"));
    }
    
    @Test
    void shouldHandleIdempotentEvents() {
        when(settlementFailureRepository.existsByEventId(testEvent.getEventId()))
            .thenReturn(true);
        
        consumer.handleMerchantSettlementFailed(testEvent);
        
        verify(settlementFailureRepository, never()).save(any(SettlementFailureRecord.class));
        verify(merchantAccountRepository, never()).findById(anyString());
    }
    
    @Test
    void shouldCalculateExponentialBackoffForRetries() {
        testEvent.setRetryable(true);
        testEvent.setFailureCategory("SYSTEM_ERROR");
        testEvent.setRetryAttempt(0);
        
        when(settlementFailureRepository.existsByEventId(anyString())).thenReturn(false);
        when(settlementFailureRepository.save(any(SettlementFailureRecord.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(merchantAccountRepository.findById("merchant-456"))
            .thenReturn(Optional.of(testMerchant));
        when(riskAssessmentService.calculateMerchantRiskScore(anyString()))
            .thenReturn(25);
        
        consumer.handleMerchantSettlementFailed(testEvent);
        
        ArgumentCaptor<LocalDateTime> retryTimeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(settlementRetryService).scheduleRetry(
            eq("settlement-123"), eq("merchant-456"),
            retryTimeCaptor.capture(), eq(1), eq(3));
        
        LocalDateTime retryTime = retryTimeCaptor.getValue();
        assertThat(retryTime).isAfter(LocalDateTime.now().plusMinutes(4));
        assertThat(retryTime).isBefore(LocalDateTime.now().plusMinutes(6));
    }
    
    @Test
    void shouldNotRetryAfterMaxAttemptsReached() {
        testEvent.setRetryable(true);
        testEvent.setFailureCategory("SYSTEM_ERROR");
        testEvent.setRetryAttempt(3);
        testEvent.setMaxRetryAttempts(3);
        
        when(settlementFailureRepository.existsByEventId(anyString())).thenReturn(false);
        when(settlementFailureRepository.save(any(SettlementFailureRecord.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(merchantAccountRepository.findById("merchant-456"))
            .thenReturn(Optional.of(testMerchant));
        when(supportTicketService.createTicket(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn("ticket-max-retry");
        when(riskAssessmentService.calculateMerchantRiskScore(anyString()))
            .thenReturn(40);
        
        consumer.handleMerchantSettlementFailed(testEvent);
        
        verify(settlementRetryService, never()).scheduleRetry(
            anyString(), anyString(), any(LocalDateTime.class), anyInt(), anyInt());
        verify(supportTicketService).createTicket(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }
    
    @Test
    void shouldCreateCriticalAlertOnProcessingFailure() {
        when(settlementFailureRepository.existsByEventId(anyString())).thenReturn(false);
        when(settlementFailureRepository.save(any(SettlementFailureRecord.class)))
            .thenThrow(new RuntimeException("Database connection failed"));
        
        assertThrows(RuntimeException.class, () -> {
            consumer.handleMerchantSettlementFailed(testEvent);
        });
        
        verify(kafkaTemplate).send(eq("monitoring.critical-alerts"), anyMap());
    }
    
    @Test
    void shouldHandleMerchantNotFoundGracefully() {
        when(settlementFailureRepository.existsByEventId(anyString())).thenReturn(false);
        when(settlementFailureRepository.save(any(SettlementFailureRecord.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(merchantAccountRepository.findById("merchant-456"))
            .thenReturn(Optional.empty());
        
        assertThrows(RuntimeException.class, () -> {
            consumer.handleMerchantSettlementFailed(testEvent);
        });
        
        verify(settlementFailureRepository).save(any(SettlementFailureRecord.class));
    }
}
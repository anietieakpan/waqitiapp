package com.waqiti.familyaccount.service;

import com.waqiti.familyaccount.domain.FamilyAccount;
import com.waqiti.familyaccount.domain.FamilyMember;
import com.waqiti.familyaccount.domain.FamilySpendingRule;
import com.waqiti.familyaccount.domain.TransactionAttempt;
import com.waqiti.familyaccount.dto.TransactionAuthorizationRequest;
import com.waqiti.familyaccount.dto.TransactionAuthorizationResponse;
import com.waqiti.familyaccount.exception.FamilyMemberNotFoundException;
import com.waqiti.familyaccount.exception.SpendingLimitExceededException;
import com.waqiti.familyaccount.repository.FamilyMemberRepository;
import com.waqiti.familyaccount.repository.TransactionAttemptRepository;
import com.waqiti.familyaccount.service.integration.FamilyExternalServiceFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FamilyTransactionAuthorizationService
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@ExtendWith(MockitoExtension.class)
class FamilyTransactionAuthorizationServiceTest {

    @Mock
    private FamilyMemberRepository familyMemberRepository;

    @Mock
    private TransactionAttemptRepository transactionAttemptRepository;

    @Mock
    private SpendingLimitService spendingLimitService;

    @Mock
    private SpendingRuleService spendingRuleService;

    @Mock
    private FamilyExternalServiceFacade externalServiceFacade;

    @InjectMocks
    private FamilyTransactionAuthorizationService authorizationService;

    private TransactionAuthorizationRequest request;
    private FamilyMember familyMember;
    private FamilyAccount familyAccount;

    @BeforeEach
    void setUp() {
        // Setup family account
        familyAccount = FamilyAccount.builder()
            .familyId("FAM-123")
            .familyName("Test Family")
            .primaryParentUserId("parent123")
            .build();

        // Setup family member
        familyMember = FamilyMember.builder()
            .userId("user123")
            .familyAccount(familyAccount)
            .memberStatus(FamilyMember.MemberStatus.ACTIVE)
            .walletId("wallet123")
            .dailySpendingLimit(new BigDecimal("100.00"))
            .transactionApprovalRequired(false)
            .build();

        // Setup request
        request = TransactionAuthorizationRequest.builder()
            .userId("user123")
            .transactionAmount(new BigDecimal("50.00"))
            .merchantName("Test Merchant")
            .merchantCategory("RETAIL")
            .description("Test transaction")
            .build();
    }

    // ==================== authorizeTransaction() - Success Cases ====================

    @Test
    void testAuthorizeTransaction_AllValidationsPassed_ShouldAuthorize() {
        // Given
        when(familyMemberRepository.findByUserId("user123"))
            .thenReturn(Optional.of(familyMember));
        when(externalServiceFacade.getWalletBalance("wallet123"))
            .thenReturn(new BigDecimal("200.00"));
        doNothing().when(spendingLimitService)
            .validateSpendingLimits(eq(familyMember), any(BigDecimal.class));
        when(spendingRuleService.checkRuleViolation(
            eq(familyMember), anyString(), any(BigDecimal.class), any(LocalDateTime.class)))
            .thenReturn(null);
        when(transactionAttemptRepository.save(any(TransactionAttempt.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        TransactionAuthorizationResponse response = authorizationService.authorizeTransaction(request);

        // Then
        assertTrue(response.getAuthorized());
        assertNull(response.getDeclineReason());
        assertFalse(response.getRequiresParentApproval());
        assertNull(response.getApprovalMessage());

        verify(familyMemberRepository).findByUserId("user123");
        verify(externalServiceFacade).getWalletBalance("wallet123");
        verify(spendingLimitService).validateSpendingLimits(eq(familyMember), any(BigDecimal.class));
        verify(spendingRuleService).checkRuleViolation(any(), anyString(), any(), any());
        verify(transactionAttemptRepository).save(any(TransactionAttempt.class));
        verify(externalServiceFacade).sendTransactionAuthorizedNotification(anyString(), anyString(), any(), anyString());
    }

    // ==================== authorizeTransaction() - Decline Cases ====================

    @Test
    void testAuthorizeTransaction_MemberNotFound_ShouldDecline() {
        // Given
        when(familyMemberRepository.findByUserId("user123"))
            .thenReturn(Optional.empty());

        // When
        TransactionAuthorizationResponse response = authorizationService.authorizeTransaction(request);

        // Then
        assertFalse(response.getAuthorized());
        assertEquals("Family member not found", response.getDeclineReason());
        assertFalse(response.getRequiresParentApproval());

        verify(familyMemberRepository).findByUserId("user123");
        verifyNoInteractions(externalServiceFacade);
    }

    @Test
    void testAuthorizeTransaction_MemberInactive_ShouldDecline() {
        // Given
        familyMember.setMemberStatus(FamilyMember.MemberStatus.INACTIVE);

        when(familyMemberRepository.findByUserId("user123"))
            .thenReturn(Optional.of(familyMember));
        when(transactionAttemptRepository.save(any(TransactionAttempt.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        TransactionAuthorizationResponse response = authorizationService.authorizeTransaction(request);

        // Then
        assertFalse(response.getAuthorized());
        assertEquals("Family member account is not active", response.getDeclineReason());
        assertFalse(response.getRequiresParentApproval());
    }

    @Test
    void testAuthorizeTransaction_InsufficientBalance_ShouldDecline() {
        // Given
        when(familyMemberRepository.findByUserId("user123"))
            .thenReturn(Optional.of(familyMember));
        when(externalServiceFacade.getWalletBalance("wallet123"))
            .thenReturn(new BigDecimal("30.00"));  // Less than transaction amount
        when(transactionAttemptRepository.save(any(TransactionAttempt.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        TransactionAuthorizationResponse response = authorizationService.authorizeTransaction(request);

        // Then
        assertFalse(response.getAuthorized());
        assertEquals("Insufficient wallet balance", response.getDeclineReason());
        assertFalse(response.getRequiresParentApproval());

        verify(transactionAttemptRepository).save(any(TransactionAttempt.class));
    }

    @Test
    void testAuthorizeTransaction_SpendingLimitExceeded_ShouldDecline() {
        // Given
        when(familyMemberRepository.findByUserId("user123"))
            .thenReturn(Optional.of(familyMember));
        when(externalServiceFacade.getWalletBalance("wallet123"))
            .thenReturn(new BigDecimal("200.00"));
        doThrow(new SpendingLimitExceededException("Daily", new BigDecimal("100.00"), new BigDecimal("150.00")))
            .when(spendingLimitService)
            .validateSpendingLimits(eq(familyMember), any(BigDecimal.class));
        when(transactionAttemptRepository.save(any(TransactionAttempt.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        TransactionAuthorizationResponse response = authorizationService.authorizeTransaction(request);

        // Then
        assertFalse(response.getAuthorized());
        assertTrue(response.getDeclineReason().contains("Daily"));
        assertTrue(response.getDeclineReason().contains("spending limit exceeded"));
        assertFalse(response.getRequiresParentApproval());
    }

    @Test
    void testAuthorizeTransaction_SpendingRuleViolated_ShouldDecline() {
        // Given
        FamilySpendingRule violatedRule = FamilySpendingRule.builder()
            .ruleName("No Gaming Purchases")
            .ruleType(FamilySpendingRule.RuleType.MERCHANT_RESTRICTION)
            .requiresApproval(false)
            .build();

        when(familyMemberRepository.findByUserId("user123"))
            .thenReturn(Optional.of(familyMember));
        when(externalServiceFacade.getWalletBalance("wallet123"))
            .thenReturn(new BigDecimal("200.00"));
        doNothing().when(spendingLimitService)
            .validateSpendingLimits(eq(familyMember), any(BigDecimal.class));
        when(spendingRuleService.checkRuleViolation(
            eq(familyMember), anyString(), any(BigDecimal.class), any(LocalDateTime.class)))
            .thenReturn(violatedRule);
        when(transactionAttemptRepository.save(any(TransactionAttempt.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        TransactionAuthorizationResponse response = authorizationService.authorizeTransaction(request);

        // Then
        assertFalse(response.getAuthorized());
        assertTrue(response.getDeclineReason().contains("No Gaming Purchases"));
        assertFalse(response.getRequiresParentApproval());
    }

    // ==================== authorizeTransaction() - Approval Required Cases ====================

    @Test
    void testAuthorizeTransaction_RuleRequiresApproval_ShouldRequireApproval() {
        // Given
        FamilySpendingRule approvalRule = FamilySpendingRule.builder()
            .ruleName("Large Purchases")
            .ruleType(FamilySpendingRule.RuleType.APPROVAL_REQUIRED)
            .requiresApproval(true)
            .build();

        when(familyMemberRepository.findByUserId("user123"))
            .thenReturn(Optional.of(familyMember));
        when(externalServiceFacade.getWalletBalance("wallet123"))
            .thenReturn(new BigDecimal("200.00"));
        doNothing().when(spendingLimitService)
            .validateSpendingLimits(eq(familyMember), any(BigDecimal.class));
        when(spendingRuleService.checkRuleViolation(
            eq(familyMember), anyString(), any(BigDecimal.class), any(LocalDateTime.class)))
            .thenReturn(approvalRule);
        when(transactionAttemptRepository.save(any(TransactionAttempt.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        TransactionAuthorizationResponse response = authorizationService.authorizeTransaction(request);

        // Then
        assertFalse(response.getAuthorized());
        assertNull(response.getDeclineReason());
        assertTrue(response.getRequiresParentApproval());
        assertTrue(response.getApprovalMessage().contains("Large Purchases"));

        verify(externalServiceFacade).sendApprovalRequiredNotification(
            anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void testAuthorizeTransaction_MemberRequiresApproval_ShouldRequireApproval() {
        // Given
        familyMember.setTransactionApprovalRequired(true);

        when(familyMemberRepository.findByUserId("user123"))
            .thenReturn(Optional.of(familyMember));
        when(externalServiceFacade.getWalletBalance("wallet123"))
            .thenReturn(new BigDecimal("200.00"));
        doNothing().when(spendingLimitService)
            .validateSpendingLimits(eq(familyMember), any(BigDecimal.class));
        when(spendingRuleService.checkRuleViolation(
            eq(familyMember), anyString(), any(BigDecimal.class), any(LocalDateTime.class)))
            .thenReturn(null);
        when(transactionAttemptRepository.save(any(TransactionAttempt.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        TransactionAuthorizationResponse response = authorizationService.authorizeTransaction(request);

        // Then
        assertFalse(response.getAuthorized());
        assertTrue(response.getRequiresParentApproval());
        assertTrue(response.getApprovalMessage().contains("require parent approval"));
    }

    // ==================== approveTransaction() Tests ====================

    @Test
    void testApproveTransaction_ValidParent_ShouldApprove() {
        // Given
        TransactionAttempt attempt = TransactionAttempt.builder()
            .id(1L)
            .familyMember(familyMember)
            .amount(new BigDecimal("50.00"))
            .merchantName("Test Merchant")
            .requiresParentApproval(true)
            .approvalStatus("PENDING")
            .build();

        when(transactionAttemptRepository.findById(1L))
            .thenReturn(Optional.of(attempt));
        when(externalServiceFacade.getWalletBalance("wallet123"))
            .thenReturn(new BigDecimal("200.00"));
        when(transactionAttemptRepository.save(any(TransactionAttempt.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        boolean result = authorizationService.approveTransaction(1L, "parent123");

        // Then
        assertTrue(result);
        verify(transactionAttemptRepository).save(argThat(savedAttempt ->
            savedAttempt.getAuthorized() &&
            "APPROVED".equals(savedAttempt.getApprovalStatus()) &&
            "parent123".equals(savedAttempt.getApprovedByUserId())
        ));
        verify(externalServiceFacade).sendTransactionApprovedNotification(
            anyString(), anyString(), any(), anyString());
    }

    @Test
    void testApproveTransaction_NotParent_ShouldThrowException() {
        // Given
        TransactionAttempt attempt = TransactionAttempt.builder()
            .id(1L)
            .familyMember(familyMember)
            .amount(new BigDecimal("50.00"))
            .requiresParentApproval(true)
            .approvalStatus("PENDING")
            .build();

        when(transactionAttemptRepository.findById(1L))
            .thenReturn(Optional.of(attempt));

        // When / Then
        assertThrows(RuntimeException.class, () ->
            authorizationService.approveTransaction(1L, "nonparent456")
        );

        verify(transactionAttemptRepository, never()).save(any());
    }

    @Test
    void testApproveTransaction_InsufficientBalanceAtApprovalTime_ShouldDecline() {
        // Given
        TransactionAttempt attempt = TransactionAttempt.builder()
            .id(1L)
            .familyMember(familyMember)
            .amount(new BigDecimal("50.00"))
            .requiresParentApproval(true)
            .approvalStatus("PENDING")
            .build();

        when(transactionAttemptRepository.findById(1L))
            .thenReturn(Optional.of(attempt));
        when(externalServiceFacade.getWalletBalance("wallet123"))
            .thenReturn(new BigDecimal("30.00"));  // Insufficient
        when(transactionAttemptRepository.save(any(TransactionAttempt.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        boolean result = authorizationService.approveTransaction(1L, "parent123");

        // Then
        assertFalse(result);
        verify(transactionAttemptRepository).save(argThat(savedAttempt ->
            "DECLINED".equals(savedAttempt.getApprovalStatus())
        ));
    }

    // ==================== declineTransaction() Tests ====================

    @Test
    void testDeclineTransaction_ValidParent_ShouldDecline() {
        // Given
        TransactionAttempt attempt = TransactionAttempt.builder()
            .id(1L)
            .familyMember(familyMember)
            .amount(new BigDecimal("50.00"))
            .merchantName("Test Merchant")
            .requiresParentApproval(true)
            .approvalStatus("PENDING")
            .build();

        when(transactionAttemptRepository.findById(1L))
            .thenReturn(Optional.of(attempt));
        when(transactionAttemptRepository.save(any(TransactionAttempt.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        authorizationService.declineTransaction(1L, "parent123", "Not appropriate");

        // Then
        verify(transactionAttemptRepository).save(argThat(savedAttempt ->
            !savedAttempt.getAuthorized() &&
            "DECLINED".equals(savedAttempt.getApprovalStatus()) &&
            "Not appropriate".equals(savedAttempt.getDeclineReason()) &&
            "parent123".equals(savedAttempt.getApprovedByUserId())
        ));
        verify(externalServiceFacade).sendTransactionDeclinedNotification(
            anyString(), anyString(), any(), anyString(), eq("Not appropriate"));
    }

    @Test
    void testDeclineTransaction_NotParent_ShouldThrowException() {
        // Given
        TransactionAttempt attempt = TransactionAttempt.builder()
            .id(1L)
            .familyMember(familyMember)
            .amount(new BigDecimal("50.00"))
            .requiresParentApproval(true)
            .approvalStatus("PENDING")
            .build();

        when(transactionAttemptRepository.findById(1L))
            .thenReturn(Optional.of(attempt));

        // When / Then
        assertThrows(RuntimeException.class, () ->
            authorizationService.declineTransaction(1L, "nonparent456", "Test reason")
        );

        verify(transactionAttemptRepository, never()).save(any());
    }
}

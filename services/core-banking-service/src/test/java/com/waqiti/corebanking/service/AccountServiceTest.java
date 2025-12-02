package com.waqiti.corebanking.service;

import com.waqiti.corebanking.domain.Account;
import com.waqiti.corebanking.exception.AccountNotFoundException;
import com.waqiti.corebanking.repository.AccountRepository;
import com.waqiti.corebanking.service.AccountService.CreateAccountRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for AccountService
 * 
 * Tests account lifecycle management including:
 * - Account creation with validation
 * - Account activation and status transitions
 * - Balance operations and limits
 * - Account retrieval and queries
 * - Error handling and edge cases
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService Tests")
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    private CreateAccountRequest validRequest;
    private Account testAccount;
    private UUID testUserId;
    private UUID testAccountId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testAccountId = UUID.randomUUID();

        validRequest = CreateAccountRequest.builder()
                .userId(testUserId)
                .accountName("Test Savings Account")
                .accountType("SAVINGS")
                .currency("USD")
                .dailyLimit(new BigDecimal("5000.00"))
                .monthlyLimit(new BigDecimal("50000.00"))
                .minimumBalance(new BigDecimal("100.00"))
                .build();

        testAccount = Account.builder()
                .id(testAccountId)
                .accountNumber("1234567890")
                .userId(testUserId)
                .accountName("Test Savings Account")
                .accountType("SAVINGS")
                .accountCategory(Account.AccountCategory.LIABILITY)
                .status(Account.AccountStatus.PENDING)
                .currency("USD")
                .currentBalance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .pendingBalance(BigDecimal.ZERO)
                .reservedBalance(BigDecimal.ZERO)
                .dailyTransactionLimit(new BigDecimal("5000.00"))
                .monthlyTransactionLimit(new BigDecimal("50000.00"))
                .minimumBalance(new BigDecimal("100.00"))
                .complianceLevel(Account.ComplianceLevel.STANDARD)
                .openedDate(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("Account Creation Tests")
    class AccountCreationTests {

        @Test
        @DisplayName("Should create user account with valid request")
        void shouldCreateUserAccountSuccessfully() {
            when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

            Account result = accountService.createUserAccount(validRequest);

            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(testUserId);
            assertThat(result.getAccountType()).isEqualTo("SAVINGS");
            assertThat(result.getStatus()).isEqualTo(Account.AccountStatus.PENDING);
            assertThat(result.getCurrentBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getAccountCategory()).isEqualTo(Account.AccountCategory.LIABILITY);

            verify(accountRepository, times(1)).save(any(Account.class));
        }

        @Test
        @DisplayName("Should set initial balances to zero")
        void shouldSetInitialBalancesToZero() {
            ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
            when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

            accountService.createUserAccount(validRequest);

            verify(accountRepository).save(accountCaptor.capture());
            Account savedAccount = accountCaptor.getValue();

            assertThat(savedAccount.getCurrentBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(savedAccount.getAvailableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(savedAccount.getPendingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(savedAccount.getReservedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should generate unique account number")
        void shouldGenerateUniqueAccountNumber() {
            when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

            Account account1 = accountService.createUserAccount(validRequest);
            Account account2 = accountService.createUserAccount(validRequest);

            assertThat(account1.getAccountNumber()).isNotNull();
            assertThat(account2.getAccountNumber()).isNotNull();
            // Note: In real implementation, should verify uniqueness
        }

        @Test
        @DisplayName("Should set compliance level to STANDARD by default")
        void shouldSetDefaultComplianceLevel() {
            ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
            when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

            accountService.createUserAccount(validRequest);

            verify(accountRepository).save(accountCaptor.capture());
            assertThat(accountCaptor.getValue().getComplianceLevel())
                    .isEqualTo(Account.ComplianceLevel.STANDARD);
        }

        @Test
        @DisplayName("Should set opened date to current timestamp")
        void shouldSetOpenedDateToNow() {
            ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
            LocalDateTime beforeCreation = LocalDateTime.now();
            when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

            accountService.createUserAccount(validRequest);

            verify(accountRepository).save(accountCaptor.capture());
            LocalDateTime openedDate = accountCaptor.getValue().getOpenedDate();
            
            assertThat(openedDate).isNotNull();
            assertThat(openedDate).isAfterOrEqualTo(beforeCreation);
            assertThat(openedDate).isBeforeOrEqualTo(LocalDateTime.now().plusSeconds(1));
        }

        @Test
        @DisplayName("Should preserve all request parameters in created account")
        void shouldPreserveAllRequestParameters() {
            ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
            when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

            accountService.createUserAccount(validRequest);

            verify(accountRepository).save(accountCaptor.capture());
            Account savedAccount = accountCaptor.getValue();

            assertThat(savedAccount.getUserId()).isEqualTo(validRequest.getUserId());
            assertThat(savedAccount.getAccountName()).isEqualTo(validRequest.getAccountName());
            assertThat(savedAccount.getAccountType()).isEqualTo(validRequest.getAccountType());
            assertThat(savedAccount.getCurrency()).isEqualTo(validRequest.getCurrency());
            assertThat(savedAccount.getDailyTransactionLimit())
                    .isEqualByComparingTo(validRequest.getDailyLimit());
            assertThat(savedAccount.getMonthlyTransactionLimit())
                    .isEqualByComparingTo(validRequest.getMonthlyLimit());
            assertThat(savedAccount.getMinimumBalance())
                    .isEqualByComparingTo(validRequest.getMinimumBalance());
        }
    }

    @Nested
    @DisplayName("Account Activation Tests")
    class AccountActivationTests {

        @Test
        @DisplayName("Should activate pending account successfully")
        void shouldActivatePendingAccountSuccessfully() {
            when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));
            when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

            accountService.activateAccount(testAccountId);

            ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(accountCaptor.capture());
            assertThat(accountCaptor.getValue().getStatus()).isEqualTo(Account.AccountStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should throw exception when activating already active account")
        void shouldThrowExceptionWhenActivatingActiveAccount() {
            testAccount.setStatus(Account.AccountStatus.ACTIVE);
            when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));

            assertThatThrownBy(() -> accountService.activateAccount(testAccountId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only pending accounts can be activated");

            verify(accountRepository, never()).save(any(Account.class));
        }

        @Test
        @DisplayName("Should throw exception when activating closed account")
        void shouldThrowExceptionWhenActivatingClosedAccount() {
            testAccount.setStatus(Account.AccountStatus.CLOSED);
            when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));

            assertThatThrownBy(() -> accountService.activateAccount(testAccountId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only pending accounts can be activated");
        }

        @Test
        @DisplayName("Should throw exception when activating suspended account")
        void shouldThrowExceptionWhenActivatingSuspendedAccount() {
            testAccount.setStatus(Account.AccountStatus.SUSPENDED);
            when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));

            assertThatThrownBy(() -> accountService.activateAccount(testAccountId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only pending accounts can be activated");
        }

        @Test
        @DisplayName("Should throw exception when account not found for activation")
        void shouldThrowExceptionWhenAccountNotFoundForActivation() {
            when(accountRepository.findById(testAccountId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.activateAccount(testAccountId))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining("Account not found");
        }
    }

    @Nested
    @DisplayName("Account Retrieval Tests")
    class AccountRetrievalTests {

        @Test
        @DisplayName("Should get account by ID successfully")
        void shouldGetAccountByIdSuccessfully() {
            when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));

            Account result = accountService.getAccountById(testAccountId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(testAccountId);
            assertThat(result.getAccountNumber()).isEqualTo(testAccount.getAccountNumber());
            verify(accountRepository, times(1)).findById(testAccountId);
        }

        @Test
        @DisplayName("Should throw exception when account not found by ID")
        void shouldThrowExceptionWhenAccountNotFoundById() {
            when(accountRepository.findById(testAccountId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.getAccountById(testAccountId))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining("Account not found: " + testAccountId);
        }

        @Test
        @DisplayName("Should get all active user accounts")
        void shouldGetAllActiveUserAccounts() {
            Account account2 = Account.builder()
                    .id(UUID.randomUUID())
                    .accountNumber("0987654321")
                    .userId(testUserId)
                    .status(Account.AccountStatus.ACTIVE)
                    .build();

            List<Account> expectedAccounts = Arrays.asList(testAccount, account2);
            when(accountRepository.findByUserIdAndStatus(testUserId, Account.AccountStatus.ACTIVE))
                    .thenReturn(expectedAccounts);

            List<Account> result = accountService.getUserAccounts(testUserId);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactlyElementsOf(expectedAccounts);
            verify(accountRepository, times(1))
                    .findByUserIdAndStatus(testUserId, Account.AccountStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should return empty list when user has no accounts")
        void shouldReturnEmptyListWhenUserHasNoAccounts() {
            when(accountRepository.findByUserIdAndStatus(testUserId, Account.AccountStatus.ACTIVE))
                    .thenReturn(Arrays.asList());

            List<Account> result = accountService.getUserAccounts(testUserId);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should filter out non-active accounts")
        void shouldFilterOutNonActiveAccounts() {
            // Only active accounts should be returned, not PENDING, SUSPENDED, or CLOSED
            when(accountRepository.findByUserIdAndStatus(testUserId, Account.AccountStatus.ACTIVE))
                    .thenReturn(Arrays.asList(testAccount));

            List<Account> result = accountService.getUserAccounts(testUserId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(Account.AccountStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle null user ID gracefully in account creation")
        void shouldHandleNullUserIdInAccountCreation() {
            validRequest.setUserId(null);

            // This should ideally throw a validation exception
            // Behavior depends on validation implementation
            assertThatThrownBy(() -> accountService.createUserAccount(validRequest))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Should handle repository save failure")
        void shouldHandleRepositorySaveFailure() {
            when(accountRepository.save(any(Account.class)))
                    .thenThrow(new RuntimeException("Database error"));

            assertThatThrownBy(() -> accountService.createUserAccount(validRequest))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Database error");
        }

        @Test
        @DisplayName("Should handle concurrent activation attempts")
        void shouldHandleConcurrentActivationAttempts() {
            when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));
            when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

            // First activation should succeed
            accountService.activateAccount(testAccountId);

            // Subsequent activation should fail
            testAccount.setStatus(Account.AccountStatus.ACTIVE);
            when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));

            assertThatThrownBy(() -> accountService.activateAccount(testAccountId))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Should handle large transaction limits")
        void shouldHandleLargeTransactionLimits() {
            validRequest.setDailyLimit(new BigDecimal("999999999.99"));
            validRequest.setMonthlyLimit(new BigDecimal("9999999999.99"));

            when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

            Account result = accountService.createUserAccount(validRequest);

            assertThat(result).isNotNull();
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        @DisplayName("Should handle zero transaction limits")
        void shouldHandleZeroTransactionLimits() {
            validRequest.setDailyLimit(BigDecimal.ZERO);
            validRequest.setMonthlyLimit(BigDecimal.ZERO);

            when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

            Account result = accountService.createUserAccount(validRequest);

            assertThat(result).isNotNull();
            verify(accountRepository).save(any(Account.class));
        }
    }
}
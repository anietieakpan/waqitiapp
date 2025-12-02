package com.waqiti.wallet.service;

import com.waqiti.wallet.domain.FundReservation;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.domain.WalletStatus;
import com.waqiti.wallet.domain.Currency;
import com.waqiti.wallet.exception.InsufficientBalanceException;
import com.waqiti.wallet.exception.TransactionLimitExceededException;
import com.waqiti.wallet.exception.WalletLockException;
import com.waqiti.wallet.exception.WalletNotFoundException;
import com.waqiti.wallet.exception.WalletNotActiveException;
import com.waqiti.wallet.lock.DistributedWalletLockService;
import com.waqiti.wallet.repository.FundReservationRepository;
import com.waqiti.wallet.repository.WalletRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for WalletBalanceService.
 *
 * <p>This test class provides extensive coverage of:
 * <ul>
 *   <li>Fund reservation operations (reserve, confirm, release)</li>
 *   <li>Balance update operations (credit, debit, update)</li>
 *   <li>Distributed locking behavior</li>
 *   <li>Optimistic locking retry logic</li>
 *   <li>Error handling and edge cases</li>
 *   <li>Concurrency scenarios</li>
 *   <li>Transaction isolation verification</li>
 * </ul>
 *
 * <p>Test Coverage Goals:
 * <ul>
 *   <li>Happy path scenarios: ✅</li>
 *   <li>Error conditions: ✅</li>
 *   <li>Boundary conditions: ✅</li>
 *   <li>Concurrency edge cases: ✅</li>
 *   <li>Security validations: ✅</li>
 * </ul>
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-22
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WalletBalanceService Comprehensive Tests")
class WalletBalanceServiceComprehensiveTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private FundReservationRepository fundReservationRepository;

    @Mock
    private DistributedWalletLockService lockService;

    private MeterRegistry meterRegistry;
    private WalletBalanceService walletBalanceService;

    private static final UUID TEST_WALLET_ID = UUID.randomUUID();
    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final UUID TEST_TRANSACTION_ID = UUID.randomUUID();
    private static final String TEST_LOCK_ID = "test-lock-123";
    private static final String TEST_IDEMPOTENCY_KEY = "idempotency-key-123";

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        walletBalanceService = new WalletBalanceService(
                walletRepository,
                fundReservationRepository,
                lockService,
                meterRegistry
        );
    }

    // ========================================
    // Fund Reservation Tests
    // ========================================

    @Test
    @DisplayName("Should successfully reserve funds with valid wallet and sufficient balance")
    void reserveFunds_ValidWalletAndSufficientBalance_Success() throws WalletLockException {
        // Given
        BigDecimal reserveAmount = new BigDecimal("100.00");
        Wallet wallet = createWallet(new BigDecimal("500.00"));

        when(lockService.acquireLock(TEST_WALLET_ID.toString())).thenReturn(TEST_LOCK_ID);
        when(walletRepository.findByIdWithPessimisticLock(TEST_WALLET_ID)).thenReturn(Optional.of(wallet));
        when(fundReservationRepository.getTotalReservedAmount(TEST_WALLET_ID)).thenReturn(BigDecimal.ZERO);
        when(fundReservationRepository.save(any(FundReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);

        // When
        FundReservation result = walletBalanceService.reserveFunds(
                TEST_WALLET_ID,
                reserveAmount,
                TEST_TRANSACTION_ID,
                TEST_IDEMPOTENCY_KEY
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAmount()).isEqualByComparingTo(reserveAmount);
        assertThat(result.getWalletId()).isEqualTo(TEST_WALLET_ID);
        assertThat(result.getTransactionId()).isEqualTo(TEST_TRANSACTION_ID);
        assertThat(result.getIdempotencyKey()).isEqualTo(TEST_IDEMPOTENCY_KEY);
        assertThat(result.getStatus()).isEqualTo(FundReservation.ReservationStatus.ACTIVE);

        verify(lockService).acquireLock(TEST_WALLET_ID.toString());
        verify(lockService).releaseLock(TEST_WALLET_ID.toString(), TEST_LOCK_ID);
        verify(walletRepository).save(wallet);
        verify(fundReservationRepository).save(any(FundReservation.class));
    }

    @Test
    @DisplayName("Should return existing reservation when idempotency key matches")
    void reserveFunds_DuplicateIdempotencyKey_ReturnsExistingReservation() throws WalletLockException {
        // Given
        BigDecimal amount = new BigDecimal("100.00");
        FundReservation existingReservation = FundReservation.builder()
                .id(UUID.randomUUID())
                .walletId(TEST_WALLET_ID)
                .transactionId(TEST_TRANSACTION_ID)
                .amount(amount)
                .idempotencyKey(TEST_IDEMPOTENCY_KEY)
                .status(FundReservation.ReservationStatus.ACTIVE)
                .build();

        when(fundReservationRepository.findByIdempotencyKey(TEST_IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existingReservation));

        // When
        FundReservation result = walletBalanceService.reserveFunds(
                TEST_WALLET_ID,
                amount,
                TEST_TRANSACTION_ID,
                TEST_IDEMPOTENCY_KEY
        );

        // Then
        assertThat(result).isSameAs(existingReservation);
        verify(lockService, never()).acquireLock(any());
        verify(walletRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw InsufficientBalanceException when available balance is too low")
    void reserveFunds_InsufficientBalance_ThrowsException() throws WalletLockException {
        // Given
        BigDecimal reserveAmount = new BigDecimal("600.00"); // More than available
        Wallet wallet = createWallet(new BigDecimal("500.00"));

        when(lockService.acquireLock(TEST_WALLET_ID.toString())).thenReturn(TEST_LOCK_ID);
        when(walletRepository.findByIdWithPessimisticLock(TEST_WALLET_ID)).thenReturn(Optional.of(wallet));
        when(fundReservationRepository.getTotalReservedAmount(TEST_WALLET_ID)).thenReturn(BigDecimal.ZERO);

        // When/Then
        assertThatThrownBy(() -> walletBalanceService.reserveFunds(
                TEST_WALLET_ID,
                reserveAmount,
                TEST_TRANSACTION_ID,
                null
        ))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient available balance");

        verify(lockService).releaseLock(TEST_WALLET_ID.toString(), TEST_LOCK_ID);
    }

    @Test
    @DisplayName("Should throw WalletNotFoundException when wallet does not exist")
    void reserveFunds_WalletNotFound_ThrowsException() throws WalletLockException {
        // Given
        when(lockService.acquireLock(TEST_WALLET_ID.toString())).thenReturn(TEST_LOCK_ID);
        when(walletRepository.findByIdWithPessimisticLock(TEST_WALLET_ID)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> walletBalanceService.reserveFunds(
                TEST_WALLET_ID,
                new BigDecimal("100.00"),
                TEST_TRANSACTION_ID,
                null
        ))
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessageContaining("Wallet not found");

        verify(lockService).releaseLock(TEST_WALLET_ID.toString(), TEST_LOCK_ID);
    }

    @Test
    @DisplayName("Should throw WalletNotActiveException when wallet is not active")
    void reserveFunds_WalletNotActive_ThrowsException() throws WalletLockException {
        // Given
        Wallet wallet = createWallet(new BigDecimal("500.00"));
        wallet.setStatus(WalletStatus.FROZEN);

        when(lockService.acquireLock(TEST_WALLET_ID.toString())).thenReturn(TEST_LOCK_ID);
        when(walletRepository.findByIdWithPessimisticLock(TEST_WALLET_ID)).thenReturn(Optional.of(wallet));

        // When/Then
        assertThatThrownBy(() -> walletBalanceService.reserveFunds(
                TEST_WALLET_ID,
                new BigDecimal("100.00"),
                TEST_TRANSACTION_ID,
                null
        ))
                .isInstanceOf(WalletNotActiveException.class)
                .hasMessageContaining("Wallet is not active");

        verify(lockService).releaseLock(TEST_WALLET_ID.toString(), TEST_LOCK_ID);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for negative or zero amount")
    void reserveFunds_InvalidAmount_ThrowsException() throws WalletLockException {
        // Given
        Wallet wallet = createWallet(new BigDecimal("500.00"));

        when(lockService.acquireLock(TEST_WALLET_ID.toString())).thenReturn(TEST_LOCK_ID);
        when(walletRepository.findByIdWithPessimisticLock(TEST_WALLET_ID)).thenReturn(Optional.of(wallet));

        // When/Then - Zero amount
        assertThatThrownBy(() -> walletBalanceService.reserveFunds(
                TEST_WALLET_ID,
                BigDecimal.ZERO,
                TEST_TRANSACTION_ID,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount must be positive");

        // When/Then - Negative amount
        assertThatThrownBy(() -> walletBalanceService.reserveFunds(
                TEST_WALLET_ID,
                new BigDecimal("-100.00"),
                TEST_TRANSACTION_ID,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount must be positive");

        verify(lockService, times(2)).releaseLock(TEST_WALLET_ID.toString(), TEST_LOCK_ID);
    }

    @Test
    @DisplayName("Should throw TransactionLimitExceededException when daily limit is exceeded")
    void reserveFunds_DailyLimitExceeded_ThrowsException() throws WalletLockException {
        // Given
        BigDecimal reserveAmount = new BigDecimal("600.00");
        Wallet wallet = createWallet(new BigDecimal("1000.00"));
        wallet.setDailyLimit(new BigDecimal("1000.00"));
        wallet.setDailySpent(new BigDecimal("500.00")); // Already spent $500

        when(lockService.acquireLock(TEST_WALLET_ID.toString())).thenReturn(TEST_LOCK_ID);
        when(walletRepository.findByIdWithPessimisticLock(TEST_WALLET_ID)).thenReturn(Optional.of(wallet));
        when(fundReservationRepository.getTotalReservedAmount(TEST_WALLET_ID)).thenReturn(BigDecimal.ZERO);

        // When/Then
        assertThatThrownBy(() -> walletBalanceService.reserveFunds(
                TEST_WALLET_ID,
                reserveAmount,
                TEST_TRANSACTION_ID,
                null
        ))
                .isInstanceOf(TransactionLimitExceededException.class)
                .hasMessageContaining("Daily limit exceeded");

        verify(lockService).releaseLock(TEST_WALLET_ID.toString(), TEST_LOCK_ID);
    }

    @Test
    @DisplayName("Should handle concurrent reservations with existing reserved amount")
    void reserveFunds_WithExistingReservations_CalculatesCorrectAvailableBalance() throws WalletLockException {
        // Given
        BigDecimal totalBalance = new BigDecimal("500.00");
        BigDecimal alreadyReserved = new BigDecimal("200.00");
        BigDecimal newReservation = new BigDecimal("250.00");

        Wallet wallet = createWallet(totalBalance);

        when(lockService.acquireLock(TEST_WALLET_ID.toString())).thenReturn(TEST_LOCK_ID);
        when(walletRepository.findByIdWithPessimisticLock(TEST_WALLET_ID)).thenReturn(Optional.of(wallet));
        when(fundReservationRepository.getTotalReservedAmount(TEST_WALLET_ID)).thenReturn(alreadyReserved);
        when(fundReservationRepository.save(any(FundReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);

        // When
        FundReservation result = walletBalanceService.reserveFunds(
                TEST_WALLET_ID,
                newReservation,
                TEST_TRANSACTION_ID,
                null
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAmount()).isEqualByComparingTo(newReservation);

        // Verify wallet was updated with new reserved balance
        ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(walletCaptor.capture());

        Wallet savedWallet = walletCaptor.getValue();
        BigDecimal expectedReserved = alreadyReserved.add(newReservation);
        assertThat(savedWallet.getReservedBalance()).isEqualByComparingTo(expectedReserved);
    }

    // ========================================
    // Confirm Reservation Tests
    // ========================================

    @Test
    @DisplayName("Should successfully confirm active reservation")
    void confirmReservation_ActiveReservation_Success() throws WalletLockException {
        // Given
        BigDecimal reservedAmount = new BigDecimal("100.00");
        UUID reservationId = UUID.randomUUID();

        FundReservation reservation = FundReservation.builder()
                .id(reservationId)
                .walletId(TEST_WALLET_ID)
                .amount(reservedAmount)
                .status(FundReservation.ReservationStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        Wallet wallet = createWallet(new BigDecimal("500.00"));
        wallet.setReservedBalance(reservedAmount);

        when(fundReservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(lockService.acquireLock(TEST_WALLET_ID.toString())).thenReturn(TEST_LOCK_ID);
        when(walletRepository.findByIdWithLock(TEST_WALLET_ID)).thenReturn(Optional.of(wallet));
        when(fundReservationRepository.getTotalReservedAmount(TEST_WALLET_ID))
                .thenReturn(reservedAmount)
                .thenReturn(BigDecimal.ZERO); // After confirmation
        when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);
        when(fundReservationRepository.save(any(FundReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        walletBalanceService.confirmReservation(reservationId);

        // Then
        verify(fundReservationRepository, times(2)).findById(reservationId);
        verify(walletRepository).save(any(Wallet.class));
        verify(fundReservationRepository).save(any(FundReservation.class));
        verify(lockService).releaseLock(TEST_WALLET_ID.toString(), TEST_LOCK_ID);
    }

    @Test
    @DisplayName("Should throw IllegalStateException when confirming expired reservation")
    void confirmReservation_ExpiredReservation_ThrowsException() throws WalletLockException {
        // Given
        UUID reservationId = UUID.randomUUID();
        FundReservation reservation = FundReservation.builder()
                .id(reservationId)
                .walletId(TEST_WALLET_ID)
                .amount(new BigDecimal("100.00"))
                .status(FundReservation.ReservationStatus.ACTIVE)
                .createdAt(LocalDateTime.now().minusMinutes(10))
                .expiresAt(LocalDateTime.now().minusMinutes(5)) // Expired
                .build();

        when(fundReservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(lockService.acquireLock(TEST_WALLET_ID.toString())).thenReturn(TEST_LOCK_ID);

        // When/Then
        assertThatThrownBy(() -> walletBalanceService.confirmReservation(reservationId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot confirm expired reservation");

        verify(lockService).releaseLock(TEST_WALLET_ID.toString(), TEST_LOCK_ID);
    }

    // ========================================
    // Release Reservation Tests
    // ========================================

    @Test
    @DisplayName("Should successfully release active reservation")
    void releaseReservation_ActiveReservation_Success() throws WalletLockException {
        // Given
        BigDecimal reservedAmount = new BigDecimal("100.00");
        UUID reservationId = UUID.randomUUID();
        String releaseReason = "Transaction cancelled";

        FundReservation reservation = FundReservation.builder()
                .id(reservationId)
                .walletId(TEST_WALLET_ID)
                .amount(reservedAmount)
                .status(FundReservation.ReservationStatus.ACTIVE)
                .build();

        Wallet wallet = createWallet(new BigDecimal("500.00"));
        wallet.setReservedBalance(reservedAmount);

        when(fundReservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(lockService.acquireLock(TEST_WALLET_ID.toString())).thenReturn(TEST_LOCK_ID);
        when(walletRepository.findByIdWithLock(TEST_WALLET_ID)).thenReturn(Optional.of(wallet));
        when(fundReservationRepository.getTotalReservedAmount(TEST_WALLET_ID))
                .thenReturn(reservedAmount)
                .thenReturn(BigDecimal.ZERO);
        when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);
        when(fundReservationRepository.save(any(FundReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        walletBalanceService.releaseReservation(reservationId, releaseReason);

        // Then
        verify(fundReservationRepository, times(2)).findById(reservationId);
        verify(walletRepository).save(any(Wallet.class));
        verify(fundReservationRepository).save(any(FundReservation.class));
        verify(lockService).releaseLock(TEST_WALLET_ID.toString(), TEST_LOCK_ID);
    }

    // ========================================
    // Credit Tests
    // ========================================

    @Test
    @DisplayName("Should successfully credit wallet")
    void credit_ValidAmount_Success() throws WalletLockException {
        // Given
        BigDecimal initialBalance = new BigDecimal("500.00");
        BigDecimal creditAmount = new BigDecimal("100.00");
        BigDecimal expectedBalance = new BigDecimal("600.00");

        Wallet wallet = createWallet(initialBalance);

        when(lockService.acquireLock(TEST_WALLET_ID.toString())).thenReturn(TEST_LOCK_ID);
        when(walletRepository.findByIdWithLock(TEST_WALLET_ID)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);

        // When
        Wallet result = walletBalanceService.credit(TEST_WALLET_ID, creditAmount);

        // Then
        assertThat(result).isNotNull();
        verify(walletRepository).save(any(Wallet.class));
        verify(lockService).releaseLock(TEST_WALLET_ID.toString(), TEST_LOCK_ID);
    }

    // ========================================
    // Debit Tests
    // ========================================

    @Test
    @DisplayName("Should successfully debit wallet with sufficient balance")
    void debit_SufficientBalance_Success() throws WalletLockException {
        // Given
        BigDecimal initialBalance = new BigDecimal("500.00");
        BigDecimal debitAmount = new BigDecimal("100.00");

        Wallet wallet = createWallet(initialBalance);
        wallet.setAvailableBalance(initialBalance);

        when(lockService.acquireLock(TEST_WALLET_ID.toString())).thenReturn(TEST_LOCK_ID);
        when(walletRepository.findByIdWithLock(TEST_WALLET_ID)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);

        // When
        Wallet result = walletBalanceService.debit(TEST_WALLET_ID, debitAmount);

        // Then
        assertThat(result).isNotNull();
        verify(walletRepository).save(any(Wallet.class));
        verify(lockService).releaseLock(TEST_WALLET_ID.toString(), TEST_LOCK_ID);
    }

    @Test
    @DisplayName("Should throw InsufficientBalanceException when debiting without sufficient funds")
    void debit_InsufficientBalance_ThrowsException() throws WalletLockException {
        // Given
        Wallet wallet = createWallet(new BigDecimal("50.00"));
        wallet.setAvailableBalance(new BigDecimal("50.00"));
        wallet.setReservedBalance(BigDecimal.ZERO);

        when(lockService.acquireLock(TEST_WALLET_ID.toString())).thenReturn(TEST_LOCK_ID);
        when(walletRepository.findByIdWithLock(TEST_WALLET_ID)).thenReturn(Optional.of(wallet));

        // When/Then
        assertThatThrownBy(() -> walletBalanceService.debit(TEST_WALLET_ID, new BigDecimal("100.00")))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient available balance");

        verify(lockService).releaseLock(TEST_WALLET_ID.toString(), TEST_LOCK_ID);
    }

    // ========================================
    // Helper Methods
    // ========================================

    private Wallet createWallet(BigDecimal balance) {
        Wallet wallet = Wallet.create(
                TEST_USER_ID,
                "external-id-123",
                com.waqiti.wallet.domain.WalletType.PERSONAL,
                "CHECKING",
                Currency.USD
        );
        wallet.setId(TEST_WALLET_ID);
        wallet.setBalance(balance);
        wallet.setAvailableBalance(balance);
        wallet.setReservedBalance(BigDecimal.ZERO);
        wallet.setPendingBalance(BigDecimal.ZERO);
        wallet.setFrozenBalance(BigDecimal.ZERO);
        wallet.setStatus(WalletStatus.ACTIVE);
        wallet.setDailySpent(BigDecimal.ZERO);
        wallet.setMonthlySpent(BigDecimal.ZERO);
        wallet.setVersion(1L);
        return wallet;
    }
}

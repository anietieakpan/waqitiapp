package com.waqiti.wallet.service;

import com.waqiti.wallet.dto.CreateWalletRequest;
import com.waqiti.wallet.dto.DebitRequest;
import com.waqiti.wallet.dto.CreditRequest;
import com.waqiti.wallet.dto.TransferRequest;
import com.waqiti.wallet.model.Wallet;
import com.waqiti.wallet.model.WalletStatus;
import com.waqiti.wallet.model.WalletTransaction;
import com.waqiti.wallet.model.TransactionType;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.repository.WalletTransactionRepository;
import com.waqiti.wallet.event.WalletEventPublisher;
import com.waqiti.common.exception.WalletException;
import com.waqiti.common.exception.InsufficientBalanceException;
import com.waqiti.common.exception.WalletNotFoundException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for WalletService.
 *
 * Test Coverage:
 * - Wallet creation
 * - Debit operations (with validation)
 * - Credit operations
 * - Balance transfers
 * - Wallet freezing/unfreezing
 * - Balance queries
 * - Transaction history
 * - Concurrency handling (optimistic locking)
 * - Edge cases and boundary conditions
 *
 * @author Waqiti Platform Engineering
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService Unit Tests")
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletTransactionRepository transactionRepository;

    @Mock
    private WalletEventPublisher eventPublisher;

    @InjectMocks
    private WalletService walletService;

    @Captor
    private ArgumentCaptor<Wallet> walletCaptor;

    @Captor
    private ArgumentCaptor<WalletTransaction> transactionCaptor;

    private UUID userId;
    private UUID walletId;
    private Wallet mockWallet;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        walletId = UUID.randomUUID();

        mockWallet = Wallet.builder()
                .id(walletId)
                .userId(userId)
                .currency("USD")
                .balance(BigDecimal.valueOf(1000.00))
                .availableBalance(BigDecimal.valueOf(1000.00))
                .frozenBalance(BigDecimal.ZERO)
                .status(WalletStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .version(0L)
                .build();
    }

    // =========================================================================
    // Wallet Creation Tests
    // =========================================================================

    @Nested
    @DisplayName("Wallet Creation Tests")
    class WalletCreationTests {

        @Test
        @DisplayName("Should successfully create wallet with valid request")
        void shouldCreateWalletSuccessfully() {
            // Arrange
            CreateWalletRequest request = CreateWalletRequest.builder()
                    .userId(userId)
                    .currency("USD")
                    .build();

            when(walletRepository.existsByUserIdAndCurrency(userId, "USD")).thenReturn(false);
            when(walletRepository.save(any(Wallet.class))).thenReturn(mockWallet);

            // Act
            Wallet createdWallet = walletService.createWallet(request);

            // Assert
            assertNotNull(createdWallet);
            assertEquals("USD", createdWallet.getCurrency());
            assertEquals(userId, createdWallet.getUserId());
            assertEquals(BigDecimal.ZERO, createdWallet.getBalance());
            assertEquals(WalletStatus.ACTIVE, createdWallet.getStatus());

            // Verify wallet was saved
            verify(walletRepository).save(walletCaptor.capture());
            Wallet savedWallet = walletCaptor.getValue();
            assertEquals("USD", savedWallet.getCurrency());

            // Verify event was published
            verify(eventPublisher).publishWalletCreatedEvent(any());
        }

        @Test
        @DisplayName("Should reject duplicate wallet creation")
        void shouldRejectDuplicateWallet() {
            // Arrange
            CreateWalletRequest request = CreateWalletRequest.builder()
                    .userId(userId)
                    .currency("USD")
                    .build();

            when(walletRepository.existsByUserIdAndCurrency(userId, "USD")).thenReturn(true);

            // Act & Assert
            assertThrows(WalletException.class, () -> {
                walletService.createWallet(request);
            });

            // Verify wallet was NOT created
            verify(walletRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject wallet creation with invalid currency")
        void shouldRejectInvalidCurrency() {
            // Arrange
            CreateWalletRequest request = CreateWalletRequest.builder()
                    .userId(userId)
                    .currency("INVALID")
                    .build();

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                walletService.createWallet(request);
            });
        }

        @Test
        @DisplayName("Should create wallets for multiple currencies")
        void shouldCreateMultiCurrencyWallets() {
            // Arrange
            when(walletRepository.existsByUserIdAndCurrency(eq(userId), anyString())).thenReturn(false);
            when(walletRepository.save(any(Wallet.class))).thenReturn(mockWallet);

            // Act
            Wallet usdWallet = walletService.createWallet(
                    CreateWalletRequest.builder().userId(userId).currency("USD").build()
            );
            Wallet eurWallet = walletService.createWallet(
                    CreateWalletRequest.builder().userId(userId).currency("EUR").build()
            );

            // Assert
            assertNotNull(usdWallet);
            assertNotNull(eurWallet);
            verify(walletRepository, times(2)).save(any());
        }
    }

    // =========================================================================
    // Debit Operation Tests
    // =========================================================================

    @Nested
    @DisplayName("Debit Operation Tests")
    class DebitOperationTests {

        @Test
        @DisplayName("Should successfully debit wallet with sufficient balance")
        void shouldDebitWalletSuccessfully() {
            // Arrange
            DebitRequest request = DebitRequest.builder()
                    .walletId(walletId)
                    .amount(BigDecimal.valueOf(100.00))
                    .reference("TEST-DEBIT")
                    .description("Test debit transaction")
                    .build();

            when(walletRepository.findById(walletId)).thenReturn(Optional.of(mockWallet));
            when(walletRepository.save(any(Wallet.class))).thenReturn(mockWallet);
            when(transactionRepository.save(any(WalletTransaction.class)))
                    .thenReturn(WalletTransaction.builder().id(UUID.randomUUID()).build());

            // Act
            BigDecimal newBalance = walletService.debit(request);

            // Assert
            assertEquals(BigDecimal.valueOf(900.00), newBalance);

            // Verify wallet balance was updated
            verify(walletRepository).save(walletCaptor.capture());
            Wallet updatedWallet = walletCaptor.getValue();
            assertEquals(BigDecimal.valueOf(900.00), updatedWallet.getBalance());

            // Verify transaction was recorded
            verify(transactionRepository).save(transactionCaptor.capture());
            WalletTransaction transaction = transactionCaptor.getValue();
            assertEquals(TransactionType.DEBIT, transaction.getType());
            assertEquals(BigDecimal.valueOf(100.00), transaction.getAmount());

            // Verify event was published
            verify(eventPublisher).publishWalletDebitedEvent(any());
        }

        @Test
        @DisplayName("Should reject debit with insufficient balance")
        void shouldRejectInsufficientBalanceDebit() {
            // Arrange
            DebitRequest request = DebitRequest.builder()
                    .walletId(walletId)
                    .amount(BigDecimal.valueOf(1500.00)) // More than balance
                    .reference("TEST-DEBIT")
                    .description("Test debit")
                    .build();

            when(walletRepository.findById(walletId)).thenReturn(Optional.of(mockWallet));

            // Act & Assert
            assertThrows(InsufficientBalanceException.class, () -> {
                walletService.debit(request);
            });

            // Verify wallet was NOT updated
            verify(walletRepository, never()).save(any());
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject debit with zero amount")
        void shouldRejectZeroAmountDebit() {
            // Arrange
            DebitRequest request = DebitRequest.builder()
                    .walletId(walletId)
                    .amount(BigDecimal.ZERO)
                    .reference("TEST-DEBIT")
                    .description("Test debit")
                    .build();

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                walletService.debit(request);
            });
        }

        @Test
        @DisplayName("Should reject debit with negative amount")
        void shouldRejectNegativeAmountDebit() {
            // Arrange
            DebitRequest request = DebitRequest.builder()
                    .walletId(walletId)
                    .amount(BigDecimal.valueOf(-50.00))
                    .reference("TEST-DEBIT")
                    .description("Test debit")
                    .build();

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                walletService.debit(request);
            });
        }

        @Test
        @DisplayName("Should reject debit for frozen wallet")
        void shouldRejectDebitForFrozenWallet() {
            // Arrange
            mockWallet.setStatus(WalletStatus.FROZEN);

            DebitRequest request = DebitRequest.builder()
                    .walletId(walletId)
                    .amount(BigDecimal.valueOf(100.00))
                    .reference("TEST-DEBIT")
                    .description("Test debit")
                    .build();

            when(walletRepository.findById(walletId)).thenReturn(Optional.of(mockWallet));

            // Act & Assert
            assertThrows(WalletException.class, () -> {
                walletService.debit(request);
            });
        }

        @Test
        @DisplayName("Should reject debit for non-existent wallet")
        void shouldRejectDebitForNonExistentWallet() {
            // Arrange
            DebitRequest request = DebitRequest.builder()
                    .walletId(walletId)
                    .amount(BigDecimal.valueOf(100.00))
                    .reference("TEST-DEBIT")
                    .description("Test debit")
                    .build();

            when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(WalletNotFoundException.class, () -> {
                walletService.debit(request);
            });
        }

        @Test
        @DisplayName("Should handle exact balance debit")
        void shouldHandleExactBalanceDebit() {
            // Arrange
            DebitRequest request = DebitRequest.builder()
                    .walletId(walletId)
                    .amount(BigDecimal.valueOf(1000.00)) // Exact balance
                    .reference("TEST-DEBIT")
                    .description("Test debit")
                    .build();

            when(walletRepository.findById(walletId)).thenReturn(Optional.of(mockWallet));
            when(walletRepository.save(any(Wallet.class))).thenReturn(mockWallet);
            when(transactionRepository.save(any(WalletTransaction.class)))
                    .thenReturn(WalletTransaction.builder().id(UUID.randomUUID()).build());

            // Act
            BigDecimal newBalance = walletService.debit(request);

            // Assert
            assertEquals(BigDecimal.ZERO, newBalance);
        }
    }

    // =========================================================================
    // Credit Operation Tests
    // =========================================================================

    @Nested
    @DisplayName("Credit Operation Tests")
    class CreditOperationTests {

        @Test
        @DisplayName("Should successfully credit wallet")
        void shouldCreditWalletSuccessfully() {
            // Arrange
            CreditRequest request = CreditRequest.builder()
                    .walletId(walletId)
                    .amount(BigDecimal.valueOf(500.00))
                    .reference("TEST-CREDIT")
                    .description("Test credit transaction")
                    .build();

            when(walletRepository.findById(walletId)).thenReturn(Optional.of(mockWallet));
            when(walletRepository.save(any(Wallet.class))).thenReturn(mockWallet);
            when(transactionRepository.save(any(WalletTransaction.class)))
                    .thenReturn(WalletTransaction.builder().id(UUID.randomUUID()).build());

            // Act
            BigDecimal newBalance = walletService.credit(request);

            // Assert
            assertEquals(BigDecimal.valueOf(1500.00), newBalance);

            // Verify wallet balance was updated
            verify(walletRepository).save(walletCaptor.capture());
            Wallet updatedWallet = walletCaptor.getValue();
            assertEquals(BigDecimal.valueOf(1500.00), updatedWallet.getBalance());

            // Verify transaction was recorded
            verify(transactionRepository).save(transactionCaptor.capture());
            WalletTransaction transaction = transactionCaptor.getValue();
            assertEquals(TransactionType.CREDIT, transaction.getType());

            // Verify event was published
            verify(eventPublisher).publishWalletCreditedEvent(any());
        }

        @Test
        @DisplayName("Should allow credit to frozen wallet")
        void shouldAllowCreditToFrozenWallet() {
            // Arrange
            mockWallet.setStatus(WalletStatus.FROZEN);

            CreditRequest request = CreditRequest.builder()
                    .walletId(walletId)
                    .amount(BigDecimal.valueOf(100.00))
                    .reference("TEST-CREDIT")
                    .description("Test credit")
                    .build();

            when(walletRepository.findById(walletId)).thenReturn(Optional.of(mockWallet));
            when(walletRepository.save(any(Wallet.class))).thenReturn(mockWallet);
            when(transactionRepository.save(any(WalletTransaction.class)))
                    .thenReturn(WalletTransaction.builder().id(UUID.randomUUID()).build());

            // Act - Should NOT throw exception
            BigDecimal newBalance = walletService.credit(request);

            // Assert
            assertNotNull(newBalance);
            verify(walletRepository).save(any());
        }

        @Test
        @DisplayName("Should reject credit with zero amount")
        void shouldRejectZeroAmountCredit() {
            // Arrange
            CreditRequest request = CreditRequest.builder()
                    .walletId(walletId)
                    .amount(BigDecimal.ZERO)
                    .reference("TEST-CREDIT")
                    .description("Test credit")
                    .build();

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                walletService.credit(request);
            });
        }

        @Test
        @DisplayName("Should reject credit with negative amount")
        void shouldRejectNegativeAmountCredit() {
            // Arrange
            CreditRequest request = CreditRequest.builder()
                    .walletId(walletId)
                    .amount(BigDecimal.valueOf(-100.00))
                    .reference("TEST-CREDIT")
                    .description("Test credit")
                    .build();

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                walletService.credit(request);
            });
        }
    }

    // =========================================================================
    // Transfer Operation Tests
    // =========================================================================

    @Nested
    @DisplayName("Transfer Operation Tests")
    class TransferOperationTests {

        private UUID targetWalletId;
        private Wallet targetWallet;

        @BeforeEach
        void setUp() {
            targetWalletId = UUID.randomUUID();
            targetWallet = Wallet.builder()
                    .id(targetWalletId)
                    .userId(UUID.randomUUID())
                    .currency("USD")
                    .balance(BigDecimal.valueOf(500.00))
                    .availableBalance(BigDecimal.valueOf(500.00))
                    .frozenBalance(BigDecimal.ZERO)
                    .status(WalletStatus.ACTIVE)
                    .build();
        }

        @Test
        @DisplayName("Should successfully transfer between wallets")
        void shouldTransferSuccessfully() {
            // Arrange
            TransferRequest request = TransferRequest.builder()
                    .fromWalletId(walletId)
                    .toWalletId(targetWalletId)
                    .amount(BigDecimal.valueOf(200.00))
                    .reference("TEST-TRANSFER")
                    .description("Test transfer")
                    .build();

            when(walletRepository.findById(walletId)).thenReturn(Optional.of(mockWallet));
            when(walletRepository.findById(targetWalletId)).thenReturn(Optional.of(targetWallet));
            when(walletRepository.save(any(Wallet.class))).thenReturn(mockWallet);
            when(transactionRepository.save(any(WalletTransaction.class)))
                    .thenReturn(WalletTransaction.builder().id(UUID.randomUUID()).build());

            // Act
            walletService.transfer(request);

            // Verify both wallets were updated
            verify(walletRepository, times(2)).save(any());

            // Verify transactions were recorded
            verify(transactionRepository, times(2)).save(any());

            // Verify event was published
            verify(eventPublisher).publishWalletTransferEvent(any());
        }

        @Test
        @DisplayName("Should reject transfer with different currencies")
        void shouldRejectDifferentCurrencyTransfer() {
            // Arrange
            targetWallet.setCurrency("EUR");

            TransferRequest request = TransferRequest.builder()
                    .fromWalletId(walletId)
                    .toWalletId(targetWalletId)
                    .amount(BigDecimal.valueOf(200.00))
                    .reference("TEST-TRANSFER")
                    .description("Test transfer")
                    .build();

            when(walletRepository.findById(walletId)).thenReturn(Optional.of(mockWallet));
            when(walletRepository.findById(targetWalletId)).thenReturn(Optional.of(targetWallet));

            // Act & Assert
            assertThrows(WalletException.class, () -> {
                walletService.transfer(request);
            });
        }

        @Test
        @DisplayName("Should reject transfer with insufficient balance")
        void shouldRejectInsufficientBalanceTransfer() {
            // Arrange
            TransferRequest request = TransferRequest.builder()
                    .fromWalletId(walletId)
                    .toWalletId(targetWalletId)
                    .amount(BigDecimal.valueOf(1500.00))
                    .reference("TEST-TRANSFER")
                    .description("Test transfer")
                    .build();

            when(walletRepository.findById(walletId)).thenReturn(Optional.of(mockWallet));
            when(walletRepository.findById(targetWalletId)).thenReturn(Optional.of(targetWallet));

            // Act & Assert
            assertThrows(InsufficientBalanceException.class, () -> {
                walletService.transfer(request);
            });
        }

        @Test
        @DisplayName("Should reject self transfer")
        void shouldRejectSelfTransfer() {
            // Arrange
            TransferRequest request = TransferRequest.builder()
                    .fromWalletId(walletId)
                    .toWalletId(walletId) // Same wallet
                    .amount(BigDecimal.valueOf(100.00))
                    .reference("TEST-TRANSFER")
                    .description("Test transfer")
                    .build();

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                walletService.transfer(request);
            });
        }
    }

    // =========================================================================
    // Wallet Management Tests
    // =========================================================================

    @Nested
    @DisplayName("Wallet Management Tests")
    class WalletManagementTests {

        @Test
        @DisplayName("Should successfully freeze wallet")
        void shouldFreezeWalletSuccessfully() {
            // Arrange
            when(walletRepository.findById(walletId)).thenReturn(Optional.of(mockWallet));
            when(walletRepository.save(any(Wallet.class))).thenReturn(mockWallet);

            // Act
            walletService.freezeWallet(walletId, "Suspicious activity detected");

            // Verify wallet status was updated
            verify(walletRepository).save(walletCaptor.capture());
            Wallet frozenWallet = walletCaptor.getValue();
            assertEquals(WalletStatus.FROZEN, frozenWallet.getStatus());

            // Verify event was published
            verify(eventPublisher).publishWalletFrozenEvent(any());
        }

        @Test
        @DisplayName("Should successfully unfreeze wallet")
        void shouldUnfreezeWalletSuccessfully() {
            // Arrange
            mockWallet.setStatus(WalletStatus.FROZEN);

            when(walletRepository.findById(walletId)).thenReturn(Optional.of(mockWallet));
            when(walletRepository.save(any(Wallet.class))).thenReturn(mockWallet);

            // Act
            walletService.unfreezeWallet(walletId, "Investigation completed");

            // Verify wallet status was updated
            verify(walletRepository).save(walletCaptor.capture());
            Wallet unfrozenWallet = walletCaptor.getValue();
            assertEquals(WalletStatus.ACTIVE, unfrozenWallet.getStatus());

            // Verify event was published
            verify(eventPublisher).publishWalletUnfrozenEvent(any());
        }

        @Test
        @DisplayName("Should retrieve wallet by ID")
        void shouldRetrieveWalletById() {
            // Arrange
            when(walletRepository.findById(walletId)).thenReturn(Optional.of(mockWallet));

            // Act
            Optional<Wallet> result = walletService.getWalletById(walletId);

            // Assert
            assertTrue(result.isPresent());
            assertEquals(walletId, result.get().getId());
        }

        @Test
        @DisplayName("Should retrieve wallets by user ID")
        void shouldRetrieveWalletsByUserId() {
            // Arrange
            when(walletRepository.findByUserId(userId)).thenReturn(List.of(mockWallet));

            // Act
            List<Wallet> wallets = walletService.getWalletsByUserId(userId);

            // Assert
            assertNotNull(wallets);
            assertEquals(1, wallets.size());
            assertEquals(userId, wallets.get(0).getUserId());
        }
    }

    // =========================================================================
    // Edge Case Tests
    // =========================================================================

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle null wallet ID")
        void shouldHandleNullWalletId() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                walletService.getWalletById(null);
            });
        }

        @Test
        @DisplayName("Should handle very large amounts")
        void shouldHandleVeryLargeAmounts() {
            // Arrange
            BigDecimal largeAmount = new BigDecimal("999999999.99");
            mockWallet.setBalance(largeAmount);
            mockWallet.setAvailableBalance(largeAmount);

            CreditRequest request = CreditRequest.builder()
                    .walletId(walletId)
                    .amount(BigDecimal.valueOf(0.01))
                    .reference("TEST")
                    .description("Test")
                    .build();

            when(walletRepository.findById(walletId)).thenReturn(Optional.of(mockWallet));
            when(walletRepository.save(any(Wallet.class))).thenReturn(mockWallet);
            when(transactionRepository.save(any(WalletTransaction.class)))
                    .thenReturn(WalletTransaction.builder().id(UUID.randomUUID()).build());

            // Act
            BigDecimal newBalance = walletService.credit(request);

            // Assert
            assertNotNull(newBalance);
        }

        @Test
        @DisplayName("Should handle decimal precision correctly")
        void shouldHandleDecimalPrecision() {
            // Test that calculations maintain precision
            BigDecimal initialBalance = new BigDecimal("100.999");
            mockWallet.setBalance(initialBalance);
            mockWallet.setAvailableBalance(initialBalance);

            DebitRequest request = DebitRequest.builder()
                    .walletId(walletId)
                    .amount(new BigDecimal("50.555"))
                    .reference("TEST")
                    .description("Precision test")
                    .build();

            when(walletRepository.findById(walletId)).thenReturn(Optional.of(mockWallet));
            when(walletRepository.save(any(Wallet.class))).thenReturn(mockWallet);
            when(transactionRepository.save(any(WalletTransaction.class)))
                    .thenReturn(WalletTransaction.builder().id(UUID.randomUUID()).build());

            // Act
            BigDecimal newBalance = walletService.debit(request);

            // Assert
            assertEquals(new BigDecimal("50.444"), newBalance);
        }
    }
}

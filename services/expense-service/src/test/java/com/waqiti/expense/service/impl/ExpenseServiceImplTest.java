package com.waqiti.expense.service.impl;

import com.waqiti.expense.domain.Expense;
import com.waqiti.expense.domain.ExpenseCategory;
import com.waqiti.expense.domain.enums.ExpenseStatus;
import com.waqiti.expense.domain.enums.ExpenseType;
import com.waqiti.expense.dto.CreateExpenseRequestDto;
import com.waqiti.expense.dto.ExpenseResponseDto;
import com.waqiti.expense.exception.ExpenseNotFoundException;
import com.waqiti.expense.exception.InvalidExpenseException;
import com.waqiti.expense.repository.BudgetRepository;
import com.waqiti.expense.repository.ExpenseCategoryRepository;
import com.waqiti.expense.repository.ExpenseRepository;
import com.waqiti.expense.service.*;
import com.waqiti.expense.util.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for ExpenseServiceImpl
 * Tests cover happy paths, error cases, edge cases, and business logic validation
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExpenseService Unit Tests")
class ExpenseServiceImplTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private ExpenseCategoryRepository categoryRepository;

    @Mock
    private ExpenseClassificationService classificationService;

    @Mock
    private ExpenseAnalyticsService analyticsService;

    @Mock
    private BudgetService budgetService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private ExpenseServiceImpl expenseService;

    private final String testUserId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        // Setup security context
        SecurityContext securityContext = mock(SecurityContext.class);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(testUserId, null);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    @DisplayName("Should create expense successfully with valid data")
    void createExpense_WithValidData_ShouldSucceed() {
        // Given
        CreateExpenseRequestDto request = CreateExpenseRequestDto.builder()
                .description("Test expense")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .expenseDate(LocalDate.now())
                .expenseType(ExpenseType.GENERAL)
                .build();

        Expense savedExpense = Expense.builder()
                .id(UUID.randomUUID().toString())
                .userId(testUserId)
                .description(request.getDescription())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .expenseDate(request.getExpenseDate())
                .expenseType(request.getExpenseType())
                .status(ExpenseStatus.PENDING)
                .build();

        when(expenseRepository.save(any(Expense.class))).thenReturn(savedExpense);

        // When
        ExpenseResponseDto result = expenseService.createExpense(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getDescription()).isEqualTo(request.getDescription());
        assertThat(result.getAmount()).isEqualByComparingTo(request.getAmount());
        assertThat(result.getCurrency()).isEqualTo(request.getCurrency());
        assertThat(result.getStatus()).isEqualTo(ExpenseStatus.PENDING);

        verify(expenseRepository).save(any(Expense.class));
        verify(kafkaTemplate).send(eq("expense.events"), anyString(), any());
    }

    @Test
    @DisplayName("Should throw exception when amount is zero or negative")
    void createExpense_WithZeroAmount_ShouldThrowException() {
        // Given
        CreateExpenseRequestDto request = CreateExpenseRequestDto.builder()
                .description("Invalid expense")
                .amount(BigDecimal.ZERO)
                .currency("USD")
                .expenseDate(LocalDate.now())
                .expenseType(ExpenseType.GENERAL)
                .build();

        // When & Then
        assertThatThrownBy(() -> expenseService.createExpense(request))
                .isInstanceOf(InvalidExpenseException.class)
                .hasMessageContaining("amount must be greater than zero");

        verify(expenseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should auto-categorize expense when category not provided")
    void createExpense_WithoutCategory_ShouldAutoCategorize() {
        // Given
        CreateExpenseRequestDto request = CreateExpenseRequestDto.builder()
                .description("Coffee at Starbucks")
                .amount(new BigDecimal("5.50"))
                .currency("USD")
                .expenseDate(LocalDate.now())
                .expenseType(ExpenseType.GENERAL)
                .merchantName("Starbucks")
                .build();

        ExpenseCategory foodCategory = new ExpenseCategory();
        foodCategory.setCategoryId("FOOD");
        foodCategory.setCategoryName("Food & Dining");

        when(classificationService.classifyExpense(any())).thenReturn(foodCategory);
        when(classificationService.getClassificationConfidence(any())).thenReturn(0.9);
        when(expenseRepository.save(any(Expense.class))).thenAnswer(i -> i.getArgument(0));

        // When
        ExpenseResponseDto result = expenseService.createExpense(request);

        // Then
        assertThat(result).isNotNull();
        verify(classificationService).classifyExpense(any());
        verify(classificationService).getClassificationConfidence(any());
    }

    @Test
    @DisplayName("Should find expense by ID successfully")
    void getExpenseById_WhenExists_ShouldReturnExpense() {
        // Given
        UUID expenseId = UUID.randomUUID();
        Expense expense = Expense.builder()
                .id(expenseId.toString())
                .userId(testUserId)
                .description("Test expense")
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .status(ExpenseStatus.PENDING)
                .build();

        when(expenseRepository.findById(expenseId.toString())).thenReturn(Optional.of(expense));

        // When
        ExpenseResponseDto result = expenseService.getExpenseById(expenseId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(expenseId.toString());
        assertThat(result.getDescription()).isEqualTo(expense.getDescription());

        verify(expenseRepository).findById(expenseId.toString());
    }

    @Test
    @DisplayName("Should throw exception when expense not found")
    void getExpenseById_WhenNotExists_ShouldThrowException() {
        // Given
        UUID expenseId = UUID.randomUUID();
        when(expenseRepository.findById(expenseId.toString())).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> expenseService.getExpenseById(expenseId))
                .isInstanceOf(ExpenseNotFoundException.class)
                .hasMessageContaining("Expense not found");

        verify(expenseRepository).findById(expenseId.toString());
    }

    @Test
    @DisplayName("Should delete expense in PENDING status")
    void deleteExpense_InPendingStatus_ShouldSucceed() {
        // Given
        UUID expenseId = UUID.randomUUID();
        Expense expense = Expense.builder()
                .id(expenseId.toString())
                .userId(testUserId)
                .status(ExpenseStatus.PENDING)
                .amount(new BigDecimal("100.00"))
                .build();

        when(expenseRepository.findById(expenseId.toString())).thenReturn(Optional.of(expense));

        // When
        expenseService.deleteExpense(expenseId);

        // Then
        verify(expenseRepository).findById(expenseId.toString());
        verify(expenseRepository).delete(expense);
        verify(kafkaTemplate).send(eq("expense.deleted"), any());
    }

    @Test
    @DisplayName("Should calculate mileage expense correctly")
    void calculateMileageExpense_WithValidData_ShouldReturnCorrectAmount() {
        // Given
        BigDecimal distance = new BigDecimal("100");
        String unit = "MILES";

        // When
        var result = expenseService.calculateMileageExpense(distance, unit, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getDistance()).isEqualByComparingTo(distance);
        assertThat(result.getUnit()).isEqualTo(unit);
        assertThat(result.getCalculatedAmount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(result.getRateSource()).isEqualTo("IRS");
    }

    @Test
    @DisplayName("Should convert kilometers to miles for mileage calculation")
    void calculateMileageExpense_WithKilometers_ShouldConvertToMiles() {
        // Given
        BigDecimal distanceKm = new BigDecimal("160.934"); // Approximately 100 miles
        String unit = "KILOMETERS";

        // When
        var result = expenseService.calculateMileageExpense(distanceKm, unit, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getDistanceInMiles()).isCloseTo(new BigDecimal("100"),
                within(new BigDecimal("0.5")));
    }
}

package com.waqiti.expense.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.expense.domain.enums.ExpenseType;
import com.waqiti.expense.dto.CreateExpenseRequestDto;
import com.waqiti.expense.repository.ExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ExpenseController
 * Tests complete HTTP request/response cycle with Spring context
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Expense Controller Integration Tests")
class ExpenseControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExpenseRepository expenseRepository;

    @BeforeEach
    void setUp() {
        // Clean up database before each test
        expenseRepository.deleteAll();
    }

    @Test
    @DisplayName("Should create expense via API successfully")
    @WithMockUser(username = "test-user", roles = {"USER"})
    void createExpense_WithValidRequest_ShouldReturn201() throws Exception {
        // Given
        CreateExpenseRequestDto request = CreateExpenseRequestDto.builder()
                .description("Test API Expense")
                .amount(new BigDecimal("75.50"))
                .currency("USD")
                .expenseDate(LocalDate.now())
                .expenseType(ExpenseType.GENERAL)
                .merchantName("Test Merchant")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value(request.getDescription()))
                .andExpect(jsonPath("$.amount").value(75.50))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    @DisplayName("Should return 400 for invalid expense data")
    @WithMockUser(username = "test-user", roles = {"USER"})
    void createExpense_WithInvalidData_ShouldReturn400() throws Exception {
        // Given - Missing required fields
        CreateExpenseRequestDto request = CreateExpenseRequestDto.builder()
                .amount(new BigDecimal("-10.00")) // Invalid negative amount
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should get expense by ID successfully")
    @WithMockUser(username = "test-user", roles = {"USER"})
    void getExpense_WhenExists_ShouldReturn200() throws Exception {
        // Given - Create an expense first
        CreateExpenseRequestDto createRequest = CreateExpenseRequestDto.builder()
                .description("Expense to retrieve")
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .expenseDate(LocalDate.now())
                .expenseType(ExpenseType.GENERAL)
                .build();

        String createResponse = mockMvc.perform(post("/api/v1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String expenseId = objectMapper.readTree(createResponse).get("id").asText();

        // When & Then
        mockMvc.perform(get("/api/v1/expenses/" + expenseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(expenseId))
                .andExpect(jsonPath("$.description").value(createRequest.getDescription()));
    }

    @Test
    @DisplayName("Should return 401 for unauthenticated requests")
    void createExpense_WithoutAuthentication_ShouldReturn401() throws Exception {
        // Given
        CreateExpenseRequestDto request = CreateExpenseRequestDto.builder()
                .description("Unauthorized expense")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .expenseDate(LocalDate.now())
                .expenseType(ExpenseType.GENERAL)
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should get expense categories successfully")
    @WithMockUser(username = "test-user", roles = {"USER"})
    void getCategories_ShouldReturn200() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/expenses/categories"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
}

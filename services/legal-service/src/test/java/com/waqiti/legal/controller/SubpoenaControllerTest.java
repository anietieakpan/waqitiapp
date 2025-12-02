package com.waqiti.legal.controller;

import com.waqiti.legal.domain.Subpoena;
import com.waqiti.legal.repository.SubpoenaRepository;
import com.waqiti.legal.service.SubpoenaProcessingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for SubpoenaController
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 */
@WebMvcTest(SubpoenaController.class)
class SubpoenaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubpoenaProcessingService subpoenaProcessingService;

    @MockBean
    private SubpoenaRepository subpoenaRepository;

    @Test
    @DisplayName("Should create subpoena with valid request")
    @WithMockUser(roles = "LEGAL_ADMIN")
    void shouldCreateSubpoenaWithValidRequest() throws Exception {
        // Given
        Subpoena subpoena = Subpoena.builder()
                .subpoenaId("SUB-123")
                .customerId("CUST-123")
                .caseNumber("CASE-2025-001")
                .issuingCourt("Superior Court")
                .issuanceDate(LocalDate.now())
                .responseDeadline(LocalDate.now().plusDays(30))
                .subpoenaType(Subpoena.SubpoenaType.CIVIL_SUBPOENA)
                .requestedRecords("Financial records")
                .status(Subpoena.SubpoenaStatus.RECEIVED)
                .createdAt(LocalDateTime.now())
                .build();

        when(subpoenaProcessingService.createSubpoenaRecord(
                anyString(), anyString(), anyString(), anyString(),
                any(LocalDate.class), any(LocalDate.class), anyString(),
                anyString(), any(LocalDateTime.class)))
                .thenReturn(subpoena);

        // When / Then
        mockMvc.perform(post("/api/v1/legal/subpoenas")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerId": "CUST-123",
                                    "caseNumber": "CASE-2025-001",
                                    "issuingCourt": "Superior Court",
                                    "issuanceDate": "2025-11-09",
                                    "responseDeadline": "2025-12-09",
                                    "subpoenaType": "CIVIL_SUBPOENA",
                                    "requestedRecords": "Financial records"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subpoenaId").value("SUB-123"))
                .andExpect(jsonPath("$.customerId").value("CUST-123"))
                .andExpect(jsonPath("$.caseNumber").value("CASE-2025-001"));
    }

    @Test
    @DisplayName("Should get subpoena by ID")
    @WithMockUser(roles = "LEGAL_VIEWER")
    void shouldGetSubpoenaById() throws Exception {
        // Given
        String subpoenaId = "SUB-123";
        Subpoena subpoena = Subpoena.builder()
                .subpoenaId(subpoenaId)
                .customerId("CUST-123")
                .caseNumber("CASE-2025-001")
                .issuingCourt("Superior Court")
                .issuanceDate(LocalDate.now())
                .responseDeadline(LocalDate.now().plusDays(30))
                .subpoenaType(Subpoena.SubpoenaType.CIVIL_SUBPOENA)
                .requestedRecords("Financial records")
                .status(Subpoena.SubpoenaStatus.RECEIVED)
                .createdAt(LocalDateTime.now())
                .build();

        when(subpoenaRepository.findBySubpoenaId(subpoenaId))
                .thenReturn(Optional.of(subpoena));

        // When / Then
        mockMvc.perform(get("/api/v1/legal/subpoenas/{subpoenaId}", subpoenaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subpoenaId").value(subpoenaId))
                .andExpect(jsonPath("$.customerId").value("CUST-123"));
    }

    @Test
    @DisplayName("Should return 404 when subpoena not found")
    @WithMockUser(roles = "LEGAL_VIEWER")
    void shouldReturn404WhenSubpoenaNotFound() throws Exception {
        // Given
        String subpoenaId = "NON-EXISTENT";
        when(subpoenaRepository.findBySubpoenaId(subpoenaId))
                .thenReturn(Optional.empty());

        // When / Then
        mockMvc.perform(get("/api/v1/legal/subpoenas/{subpoenaId}", subpoenaId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should require authentication for protected endpoints")
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/legal/subpoenas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should deny access without proper role")
    @WithMockUser(roles = "USER")
    void shouldDenyAccessWithoutProperRole() throws Exception {
        mockMvc.perform(delete("/api/v1/legal/subpoenas/SUB-123")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}

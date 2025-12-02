package com.waqiti.security.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.dto.SecureStorageRequest;
import com.waqiti.security.service.SecureStorageService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive tests for SecureStorageController
 *
 * Test Coverage:
 * - Happy path scenarios
 * - Security validations
 * - Error handling
 * - Edge cases
 * - CSRF protection
 * - Cookie attributes
 */
@ExtendWith({SpringExtension.class, MockitoExtension.class})
@WebMvcTest(SecureStorageController.class)
@DisplayName("Secure Storage Controller Tests")
class SecureStorageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SecureStorageService secureStorageService;

    private static final String BASE_URL = "/api/v1/secure-storage";
    private static final String CSRF_TOKEN = "test-csrf-token";

    @BeforeEach
    void setUp() {
        when(secureStorageService.generateCSRFToken(any())).thenReturn(CSRF_TOKEN);
        when(secureStorageService.validateCSRFToken(eq(CSRF_TOKEN), any())).thenReturn(true);
    }

    @Test
    @WithMockUser
    @DisplayName("Should set HttpOnly cookie successfully")
    void testSetCookieSuccess() throws Exception {
        // Arrange
        SecureStorageRequest request = SecureStorageRequest.builder()
                .key("test_key")
                .value("test_value")
                .options(SecureStorageRequest.StorageOptions.builder()
                        .secure(true)
                        .sameSite("Strict")
                        .maxAge(3600)
                        .path("/")
                        .build())
                .build();

        // Act & Assert
        MvcResult result = mockMvc.perform(post(BASE_URL + "/set-cookie")
                        .with(csrf())
                        .header("X-CSRF-Token", CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cookie set successfully"))
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        // Verify cookie attributes
        String setCookieHeader = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookieHeader)
                .contains("waqiti_secure_test_key")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict");

        // Verify audit logging
        verify(secureStorageService).auditStorageAccess(
                eq("SET_COOKIE"),
                eq("test_key"),
                any()
        );
    }

    @Test
    @WithMockUser
    @DisplayName("Should reject request without CSRF token")
    void testSetCookieWithoutCSRFToken() throws Exception {
        // Arrange
        SecureStorageRequest request = SecureStorageRequest.builder()
                .key("test_key")
                .value("test_value")
                .build();

        when(secureStorageService.validateCSRFToken(anyString(), any())).thenReturn(false);

        // Act & Assert
        mockMvc.perform(post(BASE_URL + "/set-cookie")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is5xxServerError());
    }

    @Test
    @WithMockUser
    @DisplayName("Should get HttpOnly cookie successfully")
    void testGetCookieSuccess() throws Exception {
        // Arrange
        Cookie cookie = new Cookie("waqiti_secure_test_key", "test_value");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);

        // Act & Assert
        mockMvc.perform(get(BASE_URL + "/get-cookie")
                        .param("key", "test_key")
                        .header("X-CSRF-Token", CSRF_TOKEN)
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.value").value("test_value"));

        // Verify audit logging
        verify(secureStorageService).auditStorageAccess(
                eq("GET_COOKIE"),
                eq("test_key"),
                any()
        );
    }

    @Test
    @WithMockUser
    @DisplayName("Should return null for non-existent cookie")
    void testGetNonExistentCookie() throws Exception {
        // Act & Assert
        mockMvc.perform(get(BASE_URL + "/get-cookie")
                        .param("key", "non_existent_key")
                        .header("X-CSRF-Token", CSRF_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.value").doesNotExist());
    }

    @Test
    @WithMockUser
    @DisplayName("Should delete HttpOnly cookie successfully")
    void testDeleteCookieSuccess() throws Exception {
        // Arrange
        SecureStorageRequest request = SecureStorageRequest.builder()
                .key("test_key")
                .build();

        // Act & Assert
        MvcResult result = mockMvc.perform(delete(BASE_URL + "/delete-cookie")
                        .with(csrf())
                        .header("X-CSRF-Token", CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cookie deleted successfully"))
                .andReturn();

        // Verify cookie deletion (MaxAge=0)
        Cookie deletedCookie = result.getResponse().getCookie("waqiti_secure_test_key");
        assertThat(deletedCookie).isNotNull();
        assertThat(deletedCookie.getMaxAge()).isEqualTo(0);

        // Verify audit logging
        verify(secureStorageService).auditStorageAccess(
                eq("DELETE_COOKIE"),
                eq("test_key"),
                any()
        );
    }

    @Test
    @WithMockUser
    @DisplayName("Should clear all HttpOnly cookies successfully")
    void testClearCookiesSuccess() throws Exception {
        // Arrange
        Cookie cookie1 = new Cookie("waqiti_secure_key1", "value1");
        Cookie cookie2 = new Cookie("waqiti_secure_key2", "value2");
        Cookie otherCookie = new Cookie("other_cookie", "value");

        // Act & Assert
        mockMvc.perform(post(BASE_URL + "/clear-cookies")
                        .with(csrf())
                        .header("X-CSRF-Token", CSRF_TOKEN)
                        .cookie(cookie1, cookie2, otherCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("All cookies cleared successfully"));

        // Verify audit logging
        verify(secureStorageService).auditStorageAccess(
                eq("CLEAR_COOKIES"),
                eq("all"),
                any()
        );
    }

    @Test
    @DisplayName("Should generate CSRF token successfully")
    void testGetCSRFToken() throws Exception {
        // Act & Assert
        mockMvc.perform(get(BASE_URL + "/csrf-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.value").value(CSRF_TOKEN));

        verify(secureStorageService).generateCSRFToken(any());
    }

    @Test
    @WithMockUser
    @DisplayName("Should sanitize malicious key")
    void testKeySanitization() throws Exception {
        // Arrange
        SecureStorageRequest request = SecureStorageRequest.builder()
                .key("test<script>alert('xss')</script>key")
                .value("test_value")
                .build();

        // Act & Assert
        MvcResult result = mockMvc.perform(post(BASE_URL + "/set-cookie")
                        .with(csrf())
                        .header("X-CSRF-Token", CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // Verify sanitized key
        String setCookieHeader = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookieHeader)
                .contains("waqiti_secure_testscriptalertxssscriptkey")
                .doesNotContain("<script>")
                .doesNotContain("alert");
    }

    @Test
    @WithMockUser
    @DisplayName("Should reject key that is too long")
    void testKeyTooLong() throws Exception {
        // Arrange
        String longKey = "a".repeat(129); // Max is 128
        SecureStorageRequest request = SecureStorageRequest.builder()
                .key(longKey)
                .value("test_value")
                .build();

        // Act & Assert
        mockMvc.perform(post(BASE_URL + "/set-cookie")
                        .with(csrf())
                        .header("X-CSRF-Token", CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Should require authentication")
    void testAuthenticationRequired() throws Exception {
        // Arrange
        SecureStorageRequest request = SecureStorageRequest.builder()
                .key("test_key")
                .value("test_value")
                .build();

        // Act & Assert
        mockMvc.perform(post(BASE_URL + "/set-cookie")
                        .with(csrf())
                        .header("X-CSRF-Token", CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("Should handle service exception gracefully")
    void testServiceExceptionHandling() throws Exception {
        // Arrange
        SecureStorageRequest request = SecureStorageRequest.builder()
                .key("test_key")
                .value("test_value")
                .build();

        doThrow(new RuntimeException("Service error"))
                .when(secureStorageService)
                .auditStorageAccess(anyString(), anyString(), any());

        // Act & Assert
        mockMvc.perform(post(BASE_URL + "/set-cookie")
                        .with(csrf())
                        .header("X-CSRF-Token", CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()); // Should still succeed despite audit failure
    }
}

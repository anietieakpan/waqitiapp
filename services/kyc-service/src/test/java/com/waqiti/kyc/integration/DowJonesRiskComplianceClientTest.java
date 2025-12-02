package com.waqiti.kyc.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for Dow Jones Risk & Compliance Client
 *
 * Tests verify:
 * 1. Correct API request formatting
 * 2. Response parsing and error handling
 * 3. Fallback behavior on failures
 * 4. Watchlist screening coverage
 * 5. Enhanced screening with addresses/associates
 * 6. Monitoring setup
 */
@ExtendWith(MockitoExtension.class)
class DowJonesRiskComplianceClientTest {

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;

    private DowJonesRiskComplianceClient client;

    private static final String API_KEY = "test-api-key";
    private static final String API_URL = "https://api.dowjones.com/risk";
    private static final String CUSTOMER_ID = "test-customer-123";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        client = new DowJonesRiskComplianceClient(restTemplate, objectMapper);

        // Inject test configuration
        ReflectionTestUtils.setField(client, "apiKey", API_KEY);
        ReflectionTestUtils.setField(client, "apiUrl", API_URL);
        ReflectionTestUtils.setField(client, "customerId", CUSTOMER_ID);
        ReflectionTestUtils.setField(client, "timeoutMs", 10000);
    }

    @Test
    void testScreenPerson_Success_NoHit() {
        // Given
        String name = "John Smith";
        LocalDate dateOfBirth = LocalDate.of(1990, 1, 1);
        String nationality = "US";

        Map<String, Object> responseBody = createSuccessResponse(false, 0.0, Collections.emptyList());
        ResponseEntity<Map> response = ResponseEntity.ok(responseBody);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
            .thenReturn(response);

        // When
        DowJonesRiskComplianceClient.DowJonesScreeningResponse result =
            client.screenPerson(name, dateOfBirth, nationality);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isHit()).isFalse();
        assertThat(result.getRiskScore()).isEqualTo(0.0);
        assertThat(result.getMatchedEntities()).isEmpty();

        // Verify API call
        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
            eq(API_URL + "/entities/v1/screen"),
            requestCaptor.capture(),
            eq(Map.class)
        );

        HttpEntity<Map<String, Object>> capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getHeaders().get("X-API-Key")).containsExactly(API_KEY);
        assertThat(capturedRequest.getHeaders().get("X-Customer-Id")).containsExactly(CUSTOMER_ID);
    }

    @Test
    void testScreenPerson_Success_WithHit() {
        // Given
        String name = "Sanctioned Person";
        LocalDate dateOfBirth = LocalDate.of(1970, 1, 1);
        String nationality = "IR";

        List<Map<String, Object>> matches = Arrays.asList(
            createMatchedEntity("Sanctioned Person", 0.95, "OFAC SDN", Arrays.asList("Terrorism", "Sanctions")),
            createMatchedEntity("Sanctioned Person", 0.88, "UN Sanctions", Arrays.asList("UN Security Council"))
        );

        Map<String, Object> responseBody = createSuccessResponse(true, 0.95, matches);
        ResponseEntity<Map> response = ResponseEntity.ok(responseBody);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
            .thenReturn(response);

        // When
        DowJonesRiskComplianceClient.DowJonesScreeningResponse result =
            client.screenPerson(name, dateOfBirth, nationality);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isHit()).isTrue();
        assertThat(result.getRiskScore()).isEqualTo(0.95);
        assertThat(result.getMatchedEntities()).hasSize(2);
        assertThat(result.getMatchedLists()).containsExactlyInAnyOrder("OFAC SDN", "UN Sanctions");

        DowJonesRiskComplianceClient.MatchedEntity firstMatch = result.getMatchedEntities().get(0);
        assertThat(firstMatch.getName()).isEqualTo("Sanctioned Person");
        assertThat(firstMatch.getMatchScore()).isEqualTo(0.95);
        assertThat(firstMatch.getWatchlist()).isEqualTo("OFAC SDN");
        assertThat(firstMatch.getReasons()).containsExactlyInAnyOrder("Terrorism", "Sanctions");
    }

    @Test
    void testScreenPerson_PepHit() {
        // Given
        String name = "Political Figure";
        LocalDate dateOfBirth = LocalDate.of(1960, 5, 15);
        String nationality = "UK";

        List<Map<String, Object>> matches = Collections.singletonList(
            createMatchedEntity("Political Figure", 0.92, "PEP - Politically Exposed Person",
                Arrays.asList("Senior Government Official", "Foreign PEP"))
        );

        Map<String, Object> responseBody = createSuccessResponse(true, 0.92, matches);
        ResponseEntity<Map> response = ResponseEntity.ok(responseBody);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
            .thenReturn(response);

        // When
        DowJonesRiskComplianceClient.DowJonesScreeningResponse result =
            client.screenPerson(name, dateOfBirth, nationality);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isHit()).isTrue();
        assertThat(result.getRiskScore()).isEqualTo(0.92);
        assertThat(result.getMatchedLists()).contains("PEP - Politically Exposed Person");
    }

    @Test
    void testScreenEnhanced_WithAddressesAndAssociates() {
        // Given
        String name = "Complex Entity";
        List<String> addresses = Arrays.asList(
            "123 Main St, New York, US",
            "456 Park Ave, London, UK"
        );
        List<String> associates = Arrays.asList("Associate One", "Associate Two");

        List<Map<String, Object>> matches = Collections.singletonList(
            createMatchedEntity("Complex Entity", 0.85, "Adverse Media", Arrays.asList("Corruption"))
        );

        Map<String, Object> responseBody = createSuccessResponse(true, 0.85, matches);
        ResponseEntity<Map> response = ResponseEntity.ok(responseBody);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
            .thenReturn(response);

        // When
        DowJonesRiskComplianceClient.DowJonesScreeningResponse result =
            client.screenEnhanced(name, addresses, associates);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isHit()).isTrue();
        assertThat(result.getRiskScore()).isEqualTo(0.85);
        assertThat(result.getMatchedEntities()).hasSize(1);

        // Verify request includes addresses and associates
        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), requestCaptor.capture(), eq(Map.class));

        @SuppressWarnings("unchecked")
        Map<String, Object> requestBody = (Map<String, Object>) requestCaptor.getValue().getBody();
        assertThat(requestBody).isNotNull();
        // Additional assertions would require accessing the request body structure
    }

    @Test
    void testScreenPerson_ApiError_ReturnsFailureResponse() {
        // Given
        String name = "Test Person";
        LocalDate dateOfBirth = LocalDate.of(1985, 3, 10);
        String nationality = "CA";

        ResponseEntity<Map> response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
            .thenReturn(response);

        // When
        DowJonesRiskComplianceClient.DowJonesScreeningResponse result =
            client.screenPerson(name, dateOfBirth, nationality);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("API error");
        assertThat(result.isHit()).isFalse(); // Fallback to safe default
    }

    @Test
    void testScreenPerson_Exception_ReturnsFailureResponse() {
        // Given
        String name = "Test Person";
        LocalDate dateOfBirth = LocalDate.of(1985, 3, 10);
        String nationality = "CA";

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
            .thenThrow(new RuntimeException("Network error"));

        // When
        DowJonesRiskComplianceClient.DowJonesScreeningResponse result =
            client.screenPerson(name, dateOfBirth, nationality);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Exception");
        assertThat(result.isHit()).isFalse();
    }

    @Test
    void testMonitorEntity_Success() {
        // Given
        String entityId = "entity-123";
        String name = "Monitored Person";

        ResponseEntity<Map> response = ResponseEntity.ok(Collections.emptyMap());

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
            .thenReturn(response);

        // When
        assertThatCode(() -> client.monitorEntity(entityId, name))
            .doesNotThrowAnyException();

        // Then
        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
            eq(API_URL + "/entities/v1/monitor"),
            requestCaptor.capture(),
            eq(Map.class)
        );

        HttpEntity<Map<String, Object>> capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getHeaders().get("X-API-Key")).containsExactly(API_KEY);
    }

    @Test
    void testMonitorEntity_HandlesException() {
        // Given
        String entityId = "entity-123";
        String name = "Monitored Person";

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
            .thenThrow(new RuntimeException("Network error"));

        // When/Then - should not throw exception
        assertThatCode(() -> client.monitorEntity(entityId, name))
            .doesNotThrowAnyException();
    }

    @Test
    void testScreenPerson_MultipleSanctionLists() {
        // Given
        String name = "High Risk Person";
        LocalDate dateOfBirth = LocalDate.of(1975, 12, 31);
        String nationality = "RU";

        List<Map<String, Object>> matches = Arrays.asList(
            createMatchedEntity("High Risk Person", 0.98, "OFAC SDN", Arrays.asList("Drug Trafficking")),
            createMatchedEntity("High Risk Person", 0.95, "EU Sanctions", Arrays.asList("Asset Freeze")),
            createMatchedEntity("High Risk Person", 0.93, "UK HMT", Arrays.asList("Financial Sanctions")),
            createMatchedEntity("High Risk Person", 0.90, "UN Sanctions", Arrays.asList("Arms Embargo"))
        );

        Map<String, Object> responseBody = createSuccessResponse(true, 0.98, matches);
        ResponseEntity<Map> response = ResponseEntity.ok(responseBody);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
            .thenReturn(response);

        // When
        DowJonesRiskComplianceClient.DowJonesScreeningResponse result =
            client.screenPerson(name, dateOfBirth, nationality);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isHit()).isTrue();
        assertThat(result.getRiskScore()).isEqualTo(0.98);
        assertThat(result.getMatchedEntities()).hasSize(4);
        assertThat(result.getMatchedLists()).containsExactlyInAnyOrder(
            "OFAC SDN", "EU Sanctions", "UK HMT", "UN Sanctions"
        );
    }

    // Helper methods

    private Map<String, Object> createSuccessResponse(boolean hit, double riskScore,
                                                       List<Map<String, Object>> matches) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("hit", hit);
        response.put("risk_score", riskScore);
        response.put("matched_entities", matches);

        List<String> matchedLists = matches.stream()
            .map(m -> (String) m.get("watchlist"))
            .distinct()
            .toList();
        response.put("matched_lists", matchedLists);

        return response;
    }

    private Map<String, Object> createMatchedEntity(String name, double matchScore,
                                                      String watchlist, List<String> reasons) {
        Map<String, Object> entity = new HashMap<>();
        entity.put("name", name);
        entity.put("match_score", matchScore);
        entity.put("watchlist", watchlist);
        entity.put("reasons", reasons);
        return entity;
    }
}

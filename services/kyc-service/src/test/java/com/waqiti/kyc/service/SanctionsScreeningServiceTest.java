package com.waqiti.kyc.service;

import com.waqiti.kyc.dto.InternationalKycModels.*;
import com.waqiti.kyc.integration.DowJonesRiskComplianceClient;
import com.waqiti.kyc.integration.DowJonesRiskComplianceClient.DowJonesScreeningResponse;
import com.waqiti.kyc.integration.DowJonesRiskComplianceClient.MatchedEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for SanctionsScreeningService using real Dow Jones integration
 *
 * Verifies:
 * 1. Successful screening with no hits
 * 2. Successful screening with hits
 * 3. Fallback behavior on API failures
 * 4. Enhanced screening with addresses and associates
 * 5. Mapping of Dow Jones responses to internal format
 */
@ExtendWith(MockitoExtension.class)
class SanctionsScreeningServiceTest {

    @Mock
    private DowJonesRiskComplianceClient dowJonesClient;

    @InjectMocks
    private SanctionsScreeningService sanctionsScreeningService;

    @Test
    void testScreenBasic_NoHit_ReturnsCleanResult() {
        // Given
        String name = "John Doe";
        LocalDate dateOfBirth = LocalDate.of(1990, 5, 15);
        String nationality = "US";

        DowJonesScreeningResponse dowJonesResponse = DowJonesScreeningResponse.builder()
            .success(true)
            .hit(false)
            .riskScore(0.0)
            .matchedEntities(Collections.emptyList())
            .matchedLists(Collections.emptyList())
            .build();

        when(dowJonesClient.screenPerson(name, dateOfBirth, nationality))
            .thenReturn(dowJonesResponse);

        // When
        SanctionsResult result = sanctionsScreeningService.screenBasic(name, dateOfBirth, nationality);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isHit()).isFalse();
        assertThat(result.getScore()).isEqualTo(0.0);
        assertThat(result.getMatchDetails()).isEqualTo("Clear - No matches found");
        assertThat(result.getMatchedLists()).isEmpty();

        verify(dowJonesClient).screenPerson(name, dateOfBirth, nationality);
    }

    @Test
    void testScreenBasic_WithHit_ReturnsSanctionsMatch() {
        // Given
        String name = "Sanctioned Person";
        LocalDate dateOfBirth = LocalDate.of(1970, 1, 1);
        String nationality = "IR";

        MatchedEntity match = MatchedEntity.builder()
            .name("Sanctioned Person")
            .matchScore(0.95)
            .watchlist("OFAC SDN")
            .reasons(Arrays.asList("Terrorism", "Sanctions"))
            .build();

        DowJonesScreeningResponse dowJonesResponse = DowJonesScreeningResponse.builder()
            .success(true)
            .hit(true)
            .riskScore(0.95)
            .matchedEntities(Collections.singletonList(match))
            .matchedLists(Collections.singletonList("OFAC SDN"))
            .build();

        when(dowJonesClient.screenPerson(name, dateOfBirth, nationality))
            .thenReturn(dowJonesResponse);

        // When
        SanctionsResult result = sanctionsScreeningService.screenBasic(name, dateOfBirth, nationality);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isHit()).isTrue();
        assertThat(result.getScore()).isEqualTo(0.95);
        assertThat(result.getMatchDetails()).contains("Sanctioned Person");
        assertThat(result.getMatchDetails()).contains("0.95");
        assertThat(result.getMatchDetails()).contains("OFAC SDN");
        assertThat(result.getMatchedLists()).containsExactly("OFAC SDN");
    }

    @Test
    void testScreenBasic_WithMultipleHits_FormatsAllMatches() {
        // Given
        String name = "High Risk Person";
        LocalDate dateOfBirth = LocalDate.of(1975, 12, 31);
        String nationality = "RU";

        List<MatchedEntity> matches = Arrays.asList(
            MatchedEntity.builder()
                .name("High Risk Person")
                .matchScore(0.98)
                .watchlist("OFAC SDN")
                .reasons(Collections.singletonList("Drug Trafficking"))
                .build(),
            MatchedEntity.builder()
                .name("High Risk Person")
                .matchScore(0.95)
                .watchlist("EU Sanctions")
                .reasons(Collections.singletonList("Asset Freeze"))
                .build()
        );

        DowJonesScreeningResponse dowJonesResponse = DowJonesScreeningResponse.builder()
            .success(true)
            .hit(true)
            .riskScore(0.98)
            .matchedEntities(matches)
            .matchedLists(Arrays.asList("OFAC SDN", "EU Sanctions"))
            .build();

        when(dowJonesClient.screenPerson(name, dateOfBirth, nationality))
            .thenReturn(dowJonesResponse);

        // When
        SanctionsResult result = sanctionsScreeningService.screenBasic(name, dateOfBirth, nationality);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isHit()).isTrue();
        assertThat(result.getScore()).isEqualTo(0.98);
        assertThat(result.getMatchDetails()).contains("OFAC SDN");
        assertThat(result.getMatchDetails()).contains("EU Sanctions");
        assertThat(result.getMatchedLists()).containsExactlyInAnyOrder("OFAC SDN", "EU Sanctions");
    }

    @Test
    void testScreenBasic_ApiFailure_ReturnsFallbackForManualReview() {
        // Given
        String name = "Test Person";
        LocalDate dateOfBirth = LocalDate.of(1985, 3, 10);
        String nationality = "CA";

        DowJonesScreeningResponse dowJonesResponse = DowJonesScreeningResponse.builder()
            .success(false)
            .errorMessage("API connection timeout")
            .build();

        when(dowJonesClient.screenPerson(name, dateOfBirth, nationality))
            .thenReturn(dowJonesResponse);

        // When
        SanctionsResult result = sanctionsScreeningService.screenBasic(name, dateOfBirth, nationality);

        // Then - Conservative approach: flag for manual review
        assertThat(result).isNotNull();
        assertThat(result.isHit()).isTrue(); // Flagged for review
        assertThat(result.getScore()).isEqualTo(0.5);
        assertThat(result.getMatchDetails()).contains("API unavailable");
        assertThat(result.getMatchDetails()).contains("manual review");
    }

    @Test
    void testScreenBasic_Exception_ReturnsFallbackForManualReview() {
        // Given
        String name = "Test Person";
        LocalDate dateOfBirth = LocalDate.of(1985, 3, 10);
        String nationality = "CA";

        when(dowJonesClient.screenPerson(name, dateOfBirth, nationality))
            .thenThrow(new RuntimeException("Network error"));

        // When
        SanctionsResult result = sanctionsScreeningService.screenBasic(name, dateOfBirth, nationality);

        // Then - Conservative approach: flag for manual review
        assertThat(result).isNotNull();
        assertThat(result.isHit()).isTrue(); // Flagged for review
        assertThat(result.getScore()).isEqualTo(0.5);
        assertThat(result.getMatchDetails()).contains("API unavailable");
    }

    @Test
    void testPerformEnhancedScreening_NoHit() {
        // Given
        String name = "Clean Entity";
        List<Address> addresses = Arrays.asList(
            Address.builder().city("New York").state("NY").country("US").build(),
            Address.builder().city("London").state("").country("UK").build()
        );
        List<String> associates = Arrays.asList("Associate One", "Associate Two");

        DowJonesScreeningResponse dowJonesResponse = DowJonesScreeningResponse.builder()
            .success(true)
            .hit(false)
            .riskScore(0.0)
            .matchedEntities(Collections.emptyList())
            .matchedLists(Collections.emptyList())
            .build();

        when(dowJonesClient.screenEnhanced(eq(name), anyList(), eq(associates)))
            .thenReturn(dowJonesResponse);

        // When
        EnhancedSanctionsResult result =
            sanctionsScreeningService.performEnhancedScreening(name, addresses, associates);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isHit()).isFalse();
        assertThat(result.getRiskScore()).isEqualTo(0.0);
        assertThat(result.getMatchedEntities()).isEmpty();
        assertThat(result.getSanctions()).isEmpty();
    }

    @Test
    void testPerformEnhancedScreening_WithHit_MapsEntities() {
        // Given
        String name = "Complex Entity";
        List<Address> addresses = Collections.singletonList(
            Address.builder().city("Dubai").state("").country("AE").build()
        );
        List<String> associates = Collections.singletonList("Suspect Associate");

        MatchedEntity dowJonesMatch = MatchedEntity.builder()
            .name("Complex Entity")
            .matchScore(0.87)
            .watchlist("Adverse Media")
            .reasons(Arrays.asList("Corruption", "Money Laundering"))
            .build();

        DowJonesScreeningResponse dowJonesResponse = DowJonesScreeningResponse.builder()
            .success(true)
            .hit(true)
            .riskScore(0.87)
            .matchedEntities(Collections.singletonList(dowJonesMatch))
            .matchedLists(Collections.singletonList("Adverse Media"))
            .build();

        when(dowJonesClient.screenEnhanced(eq(name), anyList(), eq(associates)))
            .thenReturn(dowJonesResponse);

        // When
        EnhancedSanctionsResult result =
            sanctionsScreeningService.performEnhancedScreening(name, addresses, associates);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isHit()).isTrue();
        assertThat(result.getRiskScore()).isEqualTo(0.87);
        assertThat(result.getMatchedEntities()).hasSize(1);

        com.waqiti.kyc.dto.InternationalKycModels.MatchedEntity mappedEntity =
            result.getMatchedEntities().get(0);
        assertThat(mappedEntity.getName()).isEqualTo("Complex Entity");
        assertThat(mappedEntity.getMatchScore()).isEqualTo(0.87);
        assertThat(mappedEntity.getListName()).isEqualTo("Adverse Media");
        assertThat(mappedEntity.getReasons()).containsExactlyInAnyOrder("Corruption", "Money Laundering");

        assertThat(result.getSanctions()).containsExactly("Adverse Media");
    }

    @Test
    void testPerformEnhancedScreening_NullAddresses_HandlesGracefully() {
        // Given
        String name = "Test Entity";
        List<Address> addresses = null;
        List<String> associates = Collections.emptyList();

        DowJonesScreeningResponse dowJonesResponse = DowJonesScreeningResponse.builder()
            .success(true)
            .hit(false)
            .riskScore(0.0)
            .matchedEntities(Collections.emptyList())
            .matchedLists(Collections.emptyList())
            .build();

        when(dowJonesClient.screenEnhanced(eq(name), anyList(), eq(associates)))
            .thenReturn(dowJonesResponse);

        // When/Then - should not throw exception
        assertThatCode(() ->
            sanctionsScreeningService.performEnhancedScreening(name, addresses, associates)
        ).doesNotThrowAnyException();
    }

    @Test
    void testPerformEnhancedScreening_ApiFailure_ReturnsFallback() {
        // Given
        String name = "Test Entity";
        List<Address> addresses = Collections.emptyList();
        List<String> associates = Collections.emptyList();

        DowJonesScreeningResponse dowJonesResponse = DowJonesScreeningResponse.builder()
            .success(false)
            .errorMessage("Service unavailable")
            .build();

        when(dowJonesClient.screenEnhanced(eq(name), anyList(), eq(associates)))
            .thenReturn(dowJonesResponse);

        // When
        EnhancedSanctionsResult result =
            sanctionsScreeningService.performEnhancedScreening(name, addresses, associates);

        // Then - Conservative approach
        assertThat(result).isNotNull();
        assertThat(result.isHit()).isTrue(); // Flagged for review
        assertThat(result.getRiskScore()).isEqualTo(0.5);
    }

    @Test
    void testPerformEnhancedScreening_Exception_ReturnsFallback() {
        // Given
        String name = "Test Entity";
        List<Address> addresses = Collections.emptyList();
        List<String> associates = Collections.emptyList();

        when(dowJonesClient.screenEnhanced(eq(name), anyList(), eq(associates)))
            .thenThrow(new RuntimeException("Unexpected error"));

        // When
        EnhancedSanctionsResult result =
            sanctionsScreeningService.performEnhancedScreening(name, addresses, associates);

        // Then - Conservative approach
        assertThat(result).isNotNull();
        assertThat(result.isHit()).isTrue(); // Flagged for review
        assertThat(result.getRiskScore()).isEqualTo(0.5);
    }

    @Test
    void testScreenBasic_PepHit_CorrectlyIdentified() {
        // Given
        String name = "Political Figure";
        LocalDate dateOfBirth = LocalDate.of(1960, 5, 15);
        String nationality = "UK";

        MatchedEntity match = MatchedEntity.builder()
            .name("Political Figure")
            .matchScore(0.92)
            .watchlist("PEP - Politically Exposed Person")
            .reasons(Arrays.asList("Senior Government Official", "Foreign PEP"))
            .build();

        DowJonesScreeningResponse dowJonesResponse = DowJonesScreeningResponse.builder()
            .success(true)
            .hit(true)
            .riskScore(0.92)
            .matchedEntities(Collections.singletonList(match))
            .matchedLists(Collections.singletonList("PEP - Politically Exposed Person"))
            .build();

        when(dowJonesClient.screenPerson(name, dateOfBirth, nationality))
            .thenReturn(dowJonesResponse);

        // When
        SanctionsResult result = sanctionsScreeningService.screenBasic(name, dateOfBirth, nationality);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isHit()).isTrue();
        assertThat(result.getScore()).isEqualTo(0.92);
        assertThat(result.getMatchedLists()).contains("PEP - Politically Exposed Person");
        assertThat(result.getMatchDetails()).contains("Foreign PEP");
    }
}

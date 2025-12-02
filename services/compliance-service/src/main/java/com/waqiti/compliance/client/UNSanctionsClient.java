package com.waqiti.compliance.client;

import com.waqiti.compliance.service.EUUNSanctionsScreeningService.SanctionMatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * UN Sanctions API Client
 *
 * Integrates with United Nations Security Council sanctions lists:
 * - UN Consolidated Sanctions List
 * - UN 1267/1989 Committee List (Al-Qaida/ISIS Sanctions)
 * - UN Proliferation Lists (DPRK, Iran)
 * - UN Arms Embargo Lists
 * - UN Travel Ban Lists
 *
 * API Documentation: https://scsanctions.un.org/resources/xml-and-apis
 *
 * @author Waqiti Compliance Team
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UNSanctionsClient {

    private final RestTemplate restTemplate;

    @Value("${un.sanctions.api.url:https://scsanctions.un.org/resources/api}")
    private String unSanctionsApiUrl;

    @Value("${un.sanctions.api.timeout:30000}")
    private int timeout;

    /**
     * Search UN Consolidated Sanctions List
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public List<SanctionMatch> searchConsolidatedList(String name, String dob) {
        log.debug("Searching UN Consolidated list for: {}", name);

        try {
            HttpHeaders headers = createHeaders();

            String url = String.format("%s/consolidated/search?name=%s&dob=%s",
                unSanctionsApiUrl, name, dob != null ? dob : "");

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<UNSanctionsResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, UNSanctionsResponse.class);

            if (response.getBody() != null && response.getBody().getEntries() != null) {
                return mapToSanctionMatches(response.getBody().getEntries(), "UN_CONSOLIDATED");
            }

            return new ArrayList<>();

        } catch (Exception e) {
            log.error("Failed to search UN Consolidated list", e);
            throw new RuntimeException("UN Consolidated search failed", e);
        }
    }

    /**
     * Search UN 1267/1989 Committee List (Al-Qaida/ISIS)
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public List<SanctionMatch> search1267List(String name) {
        log.debug("Searching UN 1267 list for: {}", name);

        try {
            HttpHeaders headers = createHeaders();

            String url = String.format("%s/1267/search?name=%s",
                unSanctionsApiUrl, name);

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<UNSanctionsResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, UNSanctionsResponse.class);

            if (response.getBody() != null && response.getBody().getEntries() != null) {
                return mapToSanctionMatches(response.getBody().getEntries(), "UN_1267_TERRORISM");
            }

            return new ArrayList<>();

        } catch (Exception e) {
            log.error("Failed to search UN 1267 list", e);
            throw new RuntimeException("UN 1267 search failed", e);
        }
    }

    /**
     * Search UN Proliferation Lists (North Korea, Iran)
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public List<SanctionMatch> searchProliferationLists(String name, String country) {
        log.debug("Searching UN Proliferation lists for: {}", name);

        try {
            HttpHeaders headers = createHeaders();

            String url = String.format("%s/proliferation/search?name=%s&country=%s",
                unSanctionsApiUrl, name, country != null ? country : "");

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<UNSanctionsResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, UNSanctionsResponse.class);

            if (response.getBody() != null && response.getBody().getEntries() != null) {
                return mapToSanctionMatches(response.getBody().getEntries(), "UN_PROLIFERATION");
            }

            return new ArrayList<>();

        } catch (Exception e) {
            log.error("Failed to search UN Proliferation lists", e);
            throw new RuntimeException("UN Proliferation search failed", e);
        }
    }

    /**
     * Download latest UN sanctions lists
     */
    public void downloadLatestLists() {
        log.info("Downloading latest UN sanctions lists");

        try {
            HttpHeaders headers = createHeaders();

            String url = unSanctionsApiUrl + "/download/xml";
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, byte[].class);

            if (response.getStatusCode() == HttpStatus.OK) {
                // Store downloaded lists
                log.info("UN sanctions lists downloaded successfully");
            }

        } catch (Exception e) {
            log.error("Failed to download UN sanctions lists", e);
            throw new RuntimeException("UN sanctions list download failed", e);
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/json");
        return headers;
    }

    private List<SanctionMatch> mapToSanctionMatches(
            List<UNSanctionsResponse.SanctionEntry> entries, String listName) {

        List<SanctionMatch> matches = new ArrayList<>();

        for (UNSanctionsResponse.SanctionEntry entry : entries) {
            matches.add(SanctionMatch.builder()
                .matchedName(entry.getName())
                .matchScore(calculateMatchScore(entry))
                .sanctionType(entry.getSanctionType())
                .sanctionsList(listName)
                .programName(entry.getCommittee())
                .listingDate(entry.getListedOn())
                .reason(entry.getNarrative())
                .build());
        }

        return matches;
    }

    private double calculateMatchScore(UNSanctionsResponse.SanctionEntry entry) {
        // Fuzzy matching score calculation
        return entry.getMatchScore() / 100.0;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class UNSanctionsResponse {
        private List<SanctionEntry> entries;

        @lombok.Data
        @lombok.NoArgsConstructor
        @lombok.AllArgsConstructor
        public static class SanctionEntry {
            private String name;
            private String sanctionType;
            private String committee;
            private LocalDateTime listedOn;
            private String narrative;
            private double matchScore;
        }
    }
}

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
 * EU Sanctions API Client
 *
 * Integrates with European Union sanctions lists and databases:
 * - EU CFSP Consolidated List of Persons, Groups and Entities
 * - EU Financial Sanctions Files
 * - EU Asset Freeze Targets
 * - EEAS (European External Action Service) Sanctions Data
 *
 * API Documentation: https://webgate.ec.europa.eu/fsd/fsf
 *
 * @author Waqiti Compliance Team
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EUSanctionsClient {

    private final RestTemplate restTemplate;

    @Value("${eu.sanctions.api.url:https://webgate.ec.europa.eu/fsd/fsf/public/api}")
    private String euSanctionsApiUrl;

    @Value("${eu.sanctions.api.key:}")
    private String apiKey;

    @Value("${eu.sanctions.api.timeout:30000}")
    private int timeout;

    /**
     * Search EU CFSP Consolidated List
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public List<SanctionMatch> searchCFSPList(String name, String dob, String country) {
        log.debug("Searching EU CFSP list for: {}", name);

        try {
            HttpHeaders headers = createHeaders();

            String url = String.format("%s/cfsp/search?name=%s&dob=%s&country=%s",
                euSanctionsApiUrl, name, dob != null ? dob : "", country != null ? country : "");

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<EUSanctionsResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, EUSanctionsResponse.class);

            if (response.getBody() != null && response.getBody().getResults() != null) {
                return mapToSanctionMatches(response.getBody().getResults(), "EU_CFSP");
            }

            return new ArrayList<>();

        } catch (Exception e) {
            log.error("Failed to search EU CFSP list", e);
            throw new RuntimeException("EU CFSP search failed", e);
        }
    }

    /**
     * Search EU Financial Sanctions List
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public List<SanctionMatch> searchFinancialSanctionsList(String name) {
        log.debug("Searching EU Financial Sanctions list for: {}", name);

        try {
            HttpHeaders headers = createHeaders();

            String url = String.format("%s/financial/search?name=%s",
                euSanctionsApiUrl, name);

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<EUSanctionsResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, EUSanctionsResponse.class);

            if (response.getBody() != null && response.getBody().getResults() != null) {
                return mapToSanctionMatches(response.getBody().getResults(), "EU_FINANCIAL");
            }

            return new ArrayList<>();

        } catch (Exception e) {
            log.error("Failed to search EU Financial Sanctions list", e);
            throw new RuntimeException("EU Financial Sanctions search failed", e);
        }
    }

    /**
     * Search EU Asset Freeze List
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public List<SanctionMatch> searchAssetFreezeList(String name, String country) {
        log.debug("Searching EU Asset Freeze list for: {}", name);

        try {
            HttpHeaders headers = createHeaders();

            String url = String.format("%s/assetfreeze/search?name=%s&country=%s",
                euSanctionsApiUrl, name, country != null ? country : "");

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<EUSanctionsResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, EUSanctionsResponse.class);

            if (response.getBody() != null && response.getBody().getResults() != null) {
                return mapToSanctionMatches(response.getBody().getResults(), "EU_ASSET_FREEZE");
            }

            return new ArrayList<>();

        } catch (Exception e) {
            log.error("Failed to search EU Asset Freeze list", e);
            throw new RuntimeException("EU Asset Freeze search failed", e);
        }
    }

    /**
     * Download latest EU sanctions lists
     */
    public void downloadLatestLists() {
        log.info("Downloading latest EU sanctions lists");

        try {
            HttpHeaders headers = createHeaders();

            String url = euSanctionsApiUrl + "/download/all";
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, byte[].class);

            if (response.getStatusCode() == HttpStatus.OK) {
                // Store downloaded lists
                log.info("EU sanctions lists downloaded successfully");
            }

        } catch (Exception e) {
            log.error("Failed to download EU sanctions lists", e);
            throw new RuntimeException("EU sanctions list download failed", e);
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (apiKey != null && !apiKey.isEmpty()) {
            headers.set("X-API-Key", apiKey);
        }

        return headers;
    }

    private List<SanctionMatch> mapToSanctionMatches(
            List<EUSanctionsResponse.SanctionEntry> entries, String listName) {

        List<SanctionMatch> matches = new ArrayList<>();

        for (EUSanctionsResponse.SanctionEntry entry : entries) {
            matches.add(SanctionMatch.builder()
                .matchedName(entry.getName())
                .matchScore(calculateMatchScore(entry))
                .sanctionType(entry.getSanctionType())
                .sanctionsList(listName)
                .programName(entry.getProgramName())
                .listingDate(entry.getListingDate())
                .reason(entry.getReason())
                .build());
        }

        return matches;
    }

    private double calculateMatchScore(EUSanctionsResponse.SanctionEntry entry) {
        // Fuzzy matching score calculation
        return entry.getMatchPercentage() / 100.0;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class EUSanctionsResponse {
        private List<SanctionEntry> results;

        @lombok.Data
        @lombok.NoArgsConstructor
        @lombok.AllArgsConstructor
        public static class SanctionEntry {
            private String name;
            private String sanctionType;
            private String programName;
            private LocalDateTime listingDate;
            private String reason;
            private double matchPercentage;
        }
    }
}

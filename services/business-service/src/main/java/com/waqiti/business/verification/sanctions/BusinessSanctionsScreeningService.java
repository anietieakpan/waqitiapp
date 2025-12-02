package com.waqiti.business.verification.sanctions;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Business Sanctions Screening Service
 *
 * Screens businesses and their principals against OFAC and international sanctions lists.
 *
 * Integrates with fraud-detection-service for comprehensive sanctions screening:
 * - OFAC SDN list (businesses and individuals)
 * - EU Sanctions list
 * - UN Sanctions list
 * - Beneficial owner screening
 *
 * Compliance:
 * - OFAC Sanctions Compliance
 * - BSA/AML business verification
 * - FinCEN beneficial ownership requirements
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessSanctionsScreeningService {

    private final RestTemplate restTemplate;

    /**
     * Screen business and principal owner against sanctions lists.
     *
     * @param businessName Business legal name
     * @param principalOwnerName Principal owner/beneficial owner name
     * @param businessAddress Business address
     * @param country Country of operation
     * @return Sanctions screening result
     */
    public BusinessSanctionsResult screenBusiness(String businessName, String principalOwnerName,
                                                  String businessAddress, String country) {
        log.info("COMPLIANCE: Screening business against sanctions lists - Business: {}", businessName);

        long startTime = System.currentTimeMillis();
        List<SanctionsMatch> matches = new ArrayList<>();

        try {
            // Screen business entity
            SanctionsScreeningRequest businessRequest = SanctionsScreeningRequest.builder()
                .entityType("BUSINESS_ENTITY")
                .fullName(businessName)
                .address(businessAddress)
                .country(country)
                .checkSource("BUSINESS_ONBOARDING")
                .build();

            SanctionsScreeningResult businessResult = callSanctionsScreeningService(businessRequest);
            if (businessResult != null && businessResult.getMatchFound()) {
                matches.add(SanctionsMatch.builder()
                    .entityType("BUSINESS")
                    .matchedName(businessName)
                    .listName(businessResult.getMatchedList())
                    .confidence(businessResult.getMatchScore())
                    .riskLevel(businessResult.getRiskLevel())
                    .build());
            }

            // Screen principal owner
            if (principalOwnerName != null && !principalOwnerName.isEmpty()) {
                SanctionsScreeningRequest ownerRequest = SanctionsScreeningRequest.builder()
                    .entityType("BENEFICIAL_OWNER")
                    .fullName(principalOwnerName)
                    .address(businessAddress)
                    .country(country)
                    .checkSource("BUSINESS_ONBOARDING")
                    .build();

                SanctionsScreeningResult ownerResult = callSanctionsScreeningService(ownerRequest);
                if (ownerResult != null && ownerResult.getMatchFound()) {
                    matches.add(SanctionsMatch.builder()
                        .entityType("PRINCIPAL_OWNER")
                        .matchedName(principalOwnerName)
                        .listName(ownerResult.getMatchedList())
                        .confidence(ownerResult.getMatchScore())
                        .riskLevel(ownerResult.getRiskLevel())
                        .build());
                }
            }

            String status = matches.isEmpty() ? "CLEARED" : "MATCH_FOUND";
            String message = matches.isEmpty() ?
                "No sanctions matches found" :
                String.format("Found %d sanctions match(es)", matches.size());

            return BusinessSanctionsResult.builder()
                .hasMatch(!matches.isEmpty())
                .matchCount(matches.size())
                .matches(matches)
                .status(status)
                .message(message)
                .screenedAt(LocalDateTime.now())
                .durationMs(System.currentTimeMillis() - startTime)
                .build();

        } catch (Exception e) {
            log.error("COMPLIANCE: Business sanctions screening failed", e);

            return BusinessSanctionsResult.builder()
                .hasMatch(false)
                .matchCount(0)
                .matches(new ArrayList<>())
                .status("ERROR")
                .message("Sanctions screening error: " + e.getMessage())
                .screenedAt(LocalDateTime.now())
                .durationMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }

    /**
     * Call fraud-detection-service sanctions screening API.
     */
    private SanctionsScreeningResult callSanctionsScreeningService(SanctionsScreeningRequest request) {
        try {
            // Call internal fraud-detection-service API
            String fraudServiceUrl = "http://fraud-detection-service/api/v1/sanctions/screen";

            var response = restTemplate.postForEntity(
                fraudServiceUrl,
                request,
                SanctionsScreeningResult.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }

            return null;

        } catch (Exception e) {
            log.warn("COMPLIANCE: Failed to call sanctions screening service", e);
            return null;
        }
    }

    // Supporting DTOs

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessSanctionsResult {
        private Boolean hasMatch;
        private Integer matchCount;
        private List<SanctionsMatch> matches;
        private String status;
        private String message;
        private LocalDateTime screenedAt;
        private Long durationMs;

        public String getMatchDetails() {
            if (matches == null || matches.isEmpty()) {
                return "No matches";
            }

            StringBuilder details = new StringBuilder();
            for (SanctionsMatch match : matches) {
                details.append(String.format("%s: %s (List: %s, Confidence: %.2f%%, Risk: %s); ",
                    match.getEntityType(), match.getMatchedName(), match.getListName(),
                    match.getConfidence(), match.getRiskLevel()));
            }
            return details.toString();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SanctionsMatch {
        private String entityType;
        private String matchedName;
        private String listName;
        private BigDecimal confidence;
        private String riskLevel;
    }

    @Data
    @Builder
    private static class SanctionsScreeningRequest {
        private String entityType;
        private String fullName;
        private String address;
        private String country;
        private String checkSource;
    }

    @Data
    private static class SanctionsScreeningResult {
        private Boolean matchFound;
        private String matchedList;
        private BigDecimal matchScore;
        private String riskLevel;
    }
}

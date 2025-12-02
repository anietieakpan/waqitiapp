package com.waqiti.payment.client;

import com.waqiti.payment.domain.InstantTransfer;
import com.waqiti.payment.dto.NetworkTransferRequest;
import com.waqiti.payment.dto.NetworkTransferResponse;
import com.waqiti.payment.dto.NetworkStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Client for integrating with real-time payment networks (FedNow, RTP, Zelle)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RealTimePaymentNetworkClient {

    private final RestTemplate restTemplate;

    @Value("${payment.networks.fednow.url:https://api.fednow.fed.gov}")
    private String fedNowUrl;

    @Value("${payment.networks.rtp.url:https://api.rtp.clearinghouse.com}")
    private String rtpUrl;

    @Value("${payment.networks.zelle.url:https://api.zellepay.com}")
    private String zelleUrl;

    @Value("${payment.networks.timeout:30000}")
    private int timeoutMs;

    /**
     * Submit transfer to real-time payment network
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public CompletableFuture<NetworkTransferResponse> submitTransfer(InstantTransfer transfer) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Submitting transfer {} to {} network", transfer.getId(), transfer.getNetworkType());

                String networkUrl = getNetworkUrl(transfer.getNetworkType());
                NetworkTransferRequest request = buildNetworkRequest(transfer);

                HttpHeaders headers = buildAuthHeaders(transfer.getNetworkType());
                HttpEntity<NetworkTransferRequest> httpEntity = new HttpEntity<>(request, headers);

                ResponseEntity<NetworkTransferResponse> response = restTemplate.exchange(
                    networkUrl + "/transfers",
                    HttpMethod.POST,
                    httpEntity,
                    NetworkTransferResponse.class
                );

                NetworkTransferResponse responseBody = response.getBody();
                if (responseBody != null) {
                    responseBody.setSubmittedAt(LocalDateTime.now());
                    log.info("Transfer {} successfully submitted to {} network with external ID: {}", 
                        transfer.getId(), transfer.getNetworkType(), responseBody.getExternalTransactionId());
                }

                return responseBody;

            } catch (Exception e) {
                log.error("Failed to submit transfer {} to {} network", transfer.getId(), transfer.getNetworkType(), e);
                throw new RuntimeException("Network submission failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Check status of transfer in network
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 500))
    public CompletableFuture<NetworkStatusResponse> checkTransferStatus(String externalTransactionId, String networkType) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Checking status of transfer {} in {} network", externalTransactionId, networkType);

                String networkUrl = getNetworkUrl(networkType);
                HttpHeaders headers = buildAuthHeaders(networkType);
                HttpEntity<?> httpEntity = new HttpEntity<>(headers);

                ResponseEntity<NetworkStatusResponse> response = restTemplate.exchange(
                    networkUrl + "/transfers/" + externalTransactionId + "/status",
                    HttpMethod.GET,
                    httpEntity,
                    NetworkStatusResponse.class
                );

                NetworkStatusResponse responseBody = response.getBody();
                if (responseBody != null) {
                    responseBody.setCheckedAt(LocalDateTime.now());
                    log.debug("Status check completed for transfer {}: {}", externalTransactionId, responseBody.getStatus());
                }

                return responseBody;

            } catch (Exception e) {
                log.warn("Failed to check transfer status {} in {} network", externalTransactionId, networkType, e);
                // Return unknown status instead of throwing to avoid breaking the flow
                return NetworkStatusResponse.builder()
                    .externalTransactionId(externalTransactionId)
                    .status("UNKNOWN")
                    .errorMessage(e.getMessage())
                    .checkedAt(LocalDateTime.now())
                    .build();
            }
        });
    }

    /**
     * Cancel transfer in network (if supported)
     */
    @Retryable(value = {Exception.class}, maxAttempts = 2, backoff = @Backoff(delay = 1000))
    public CompletableFuture<Boolean> cancelTransfer(String externalTransactionId, String networkType, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Attempting to cancel transfer {} in {} network", externalTransactionId, networkType);

                String networkUrl = getNetworkUrl(networkType);
                HttpHeaders headers = buildAuthHeaders(networkType);
                
                Map<String, String> cancelRequest = Map.of(
                    "reason", reason,
                    "cancelledAt", LocalDateTime.now().toString()
                );
                
                HttpEntity<Map<String, String>> httpEntity = new HttpEntity<>(cancelRequest, headers);

                ResponseEntity<Void> response = restTemplate.exchange(
                    networkUrl + "/transfers/" + externalTransactionId + "/cancel",
                    HttpMethod.POST,
                    httpEntity,
                    Void.class
                );

                boolean cancelled = response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.ACCEPTED;
                
                log.info("Cancel request for transfer {} in {} network: {}", 
                    externalTransactionId, networkType, cancelled ? "SUCCESS" : "FAILED");

                return cancelled;

            } catch (Exception e) {
                log.error("Failed to cancel transfer {} in {} network", externalTransactionId, networkType, e);
                return false;
            }
        });
    }

    /**
     * Check network availability
     */
    public CompletableFuture<Boolean> checkNetworkHealth(String networkType) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String networkUrl = getNetworkUrl(networkType);
                HttpHeaders headers = buildAuthHeaders(networkType);
                HttpEntity<?> httpEntity = new HttpEntity<>(headers);

                ResponseEntity<Map> response = restTemplate.exchange(
                    networkUrl + "/health",
                    HttpMethod.GET,
                    httpEntity,
                    Map.class
                );

                boolean healthy = response.getStatusCode() == HttpStatus.OK;
                log.debug("Network {} health check: {}", networkType, healthy ? "HEALTHY" : "UNHEALTHY");

                return healthy;

            } catch (Exception e) {
                log.warn("Network {} health check failed", networkType, e);
                return false;
            }
        });
    }

    /**
     * Get network-specific transfer limits
     */
    public CompletableFuture<Map<String, Object>> getNetworkLimits(String networkType) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String networkUrl = getNetworkUrl(networkType);
                HttpHeaders headers = buildAuthHeaders(networkType);
                HttpEntity<?> httpEntity = new HttpEntity<>(headers);

                ResponseEntity<Map> response = restTemplate.exchange(
                    networkUrl + "/limits",
                    HttpMethod.GET,
                    httpEntity,
                    Map.class
                );

                Map<String, Object> limits = response.getBody();
                log.debug("Retrieved limits for network {}: {}", networkType, limits);

                return limits != null ? limits : Map.of();

            } catch (Exception e) {
                log.warn("Failed to get limits for network {}", networkType, e);
                return Map.of();
            }
        });
    }

    /**
     * Validate recipient in network
     */
    public CompletableFuture<Boolean> validateRecipient(String recipientIdentifier, String identifierType, String networkType) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Validating recipient {} ({}) in {} network", recipientIdentifier, identifierType, networkType);

                String networkUrl = getNetworkUrl(networkType);
                HttpHeaders headers = buildAuthHeaders(networkType);
                
                Map<String, String> validationRequest = Map.of(
                    "identifier", recipientIdentifier,
                    "identifierType", identifierType
                );
                
                HttpEntity<Map<String, String>> httpEntity = new HttpEntity<>(validationRequest, headers);

                ResponseEntity<Map> response = restTemplate.exchange(
                    networkUrl + "/recipients/validate",
                    HttpMethod.POST,
                    httpEntity,
                    Map.class
                );

                Map<String, Object> result = response.getBody();
                boolean valid = result != null && Boolean.TRUE.equals(result.get("valid"));
                
                log.debug("Recipient validation for {} in {} network: {}", recipientIdentifier, networkType, valid ? "VALID" : "INVALID");

                return valid;

            } catch (Exception e) {
                log.warn("Failed to validate recipient {} in {} network", recipientIdentifier, networkType, e);
                return false;
            }
        });
    }

    private String getNetworkUrl(String networkType) {
        switch (networkType.toUpperCase()) {
            case "FEDNOW":
                return fedNowUrl;
            case "RTP":
                return rtpUrl;
            case "ZELLE":
                return zelleUrl;
            default:
                throw new IllegalArgumentException("Unsupported network type: " + networkType);
        }
    }

    private HttpHeaders buildAuthHeaders(String networkType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", "Waqiti-Payment-Service/1.0");
        headers.set("X-Request-ID", java.util.UUID.randomUUID().toString());

        // Add network-specific authentication headers
        switch (networkType.toUpperCase()) {
            case "FEDNOW":
                headers.set("Authorization", "Bearer " + getFedNowToken());
                headers.set("X-FedNow-Participant-Id", getFedNowParticipantId());
                break;
            case "RTP":
                headers.set("Authorization", "Bearer " + getRTPToken());
                headers.set("X-RTP-Participant-Id", getRTPParticipantId());
                break;
            case "ZELLE":
                headers.set("Authorization", "Bearer " + getZelleToken());
                headers.set("X-Zelle-Partner-Id", getZellePartnerId());
                break;
        }

        return headers;
    }

    private NetworkTransferRequest buildNetworkRequest(InstantTransfer transfer) {
        return NetworkTransferRequest.builder()
            .internalTransferId(transfer.getId().toString())
            .senderId(transfer.getSenderId())
            .recipientId(transfer.getRecipientId())
            .recipientIdentifier(transfer.getRecipientIdentifier())
            .recipientIdentifierType(transfer.getRecipientIdentifierType())
            .amount(transfer.getAmount().getAmount())
            .currency(transfer.getAmount().getCurrency())
            .memo(transfer.getMemo())
            .purpose(transfer.getPurpose())
            .urgency("IMMEDIATE")
            .expectedSettlement(transfer.getExpectedSettlementTime())
            .metadata(Map.of(
                "originatingInstitution", "WAQITI_BANK",
                "receivingInstitution", "UNKNOWN", // Would be resolved
                "messageType", "INSTANT_TRANSFER",
                "businessFunction", "P2P_TRANSFER"
            ))
            .build();
    }

    // Token retrieval methods (these would integrate with secure token storage)
    private String getFedNowToken() {
        // In production, this would fetch from secure storage or token service
        return System.getenv("FEDNOW_ACCESS_TOKEN");
    }

    private String getRTPToken() {
        return System.getenv("RTP_ACCESS_TOKEN");
    }

    private String getZelleToken() {
        return System.getenv("ZELLE_ACCESS_TOKEN");
    }

    private String getFedNowParticipantId() {
        return System.getenv("FEDNOW_PARTICIPANT_ID");
    }

    private String getRTPParticipantId() {
        return System.getenv("RTP_PARTICIPANT_ID");
    }

    private String getZellePartnerId() {
        return System.getenv("ZELLE_PARTNER_ID");
    }

    /**
     * Get client health status
     */
    public NetworkClientHealth getHealthStatus() {
        return NetworkClientHealth.builder()
            .fedNowHealthy(checkNetworkHealth("FEDNOW").join())
            .rtpHealthy(checkNetworkHealth("RTP").join())
            .zelleHealthy(checkNetworkHealth("ZELLE").join())
            .lastHealthCheck(LocalDateTime.now())
            .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class NetworkClientHealth {
        private boolean fedNowHealthy;
        private boolean rtpHealthy;
        private boolean zelleHealthy;
        private LocalDateTime lastHealthCheck;
    }
}
package com.waqiti.kyc.controller;

import com.waqiti.kyc.service.KYCService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/kyc/webhooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "KYC Webhooks", description = "Webhook endpoints for KYC providers")
public class WebhookController {

    private final KYCService kycService;

    @Operation(summary = "Handle Onfido webhook")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Webhook processed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid webhook data"),
        @ApiResponse(responseCode = "401", description = "Invalid webhook signature")
    })
    @PostMapping("/onfido")
    public ResponseEntity<Map<String, String>> handleOnfidoWebhook(
            @RequestHeader(value = "X-SHA2-Signature", required = false) String signature,
            @RequestBody String payload) {
        
        log.info("Received Onfido webhook");
        try {
            kycService.processProviderWebhook("ONFIDO", payload);
            return ResponseEntity.ok(Map.of("status", "processed"));
        } catch (Exception e) {
            log.error("Error processing Onfido webhook", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Handle Jumio webhook")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Webhook processed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid webhook data"),
        @ApiResponse(responseCode = "401", description = "Invalid webhook signature")
    })
    @PostMapping("/jumio")
    public ResponseEntity<Map<String, String>> handleJumioWebhook(
            @RequestHeader(value = "X-Jumio-Signature", required = false) String signature,
            @RequestBody String payload) {
        
        log.info("Received Jumio webhook");
        try {
            kycService.processProviderWebhook("JUMIO", payload);
            return ResponseEntity.ok(Map.of("status", "processed"));
        } catch (Exception e) {
            log.error("Error processing Jumio webhook", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Handle ComplyAdvantage webhook")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Webhook processed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid webhook data")
    })
    @PostMapping("/complyadvantage")
    public ResponseEntity<Map<String, String>> handleComplyAdvantageWebhook(
            @RequestHeader(value = "X-CA-Signature", required = false) String signature,
            @RequestBody String payload) {
        
        log.info("Received ComplyAdvantage webhook");
        try {
            kycService.processProviderWebhook("COMPLY_ADVANTAGE", payload);
            return ResponseEntity.ok(Map.of("status", "processed"));
        } catch (Exception e) {
            log.error("Error processing ComplyAdvantage webhook", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Handle generic provider webhook")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Webhook processed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid webhook data")
    })
    @PostMapping("/{provider}")
    public ResponseEntity<Map<String, String>> handleGenericWebhook(
            @PathVariable String provider,
            @RequestHeader Map<String, String> headers,
            @RequestBody String payload) {
        
        log.info("Received webhook from provider: {}", provider);
        try {
            kycService.processProviderWebhook(provider.toUpperCase(), payload);
            return ResponseEntity.ok(Map.of("status", "processed"));
        } catch (Exception e) {
            log.error("Error processing {} webhook", provider, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Test webhook endpoint")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Webhook test successful")
    })
    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> testWebhook() {
        log.info("Webhook test endpoint called");
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Webhook endpoint is active",
                "timestamp", String.valueOf(System.currentTimeMillis())
        ));
    }
}
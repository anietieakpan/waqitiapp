package com.waqiti.common.kyc.client;

import com.waqiti.common.kyc.dto.KYCStatusResponse;
import com.waqiti.common.kyc.dto.KYCVerificationRequest;
import com.waqiti.common.kyc.dto.KYCVerificationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(
    name = "kyc-service",
    path = "/api/v1/kyc",
    fallbackFactory = KYCClientFallbackFactory.class
)
public interface KYCFeignClient {

    @PostMapping("/users/{userId}/verifications")
    KYCVerificationResponse initiateVerification(
            @PathVariable("userId") String userId,
            @RequestBody KYCVerificationRequest request);

    @GetMapping("/verifications/{verificationId}")
    KYCVerificationResponse getVerification(
            @PathVariable("verificationId") String verificationId);

    @GetMapping("/users/{userId}/verifications/active")
    KYCVerificationResponse getActiveVerification(
            @PathVariable("userId") String userId);

    @GetMapping("/users/{userId}/verifications")
    List<KYCVerificationResponse> getUserVerifications(
            @PathVariable("userId") String userId);

    @GetMapping("/users/{userId}/status")
    KYCStatusResponse getUserKYCStatus(
            @PathVariable("userId") String userId);

    @GetMapping("/users/{userId}/verified")
    Boolean isUserVerified(
            @PathVariable("userId") String userId,
            @RequestParam(value = "level", defaultValue = "BASIC") String level);

    @GetMapping("/users/{userId}/can-perform")
    Boolean canUserPerformAction(
            @PathVariable("userId") String userId,
            @RequestParam("action") String action);

    @DeleteMapping("/verifications/{verificationId}/cancel")
    KYCVerificationResponse cancelVerification(
            @PathVariable("verificationId") String verificationId,
            @RequestParam(value = "reason", required = false) String reason);
}
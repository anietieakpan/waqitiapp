package com.waqiti.compliance.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@FeignClient(
    name = "compliance-service",
    url = "${services.compliance.url:http://compliance-service:8083}"
)
public interface ComplianceServiceClient {

    @PostMapping("/api/v1/compliance/sar/file")
    Object fileSAR(@RequestBody Map<String, Object> request);

    @PostMapping("/api/v1/compliance/alert")
    Object sendComplianceAlert(@RequestBody Map<String, Object> request);

    @PostMapping("/api/v1/compliance/report")
    Object generateReport(@RequestBody Map<String, Object> request);

    @GetMapping("/api/v1/compliance/status/{userId}")
    Object getComplianceStatus(@PathVariable("userId") String userId);
    
    @PostMapping("/api/v1/compliance/cases/urgent")
    void createUrgentCase(
        @RequestParam("userId") UUID userId,
        @RequestParam("caseId") String caseId,
        @RequestParam("violationType") String violationType,
        @RequestParam("investigationId") String investigationId,
        @RequestParam("priority") String priority
    );
    
    @PostMapping("/api/v1/compliance/cases")
    void createCase(
        @RequestParam("userId") UUID userId,
        @RequestParam("caseId") String caseId,
        @RequestParam("violationType") String violationType,
        @RequestParam("reviewDate") LocalDateTime reviewDate
    );
    
    @PostMapping("/api/v1/compliance/reviews/schedule")
    void scheduleReview(
        @RequestParam("userId") UUID userId,
        @RequestParam("freezeId") String freezeId,
        @RequestParam("reviewTime") LocalDateTime reviewTime
    );
    
    @PostMapping("/api/v1/compliance/investigations/trigger")
    void triggerInvestigation(
        @RequestParam("relatedUserId") UUID relatedUserId,
        @RequestParam("primaryUserId") UUID primaryUserId,
        @RequestParam("reason") String reason,
        @RequestParam("investigationId") String investigationId
    );
}
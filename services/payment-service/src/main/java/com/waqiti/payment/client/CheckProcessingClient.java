package com.waqiti.payment.client;

import com.waqiti.payment.dto.ExternalCheckRequest;
import com.waqiti.payment.dto.ExternalCheckResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Client for external check processing service
 */
@FeignClient(
    name = "check-processing-service", 
    url = "${check.processor.url}",
    fallback = CheckProcessingClientFallback.class
)
public interface CheckProcessingClient {
    
    @PostMapping("/api/v1/checks/submit")
    ExternalCheckResponse submitCheckDeposit(@RequestBody ExternalCheckRequest request);
}
package com.waqiti.notification.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@FeignClient(
    name = "notification-service",
    url = "${services.notification.url:http://notification-service:8085}"
)
public interface NotificationServiceClient {

    @PostMapping("/api/v1/notifications/send")
    Object sendNotification(@RequestBody Map<String, Object> request);

    @PostMapping("/api/v1/notifications/send-email")
    Object sendEmail(@RequestBody Map<String, Object> request);

    @PostMapping("/api/v1/notifications/send-sms")
    Object sendSms(@RequestBody Map<String, Object> request);

    @PostMapping("/api/v1/notifications/alert")
    Object sendAlert(@RequestBody Map<String, Object> request);
    
    @PostMapping("/api/v1/notifications/executive-alert")
    void sendExecutiveAlert(
        @RequestParam("alertType") String alertType,
        @RequestParam("title") String title,
        @RequestParam("message") String message,
        @RequestParam("correlationId") String correlationId
    );
    
    @PostMapping("/api/v1/notifications/customer/{userId}")
    void sendCustomerNotification(
        @PathVariable("userId") UUID userId,
        @RequestParam("subject") String subject,
        @RequestParam("message") String message,
        @RequestParam("notificationType") String notificationType
    );
}
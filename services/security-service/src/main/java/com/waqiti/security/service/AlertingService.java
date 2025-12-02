package com.waqiti.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertingService {
    
    public void createDLQAlert(String topic, String eventId, String errorMessage) {
        log.error("DLQ ALERT - Topic: {} EventId: {} Error: {}", topic, eventId, errorMessage);
    }
    
    public void sendCriticalAlert(String title, String message) {
        log.error("CRITICAL ALERT - {}: {}", title, message);
    }
}
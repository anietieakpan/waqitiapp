package com.waqiti.common.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for validation operations
 */
@Slf4j
@Service
public class ValidationService {

    public boolean validatePhoneNumber(String phoneNumber) {
        log.debug("Validating phone number: {}", phoneNumber);
        return phoneNumber != null && phoneNumber.matches("^\\+?[1-9]\\d{1,14}$");
    }

    public boolean validateEmail(String email) {
        log.debug("Validating email: {}", email);
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }

    public boolean validateUserId(String userId) {
        log.debug("Validating user ID: {}", userId);
        return userId != null && !userId.trim().isEmpty();
    }
}

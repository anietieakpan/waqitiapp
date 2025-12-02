package com.waqiti.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSecurityService {
    
    public void updateSecurityProfile(String userId, Object result) {
        log.debug("Updating security profile for userId: {}", userId);
    }
}
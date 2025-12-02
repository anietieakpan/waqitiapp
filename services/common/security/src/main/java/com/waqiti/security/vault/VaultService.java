package com.waqiti.security.vault;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VaultService {
    
    public String getSecret(String path) {
        log.debug("Retrieving secret from path: {}", path);
        return "secret-value";
    }
    
    public void storeSecret(String path, String value) {
        log.debug("Storing secret at path: {}", path);
    }
}

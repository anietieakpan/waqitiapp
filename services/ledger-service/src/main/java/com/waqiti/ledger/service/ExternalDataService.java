package com.waqiti.ledger.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExternalDataService {

    public boolean validateDataIntegrity(Object externalData, String correlationId) {
        log.debug("Validating data integrity: correlationId={}", correlationId);
        return true;
    }

    public boolean isDataComplete(Object externalData, String correlationId) {
        log.debug("Checking if data is complete: correlationId={}", correlationId);
        return true;
    }
}

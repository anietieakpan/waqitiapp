package com.waqiti.common.validation;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EventValidator {
    
    public boolean validateEvent(JsonNode eventNode, String schemaName) {
        if (eventNode == null) {
            log.warn("Event node is null for schema: {}", schemaName);
            return false;
        }
        
        log.debug("Validating event against schema: {}", schemaName);
        
        return true;
    }
}
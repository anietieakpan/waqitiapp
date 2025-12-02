package com.waqiti.payment.tokenization;

import com.waqiti.common.audit.AuditService;
import com.waqiti.common.audit.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Audit service for tokenization operations
 * Provides immutable audit trail for compliance (PCI DSS, SOX)
 *
 * @author Waqiti Security Team
 * @version 3.0.0
 * @since 2025-10-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenizationAuditService {

    private final AuditService auditService;

    public void logTokenization(String userId, String dataType, String token,
                                 String ipAddress, boolean success, String message) {
        Map<String, Object> details = new HashMap<>();
        details.put("dataType", dataType);
        details.put("token", maskToken(token));
        details.put("ipAddress", ipAddress);
        details.put("success", success);
        details.put("message", message);
        details.put("timestamp", Instant.now().toString());

        AuditEvent event = AuditEvent.builder()
            .eventType("TOKENIZATION")
            .userId(userId)
            .action("CREATE_TOKEN")
            .resourceType("PAYMENT_TOKEN")
            .resourceId(maskToken(token))
            .details(details)
            .success(success)
            .ipAddress(ipAddress)
            .timestamp(Instant.now())
            .build();

        auditService.log(event);

        log.info("Tokenization audit logged: userId={}, dataType={}, success={}",
            userId, dataType, success);
    }

    public void logDetokenization(String userId, String token, String ipAddress,
                                   String reason, boolean success, String message) {
        Map<String, Object> details = new HashMap<>();
        details.put("token", maskToken(token));
        details.put("reason", reason);
        details.put("ipAddress", ipAddress);
        details.put("success", success);
        details.put("message", message);
        details.put("timestamp", Instant.now().toString());

        AuditEvent event = AuditEvent.builder()
            .eventType("DETOKENIZATION")
            .userId(userId)
            .action("RETRIEVE_SENSITIVE_DATA")
            .resourceType("PAYMENT_TOKEN")
            .resourceId(maskToken(token))
            .details(details)
            .success(success)
            .ipAddress(ipAddress)
            .timestamp(Instant.now())
            .build();

        auditService.log(event);

        log.info("Detokenization audit logged: userId={}, token={}, reason={}, success={}",
            userId, maskToken(token), reason, success);
    }

    public void logTokenRotation(String userId, String oldToken, String newToken) {
        Map<String, Object> details = new HashMap<>();
        details.put("oldToken", maskToken(oldToken));
        details.put("newToken", maskToken(newToken));
        details.put("timestamp", Instant.now().toString());

        AuditEvent event = AuditEvent.builder()
            .eventType("TOKEN_ROTATION")
            .userId(userId)
            .action("ROTATE_TOKEN")
            .resourceType("PAYMENT_TOKEN")
            .resourceId(maskToken(oldToken))
            .details(details)
            .success(true)
            .timestamp(Instant.now())
            .build();

        auditService.log(event);

        log.info("Token rotation audit logged: userId={}, oldToken={}, newToken={}",
            userId, maskToken(oldToken), maskToken(newToken));
    }

    public void logTokenDeletion(String userId, String token, boolean success, String message) {
        Map<String, Object> details = new HashMap<>();
        details.put("token", maskToken(token));
        details.put("success", success);
        details.put("message", message);
        details.put("timestamp", Instant.now().toString());

        AuditEvent event = AuditEvent.builder()
            .eventType("TOKEN_DELETION")
            .userId(userId)
            .action("DELETE_TOKEN")
            .resourceType("PAYMENT_TOKEN")
            .resourceId(maskToken(token))
            .details(details)
            .success(success)
            .timestamp(Instant.now())
            .build();

        auditService.log(event);

        log.info("Token deletion audit logged: userId={}, token={}, success={}",
            userId, maskToken(token), success);
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "****";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }
}

// File: services/notification-service/src/main/java/com/waqiti/notification/service/provider/SmsProvider.java
package com.waqiti.notification.service.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import com.waqiti.notification.exception.SmsDeliveryException;

/**
 * Provider for SMS notifications using Twilio
 */
@Service
@Slf4j
public class SmsProvider {

    @Value("${notification.sms.from}")
    private String fromNumber;

    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.enabled:false}")
    private boolean twilioEnabled;

    /**
     * Initializes Twilio client on service startup
     */
    public void init() {
        if (twilioEnabled) {
            try {
                log.info("Initializing Twilio SMS provider with account SID: {}", maskSid(accountSid));
                Twilio.init(accountSid, authToken);
                log.info("Twilio SMS provider initialized successfully");
            } catch (Exception e) {
                log.error("Failed to initialize Twilio SMS provider", e);
            }
        } else {
            log.info("Twilio SMS provider is disabled");
        }
    }

    /**
     * Sends an SMS message to the specified phone number
     *
     * @param to The recipient's phone number (E.164 format)
     * @param messageText The message text to send
     * @return The message SID if successful, null otherwise
     */
    public String sendSms(String to, String messageText) {
        if (!twilioEnabled) {
            log.info("SMS sending skipped as Twilio is disabled. Would send to: {}, message: {}",
                    to, messageText);
            return "DISABLED";
        }

        try {
            log.info("Sending SMS to: {}", to);
            Message message = Message.creator(
                    new PhoneNumber(to),
                    new PhoneNumber(fromNumber),
                    messageText
            ).create();

            log.info("SMS sent successfully with SID: {}", message.getSid());
            return message.getSid();
        } catch (Exception e) {
            log.error("CRITICAL: Failed to send SMS to {} - Customer communication failure", to, e);
            throw new SmsDeliveryException("SMS delivery failed to: " + to, e);
        }
    }

    /**
     * Mask most of the SID for security in logs
     */
    private String maskSid(String sid) {
        if (sid == null || sid.length() < 8) {
            return "***";
        }
        return sid.substring(0, 4) + "..." + sid.substring(sid.length() - 4);
    }
}
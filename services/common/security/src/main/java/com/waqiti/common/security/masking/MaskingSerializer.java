package com.waqiti.common.security.masking;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * Jackson serializer for automatic data masking in JSON responses
 */
public class MaskingSerializer extends JsonSerializer<String> {

    private final MaskingType maskingType;
    private final DataMaskingService maskingService;

    public MaskingSerializer(MaskingType maskingType) {
        this.maskingType = maskingType;
        this.maskingService = new DataMaskingService();
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }

        String maskedValue = switch (maskingType) {
            case EMAIL -> maskingService.maskEmail(value);
            case PHONE -> maskingService.maskPhoneNumber(value);
            case SSN -> maskingService.maskSSN(value);
            case CARD_NUMBER -> maskingService.maskCardNumber(value);
            case ACCOUNT_NUMBER -> maskingService.maskAccountNumber(value);
            case NAME -> maskingService.maskName(value);
            case ADDRESS -> maskingService.maskAddress(value);
            case IP_ADDRESS -> maskingService.maskIpAddress(value);
            case GENERIC -> maskingService.maskGeneric(value);
        };

        gen.writeString(maskedValue);
    }

    public enum MaskingType {
        EMAIL,
        PHONE,
        SSN,
        CARD_NUMBER,
        ACCOUNT_NUMBER,
        NAME,
        ADDRESS,
        IP_ADDRESS,
        GENERIC
    }
}
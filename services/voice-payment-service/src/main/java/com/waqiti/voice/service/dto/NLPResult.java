package com.waqiti.voice.service.dto;

import com.waqiti.voice.domain.VoiceCommand;
import lombok.*;

import java.util.Map;

/**
 * NLP Processing Result DTO
 *
 * Contains results of natural language processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NLPResult {

    /**
     * Whether processing was successful
     */
    private Boolean successful;

    /**
     * Detected intent (string representation)
     */
    private String intent;

    /**
     * Command type enum
     */
    private VoiceCommand.CommandType commandType;

    /**
     * Extracted entities (amount, recipient, currency, etc.)
     */
    private Map<String, Object> entities;

    /**
     * Confidence score (0.0 - 1.0)
     */
    private Double confidence;

    /**
     * Processing time in milliseconds
     */
    private Long processingTimeMs;

    /**
     * Error message (if failed)
     */
    private String errorMessage;
}

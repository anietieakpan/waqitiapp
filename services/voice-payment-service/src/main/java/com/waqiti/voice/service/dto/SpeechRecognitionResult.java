package com.waqiti.voice.service.dto;

import lombok.*;

import java.util.List;

/**
 * Speech Recognition Result DTO
 *
 * Contains transcription results from Google Cloud Speech-to-Text
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeechRecognitionResult {

    /**
     * Whether transcription was successful
     */
    private Boolean successful;

    /**
     * Primary transcribed text
     */
    private String transcript;

    /**
     * Confidence score (0.0 - 1.0)
     */
    private Double confidence;

    /**
     * Detected language code
     */
    private String detectedLanguage;

    /**
     * Alternative transcriptions
     */
    private List<Alternative> alternatives;

    /**
     * Word-level timestamps (if enabled)
     */
    private List<WordInfo> words;

    /**
     * Processing time in milliseconds
     */
    private Long processingTimeMs;

    /**
     * Error message (if failed)
     */
    private String errorMessage;

    /**
     * Alternative transcription
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Alternative {
        private String transcript;
        private Double confidence;
    }

    /**
     * Word timing information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WordInfo {
        private String word;
        private Long startTimeMs;
        private Long endTimeMs;
        private Double confidence;
    }
}

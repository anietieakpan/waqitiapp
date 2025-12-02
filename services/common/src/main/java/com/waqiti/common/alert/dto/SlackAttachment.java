package com.waqiti.common.alert.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Slack Attachment DTO
 *
 * Represents a rich attachment for Slack messages with structured data.
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlackAttachment {

    /**
     * Color bar on left side (hex code or "good", "warning", "danger")
     */
    @JsonProperty("color")
    private String color;

    /**
     * Fallback text for notifications
     */
    @JsonProperty("fallback")
    private String fallback;

    /**
     * Pretext shown above the attachment
     */
    @JsonProperty("pretext")
    private String pretext;

    /**
     * Attachment title
     */
    @JsonProperty("title")
    private String title;

    /**
     * Title link URL
     */
    @JsonProperty("title_link")
    private String titleLink;

    /**
     * Main text content (supports markdown)
     */
    @JsonProperty("text")
    private String text;

    /**
     * Structured fields
     */
    @JsonProperty("fields")
    private List<Field> fields;

    /**
     * Thumbnail image URL
     */
    @JsonProperty("thumb_url")
    private String thumbUrl;

    /**
     * Footer text
     */
    @JsonProperty("footer")
    private String footer;

    /**
     * Footer icon URL
     */
    @JsonProperty("footer_icon")
    private String footerIcon;

    /**
     * Unix timestamp (seconds)
     */
    @JsonProperty("ts")
    private Long timestamp;

    /**
     * Field inner class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Field {

        /**
         * Field title
         */
        @JsonProperty("title")
        private String title;

        /**
         * Field value (supports markdown)
         */
        @JsonProperty("value")
        private String value;

        /**
         * Whether field should be displayed as short (side-by-side)
         */
        @JsonProperty("short")
        private Boolean shortField;
    }
}

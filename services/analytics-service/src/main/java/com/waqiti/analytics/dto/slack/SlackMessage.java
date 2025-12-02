package com.waqiti.analytics.dto.slack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Slack Message Request
 *
 * Sends formatted messages to Slack channels.
 * Supports blocks for rich formatting.
 *
 * API Reference: https://api.slack.com/methods/chat.postMessage
 *
 * @author Waqiti Analytics Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-15
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlackMessage {

    /**
     * Channel ID or name (#channel-name)
     */
    @JsonProperty("channel")
    private String channel;

    /**
     * Message text (plain text or markdown)
     */
    @JsonProperty("text")
    private String text;

    /**
     * Blocks for rich message formatting
     */
    @JsonProperty("blocks")
    private List<Block> blocks;

    /**
     * Username for the bot
     */
    @JsonProperty("username")
    @Builder.Default
    private String username = "Analytics Service";

    /**
     * Icon emoji for the bot
     */
    @JsonProperty("icon_emoji")
    @Builder.Default
    private String iconEmoji = ":chart_with_upwards_trend:";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Block {
        @JsonProperty("type")
        private String type;

        @JsonProperty("text")
        private Text text;

        @JsonProperty("fields")
        private List<Text> fields;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Text {
        @JsonProperty("type")
        private String type;

        @JsonProperty("text")
        private String text;
    }
}

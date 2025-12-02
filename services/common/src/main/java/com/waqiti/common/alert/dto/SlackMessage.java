package com.waqiti.common.alert.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Slack Message DTO
 *
 * Represents a Slack Incoming Webhook message payload.
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlackMessage {

    /**
     * Message text (supports markdown)
     */
    @JsonProperty("text")
    private String text;

    /**
     * Username to display as sender
     */
    @JsonProperty("username")
    private String username;

    /**
     * Icon emoji for the bot (e.g., :robot_face:)
     */
    @JsonProperty("icon_emoji")
    private String iconEmoji;

    /**
     * Icon URL for the bot
     */
    @JsonProperty("icon_url")
    private String iconUrl;

    /**
     * Channel to send to (overrides webhook default)
     */
    @JsonProperty("channel")
    private String channel;

    /**
     * Rich attachments for formatted messages
     */
    @JsonProperty("attachments")
    private List<SlackAttachment> attachments;

    /**
     * Thread timestamp for replies
     */
    @JsonProperty("thread_ts")
    private String threadTs;

    /**
     * Whether to reply in thread broadcast
     */
    @JsonProperty("reply_broadcast")
    private Boolean replyBroadcast;
}

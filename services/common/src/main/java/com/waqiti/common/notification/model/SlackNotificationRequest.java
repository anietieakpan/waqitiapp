package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

/**
 * Slack notification request for sending messages to Slack channels or users.
 * Supports rich formatting, attachments, interactive components, and threading.
 *
 * This class provides comprehensive Slack integration capabilities including:
 * - Channel and direct message support
 * - Rich text formatting with Blocks API
 * - File attachments and media
 * - Threading and conversation management
 * - User/group mentions
 * - Interactive buttons and menus
 * - Markdown formatting
 * - Custom bot configuration
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class SlackNotificationRequest extends NotificationRequest {

    /**
     * Target Slack channel (e.g., "#general", "#incidents")
     * Can be channel name with # or channel ID
     */
    private String slackChannel;

    /**
     * Target user ID for direct messages
     * Alternative to channel for DM notifications
     */
    private String targetUserId;

    /**
     * Message text content (supports Slack markdown)
     * This is the primary message content
     */
    private String message;

    /**
     * Thread timestamp for threaded replies
     * Set this to reply to a specific message in a thread
     */
    private String threadTs;

    /**
     * Whether to also send to channel when replying in thread
     * Only valid when threadTs is set
     */
    @Builder.Default
    private boolean replyBroadcast = false;

    /**
     * User mentions (e.g., "@user", "@channel", "@here")
     * These will be notified when the message is sent
     */
    private List<String> mentions;

    /**
     * User group mentions (e.g., "@engineering-team")
     */
    private List<String> groupMentions;

    /**
     * Slack blocks for rich formatting
     * Use Slack's Block Kit for advanced layouts
     */
    private List<SlackBlock> blocks;

    /**
     * Legacy message attachments
     * Prefer blocks for new implementations
     */
    private List<SlackAttachment> attachments;

    /**
     * Files to attach to the message
     * Maps filename to file URL or content
     */
    private Map<String, String> files;

    /**
     * Icon emoji for the bot message (e.g., ":robot_face:")
     */
    private String iconEmoji;

    /**
     * Icon URL for the bot message
     * Alternative to iconEmoji
     */
    private String iconUrl;

    /**
     * Custom username for the bot sending the message
     */
    private String username;

    /**
     * Message formatting mode
     */
    @Builder.Default
    private FormattingMode formattingMode = FormattingMode.MARKDOWN;

    /**
     * Whether to unfurl links in the message
     */
    @Builder.Default
    private boolean unfurlLinks = true;

    /**
     * Whether to unfurl media (images, videos) in the message
     */
    @Builder.Default
    private boolean unfurlMedia = true;

    /**
     * Whether to parse links and mentions
     */
    @Builder.Default
    private boolean linkNames = true;

    /**
     * Whether to send as ephemeral message (only visible to target user)
     */
    @Builder.Default
    private boolean ephemeral = false;

    /**
     * Slack-specific metadata for the message (for future retrieval)
     * Note: Use setMetadata() from parent for general metadata
     */
    private Map<String, String> slackMetadata;

    /**
     * Slack workspace/team ID (for multi-workspace bots)
     */
    private String workspaceId;

    /**
     * Custom bot token (overrides default)
     */
    private String botToken;

    /**
     * Callback ID for interactive components
     */
    private String callbackId;

    /**
     * Actions/buttons to include in the message
     */
    private List<SlackAction> actions;

    /**
     * Message update mode (for editing existing messages)
     */
    private UpdateMode updateMode;

    /**
     * Message timestamp to update (when updateMode is UPDATE)
     */
    private String updateMessageTs;

    /**
     * Slack block representation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlackBlock {
        private String type; // section, header, divider, image, actions, context
        private String blockId;
        private SlackText text;
        private List<SlackElement> elements;
        private String imageUrl;
        private String altText;
        private List<SlackAction> actions;
        private Map<String, Object> fields;
    }

    /**
     * Slack text object
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlackText {
        private String type; // plain_text or mrkdwn
        private String text;
        private boolean emoji;
        private boolean verbatim;
    }

    /**
     * Slack element (for context blocks)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlackElement {
        private String type; // image, mrkdwn, plain_text
        private String text;
        private String imageUrl;
        private String altText;
    }

    /**
     * Legacy Slack attachment
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlackAttachment {
        private String fallback;
        private String color;
        private String pretext;
        private String authorName;
        private String authorLink;
        private String authorIcon;
        private String title;
        private String titleLink;
        private String text;
        private List<SlackField> fields;
        private String imageUrl;
        private String thumbUrl;
        private String footer;
        private String footerIcon;
        private Long timestamp;
        private String callbackId;
        private List<SlackAction> actions;
    }

    /**
     * Slack attachment field
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlackField {
        private String title;
        private String value;
        @Builder.Default
        private boolean isShort = false;
    }

    /**
     * Slack interactive action/button
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlackAction {
        private String type; // button, select, datepicker, etc.
        private String actionId;
        private String text;
        private String value;
        private String url;
        private String style; // primary, danger, default
        private List<SlackOption> options;
        private SlackConfirm confirm;
    }

    /**
     * Slack select option
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlackOption {
        private String text;
        private String value;
        private String description;
    }

    /**
     * Slack confirmation dialog
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlackConfirm {
        private String title;
        private String text;
        private String confirmText;
        private String denyText;
    }

    /**
     * Message formatting mode
     */
    public enum FormattingMode {
        MARKDOWN,
        PLAIN_TEXT,
        BLOCK_KIT
    }

    /**
     * Message update mode
     */
    public enum UpdateMode {
        NEW_MESSAGE,
        UPDATE,
        DELETE
    }

    /**
     * Helper method to add a simple text block
     */
    public void addSimpleTextBlock(String text) {
        if (this.blocks == null) {
            this.blocks = new java.util.ArrayList<>();
        }
        this.blocks.add(SlackBlock.builder()
                .type("section")
                .text(SlackText.builder()
                        .type("mrkdwn")
                        .text(text)
                        .build())
                .build());
    }

    /**
     * Helper method to add a header block
     */
    public void addHeaderBlock(String text) {
        if (this.blocks == null) {
            this.blocks = new java.util.ArrayList<>();
        }
        this.blocks.add(SlackBlock.builder()
                .type("header")
                .text(SlackText.builder()
                        .type("plain_text")
                        .text(text)
                        .emoji(true)
                        .build())
                .build());
    }

    /**
     * Helper method to add a divider
     */
    public void addDivider() {
        if (this.blocks == null) {
            this.blocks = new java.util.ArrayList<>();
        }
        this.blocks.add(SlackBlock.builder()
                .type("divider")
                .build());
    }

    /**
     * Helper method to add an image block
     */
    public void addImageBlock(String imageUrl, String altText) {
        if (this.blocks == null) {
            this.blocks = new java.util.ArrayList<>();
        }
        this.blocks.add(SlackBlock.builder()
                .type("image")
                .imageUrl(imageUrl)
                .altText(altText)
                .build());
    }

    /**
     * Helper method to add a button action
     */
    public void addButton(String text, String actionId, String value, String style) {
        if (this.actions == null) {
            this.actions = new java.util.ArrayList<>();
        }
        this.actions.add(SlackAction.builder()
                .type("button")
                .actionId(actionId)
                .text(text)
                .value(value)
                .style(style)
                .build());
    }

    /**
     * Helper method to add user mention
     */
    public void addMention(String userId) {
        if (this.mentions == null) {
            this.mentions = new java.util.ArrayList<>();
        }
        if (!this.mentions.contains(userId)) {
            this.mentions.add(userId);
        }
    }

    /**
     * Helper method to mention @channel
     */
    public void mentionChannel() {
        addMention("@channel");
    }

    /**
     * Helper method to mention @here
     */
    public void mentionHere() {
        addMention("@here");
    }

    /**
     * Validate the request
     */
    public boolean isValid() {
        // Must have either slackChannel or targetUserId
        if (slackChannel == null && targetUserId == null) {
            return false;
        }

        // Must have either message or blocks
        if (message == null && (blocks == null || blocks.isEmpty())) {
            return false;
        }

        // If updating, must have updateMessageTs
        if (updateMode == UpdateMode.UPDATE && updateMessageTs == null) {
            return false;
        }

        return true;
    }

    /**
     * Get channel (override parent to return SLACK enum value)
     */
    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.SLACK;
    }

    /**
     * Set channel (for compatibility - sets slackChannel string)
     */
    public void setChannel(String channel) {
        this.slackChannel = channel;
    }

    /**
     * Get Slack channel name/ID
     */
    public String getSlackChannelName() {
        return slackChannel;
    }

    /**
     * Get formatted message with mentions
     */
    public String getFormattedMessage() {
        StringBuilder formatted = new StringBuilder();

        // Add mentions at the beginning
        if (mentions != null && !mentions.isEmpty()) {
            for (String mention : mentions) {
                formatted.append(mention).append(" ");
            }
        }

        // Add group mentions
        if (groupMentions != null && !groupMentions.isEmpty()) {
            for (String groupMention : groupMentions) {
                formatted.append("<!subteam^").append(groupMention).append("> ");
            }
        }

        // Add main message
        if (message != null) {
            formatted.append(message);
        }

        return formatted.toString().trim();
    }
}

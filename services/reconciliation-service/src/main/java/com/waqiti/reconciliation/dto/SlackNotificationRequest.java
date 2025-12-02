package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlackNotificationRequest {

    private String channel;
    
    private String username;
    
    private String text;
    
    private String iconEmoji;
    
    private String iconUrl;
    
    private boolean linkNames;
    
    private boolean unfurlLinks;
    
    private boolean unfurlMedia;
    
    private List<SlackAttachment> attachments;
    
    private List<SlackBlock> blocks;
    
    private String threadTs;
    
    private boolean replyBroadcast;
    
    @Builder.Default
    private LocalDateTime scheduledFor = LocalDateTime.now();

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
        private Long ts;
    }

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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlackBlock {
        private String type;
        private Map<String, Object> element;
        private String blockId;
    }

    public boolean hasAttachments() {
        return attachments != null && !attachments.isEmpty();
    }

    public boolean hasBlocks() {
        return blocks != null && !blocks.isEmpty();
    }

    public boolean isThreadReply() {
        return threadTs != null && !threadTs.isEmpty();
    }

    public boolean isScheduled() {
        return scheduledFor != null && scheduledFor.isAfter(LocalDateTime.now());
    }
}
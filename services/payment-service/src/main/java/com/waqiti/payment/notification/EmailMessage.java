package com.waqiti.payment.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Email Message model
 *
 * @author Waqiti Platform Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailMessage {
    private List<String> to;
    private List<String> cc;
    private List<String> bcc;
    private String subject;
    private String body;
    private String htmlBody;
    private boolean isHtml;
    private Map<String, Object> templateVariables;
    private List<EmailAttachment> attachments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailAttachment {
        private String filename;
        private byte[] content;
        private String contentType;
    }
}

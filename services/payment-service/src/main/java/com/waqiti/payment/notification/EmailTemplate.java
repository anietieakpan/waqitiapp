package com.waqiti.payment.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Email Template model
 *
 * @author Waqiti Platform Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailTemplate {
    private String name;
    private String subject;
    private String body;
    private String htmlBody;
    private boolean isHtml;
}

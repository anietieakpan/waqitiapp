package com.waqiti.common.security;

import java.time.Instant; /**
 * Behavior Event Data Class
 */
@lombok.Data
@lombok.Builder
public class BehaviorEvent implements java.io.Serializable {
    private Instant timestamp;
    private String endpoint;
    private String method;
    private String userAgent;
    private String ip;
    private String sessionId;
    private String referrer;
    private int contentLength;
}

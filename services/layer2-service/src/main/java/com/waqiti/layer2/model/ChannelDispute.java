package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * State channel dispute
 */
@Data
@Builder
public class ChannelDispute {
    private String id;
    private String channelId;
    private String disputerAddress;
    private String reason;
    private byte[] evidence;
    private LocalDateTime timestamp;
    private LocalDateTime resolutionDeadline;
    private ChannelDisputeStatus status;
}

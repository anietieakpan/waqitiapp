package com.waqiti.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReVerificationRequest {
    private String userId;
    private String reVerificationReason;
    private List<String> verificationsToUpdate;
    private Instant requestTimestamp;
}

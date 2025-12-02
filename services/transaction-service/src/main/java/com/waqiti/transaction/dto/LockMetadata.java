package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class LockMetadata implements Serializable {
    private String lockKey;
    private String lockOwner;
    private long expirationTime;
    private long acquiredAt;
}
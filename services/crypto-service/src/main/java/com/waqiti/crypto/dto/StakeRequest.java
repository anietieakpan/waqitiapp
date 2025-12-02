package com.waqiti.crypto.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;

@Data
@Builder
public class StakeRequest {
    private String userAddress;
    private BigInteger amount;
    private Integer lockPeriodDays;
}
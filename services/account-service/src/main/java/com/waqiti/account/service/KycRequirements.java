package com.waqiti.account.service;

import com.waqiti.account.entity.Account;

import java.time.LocalDateTime;
import java.util.List;

@lombok.Data
@lombok.Builder
public class KycRequirements {
    private Account.KycLevel minimumLevel;
    private List<String> reasons;
    private LocalDateTime evaluatedAt;
}

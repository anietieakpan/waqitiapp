package com.waqiti.analytics.dto.profile;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {
    private String userId;
    private Integer age;
    private String income; // Income bracket
    private String location;
    private String occupation;
    private String familyStatus;
    private BigDecimal creditScore;
    private LocalDate joinDate;
}
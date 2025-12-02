package com.waqiti.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTopicRequest {
    private String displayName;
    private String description;
    private String category;
    private Boolean active;
    private Boolean autoSubscribe;
    private Integer priority;
    private String icon;
    private String color;
}
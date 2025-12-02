package com.waqiti.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicCategoryDto {
    private String name;
    private String displayName;
    private String description;
    private String icon;
    private String color;
    private int topicCount;
    private int priority;
}
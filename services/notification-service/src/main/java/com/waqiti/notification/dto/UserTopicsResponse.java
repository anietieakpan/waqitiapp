package com.waqiti.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTopicsResponse {
    private String userId;
    private List<String> topics;
    private Map<String, List<String>> topicsByDevice;
    private int totalTopics;
}
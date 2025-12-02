package com.waqiti.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentRequest {
    
    private MultipartFile file;
    private Boolean isEphemeral;
    private LocationData location;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationData {
        private Double latitude;
        private Double longitude;
        private String address;
    }
}
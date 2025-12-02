package com.waqiti.messaging.dto;

import com.waqiti.messaging.domain.AttachmentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentDTO {
    
    private String id;
    private AttachmentType type;
    private String fileName;
    private Long fileSize;
    private String mimeType;
    private String url;
    private String thumbnailUrl;
    private Integer width;
    private Integer height;
    private Integer duration;
    private Double latitude;
    private Double longitude;
    private String address;
}
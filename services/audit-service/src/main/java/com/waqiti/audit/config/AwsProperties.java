package com.waqiti.audit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "aws")
public class AwsProperties {
    
    private String region = "us-east-1";
    private String accessKeyId;
    private String secretAccessKey;
    private S3Properties s3 = new S3Properties();
    
    @Data
    public static class S3Properties {
        private String bucket;
    }
}
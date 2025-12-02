package com.waqiti.messaging.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {
    
    @Value("${AWS_ACCESS_KEY_ID:}")
    private String accessKeyId;
    
    @Value("${AWS_SECRET_ACCESS_KEY:}")
    private String secretAccessKey;
    
    @Value("${AWS_REGION:us-east-1}")
    private String region;
    
    @Bean
    public S3Client s3Client() {
        if (accessKeyId.isEmpty() || secretAccessKey.isEmpty()) {
            // Use default credentials provider chain
            return S3Client.builder()
                .region(Region.of(region))
                .build();
        } else {
            // Use provided credentials
            AwsBasicCredentials awsCreds = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
            return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                .build();
        }
    }
}
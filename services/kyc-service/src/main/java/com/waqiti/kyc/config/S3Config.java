package com.waqiti.kyc.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Configuration for AWS S3 client
 */
@Configuration
@RequiredArgsConstructor
public class S3Config {
    
    private final KYCProperties kycProperties;
    
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(kycProperties.getStorage().getRegion()))
                .credentialsProvider(awsCredentialsProvider())
                .build();
    }
    
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(kycProperties.getStorage().getRegion()))
                .credentialsProvider(awsCredentialsProvider())
                .build();
    }
    
    private AwsCredentialsProvider awsCredentialsProvider() {
        // Use default credentials provider chain (supports IAM roles, env vars, etc.)
        return DefaultCredentialsProvider.create();
    }
}
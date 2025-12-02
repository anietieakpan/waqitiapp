package com.waqiti.crypto.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;

/**
 * AWS KMS Configuration
 *
 * Configures AWS KMS client for blockchain transaction signing
 *
 * Required AWS Setup:
 * 1. Create KMS key with KeySpec = ECC_SECG_P256K1 (for Bitcoin/Ethereum)
 * 2. Grant application IAM role permissions:
 *    - kms:Sign
 *    - kms:Verify
 *    - kms:GetPublicKey
 * 3. Configure AWS credentials (IAM role, environment variables, or credentials file)
 *
 * Environment Variables:
 * - AWS_REGION: AWS region (e.g., us-east-1)
 * - AWS_KMS_BLOCKCHAIN_KEY_ID: KMS key ID or ARN
 *
 * IAM Policy Example:
 * {
 *   "Version": "2012-10-17",
 *   "Statement": [
 *     {
 *       "Effect": "Allow",
 *       "Action": [
 *         "kms:Sign",
 *         "kms:Verify",
 *         "kms:GetPublicKey"
 *       ],
 *       "Resource": "arn:aws:kms:us-east-1:123456789012:key/12345678-1234-1234-1234-123456789012"
 *     }
 *   ]
 * }
 *
 * @author Waqiti Blockchain Team
 */
@Configuration
public class AwsKmsConfiguration {

    @Value("${aws.region}")
    private String awsRegion;

    @Bean
    public KmsClient kmsClient() {
        return KmsClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}

package com.waqiti.common.security.config;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * PRODUCTION JWT SECRET GENERATOR
 * 
 * This utility generates cryptographically secure JWT secrets suitable for production use.
 * 
 * Usage:
 * 1. Run this class to generate a new secret
 * 2. Set the generated secret as WAQITI_JWT_SECRET environment variable
 * 3. Never commit the generated secret to version control
 * 
 * Security Features:
 * - Uses SecureRandom for cryptographically secure randomness
 * - Generates 512-bit (64-byte) secrets for maximum security
 * - Base64 encoded for easy environment variable usage
 * - Includes validation of generated secret strength
 */
public class JwtSecretGenerator {
    
    private static final int SECRET_LENGTH_BYTES = 64; // 512 bits
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    public static void main(String[] args) {
        System.out.println("🔐 WAQITI JWT SECRET GENERATOR");
        System.out.println("==============================");
        System.out.println();

        String secret = generateSecureSecret();

        System.out.println("✅ JWT secret generated successfully");
        System.out.println();
        System.out.println("📋 DEPLOYMENT INSTRUCTIONS:");
        System.out.println("1. The secret has been generated and is ready for secure storage");
        System.out.println("2. For Docker: docker run -e WAQITI_JWT_SECRET='<paste-secret-here>' ...");
        System.out.println("3. For Kubernetes: kubectl create secret generic jwt-secret --from-literal=secret='<paste-secret-here>'");
        System.out.println("4. For HashiCorp Vault: vault kv put secret/waqiti/jwt secret='<paste-secret-here>'");
        System.out.println();
        System.out.println("⚠️  SECURITY WARNINGS:");
        System.out.println("- NEVER commit this secret to version control");
        System.out.println("- NEVER log this secret in application logs");
        System.out.println("- NEVER share this secret in plain text communications");
        System.out.println("- Rotate this secret regularly (recommended: every 90 days)");
        System.out.println("- This secret will NOT be displayed to prevent accidental exposure");
        System.out.println();
        System.out.println("📊 SECRET PROPERTIES:");
        System.out.println("- Length: " + SECRET_LENGTH_BYTES + " bytes (" + (SECRET_LENGTH_BYTES * 8) + " bits)");
        System.out.println("- Encoding: Base64");
        System.out.println("- Entropy: High (SecureRandom)");
        System.out.println("- Algorithm Compatibility: HS512, RS512, ES512");
        System.out.println();
        System.out.println("🔒 SECURE SECRET STORAGE:");
        System.out.println("To securely retrieve your generated secret, use one of the following methods:");
        System.out.println("1. Redirect output to a file: java JwtSecretGenerator > secret.txt");
        System.out.println("2. Pipe directly to vault: java JwtSecretGenerator | vault kv put secret/waqiti/jwt secret=-");
        System.out.println("3. Use environment variable: export WAQITI_JWT_SECRET=$(java JwtSecretGenerator --output-only)");
        System.out.println();
        System.out.println("Generated secret: [REDACTED FOR SECURITY - Use --output-only flag to retrieve]");

        // SECURITY: Only output secret if explicitly requested with --output-only flag
        if (args.length > 0 && "--output-only".equals(args[0])) {
            System.out.println();
            System.out.println("WAQITI_JWT_SECRET=" + secret);
        }
    }
    
    /**
     * Generate a cryptographically secure JWT secret
     */
    public static String generateSecureSecret() {
        byte[] secretBytes = new byte[SECRET_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(secretBytes);
        String secret = Base64.getEncoder().encodeToString(secretBytes);
        
        // Validate the generated secret
        validateSecretStrength(secret);
        
        return secret;
    }
    
    /**
     * Validate that the generated secret meets security requirements
     */
    private static void validateSecretStrength(String secret) {
        if (secret == null || secret.length() < 44) { // Base64 encoded 32 bytes = 44 chars minimum
            throw new IllegalStateException("Generated secret does not meet minimum length requirements");
        }
        
        try {
            byte[] decoded = Base64.getDecoder().decode(secret);
            if (decoded.length < 32) { // 256 bits minimum
                throw new IllegalStateException("Generated secret does not meet minimum entropy requirements");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Generated secret is not valid Base64", e);
        }
    }
    
    /**
     * Generate multiple secrets for key rotation
     */
    public static String[] generateSecretRotationSet(int count) {
        if (count < 1 || count > 10) {
            throw new IllegalArgumentException("Secret count must be between 1 and 10");
        }
        
        String[] secrets = new String[count];
        for (int i = 0; i < count; i++) {
            secrets[i] = generateSecureSecret();
        }
        return secrets;
    }
    
    /**
     * Validate an existing secret for security compliance
     */
    public static boolean validateExistingSecret(String secret) {
        try {
            if (secret == null || secret.trim().isEmpty()) {
                return false;
            }
            
            // Check for common weak patterns
            if (secret.toLowerCase().contains("password") ||
                secret.toLowerCase().contains("secret") ||
                secret.toLowerCase().contains("key") ||
                secret.toLowerCase().contains("token")) {
                return false;
            }
            
            // Check minimum length
            byte[] secretBytes;
            try {
                secretBytes = Base64.getDecoder().decode(secret);
            } catch (IllegalArgumentException e) {
                secretBytes = secret.getBytes();
            }
            
            return secretBytes.length >= 32; // 256 bits minimum
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Calculate entropy score of a secret (0-100)
     */
    public static int calculateEntropyScore(String secret) {
        if (secret == null || secret.isEmpty()) {
            return 0;
        }
        
        // Basic entropy calculation
        int uniqueChars = (int) secret.chars().distinct().count();
        int lengthScore = Math.min(secret.length() / 10, 10) * 10; // Max 100 for length
        int diversityScore = Math.min(uniqueChars / 10, 10) * 10; // Max 100 for diversity
        
        return Math.min((lengthScore + diversityScore) / 2, 100);
    }
}
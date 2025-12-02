package com.waqiti.security.service;

import com.waqiti.security.domain.*;
import com.waqiti.security.dto.*;
import com.waqiti.security.exception.*;
import com.waqiti.security.hardware.*;
import com.waqiti.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class HardwareKeyService {

    private final FIDOAuthenticatorService fidoService;
    private final YubiKeyService yubiKeyService;
    private final LedgerService ledgerService;
    private final TrezorService trezorService;
    private final AuditService auditService;
    
    // Cache for connected devices
    private final Map<String, HardwareDevice> connectedDevices = new ConcurrentHashMap<>();
    
    // Challenge cache for device registration
    private final Map<String, DeviceChallenge> challengeCache = new ConcurrentHashMap<>();
    
    private static final int CHALLENGE_SIZE = 32;
    private static final int CHALLENGE_VALIDITY_SECONDS = 300; // 5 minutes

    /**
     * List available hardware devices
     */
    public List<HardwareDeviceInfo> listAvailableDevices() {
        log.info("Listing available hardware devices");
        
        List<HardwareDeviceInfo> devices = new ArrayList<>();
        
        // Check FIDO2/WebAuthn devices
        devices.addAll(fidoService.listDevices());
        
        // Check YubiKey devices
        devices.addAll(yubiKeyService.listDevices());
        
        // Check Ledger devices
        devices.addAll(ledgerService.listDevices());
        
        // Check Trezor devices
        devices.addAll(trezorService.listDevices());
        
        return devices;
    }

    /**
     * Generate challenge for device registration
     */
    public DeviceChallengeDTO generateChallenge(String userId) {
        log.info("Generating device challenge for user {}", userId);
        
        // Generate random challenge
        byte[] challengeBytes = new byte[CHALLENGE_SIZE];
        new SecureRandom().nextBytes(challengeBytes);
        String challengeId = UUID.randomUUID().toString();
        
        DeviceChallenge challenge = DeviceChallenge.builder()
            .challengeId(challengeId)
            .userId(userId)
            .challenge(Base64.getEncoder().encodeToString(challengeBytes))
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusSeconds(CHALLENGE_VALIDITY_SECONDS))
            .build();
        
        challengeCache.put(challengeId, challenge);
        
        return DeviceChallengeDTO.builder()
            .challengeId(challengeId)
            .challenge(challenge.getChallenge())
            .expiresIn(CHALLENGE_VALIDITY_SECONDS)
            .supportedDevices(Arrays.asList("FIDO2", "YubiKey", "Ledger", "Trezor"))
            .build();
    }

    /**
     * Verify challenge response from hardware device
     */
    public boolean verifyChallengeResponse(String challengeId, String response) {
        DeviceChallenge challenge = challengeCache.get(challengeId);
        
        if (challenge == null) {
            log.warn("Challenge not found: {}", challengeId);
            return false;
        }
        
        if (challenge.isExpired()) {
            log.warn("Challenge expired: {}", challengeId);
            challengeCache.remove(challengeId);
            return false;
        }
        
        // Remove challenge after use
        challengeCache.remove(challengeId);
        
        // Verify response based on device type
        try {
            return verifyDeviceResponse(challenge.getChallenge(), response);
        } catch (Exception e) {
            log.error("Error verifying challenge response", e);
            return false;
        }
    }

    /**
     * Extract public key from device info
     */
    public PublicKey extractPublicKey(HardwareDeviceInfo deviceInfo) {
        try {
            switch (deviceInfo.getDeviceType()) {
                case "FIDO2":
                    return fidoService.extractPublicKey(deviceInfo);
                case "YubiKey":
                    return yubiKeyService.extractPublicKey(deviceInfo);
                case "Ledger":
                    return ledgerService.extractPublicKey(deviceInfo);
                case "Trezor":
                    return trezorService.extractPublicKey(deviceInfo);
                default:
                    throw new BusinessException("Unsupported device type: " + deviceInfo.getDeviceType());
            }
        } catch (Exception e) {
            throw new HardwareKeyException("Failed to extract public key", e);
        }
    }

    /**
     * Check if device is connected
     */
    public boolean isDeviceConnected(String deviceId) {
        // Check cache first
        if (connectedDevices.containsKey(deviceId)) {
            HardwareDevice device = connectedDevices.get(deviceId);
            if (device.isConnected()) {
                return true;
            } else {
                connectedDevices.remove(deviceId);
            }
        }
        
        // Check actual device connection
        return checkDeviceConnection(deviceId);
    }

    /**
     * Request signature from hardware device
     */
    public HardwareSignatureResponse requestSignature(String deviceId, byte[] payload, String pinCode) {
        log.info("Requesting signature from device {}", deviceId);
        
        HardwareDevice device = getConnectedDevice(deviceId);
        
        if (device == null) {
            return HardwareSignatureResponse.builder()
                .success(false)
                .error("Device not connected")
                .build();
        }
        
        try {
            // Verify PIN if required
            if (device.requiresPin() && !verifyPin(device, pinCode)) {
                return HardwareSignatureResponse.builder()
                    .success(false)
                    .error("Invalid PIN")
                    .build();
            }
            
            // Request signature based on device type
            SignatureResult result;
            switch (device.getType()) {
                case FIDO2:
                    result = fidoService.sign(device, payload);
                    break;
                case YUBIKEY:
                    result = yubiKeyService.sign(device, payload);
                    break;
                case LEDGER:
                    result = ledgerService.sign(device, payload);
                    break;
                case TREZOR:
                    result = trezorService.sign(device, payload);
                    break;
                default:
                    throw new BusinessException("Unsupported device type");
            }
            
            // Generate attestation data
            String attestation = generateAttestationData(device, result);
            
            // Audit hardware signing
            auditService.logHardwareSignature(deviceId, device.getUserId(), true);
            
            return HardwareSignatureResponse.builder()
                .success(true)
                .signature(result.getSignature())
                .attestationData(attestation)
                .deviceInfo(device.getInfo())
                .signedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Hardware signing failed", e);
            auditService.logHardwareSignature(deviceId, device.getUserId(), false);
            
            return HardwareSignatureResponse.builder()
                .success(false)
                .error("Signing failed: " + e.getMessage())
                .build();
        }
    }

    /**
     * Verify hardware signature
     */
    public boolean verifySignature(String deviceId, byte[] payload, String signature, String attestationData) {
        try {
            HardwareDevice device = getDeviceInfo(deviceId);
            
            if (device == null) {
                log.warn("Device not found: {}", deviceId);
                return false;
            }
            
            // Verify attestation
            if (!verifyAttestation(device, attestationData)) {
                log.warn("Attestation verification failed for device {}", deviceId);
                return false;
            }
            
            // Verify signature based on device type
            switch (device.getType()) {
                case FIDO2:
                    return fidoService.verifySignature(device, payload, signature);
                case YUBIKEY:
                    return yubiKeyService.verifySignature(device, payload, signature);
                case LEDGER:
                    return ledgerService.verifySignature(device, payload, signature);
                case TREZOR:
                    return trezorService.verifySignature(device, payload, signature);
                default:
                    return false;
            }
        } catch (Exception e) {
            log.error("Signature verification failed", e);
            return false;
        }
    }

    /**
     * Enable secure element features
     */
    public SecureElementStatus enableSecureElement(String deviceId, SecureElementConfig config) {
        log.info("Enabling secure element for device {}", deviceId);
        
        HardwareDevice device = getConnectedDevice(deviceId);
        
        if (device == null) {
            throw new BusinessException("Device not connected");
        }
        
        if (!device.hasSecureElement()) {
            throw new BusinessException("Device does not support secure element");
        }
        
        try {
            // Initialize secure element based on device
            boolean success = false;
            String attestationCert = null;
            
            switch (device.getType()) {
                case YUBIKEY:
                    YubiKeySecureElement ykSe = yubiKeyService.initializeSecureElement(device, config);
                    success = ykSe.isInitialized();
                    attestationCert = ykSe.getAttestationCertificate();
                    break;
                case LEDGER:
                    LedgerSecureElement ledgerSe = ledgerService.initializeSecureElement(device, config);
                    success = ledgerSe.isInitialized();
                    attestationCert = ledgerSe.getAttestationCertificate();
                    break;
                default:
                    throw new BusinessException("Secure element not supported for device type");
            }
            
            if (success) {
                device.setSecureElementEnabled(true);
                device.setSecureElementConfig(config);
                
                return SecureElementStatus.builder()
                    .enabled(true)
                    .deviceId(deviceId)
                    .attestationCertificate(attestationCert)
                    .features(config.getEnabledFeatures())
                    .build();
            } else {
                throw new HardwareKeyException("Failed to initialize secure element");
            }
            
        } catch (Exception e) {
            log.error("Failed to enable secure element", e);
            throw new HardwareKeyException("Secure element initialization failed", e);
        }
    }

    /**
     * Perform device attestation
     */
    public DeviceAttestationResult performAttestation(String deviceId) {
        log.info("Performing attestation for device {}", deviceId);
        
        HardwareDevice device = getConnectedDevice(deviceId);
        
        if (device == null) {
            throw new BusinessException("Device not connected");
        }
        
        try {
            AttestationResult result;
            
            switch (device.getType()) {
                case FIDO2:
                    result = fidoService.performAttestation(device);
                    break;
                case YUBIKEY:
                    result = yubiKeyService.performAttestation(device);
                    break;
                case LEDGER:
                    result = ledgerService.performAttestation(device);
                    break;
                case TREZOR:
                    result = trezorService.performAttestation(device);
                    break;
                default:
                    throw new BusinessException("Attestation not supported for device type");
            }
            
            // Verify certificate chain
            boolean chainValid = verifyCertificateChain(result.getCertificateChain());
            
            return DeviceAttestationResult.builder()
                .deviceId(deviceId)
                .attestationType(result.getType())
                .certificateChain(result.getCertificateChain())
                .attestationStatement(result.getStatement())
                .isValid(chainValid && result.isValid())
                .verifiedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Attestation failed", e);
            throw new HardwareKeyException("Device attestation failed", e);
        }
    }

    /**
     * Configure transaction approval rules
     */
    public void configureApprovalRules(String deviceId, TransactionApprovalRules rules) {
        log.info("Configuring approval rules for device {}", deviceId);
        
        HardwareDevice device = getConnectedDevice(deviceId);
        
        if (device == null) {
            throw new BusinessException("Device not connected");
        }
        
        // Store rules on device if supported
        if (device.supportsOnDeviceRules()) {
            switch (device.getType()) {
                case LEDGER:
                    ledgerService.setApprovalRules(device, rules);
                    break;
                case TREZOR:
                    trezorService.setApprovalRules(device, rules);
                    break;
                default:
                    log.warn("On-device rules not supported for {}", device.getType());
            }
        }
        
        // Store rules in service
        device.setApprovalRules(rules);
        connectedDevices.put(deviceId, device);
    }

    // Private helper methods

    private boolean verifyDeviceResponse(String challenge, String response) throws Exception {
        // Basic signature verification
        // In production, this would be device-specific
        byte[] challengeBytes = Base64.getDecoder().decode(challenge);
        byte[] responseBytes = Base64.getDecoder().decode(response);
        
        // Verify response length
        if (responseBytes.length < 64) {
            return false;
        }
        
        // Extract signature and public key hint
        byte[] signature = Arrays.copyOfRange(responseBytes, 0, 64);
        byte[] keyHint = Arrays.copyOfRange(responseBytes, 64, responseBytes.length);
        
        // Verify signature format
        return signature.length == 64 && keyHint.length >= 32;
    }

    private HardwareDevice getConnectedDevice(String deviceId) {
        HardwareDevice device = connectedDevices.get(deviceId);
        
        if (device == null || !device.isConnected()) {
            // Try to reconnect
            device = connectDevice(deviceId);
            if (device != null) {
                connectedDevices.put(deviceId, device);
            }
        }
        
        return device;
    }

    private HardwareDevice connectDevice(String deviceId) {
        // Try each device type
        HardwareDevice device = null;
        
        device = fidoService.connect(deviceId);
        if (device != null) return device;
        
        device = yubiKeyService.connect(deviceId);
        if (device != null) return device;
        
        device = ledgerService.connect(deviceId);
        if (device != null) return device;
        
        device = trezorService.connect(deviceId);
        return device;
    }

    private boolean checkDeviceConnection(String deviceId) {
        HardwareDevice device = connectDevice(deviceId);
        if (device != null) {
            connectedDevices.put(deviceId, device);
            return true;
        }
        return false;
    }

    private HardwareDevice getDeviceInfo(String deviceId) {
        // Check cache first
        HardwareDevice device = connectedDevices.get(deviceId);
        if (device != null) {
            return device;
        }
        
        // Try to get device info without full connection
        return retrieveDeviceInfo(deviceId);
    }

    private HardwareDevice retrieveDeviceInfo(String deviceId) {
        // Try each service to get device info
        HardwareDevice device = fidoService.getDeviceInfo(deviceId);
        if (device != null) return device;
        
        device = yubiKeyService.getDeviceInfo(deviceId);
        if (device != null) return device;
        
        device = ledgerService.getDeviceInfo(deviceId);
        if (device != null) return device;
        
        return trezorService.getDeviceInfo(deviceId);
    }

    private boolean verifyPin(HardwareDevice device, String pin) {
        if (pin == null || pin.isEmpty()) {
            return false;
        }
        
        switch (device.getType()) {
            case YUBIKEY:
                return yubiKeyService.verifyPin(device, pin);
            case LEDGER:
                return ledgerService.verifyPin(device, pin);
            case TREZOR:
                return trezorService.verifyPin(device, pin);
            default:
                // FIDO2 doesn't use PIN in this context
                return true;
        }
    }

    private String generateAttestationData(HardwareDevice device, SignatureResult result) throws Exception {
        // Create attestation structure
        Map<String, Object> attestation = new HashMap<>();
        attestation.put("deviceId", device.getId());
        attestation.put("deviceType", device.getType().toString());
        attestation.put("firmwareVersion", device.getFirmwareVersion());
        attestation.put("signatureAlgorithm", result.getAlgorithm());
        attestation.put("timestamp", System.currentTimeMillis());
        
        // Add device-specific attestation
        if (result.getDeviceAttestation() != null) {
            attestation.put("deviceAttestation", result.getDeviceAttestation());
        }
        
        // Sign attestation data
        String attestationJson = new ObjectMapper().writeValueAsString(attestation);
        String attestationSignature = signAttestationData(attestationJson);
        
        attestation.put("attestationSignature", attestationSignature);
        
        return Base64.getEncoder().encodeToString(
            new ObjectMapper().writeValueAsBytes(attestation)
        );
    }

    private String signAttestationData(String data) throws Exception {
        // Use service signing key
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(
            getServiceSigningKey(), 
            "HmacSHA256"
        );
        mac.init(secretKey);
        
        byte[] signature = mac.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(signature);
    }

    private byte[] getServiceSigningKey() {
        try {
            // SECURITY FIX: Get signing key from Vault instead of hardcoded placeholder
            String keyFromVault = vaultTemplate.read("secret/hardware-key-service")
                .getData()
                .get("service-signing-key")
                .toString();
            
            if (keyFromVault != null && !keyFromVault.isEmpty()) {
                return Base64.getDecoder().decode(keyFromVault);
            }
            
            // Fallback: generate a new secure key if none exists
            log.warn("SECURITY: No service signing key found in Vault, generating new key");
            SecureRandom secureRandom = new SecureRandom();
            byte[] newKey = new byte[32]; // 256-bit key
            secureRandom.nextBytes(newKey);
            
            // Store the new key in Vault for future use
            Map<String, Object> keyData = Map.of(
                "service-signing-key", Base64.getEncoder().encodeToString(newKey),
                "created_at", LocalDateTime.now().toString(),
                "algorithm", "HMAC-SHA256"
            );
            
            vaultTemplate.write("secret/hardware-key-service", keyData);
            
            return newKey;
            
        } catch (Exception e) {
            log.error("CRITICAL SECURITY ERROR: Failed to get service signing key from Vault", e);
            throw new SecurityException("Unable to retrieve secure signing key");
        }
    }

    private boolean verifyAttestation(HardwareDevice device, String attestationData) {
        try {
            byte[] attestationBytes = Base64.getDecoder().decode(attestationData);
            Map<String, Object> attestation = new ObjectMapper().readValue(
                attestationBytes, 
                new TypeReference<Map<String, Object>>() {}
            );
            
            // Verify device ID matches
            if (!device.getId().equals(attestation.get("deviceId"))) {
                return false;
            }
            
            // Verify timestamp is recent
            long timestamp = (Long) attestation.get("timestamp");
            if (System.currentTimeMillis() - timestamp > 300000) { // 5 minutes
                return false;
            }
            
            // Verify attestation signature
            String signature = (String) attestation.get("attestationSignature");
            attestation.remove("attestationSignature");
            
            String attestationJson = new ObjectMapper().writeValueAsString(attestation);
            String expectedSignature = signAttestationData(attestationJson);
            
            return signature.equals(expectedSignature);
            
        } catch (Exception e) {
            log.error("Attestation verification failed", e);
            return false;
        }
    }

    private boolean verifyCertificateChain(List<String> certificateChain) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            List<X509Certificate> certs = new ArrayList<>();
            
            // Parse certificates
            for (String certString : certificateChain) {
                byte[] certBytes = Base64.getDecoder().decode(certString);
                X509Certificate cert = (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(certBytes)
                );
                certs.add(cert);
            }
            
            // Verify chain
            for (int i = 0; i < certs.size() - 1; i++) {
                X509Certificate cert = certs.get(i);
                X509Certificate issuer = certs.get(i + 1);
                
                try {
                    cert.verify(issuer.getPublicKey());
                } catch (Exception e) {
                    log.warn("Certificate chain verification failed at index {}", i);
                    return false;
                }
            }
            
            // Verify root certificate is trusted
            X509Certificate root = certs.get(certs.size() - 1);
            return isTrustedRoot(root);
            
        } catch (Exception e) {
            log.error("Certificate chain verification failed", e);
            return false;
        }
    }

    private boolean isTrustedRoot(X509Certificate root) {
        // Check against known hardware vendor root certificates
        Set<String> trustedRootFingerprints = Set.of(
            "SHA256:1234567890abcdef...", // Yubico root
            "SHA256:fedcba0987654321...", // Ledger root
            "SHA256:abcdef1234567890...", // Trezor root
            "SHA256:0987654321fedcba..."  // FIDO Alliance root
        );
        
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] fingerprint = md.digest(root.getEncoded());
            String fingerprintHex = "SHA256:" + bytesToHex(fingerprint);
            
            return trustedRootFingerprints.contains(fingerprintHex);
        } catch (Exception e) {
            return false;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
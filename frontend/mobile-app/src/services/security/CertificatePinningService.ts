/**
 * Certificate Pinning Service
 * Implements certificate pinning for enhanced security in the Waqiti mobile app
 * Prevents man-in-the-middle attacks by validating server certificates
 */

import { Platform, NativeModules } from 'react-native';
import CryptoJS from 'crypto-js';
import NetInfo from '@react-native-community/netinfo';
import DeviceInfo from 'react-native-device-info';
import { SecureStorageService } from '../biometric/SecureStorageService';
import { NativeCertificatePinning } from './NativeCertificatePinning';

// Native module for certificate pinning
const { WQTCertificatePinning } = NativeModules;

interface CertificatePin {
  hostname: string;
  pins: string[];
  backupPins: string[];
  expiresAt: number;
  enforced: boolean;
}

interface PinningConfig {
  enabled: boolean;
  enforceMode: 'strict' | 'report' | 'disabled';
  maxAge: number;
  includeSubdomains: boolean;
  reportUri?: string;
  certificatePins: CertificatePin[];
}

interface CertificateValidationResult {
  valid: boolean;
  hostname: string;
  matchedPin?: string;
  error?: string;
  reportingData?: any;
}

interface SecurityReport {
  timestamp: number;
  hostname: string;
  failureReason: string;
  certificateChain: string[];
  deviceInfo: any;
  networkInfo: any;
}

export class CertificatePinningService {
  private static instance: CertificatePinningService;
  private config: PinningConfig;
  private storageService: SecureStorageService;
  private validationCache: Map<string, CertificateValidationResult> = new Map();
  private reportQueue: SecurityReport[] = [];
  private isInitialized: boolean = false;

  // Production certificate pins for Waqiti services
  // These will be replaced with actual pins during deployment
  private readonly DEFAULT_PINS: CertificatePin[] = [
    {
      hostname: 'api.example.com',
      pins: [
        // Primary certificate pin (SHA-256 of SubjectPublicKeyInfo)
        // AWS ACM Root CA 1
        'sha256/++MBgDH5WGvL9Bcn5Be30cRcL0f5O+NyoXuWtQdX1aI=',
        // Amazon Root CA 1
        'sha256/9+ze1cZgR9KO1kZrVDxA4HQ6voHRCSVNz4RdTCx4U8U='
      ],
      backupPins: [
        // Backup pins for certificate rotation
        // Amazon Root CA 2
        'sha256/f0KW/FtqTjs108NpYj42SrGvOB2PpxIVM8nWxjPqJGE=',
        // Amazon Root CA 3
        'sha256/NqvDJlas/GRcYbcWE8S/IceH9cq77kg0jVhZeAPXq8k='
      ],
      expiresAt: Date.now() + (365 * 24 * 60 * 60 * 1000), // 1 year
      enforced: true
    },
    {
      hostname: 'auth.example.com',
      pins: [
        'sha256/++MBgDH5WGvL9Bcn5Be30cRcL0f5O+NyoXuWtQdX1aI=',
        'sha256/9+ze1cZgR9KO1kZrVDxA4HQ6voHRCSVNz4RdTCx4U8U='
      ],
      backupPins: [
        'sha256/f0KW/FtqTjs108NpYj42SrGvOB2PpxIVM8nWxjPqJGE=',
        'sha256/NqvDJlas/GRcYbcWE8S/IceH9cq77kg0jVhZeAPXq8k='
      ],
      expiresAt: Date.now() + (365 * 24 * 60 * 60 * 1000),
      enforced: true
    },
    {
      hostname: 'payments.example.com',
      pins: [
        'sha256/++MBgDH5WGvL9Bcn5Be30cRcL0f5O+NyoXuWtQdX1aI=',
        'sha256/9+ze1cZgR9KO1kZrVDxA4HQ6voHRCSVNz4RdTCx4U8U='
      ],
      backupPins: [
        'sha256/f0KW/FtqTjs108NpYj42SrGvOB2PpxIVM8nWxjPqJGE=',
        'sha256/NqvDJlas/GRcYbcWE8S/IceH9cq77kg0jVhZeAPXq8k='
      ],
      expiresAt: Date.now() + (365 * 24 * 60 * 60 * 1000),
      enforced: true
    },
    // CloudFlare pins for CDN content
    {
      hostname: 'cdn.example.com',
      pins: [
        // CloudFlare Origin CA
        'sha256/DivxlM2cNsXEei31YfRXJLZW2yKp5AySPvTQ2fn6SLg=',
        // Baltimore CyberTrust Root
        'sha256/Y9mvm0exBk1JoQ57f9Vm28jKo5lFm/woKcVxrYxu80o='
      ],
      backupPins: [
        // DigiCert Global Root CA
        'sha256/WoiWRyIOVNa9ihaBciRSC7XHjliYS9VwUGOIud4PB18=',
        // Let's Encrypt Authority X3
        'sha256/YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg='
      ],
      expiresAt: Date.now() + (365 * 24 * 60 * 60 * 1000),
      enforced: true
    }
  ];

  private constructor() {
    this.storageService = SecureStorageService.getInstance();
    this.config = {
      enabled: true,
      enforceMode: 'strict',
      maxAge: 60 * 60 * 24 * 30, // 30 days
      includeSubdomains: true,
      reportUri: 'https://security.example.com/pinning-report',
      certificatePins: this.DEFAULT_PINS
    };
  }

  public static getInstance(): CertificatePinningService {
    if (!CertificatePinningService.instance) {
      CertificatePinningService.instance = new CertificatePinningService();
    }
    return CertificatePinningService.instance;
  }

  /**
   * Initialize certificate pinning service
   */
  public async initialize(): Promise<void> {
    try {
      console.log('Initializing certificate pinning service');

      // Initialize native certificate pinning if available
      if (WQTCertificatePinning && WQTCertificatePinning.initialize) {
        await WQTCertificatePinning.initialize({
          pins: this.DEFAULT_PINS,
          enforceMode: this.config.enforceMode,
          reportUri: this.config.reportUri
        });
        console.log('Native certificate pinning initialized');
      }

      // Load stored configuration
      await this.loadConfiguration();

      // Update pins from remote if needed
      await this.updatePinsFromRemote();

      // Set up periodic pin updates
      this.schedulePeriodicPinUpdates();

      // Process any pending security reports
      await this.processPendingReports();

      // Configure network interceptor for automatic pinning
      await this.configureNetworkInterceptor();

      this.isInitialized = true;
      console.log('Certificate pinning service initialized successfully');
    } catch (error) {
      console.error('Failed to initialize certificate pinning:', error);
      throw error;
    }
  }

  /**
   * Validate certificate for a given hostname
   */
  public async validateCertificate(
    hostname: string,
    certificateChain: string[]
  ): Promise<CertificateValidationResult> {
    try {
      // Check if pinning is enabled
      if (!this.config.enabled || this.config.enforceMode === 'disabled') {
        return { valid: true, hostname };
      }

      // Check cache first
      const cacheKey = `${hostname}:${certificateChain[0]}`;
      const cached = this.validationCache.get(cacheKey);
      if (cached && cached.timestamp > Date.now() - 300000) { // 5 minute cache
        return cached;
      }

      // Find pins for hostname
      const pinConfig = this.findPinConfiguration(hostname);
      if (!pinConfig) {
        // No pins configured for this hostname
        return { valid: true, hostname };
      }

      // Check if pins are expired
      if (pinConfig.expiresAt < Date.now()) {
        console.warn(`Certificate pins expired for ${hostname}`);
        await this.reportPinningFailure(hostname, 'PINS_EXPIRED', certificateChain);
        
        if (this.config.enforceMode === 'strict') {
          return {
            valid: false,
            hostname,
            error: 'Certificate pins expired'
          };
        }
      }

      // Validate certificate chain
      const validationResult = await this.validateCertificateChain(
        certificateChain,
        pinConfig
      );

      // Cache result
      this.validationCache.set(cacheKey, {
        ...validationResult,
        timestamp: Date.now()
      });

      // Report failure if validation failed
      if (!validationResult.valid && this.config.reportUri) {
        await this.reportPinningFailure(
          hostname,
          validationResult.error || 'VALIDATION_FAILED',
          certificateChain
        );
      }

      return validationResult;
    } catch (error) {
      console.error('Certificate validation error:', error);
      
      // In case of error, fail based on enforce mode
      if (this.config.enforceMode === 'strict') {
        return {
          valid: false,
          hostname,
          error: 'Certificate validation error'
        };
      } else {
        return { valid: true, hostname };
      }
    }
  }

  /**
   * Validate certificate chain against pins
   */
  private async validateCertificateChain(
    certificateChain: string[],
    pinConfig: CertificatePin
  ): Promise<CertificateValidationResult> {
    try {
      // Extract SPKI (Subject Public Key Info) from certificates
      const spkiHashes = await this.extractSPKIHashes(certificateChain);

      // Check primary pins
      for (const pin of pinConfig.pins) {
        if (spkiHashes.includes(pin)) {
          return {
            valid: true,
            hostname: pinConfig.hostname,
            matchedPin: pin
          };
        }
      }

      // Check backup pins
      for (const pin of pinConfig.backupPins) {
        if (spkiHashes.includes(pin)) {
          console.warn(`Matched backup pin for ${pinConfig.hostname}`);
          return {
            valid: true,
            hostname: pinConfig.hostname,
            matchedPin: pin
          };
        }
      }

      // No matching pins found
      return {
        valid: false,
        hostname: pinConfig.hostname,
        error: 'No matching certificate pins'
      };
    } catch (error) {
      console.error('Certificate chain validation error:', error);
      return {
        valid: false,
        hostname: pinConfig.hostname,
        error: 'Failed to validate certificate chain'
      };
    }
  }

  /**
   * Extract SPKI hashes from certificate chain
   */
  private async extractSPKIHashes(certificateChain: string[]): Promise<string[]> {
    try {
      // Use native module for better performance and accuracy
      if (WQTCertificatePinning && WQTCertificatePinning.extractSPKIHashes) {
        return await WQTCertificatePinning.extractSPKIHashes(certificateChain);
      }
      
      // Fallback to JavaScript implementation
      const hashes: string[] = [];

      for (const cert of certificateChain) {
        try {
          // Extract SPKI from certificate
          const spki = await this.extractSPKIFromCertificate(cert);
          
          // Calculate SHA-256 hash
          const hash = CryptoJS.SHA256(spki);
          const base64Hash = CryptoJS.enc.Base64.stringify(hash);
          
          hashes.push(`sha256/${base64Hash}`);
        } catch (error) {
          console.error('Failed to extract SPKI hash:', error);
        }
      }

      return hashes;
    } catch (error) {
      console.error('Failed to extract SPKI hashes:', error);
      return [];
    }
  }

  /**
   * Extract SPKI from X.509 certificate
   */
  private async extractSPKIFromCertificate(certificate: string): Promise<string> {
    try {
      // Use native module for proper SPKI extraction
      if (WQTCertificatePinning && WQTCertificatePinning.extractSPKI) {
        return await WQTCertificatePinning.extractSPKI(certificate);
      }
      
      // Fallback to JavaScript implementation
      // Remove PEM headers
      const base64Cert = certificate
        .replace(/-----BEGIN CERTIFICATE-----/g, '')
        .replace(/-----END CERTIFICATE-----/g, '')
        .replace(/\s/g, '');

      // For production, this should use a proper ASN.1 parser
      // This is a placeholder that would need proper implementation
      const certData = CryptoJS.enc.Base64.parse(base64Cert);
      
      // In real implementation, parse ASN.1 structure to extract SPKI
      // This requires proper DER decoding
      console.warn('Using fallback SPKI extraction - implement proper ASN.1 parsing');
      return certData.toString();
    } catch (error) {
      console.error('Failed to extract SPKI:', error);
      throw error;
    }
  }

  /**
   * Find pin configuration for hostname
   */
  private findPinConfiguration(hostname: string): CertificatePin | null {
    // Direct match
    const directMatch = this.config.certificatePins.find(
      pin => pin.hostname === hostname
    );
    if (directMatch) return directMatch;

    // Subdomain match if includeSubdomains is enabled
    if (this.config.includeSubdomains) {
      return this.config.certificatePins.find(pin => {
        const domain = pin.hostname.startsWith('*.') 
          ? pin.hostname.substring(2) 
          : pin.hostname;
        return hostname.endsWith(domain);
      }) || null;
    }

    return null;
  }

  /**
   * Report pinning failure
   */
  private async reportPinningFailure(
    hostname: string,
    failureReason: string,
    certificateChain: string[]
  ): Promise<void> {
    try {
      const report: SecurityReport = {
        timestamp: Date.now(),
        hostname,
        failureReason,
        certificateChain,
        deviceInfo: {
          platform: Platform.OS,
          version: Platform.Version,
          deviceId: await DeviceInfo.getUniqueId(),
          appVersion: DeviceInfo.getVersion(),
          buildNumber: DeviceInfo.getBuildNumber()
        },
        networkInfo: await NetInfo.fetch()
      };

      // Add to report queue
      this.reportQueue.push(report);

      // Send report if online
      if (report.networkInfo.isConnected) {
        await this.sendSecurityReport(report);
      } else {
        // Store for later sending
        await this.storeReportForLater(report);
      }
    } catch (error) {
      console.error('Failed to report pinning failure:', error);
    }
  }

  /**
   * Send security report to server
   */
  private async sendSecurityReport(report: SecurityReport): Promise<void> {
    if (!this.config.reportUri) return;

    try {
      const response = await fetch(this.config.reportUri, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Pin-Report': 'true'
        },
        body: JSON.stringify(report)
      });

      if (!response.ok) {
        throw new Error(`Report failed: ${response.status}`);
      }

      console.log('Security report sent successfully');
    } catch (error) {
      console.error('Failed to send security report:', error);
      await this.storeReportForLater(report);
    }
  }

  /**
   * Store report for later sending
   */
  private async storeReportForLater(report: SecurityReport): Promise<void> {
    try {
      const storedReports = await this.storageService.getItem('pending_pin_reports') || [];
      storedReports.push(report);
      
      // Keep only last 100 reports
      const trimmedReports = storedReports.slice(-100);
      await this.storageService.setItem('pending_pin_reports', trimmedReports);
    } catch (error) {
      console.error('Failed to store report:', error);
    }
  }

  /**
   * Process pending security reports
   */
  private async processPendingReports(): Promise<void> {
    try {
      const reports = await this.storageService.getItem('pending_pin_reports') || [];
      
      for (const report of reports) {
        await this.sendSecurityReport(report);
      }

      // Clear processed reports
      await this.storageService.removeItem('pending_pin_reports');
    } catch (error) {
      console.error('Failed to process pending reports:', error);
    }
  }

  /**
   * Load configuration from storage
   */
  private async loadConfiguration(): Promise<void> {
    try {
      const storedConfig = await this.storageService.getItem('certificate_pinning_config');
      if (storedConfig) {
        this.config = { ...this.config, ...storedConfig };
      }
    } catch (error) {
      console.error('Failed to load pinning configuration:', error);
    }
  }

  /**
   * Update pins from remote configuration
   */
  private async updatePinsFromRemote(): Promise<void> {
    try {
      // Only update if connected
      const netInfo = await NetInfo.fetch();
      if (!netInfo.isConnected) return;

      const response = await fetch('https://config.example.com/certificate-pins', {
        method: 'GET',
        headers: {
          'X-App-Version': DeviceInfo.getVersion(),
          'X-Platform': Platform.OS
        }
      });

      if (response.ok) {
        const remotePins = await response.json();
        
        // Validate remote pins
        if (this.validateRemotePins(remotePins)) {
          this.config.certificatePins = remotePins.pins;
          await this.storageService.setItem('certificate_pinning_config', this.config);
          console.log('Certificate pins updated from remote');
        }
      }
    } catch (error) {
      console.error('Failed to update pins from remote:', error);
    }
  }

  /**
   * Validate remote pin configuration
   */
  private validateRemotePins(remotePins: any): boolean {
    if (!remotePins || !Array.isArray(remotePins.pins)) return false;

    for (const pin of remotePins.pins) {
      if (!pin.hostname || !Array.isArray(pin.pins) || !pin.expiresAt) {
        return false;
      }
    }

    return true;
  }

  /**
   * Schedule periodic pin updates
   */
  private schedulePeriodicPinUpdates(): void {
    // Update pins every 24 hours
    setInterval(async () => {
      await this.updatePinsFromRemote();
    }, 24 * 60 * 60 * 1000);
  }

  /**
   * Clear validation cache
   */
  public clearCache(): void {
    this.validationCache.clear();
  }

  /**
   * Get current configuration
   */
  public getConfiguration(): PinningConfig {
    return { ...this.config };
  }

  /**
   * Update enforcement mode
   */
  public async setEnforcementMode(mode: 'strict' | 'report' | 'disabled'): Promise<void> {
    this.config.enforceMode = mode;
    await this.storageService.setItem('certificate_pinning_config', this.config);
  }

  /**
   * Check if hostname has valid pins
   */
  public hasValidPins(hostname: string): boolean {
    const pinConfig = this.findPinConfiguration(hostname);
    return pinConfig !== null && pinConfig.expiresAt > Date.now();
  }

  /**
   * Get pin expiration for hostname
   */
  public getPinExpiration(hostname: string): number | null {
    const pinConfig = this.findPinConfiguration(hostname);
    return pinConfig ? pinConfig.expiresAt : null;
  }

  /**
   * Test certificate pinning
   */
  public async testPinning(hostname: string): Promise<{
    success: boolean;
    error?: string;
    details?: any;
  }> {
    try {
      // Perform test request to validate pinning
      const response = await fetch(`https://${hostname}/health`, {
        method: 'GET'
      });

      return {
        success: response.ok,
        details: {
          status: response.status,
          hostname
        }
      };
    } catch (error) {
      return {
        success: false,
        error: error.message,
        details: { hostname }
      };
    }
  }
}

  /**
   * Configure network interceptor for automatic certificate validation
   */
  private async configureNetworkInterceptor(): Promise<void> {
    try {
      // Use native module to intercept all HTTPS requests
      if (WQTCertificatePinning && WQTCertificatePinning.configureInterceptor) {
        await WQTCertificatePinning.configureInterceptor({
          enabled: this.config.enabled,
          domains: this.config.certificatePins.map(pin => pin.hostname),
          validationCallback: 'certificateValidationCallback'
        });
      }
    } catch (error) {
      console.error('Failed to configure network interceptor:', error);
    }
  }

  /**
   * Disable certificate pinning (for debugging only)
   */
  public async disable(): Promise<void> {
    console.warn('Disabling certificate pinning - this should only be used for debugging!');
    this.config.enabled = false;
    
    if (WQTCertificatePinning && WQTCertificatePinning.disable) {
      await WQTCertificatePinning.disable();
    }
  }

  /**
   * Re-enable certificate pinning
   */
  public async enable(): Promise<void> {
    this.config.enabled = true;
    
    if (WQTCertificatePinning && WQTCertificatePinning.enable) {
      await WQTCertificatePinning.enable();
    }
  }

  /**
   * Add runtime pin for a specific domain
   */
  public async addPin(pin: CertificatePin): Promise<void> {
    // Validate pin
    if (!pin.hostname || !pin.pins || pin.pins.length === 0) {
      throw new Error('Invalid certificate pin configuration');
    }

    // Add to configuration
    this.config.certificatePins.push(pin);
    
    // Update native module
    if (WQTCertificatePinning && WQTCertificatePinning.addPin) {
      await WQTCertificatePinning.addPin(pin);
    }
    
    // Save configuration
    await this.storageService.setItem('certificate_pinning_config', this.config);
  }

  /**
   * Remove pin for a specific domain
   */
  public async removePin(hostname: string): Promise<void> {
    // Remove from configuration
    this.config.certificatePins = this.config.certificatePins.filter(
      pin => pin.hostname !== hostname
    );
    
    // Update native module
    if (WQTCertificatePinning && WQTCertificatePinning.removePin) {
      await WQTCertificatePinning.removePin(hostname);
    }
    
    // Save configuration
    await this.storageService.setItem('certificate_pinning_config', this.config);
  }

  /**
   * Get certificate chain for a URL (for debugging)
   */
  public async getCertificateChain(url: string): Promise<string[]> {
    if (WQTCertificatePinning && WQTCertificatePinning.getCertificateChain) {
      return await WQTCertificatePinning.getCertificateChain(url);
    }
    
    throw new Error('Certificate chain retrieval not supported');
  }
}

export default CertificatePinningService;
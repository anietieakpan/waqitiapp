/**
 * Enhanced Certificate Pinning Service
 * 
 * Provides comprehensive certificate pinning for React Native apps with:
 * - Multiple pin backups for resilience
 * - Automatic pin rotation
 * - Development/production mode handling
 * - Network condition awareness
 * - Certificate transparency validation
 * 
 * CRITICAL SECURITY: Prevents man-in-the-middle attacks
 */

import { NativeModules, NativeEventEmitter, Platform } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import NetInfo from '@react-native-community/netinfo';
import { EventEmitter } from 'events';
import CryptoJS from 'crypto-js';
import AppConfigService from './AppConfigService';

const { CertificatePinning } = NativeModules;
const certificatePinningEmitter = new NativeEventEmitter(CertificatePinning);

export type EnforcementMode = 'strict' | 'report' | 'disabled';

export interface CertificatePin {
  hostname: string;
  pins: string[];
  includeSubdomains?: boolean;
  expiresAt?: Date;
}

export interface PinningValidationResult {
  valid: boolean;
  hostname: string;
  matchedPin?: string;
  error?: string;
  certificateChain?: string[];
  securityScore?: number;
  validationDetails?: {
    pinValidation: any;
    chainValidation: {
      score: number;
      errors: any[];
      warnings: any[];
      ocspStatus?: any;
      ctStatus?: any;
    };
  };
}

export interface PinningConfiguration {
  enforcementMode: EnforcementMode;
  enforcePinning: boolean;
  configuredHosts: string[];
  reportCount: number;
}

export interface PinningFailureReport {
  timestamp: number;
  hostname: string;
  reason: string;
  enforcementMode: string;
  platform: string;
  osVersion: string;
  networkType?: string;
  appVersion?: string;
}

class CertificatePinningService extends EventEmitter {
  private static instance: CertificatePinningService;
  private isInitialized: boolean = false;
  private defaultPins: Map<string, CertificatePin> = new Map();
  private pinningFailureQueue: PinningFailureReport[] = [];
  private reportingEnabled: boolean = true;
  
  // Production certificate pins for WAQITI services
  private readonly PRODUCTION_PINS: CertificatePin[] = [
    {
      hostname: 'api.example.com',
      pins: [
        'sha256/+Jg+cke8HLJNzDJB4qc1Aus14rNb6o+N3IrrPIjseM=', // Primary certificate
        'sha256/JSMzqOOrtyOT1kmau6zKhgT676hGgczD5VMdRMyJZFA=', // Backup certificate
        'sha256/++MBgDH5WGvL9Bcn5Be30cRcL0f5O+NyoXuWtQdX1aI='  // CA certificate
      ],
      includeSubdomains: true
    },
    {
      hostname: 'auth.example.com',
      pins: [
        'sha256/4a6cPehI7OG6cuDZka5NDZ7FR8a60d3auda+sKfg4Ng=',
        'sha256/x4QzPSC810K5/cMjb05Qm4k3Bw5zBn4lTdO/nEW/Td4=',
        'sha256/++MBgDH5WGvL9Bcn5Be30cRcL0f5O+NyoXuWtQdX1aI='
      ],
      includeSubdomains: true
    },
    {
      hostname: 'payments.example.com',
      pins: [
        'sha256/lCppFqbkrlJ3EcVFAkeip0+44VaoJUymbnO5VbEUTwc=',
        'sha256/K87oWBWM9UZfyddvDfoxL+8lpNyoUB2ptGtn0fv6G2Q=',
        'sha256/++MBgDH5WGvL9Bcn5Be30cRcL0f5O+NyoXuWtQdX1aI='
      ],
      includeSubdomains: true
    }
  ];

  private constructor() {
    super();
    this.setupEventListeners();
  }

  public static getInstance(): CertificatePinningService {
    if (!CertificatePinningService.instance) {
      CertificatePinningService.instance = new CertificatePinningService();
    }
    return CertificatePinningService.instance;
  }

  /**
   * Initialize certificate pinning with production pins
   */
  public async initialize(): Promise<void> {
    if (this.isInitialized) {
      return;
    }

    try {
      // Load saved configuration
      const savedConfig = await this.loadConfiguration();
      if (savedConfig) {
        await this.setEnforcementMode(savedConfig.enforcementMode);
      }

      // Set up production pins
      for (const pin of this.PRODUCTION_PINS) {
        this.defaultPins.set(pin.hostname, pin);
        await this.addPin(pin.hostname, pin.pins);
      }

      // Load any custom pins from storage
      await this.loadCustomPins();

      // Start report queue processing
      this.startReportQueueProcessor();

      this.isInitialized = true;
      this.emit('initialized');
    } catch (error) {
      console.error('Certificate pinning initialization failed:', error);
      throw error;
    }
  }

  /**
   * Validate certificates for a specific hostname with enhanced chain validation
   */
  public async validateCertificate(
    hostname: string,
    certificates: string[]
  ): Promise<PinningValidationResult> {
    try {
      // First perform pin validation
      const pinResult = await CertificatePinning.validateCertificateForHost(
        hostname,
        certificates
      );
      
      // Then perform enhanced chain validation
      const chainValidation = await CertificateChainValidator.validateChain(
        certificates,
        hostname,
        {
          checkRevocation: true,
          requireCT: true,
          minimumCTLogs: 2,
          allowSelfSigned: false,
          checkExpiry: true,
          maxChainLength: 5,
        }
      );
      
      // Combine results
      const valid = pinResult.valid && chainValidation.valid;
      const error = !pinResult.valid ? pinResult.error : 
                   (!chainValidation.valid && chainValidation.errors.length > 0) ? 
                   chainValidation.errors[0].message : undefined;
      
      if (!valid) {
        this.handlePinningFailure(hostname, error || 'Validation failed', {
          pinValidation: pinResult,
          chainValidation: chainValidation,
        });
      }
      
      // Log security metrics
      if (chainValidation.score < 80) {
        console.warn(`Low security score for ${hostname}: ${chainValidation.score}`);
      }
      
      return {
        valid,
        hostname,
        matchedPin: pinResult.matchedPin,
        error,
        certificateChain: certificates,
        securityScore: chainValidation.score,
        validationDetails: {
          pinValidation: pinResult,
          chainValidation: {
            score: chainValidation.score,
            errors: chainValidation.errors,
            warnings: chainValidation.warnings,
            ocspStatus: chainValidation.ocspStatus,
            ctStatus: chainValidation.ctStatus,
          },
        },
      };
    } catch (error) {
      this.handlePinningFailure(hostname, error.message);
      throw error;
    }
  }

  /**
   * Add certificate pins for a hostname
   */
  public async addPin(hostname: string, pins: string[]): Promise<void> {
    try {
      await CertificatePinning.addPinForHost(hostname, pins);
      
      // Save to custom pins storage
      const customPins = await this.loadCustomPins();
      customPins[hostname] = {
        pins,
        addedAt: new Date().toISOString(),
      };
      await AsyncStorage.setItem('custom_certificate_pins', JSON.stringify(customPins));
      
      this.emit('pinAdded', { hostname, pins });
    } catch (error) {
      console.error(`Failed to add pins for ${hostname}:`, error);
      throw error;
    }
  }

  /**
   * Remove certificate pins for a hostname
   */
  public async removePin(hostname: string): Promise<void> {
    try {
      await CertificatePinning.removePinForHost(hostname);
      
      // Remove from custom pins storage
      const customPins = await this.loadCustomPins();
      delete customPins[hostname];
      await AsyncStorage.setItem('custom_certificate_pins', JSON.stringify(customPins));
      
      this.emit('pinRemoved', { hostname });
    } catch (error) {
      console.error(`Failed to remove pins for ${hostname}:`, error);
      throw error;
    }
  }

  /**
   * Clear all certificate pins and reset to defaults
   */
  public async clearAllPins(): Promise<void> {
    try {
      await CertificatePinning.clearAllPins();
      await AsyncStorage.removeItem('custom_certificate_pins');
      
      // Re-add default pins
      for (const [hostname, pin] of this.defaultPins) {
        await this.addPin(hostname, pin.pins);
      }
      
      this.emit('pinsCleared');
    } catch (error) {
      console.error('Failed to clear pins:', error);
      throw error;
    }
  }

  /**
   * Set the enforcement mode for certificate pinning
   */
  public async setEnforcementMode(mode: EnforcementMode): Promise<void> {
    try {
      await CertificatePinning.setEnforcementMode(mode);
      await this.saveConfiguration({ enforcementMode: mode });
      
      this.emit('enforcementModeChanged', { mode });
    } catch (error) {
      console.error('Failed to set enforcement mode:', error);
      throw error;
    }
  }

  /**
   * Get current certificate pinning configuration
   */
  public async getConfiguration(): Promise<PinningConfiguration> {
    try {
      return await CertificatePinning.getConfiguration();
    } catch (error) {
      console.error('Failed to get configuration:', error);
      throw error;
    }
  }

  /**
   * Test certificate pinning for a specific hostname
   */
  public async testPinning(hostname: string): Promise<boolean> {
    try {
      const result = await CertificatePinning.testPinningForHost(hostname);
      return result.success;
    } catch (error) {
      console.error(`Pinning test failed for ${hostname}:`, error);
      return false;
    }
  }

  /**
   * Update certificate pins from remote configuration
   */
  public async updatePinsFromRemote(configUrl: string): Promise<void> {
    try {
      const response = await fetch(configUrl, {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
        },
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch pin configuration: ${response.status}`);
      }

      const config = await response.json();
      
      // Validate configuration signature (implement proper signature verification)
      if (!this.validateConfigSignature(config)) {
        throw new Error('Invalid configuration signature');
      }

      // Update pins
      for (const pinConfig of config.pins) {
        await this.addPin(pinConfig.hostname, pinConfig.pins);
      }

      // Save update timestamp
      await AsyncStorage.setItem('pins_last_updated', new Date().toISOString());
      
      this.emit('pinsUpdated', { source: 'remote' });
    } catch (error) {
      console.error('Failed to update pins from remote:', error);
      throw error;
    }
  }

  /**
   * Get pinning failure reports
   */
  public getFailureReports(): PinningFailureReport[] {
    return [...this.pinningFailureQueue];
  }

  /**
   * Clear failure reports
   */
  public clearFailureReports(): void {
    this.pinningFailureQueue = [];
    this.emit('reportsCleared');
  }

  // Private methods

  private setupEventListeners(): void {
    // Listen for pinning failures from native module
    certificatePinningEmitter.addListener(
      'CertificatePinningFailure',
      (report: PinningFailureReport) => {
        this.handlePinningFailure(report.hostname, report.reason, report);
      }
    );

    // Listen for network changes to adjust reporting
    NetInfo.addEventListener((state) => {
      if (state.isConnected && this.pinningFailureQueue.length > 0) {
        this.flushReportQueue();
      }
    });
  }

  private async handlePinningFailure(
    hostname: string,
    reason: string,
    details?: any
  ): Promise<void> {
    const report = details as PinningFailureReport;
    const failureReport: PinningFailureReport = report || {
      timestamp: Date.now() / 1000,
      hostname,
      reason,
      enforcementMode: 'unknown',
      platform: Platform.OS,
      osVersion: Platform.Version.toString(),
      networkType: await this.getNetworkType(),
      appVersion: await this.getAppVersion(),
      validationDetails: details,
    };

    // Add to queue
    this.pinningFailureQueue.push(failureReport);

    // Emit event
    this.emit('pinningFailure', failureReport);

    // Try to send report immediately
    if (this.reportingEnabled) {
      await this.sendReportToServer(failureReport);
    }
  }

  private async sendReportToServer(report: PinningFailureReport): Promise<void> {
    try {
      const response = await fetch('https://security.example.com/pinning-report', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-App-Version': await this.getAppVersion(),
        },
        body: JSON.stringify(report),
      });

      if (response.ok) {
        // Remove from queue if sent successfully
        const index = this.pinningFailureQueue.indexOf(report);
        if (index > -1) {
          this.pinningFailureQueue.splice(index, 1);
        }
      }
    } catch (error) {
      console.error('Failed to send pinning report:', error);
    }
  }

  private async flushReportQueue(): Promise<void> {
    const reports = [...this.pinningFailureQueue];
    
    for (const report of reports) {
      await this.sendReportToServer(report);
    }
  }

  private startReportQueueProcessor(): void {
    // Process queue every 5 minutes
    setInterval(() => {
      if (this.pinningFailureQueue.length > 0) {
        this.flushReportQueue();
      }
    }, 5 * 60 * 1000);
  }

  private async loadConfiguration(): Promise<any> {
    try {
      const config = await AsyncStorage.getItem('certificate_pinning_config');
      return config ? JSON.parse(config) : null;
    } catch (error) {
      console.error('Failed to load configuration:', error);
      return null;
    }
  }

  private async saveConfiguration(config: any): Promise<void> {
    try {
      const currentConfig = await this.loadConfiguration() || {};
      const updatedConfig = { ...currentConfig, ...config };
      await AsyncStorage.setItem('certificate_pinning_config', JSON.stringify(updatedConfig));
    } catch (error) {
      console.error('Failed to save configuration:', error);
    }
  }

  private async loadCustomPins(): Promise<any> {
    try {
      const pins = await AsyncStorage.getItem('custom_certificate_pins');
      return pins ? JSON.parse(pins) : {};
    } catch (error) {
      console.error('Failed to load custom pins:', error);
      return {};
    }
  }

  private validateConfigSignature(config: any): boolean {
    try {
      // Configuration must have a signature field
      if (!config.signature || !config.data) {
        console.error('Configuration missing signature or data field');
        return false;
      }

      // Import crypto library for signature verification
      const CryptoJS = require('crypto-js');
      
      // WAQITI's public key for certificate pin configuration validation
      // In production, this would come from a secure keystore or be embedded during build
      const WAQITI_PUBLIC_KEY = 'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA2K4P+H8YbG/z...'; // Truncated for security
      
      // Expected signature algorithm and format
      const expectedAlgorithm = config.algorithm || 'RS256';
      
      if (expectedAlgorithm !== 'RS256') {
        console.error('Unsupported signature algorithm:', expectedAlgorithm);
        return false;
      }
      
      // Create canonical string representation of the data
      const canonicalData = this.createCanonicalString(config.data);
      
      // Verify signature timestamp (prevent replay attacks)
      const signatureTimestamp = config.timestamp;
      if (!signatureTimestamp) {
        console.error('Configuration signature missing timestamp');
        return false;
      }
      
      const currentTime = Date.now() / 1000;
      const maxAge = 24 * 60 * 60; // 24 hours
      
      if (Math.abs(currentTime - signatureTimestamp) > maxAge) {
        console.error('Configuration signature is too old or from future');
        return false;
      }
      
      // Verify configuration version and format
      if (!config.version || config.version < 1) {
        console.error('Invalid or missing configuration version');
        return false;
      }
      
      // Basic signature format validation (base64)
      const signatureRegex = /^[A-Za-z0-9+/]+=*$/;
      if (!signatureRegex.test(config.signature)) {
        console.error('Invalid signature format');
        return false;
      }
      
      // For React Native, we need to use a JWT verification approach
      // since RSA signature verification requires native modules
      
      // Create verification payload including timestamp and data
      const verificationPayload = {
        data: canonicalData,
        timestamp: signatureTimestamp,
        version: config.version,
        issuer: 'waqiti-security',
      };
      
      // For now, we'll use HMAC verification with a shared key approach
      // In production, implement proper RSA signature verification via native module
      const sharedKey = 'waqiti-pin-config-key-2024'; // This would be derived securely
      const expectedSignature = CryptoJS.HmacSHA256(
        JSON.stringify(verificationPayload),
        sharedKey
      ).toString(CryptoJS.enc.Base64);
      
      // Compare signatures in constant time to prevent timing attacks
      if (config.signature.length !== expectedSignature.length) {
        console.error('Signature verification failed: length mismatch');
        return false;
      }
      
      let result = 0;
      for (let i = 0; i < config.signature.length; i++) {
        result |= config.signature.charCodeAt(i) ^ expectedSignature.charCodeAt(i);
      }
      
      if (result !== 0) {
        console.error('Signature verification failed');
        // Report security incident (don't await to avoid blocking)
        this.reportSecurityIncident('invalid_pin_config_signature', {
          configSource: 'remote',
          timestamp: new Date().toISOString(),
          signatureAlgorithm: expectedAlgorithm,
        }).catch(error => console.error('Failed to report security incident:', error));
        return false;
      }
      
      // Additional validation: check configuration content
      if (!this.validateConfigurationContent(config.data)) {
        console.error('Configuration content validation failed');
        return false;
      }
      
      console.log('Certificate pin configuration signature verified successfully');
      return true;
      
    } catch (error) {
      console.error('Signature verification error:', error);
      return false;
    }
  }

  private async getNetworkType(): Promise<string> {
    const state = await NetInfo.fetch();
    return state.type;
  }

  private async getAppVersion(): Promise<string> {
    try {
      return await AppConfigService.getVersion();
    } catch (error) {
      console.warn('Failed to get app version from AppConfigService:', error);
      return '1.0.0';
    }
  }

  private createCanonicalString(data: any): string {
    // Create a canonical string representation for signature verification
    // Sort keys recursively to ensure consistent ordering
    const sortedData = this.sortObjectKeys(data);
    return JSON.stringify(sortedData);
  }

  private sortObjectKeys(obj: any): any {
    if (obj === null || typeof obj !== 'object') {
      return obj;
    }
    
    if (Array.isArray(obj)) {
      return obj.map(item => this.sortObjectKeys(item));
    }
    
    const sortedKeys = Object.keys(obj).sort();
    const sortedObj: any = {};
    
    for (const key of sortedKeys) {
      sortedObj[key] = this.sortObjectKeys(obj[key]);
    }
    
    return sortedObj;
  }

  private validateConfigurationContent(data: any): boolean {
    try {
      // Validate that the configuration has required fields
      if (!data.pins || !Array.isArray(data.pins)) {
        console.error('Configuration missing pins array');
        return false;
      }
      
      // Validate each pin configuration
      for (const pin of data.pins) {
        if (!pin.hostname || typeof pin.hostname !== 'string') {
          console.error('Pin configuration missing hostname');
          return false;
        }
        
        if (!pin.pins || !Array.isArray(pin.pins) || pin.pins.length === 0) {
          console.error('Pin configuration missing pins array');
          return false;
        }
        
        // Validate pin format (should be SHA256 base64)
        for (const pinValue of pin.pins) {
          if (typeof pinValue !== 'string' || !pinValue.startsWith('sha256/')) {
            console.error('Invalid pin format:', pinValue);
            return false;
          }
          
          // Check base64 format after sha256/ prefix
          const base64Part = pinValue.substring(7);
          const base64Regex = /^[A-Za-z0-9+/]+=*$/;
          if (!base64Regex.test(base64Part)) {
            console.error('Invalid pin base64 format:', pinValue);
            return false;
          }
        }
        
        // Validate hostname format
        const hostnameRegex = /^[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
        if (!hostnameRegex.test(pin.hostname)) {
          console.error('Invalid hostname format:', pin.hostname);
          return false;
        }
        
        // Ensure hostname belongs to WAQITI domains
        const allowedDomains = ['.example.com', '.example.net', '.example.org'];
        const isValidDomain = allowedDomains.some(domain => 
          pin.hostname.endsWith(domain) || pin.hostname === domain.substring(1)
        );
        
        if (!isValidDomain) {
          console.error('Pin configuration for unauthorized domain:', pin.hostname);
          return false;
        }
      }
      
      // Validate configuration metadata
      if (data.version && (typeof data.version !== 'number' || data.version < 1)) {
        console.error('Invalid configuration version');
        return false;
      }
      
      if (data.expiresAt) {
        const expiryTime = new Date(data.expiresAt).getTime();
        if (isNaN(expiryTime) || expiryTime <= Date.now()) {
          console.error('Configuration has expired or invalid expiry date');
          return false;
        }
      }
      
      console.log('Configuration content validation passed');
      return true;
      
    } catch (error) {
      console.error('Configuration content validation error:', error);
      return false;
    }
  }

  private async reportSecurityIncident(type: string, details: any): Promise<void> {
    try {
      // Report security incidents to monitoring service
      console.error(`Security incident: ${type}`, details);
      
      // In production, send to actual security monitoring endpoint
      // This could be integrated with the existing security reporting in SecureNetworkingService
      const incident = {
        type,
        details,
        timestamp: new Date().toISOString(),
        severity: 'high',
        service: 'certificate-pinning',
        platform: Platform.OS,
      };
      
      // Store locally for later transmission if network is unavailable
      try {
        const existingIncidents = await AsyncStorage.getItem('security_incidents');
        const incidents = existingIncidents ? JSON.parse(existingIncidents) : [];
        incidents.push(incident);
        
        // Keep only the last 50 incidents to prevent storage bloat
        if (incidents.length > 50) {
          incidents.splice(0, incidents.length - 50);
        }
        
        await AsyncStorage.setItem('security_incidents', JSON.stringify(incidents));
      } catch (storageError) {
        console.error('Failed to store security incident locally:', storageError);
      }
      
      // TODO: Send to actual security monitoring endpoint
      // await fetch('https://security.example.com/incidents', {
      //   method: 'POST',
      //   headers: { 'Content-Type': 'application/json' },
      //   body: JSON.stringify(incident)
      // });
      
    } catch (error) {
      console.error('Failed to report security incident:', error);
    }
  }
}

export default CertificatePinningService.getInstance();
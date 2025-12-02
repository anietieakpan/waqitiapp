/**
 * Device Fingerprint Service
 * Generates unique device fingerprints and assesses device security
 */

import { Platform, Dimensions } from 'react-native';
import DeviceInfo from 'react-native-device-info';
import { 
  IDeviceFingerprintService,
  DeviceFingerprint,
  SecurityAssessment,
  SecurityLevel,
  BiometricError,
  BiometricAuthError 
} from './types';
import { createHash } from 'crypto';

export class DeviceFingerprintService implements IDeviceFingerprintService {
  private static instance: DeviceFingerprintService;
  private cachedFingerprint: DeviceFingerprint | null = null;
  private lastFingerprintTime: number = 0;
  private readonly CACHE_DURATION = 5 * 60 * 1000; // 5 minutes

  public static getInstance(): DeviceFingerprintService {
    if (!DeviceFingerprintService.instance) {
      DeviceFingerprintService.instance = new DeviceFingerprintService();
    }
    return DeviceFingerprintService.instance;
  }

  /**
   * Generate a unique device fingerprint
   */
  async generateFingerprint(): Promise<DeviceFingerprint> {
    try {
      // Return cached fingerprint if still valid
      if (this.cachedFingerprint && 
          Date.now() - this.lastFingerprintTime < this.CACHE_DURATION) {
        return this.cachedFingerprint;
      }

      console.log('Generating new device fingerprint...');

      // Collect device information
      const [
        deviceId,
        bundleId,
        buildNumber,
        version,
        manufacturer,
        model,
        systemVersion,
        carrier,
        timezone,
        locale,
        installationTime,
        lastUpdateTime,
        isEmulator,
        installerPackageName,
        userAgent,
        baseOs,
        bootloader,
        codename,
        device,
        display,
        fingerprint,
        hardware,
        host,
        product,
        tags,
        type,
        androidId,
        apiLevel,
        securityPatch,
        maxMemory,
        totalMemory,
        freeDiskStorage,
        totalDiskCapacity,
        powerState,
        batteryLevel,
        supportedAbis,
        supported32BitAbis,
        supported64BitAbis,
        hasSystemFeature,
        hasGms,
        hasHms,
      ] = await Promise.all([
        DeviceInfo.getUniqueId(),
        DeviceInfo.getBundleId(),
        DeviceInfo.getBuildNumber(),
        DeviceInfo.getVersion(),
        DeviceInfo.getManufacturer(),
        DeviceInfo.getModel(),
        DeviceInfo.getSystemVersion(),
        this.getCarrierSafely(),
        DeviceInfo.getTimezone(),
        DeviceInfo.getDeviceLocale(),
        DeviceInfo.getFirstInstallTime(),
        DeviceInfo.getLastUpdateTime(),
        DeviceInfo.isEmulator(),
        this.getInstallerPackageNameSafely(),
        DeviceInfo.getUserAgent(),
        this.getBaseOsSafely(),
        this.getBootloaderSafely(),
        this.getCodenameSafely(),
        this.getDeviceSafely(),
        this.getDisplaySafely(),
        this.getFingerprintSafely(),
        this.getHardwareSafely(),
        this.getHostSafely(),
        this.getProductSafely(),
        this.getTagsSafely(),
        this.getTypeSafely(),
        this.getAndroidIdSafely(),
        this.getApiLevelSafely(),
        this.getSecurityPatchSafely(),
        DeviceInfo.getMaxMemory(),
        DeviceInfo.getTotalMemory(),
        DeviceInfo.getFreeDiskStorage(),
        DeviceInfo.getTotalDiskCapacity(),
        DeviceInfo.getPowerState(),
        DeviceInfo.getBatteryLevel(),
        this.getSupportedAbisSafely(),
        this.getSupported32BitAbisSafely(),
        this.getSupported64BitAbisSafely(),
        this.checkSystemFeatureSupport(),
        this.hasGmsSafely(),
        this.hasHmsSafely(),
      ]);

      // Get screen dimensions
      const { width, height, scale } = Dimensions.get('screen');

      // Collect hardware and security features
      const hardwareFeatures = await this.collectHardwareFeatures();
      const securityFeatures = await this.collectSecurityFeatures();

      // Check for security threats
      const isRooted = await this.checkRootStatus();

      // Create fingerprint object
      const fingerprint: DeviceFingerprint = {
        deviceId,
        platform: Platform.OS,
        osVersion: systemVersion,
        appVersion: version,
        manufacturer,
        model,
        buildNumber,
        bundleId,
        carrier: carrier || 'unknown',
        timeZone: timezone,
        locale,
        screenDimensions: {
          width,
          height,
          scale,
        },
        hardwareFeatures,
        securityFeatures,
        installationId: deviceId, // Using deviceId as installation ID
        firstInstallTime: installationTime,
        lastUpdateTime,
        isRooted,
        isEmulator,
        hash: '', // Will be calculated below
      };

      // Add platform-specific data
      if (Platform.OS === 'android') {
        fingerprint.androidId = androidId;
        fingerprint.apiLevel = apiLevel;
        fingerprint.securityPatch = securityPatch;
        fingerprint.supportedAbis = supportedAbis;
        fingerprint.supported32BitAbis = supported32BitAbis;
        fingerprint.supported64BitAbis = supported64BitAbis;
        fingerprint.baseOs = baseOs;
        fingerprint.bootloader = bootloader;
        fingerprint.codename = codename;
        fingerprint.device = device;
        fingerprint.display = display;
        fingerprint.fingerprint = fingerprint;
        fingerprint.hardware = hardware;
        fingerprint.host = host;
        fingerprint.product = product;
        fingerprint.tags = tags;
        fingerprint.type = type;
        fingerprint.hasGms = hasGms;
        fingerprint.hasHms = hasHms;
      }

      // Additional metadata
      fingerprint.maxMemory = maxMemory;
      fingerprint.totalMemory = totalMemory;
      fingerprint.freeDiskStorage = freeDiskStorage;
      fingerprint.totalDiskCapacity = totalDiskCapacity;
      fingerprint.batteryLevel = batteryLevel;
      fingerprint.installerPackageName = installerPackageName;
      fingerprint.userAgent = userAgent;

      // Generate hash from all collected data
      fingerprint.hash = this.generateFingerprintHash(fingerprint);

      // Cache the fingerprint
      this.cachedFingerprint = fingerprint;
      this.lastFingerprintTime = Date.now();

      console.log('Device fingerprint generated successfully');
      return fingerprint;

    } catch (error) {
      console.error('Failed to generate device fingerprint:', error);
      throw new BiometricError(
        BiometricAuthError.SYSTEM_ERROR,
        'Failed to generate device fingerprint',
        error,
        true,
        false
      );
    }
  }

  /**
   * Validate a stored fingerprint against current device
   */
  async validateFingerprint(stored: DeviceFingerprint): Promise<boolean> {
    try {
      const current = await this.generateFingerprint();
      
      // Critical fields that should never change
      const criticalFields = [
        'deviceId',
        'bundleId',
        'manufacturer',
        'model',
        'installationId',
        'firstInstallTime',
      ];

      // Check critical fields
      for (const field of criticalFields) {
        if (stored[field] !== current[field]) {
          console.warn(`Critical field mismatch: ${field}`);
          return false;
        }
      }

      // Check for major platform changes
      if (stored.platform !== current.platform) {
        console.warn('Platform mismatch');
        return false;
      }

      // Check for suspicious changes (rooting/jailbreaking)
      if (!stored.isRooted && current.isRooted) {
        console.warn('Device appears to have been rooted');
        return false;
      }

      if (!stored.isEmulator && current.isEmulator) {
        console.warn('Device appears to be running in emulator');
        return false;
      }

      // Allow some fields to change (OS updates, app updates, etc.)
      const allowedChanges = [
        'osVersion',
        'appVersion',
        'buildNumber',
        'lastUpdateTime',
        'carrier',
        'timeZone',
        'locale',
        'batteryLevel',
        'freeDiskStorage',
      ];

      return true;
    } catch (error) {
      console.error('Fingerprint validation failed:', error);
      return false;
    }
  }

  /**
   * Assess device security status
   */
  async assessSecurity(): Promise<SecurityAssessment> {
    try {
      const fingerprint = await this.generateFingerprint();
      
      let trustScore = 100;
      const threats: string[] = [];
      const recommendations: string[] = [];

      // Check for rooting/jailbreaking
      if (fingerprint.isRooted) {
        trustScore -= 50;
        threats.push('Device is rooted/jailbroken');
        recommendations.push('Use device without root access');
      }

      // Check for emulator
      if (fingerprint.isEmulator) {
        trustScore -= 40;
        threats.push('Running in emulator environment');
        recommendations.push('Use physical device');
      }

      // Check OS version
      const osVersion = parseFloat(fingerprint.osVersion);
      if (Platform.OS === 'android' && osVersion < 9.0) {
        trustScore -= 20;
        threats.push('Outdated Android version');
        recommendations.push('Update to Android 9.0 or higher');
      } else if (Platform.OS === 'ios' && osVersion < 13.0) {
        trustScore -= 20;
        threats.push('Outdated iOS version');
        recommendations.push('Update to iOS 13.0 or higher');
      }

      // Check for security features
      const hasSecureBoot = fingerprint.securityFeatures.includes('secure_boot');
      const hasHardwareKeystore = fingerprint.securityFeatures.includes('hardware_keystore');
      const hasBiometricHardware = fingerprint.securityFeatures.includes('biometric_hardware');

      if (!hasSecureBoot) {
        trustScore -= 10;
        threats.push('Secure boot not available');
      }

      if (!hasHardwareKeystore) {
        trustScore -= 15;
        threats.push('Hardware keystore not available');
        recommendations.push('Use device with hardware security module');
      }

      if (!hasBiometricHardware) {
        trustScore -= 25;
        threats.push('Biometric hardware not available');
        recommendations.push('Use device with biometric capabilities');
      }

      // Check app integrity
      const appIntegrity = await this.checkAppIntegrity();
      if (!appIntegrity) {
        trustScore -= 30;
        threats.push('App integrity compromised');
        recommendations.push('Reinstall app from official store');
      }

      // Determine risk level
      let riskLevel: SecurityLevel;
      if (trustScore >= 80) {
        riskLevel = SecurityLevel.LOW;
      } else if (trustScore >= 60) {
        riskLevel = SecurityLevel.MEDIUM;
      } else if (trustScore >= 40) {
        riskLevel = SecurityLevel.HIGH;
      } else {
        riskLevel = SecurityLevel.CRITICAL;
      }

      return {
        deviceTrustScore: Math.max(0, trustScore),
        riskLevel,
        threats,
        recommendations,
        biometricIntegrity: hasBiometricHardware && !fingerprint.isRooted,
        deviceIntegrity: !fingerprint.isRooted && !fingerprint.isEmulator,
        appIntegrity,
        networkSecurity: true, // This would need additional network checks
        timestamp: Date.now(),
      };

    } catch (error) {
      console.error('Security assessment failed:', error);
      
      return {
        deviceTrustScore: 0,
        riskLevel: SecurityLevel.CRITICAL,
        threats: ['Security assessment failed'],
        recommendations: ['Contact support'],
        biometricIntegrity: false,
        deviceIntegrity: false,
        appIntegrity: false,
        networkSecurity: false,
        timestamp: Date.now(),
      };
    }
  }

  /**
   * Check device integrity (root/jailbreak detection)
   */
  async checkDeviceIntegrity(): Promise<boolean> {
    try {
      const fingerprint = await this.generateFingerprint();
      return !fingerprint.isRooted && !fingerprint.isEmulator;
    } catch (error) {
      console.error('Device integrity check failed:', error);
      return false;
    }
  }

  /**
   * Private helper methods
   */
  private generateFingerprintHash(fingerprint: DeviceFingerprint): string {
    // Create a deterministic string from fingerprint data
    const data = [
      fingerprint.deviceId,
      fingerprint.platform,
      fingerprint.manufacturer,
      fingerprint.model,
      fingerprint.bundleId,
      fingerprint.installationId,
      fingerprint.firstInstallTime,
      fingerprint.screenDimensions.width,
      fingerprint.screenDimensions.height,
      fingerprint.hardwareFeatures.join(','),
      fingerprint.securityFeatures.join(','),
    ].join('|');

    // In a React Native environment, we'll use a simple hash
    // In production, consider using a more robust hashing library
    return this.simpleHash(data);
  }

  private simpleHash(str: string): string {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      const char = str.charCodeAt(i);
      hash = ((hash << 5) - hash) + char;
      hash = hash & hash; // Convert to 32-bit integer
    }
    return Math.abs(hash).toString(16);
  }

  private async collectHardwareFeatures(): Promise<string[]> {
    const features: string[] = [];
    
    try {
      // Basic hardware features
      features.push(`cpu_${Platform.OS}`);
      
      if (Platform.OS === 'android') {
        const supportedAbis = await this.getSupportedAbisSafely();
        if (supportedAbis) {
          features.push(`abis_${supportedAbis.join('_')}`);
        }
        
        // Check for specific Android hardware features
        const hasNfc = await this.hasSystemFeatureSafely('android.hardware.nfc');
        if (hasNfc) features.push('nfc');
        
        const hasCamera = await this.hasSystemFeatureSafely('android.hardware.camera');
        if (hasCamera) features.push('camera');
        
        const hasFingerprint = await this.hasSystemFeatureSafely('android.hardware.fingerprint');
        if (hasFingerprint) features.push('fingerprint');
        
        const hasFaceAuth = await this.hasSystemFeatureSafely('android.hardware.biometrics.face');
        if (hasFaceAuth) features.push('face_unlock');
      }
      
      // Screen features
      const { width, height, scale } = Dimensions.get('screen');
      features.push(`screen_${width}x${height}@${scale}x`);
      
    } catch (error) {
      console.warn('Failed to collect hardware features:', error);
    }
    
    return features;
  }

  private async collectSecurityFeatures(): Promise<string[]> {
    const features: string[] = [];
    
    try {
      if (Platform.OS === 'android') {
        // Check for hardware security features
        const hasStrongBox = await this.hasSystemFeatureSafely('android.hardware.strongbox_keystore');
        if (hasStrongBox) features.push('strongbox_keystore');
        
        const hasHardwareKeystore = await this.hasSystemFeatureSafely('android.hardware.keystore');
        if (hasHardwareKeystore) features.push('hardware_keystore');
        
        const hasSecureBoot = await this.hasSystemFeatureSafely('android.hardware.verified_boot');
        if (hasSecureBoot) features.push('secure_boot');
        
        const hasBiometricHardware = await this.hasSystemFeatureSafely('android.hardware.biometrics');
        if (hasBiometricHardware) features.push('biometric_hardware');
        
        // Check API level for security features
        const apiLevel = await this.getApiLevelSafely();
        if (apiLevel >= 28) features.push('api_28_security');
        if (apiLevel >= 30) features.push('scoped_storage');
      } else if (Platform.OS === 'ios') {
        // iOS security features are more standardized
        features.push('secure_enclave');
        features.push('code_signing');
        features.push('app_transport_security');
        
        const osVersion = parseFloat(await DeviceInfo.getSystemVersion());
        if (osVersion >= 13.0) features.push('ios_13_security');
        if (osVersion >= 14.0) features.push('app_tracking_transparency');
      }
      
    } catch (error) {
      console.warn('Failed to collect security features:', error);
    }
    
    return features;
  }

  private async checkRootStatus(): Promise<boolean> {
    try {
      // Use DeviceInfo's built-in root detection
      const isRooted = await DeviceInfo.isEmulator(); // This is a simplification
      
      // Additional root detection methods could be added here
      // such as checking for specific files, testing for su binary, etc.
      
      return isRooted;
    } catch (error) {
      console.warn('Root detection failed:', error);
      return false;
    }
  }

  private async checkAppIntegrity(): Promise<boolean> {
    try {
      // Basic app integrity checks
      const bundleId = await DeviceInfo.getBundleId();
      const expectedBundleId = 'com.waqiti.mobile'; // Replace with actual bundle ID
      
      if (bundleId !== expectedBundleId) {
        return false;
      }
      
      // Additional integrity checks could include:
      // - Certificate validation
      // - Code signing verification
      // - Package signature validation
      
      return true;
    } catch (error) {
      console.warn('App integrity check failed:', error);
      return false;
    }
  }

  // Safe wrapper methods for DeviceInfo calls that might fail
  private async getCarrierSafely(): Promise<string | null> {
    try {
      return await DeviceInfo.getCarrier();
    } catch {
      return null;
    }
  }

  private async getInstallerPackageNameSafely(): Promise<string | null> {
    try {
      return await DeviceInfo.getInstallerPackageName();
    } catch {
      return null;
    }
  }

  private async getBaseOsSafely(): Promise<string | null> {
    try {
      return Platform.OS === 'android' ? await DeviceInfo.getBaseOs() : null;
    } catch {
      return null;
    }
  }

  private async getBootloaderSafely(): Promise<string | null> {
    try {
      return Platform.OS === 'android' ? await DeviceInfo.getBootloader() : null;
    } catch {
      return null;
    }
  }

  private async getCodenameSafely(): Promise<string | null> {
    try {
      return Platform.OS === 'android' ? await DeviceInfo.getCodename() : null;
    } catch {
      return null;
    }
  }

  private async getDeviceSafely(): Promise<string | null> {
    try {
      return Platform.OS === 'android' ? await DeviceInfo.getDevice() : null;
    } catch {
      return null;
    }
  }

  private async getDisplaySafely(): Promise<string | null> {
    try {
      return Platform.OS === 'android' ? await DeviceInfo.getDisplay() : null;
    } catch {
      return null;
    }
  }

  private async getFingerprintSafely(): Promise<string | null> {
    try {
      return Platform.OS === 'android' ? await DeviceInfo.getFingerprint() : null;
    } catch {
      return null;
    }
  }

  private async getHardwareSafely(): Promise<string | null> {
    try {
      return Platform.OS === 'android' ? await DeviceInfo.getHardware() : null;
    } catch {
      return null;
    }
  }

  private async getHostSafely(): Promise<string | null> {
    try {
      return Platform.OS === 'android' ? await DeviceInfo.getHost() : null;
    } catch {
      return null;
    }
  }

  private async getProductSafely(): Promise<string | null> {
    try {
      return Platform.OS === 'android' ? await DeviceInfo.getProduct() : null;
    } catch {
      return null;
    }
  }

  private async getTagsSafely(): Promise<string | null> {
    try {
      return Platform.OS === 'android' ? await DeviceInfo.getTags() : null;
    } catch {
      return null;
    }
  }

  private async getTypeSafely(): Promise<string | null> {
    try {
      return Platform.OS === 'android' ? await DeviceInfo.getType() : null;
    } catch {
      return null;
    }
  }

  private async getAndroidIdSafely(): Promise<string | null> {
    try {
      return Platform.OS === 'android' ? await DeviceInfo.getAndroidId() : null;
    } catch {
      return null;
    }
  }

  private async getApiLevelSafely(): Promise<number> {
    try {
      return Platform.OS === 'android' ? await DeviceInfo.getApiLevel() : 0;
    } catch {
      return 0;
    }
  }

  private async getSecurityPatchSafely(): Promise<string | null> {
    try {
      return Platform.OS === 'android' ? await DeviceInfo.getSecurityPatch() : null;
    } catch {
      return null;
    }
  }

  private async getSupportedAbisSafely(): Promise<string[] | null> {
    try {
      return Platform.OS === 'android' ? await DeviceInfo.supportedAbis() : null;
    } catch {
      return null;
    }
  }

  private async getSupported32BitAbisSafely(): Promise<string[] | null> {
    try {
      return Platform.OS === 'android' ? await DeviceInfo.supported32BitAbis() : null;
    } catch {
      return null;
    }
  }

  private async getSupported64BitAbisSafely(): Promise<string[] | null> {
    try {
      return Platform.OS === 'android' ? await DeviceInfo.supported64BitAbis() : null;
    } catch {
      return null;
    }
  }

  private async hasSystemFeatureSafely(feature: string): Promise<boolean> {
    try {
      return Platform.OS === 'android' ? await DeviceInfo.hasSystemFeature(feature) : false;
    } catch {
      return false;
    }
  }

  private async hasGmsSafely(): Promise<boolean> {
    try {
      return Platform.OS === 'android' ? await DeviceInfo.hasGms() : false;
    } catch {
      return false;
    }
  }

  private async hasHmsSafely(): Promise<boolean> {
    try {
      return Platform.OS === 'android' ? await DeviceInfo.hasHms() : false;
    } catch {
      return false;
    }
  }

  private async checkSystemFeatureSupport(): Promise<boolean> {
    try {
      // Test if system feature checking is supported
      await DeviceInfo.hasSystemFeature('android.hardware.camera');
      return true;
    } catch {
      return false;
    }
  }
}

export default DeviceFingerprintService;
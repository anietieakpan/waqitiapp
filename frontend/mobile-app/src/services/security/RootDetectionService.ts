/**
 * Root Detection Service
 * Comprehensive security check for rooted/jailbroken devices
 *
 * @author Waqiti Mobile Security Team
 * @version 1.0.0
 */

import { Platform, NativeModules } from 'react-native';
import DeviceInfo from 'react-native-device-info';
import RNFetchBlob from 'react-native-fs';

interface RootDetectionResult {
  isRooted: boolean;
  detectionMethod: string[];
  riskLevel: 'low' | 'medium' | 'high' | 'critical';
  confidence: number;
}

/**
 * Comprehensive Root/Jailbreak Detection Service
 * Uses multiple detection techniques for high confidence
 */
class RootDetectionService {
  private static instance: RootDetectionService;
  private cache: RootDetectionResult | null = null;
  private cacheTimestamp: number = 0;
  private readonly CACHE_DURATION = 60 * 1000; // 1 minute

  private constructor() {}

  static getInstance(): RootDetectionService {
    if (!RootDetectionService.instance) {
      RootDetectionService.instance = new RootDetectionService();
    }
    return RootDetectionService.instance;
  }

  /**
   * Main detection method - runs all checks
   */
  async isDeviceCompromised(): Promise<RootDetectionResult> {
    // Check cache first
    if (this.cache && Date.now() - this.cacheTimestamp < this.CACHE_DURATION) {
      return this.cache;
    }

    const detectionMethods: string[] = [];
    let totalChecks = 0;
    let failedChecks = 0;

    // Run all detection methods
    const checks = [
      { name: 'Root Apps', method: this.checkForRootApps.bind(this) },
      { name: 'Root Binaries', method: this.checkForRootBinaries.bind(this) },
      { name: 'Dangerous Properties', method: this.checkDangerousProperties.bind(this) },
      { name: 'Modified System', method: this.checkForModifiedSystem.bind(this) },
      { name: 'Test Keys', method: this.checkForTestKeys.bind(this) },
      { name: 'Writable System', method: this.checkWritableSystem.bind(this) },
    ];

    if (Platform.OS === 'ios') {
      checks.push(
        { name: 'Cydia Apps', method: this.checkForCydiaApps.bind(this) },
        { name: 'Jailbreak Files', method: this.checkForJailbreakFiles.bind(this) },
        { name: 'Sandbox Breach', method: this.checkSandboxBreach.bind(this) }
      );
    }

    for (const check of checks) {
      totalChecks++;
      try {
        const isDetected = await check.method();
        if (isDetected) {
          failedChecks++;
          detectionMethods.push(check.name);
        }
      } catch (error) {
        console.warn(`Root detection check '${check.name}' failed:`, error);
      }
    }

    // Calculate risk level
    const confidence = Math.round((failedChecks / totalChecks) * 100);
    let riskLevel: 'low' | 'medium' | 'high' | 'critical';

    if (failedChecks === 0) {
      riskLevel = 'low';
    } else if (failedChecks <= 2) {
      riskLevel = 'medium';
    } else if (failedChecks <= 4) {
      riskLevel = 'high';
    } else {
      riskLevel = 'critical';
    }

    const result: RootDetectionResult = {
      isRooted: failedChecks > 0,
      detectionMethod: detectionMethods,
      riskLevel,
      confidence,
    };

    // Cache result
    this.cache = result;
    this.cacheTimestamp = Date.now();

    return result;
  }

  /**
   * Check for common root management apps (Android)
   */
  private async checkForRootApps(): Promise<boolean> {
    if (Platform.OS !== 'android') return false;

    const rootApps = [
      'com.topjohnwu.magisk',           // Magisk
      'com.koushikdutta.superuser',     // Superuser
      'com.noshufou.android.su',        // Superuser (old)
      'com.thirdparty.superuser',       // Superuser Elite
      'eu.chainfire.supersu',           // SuperSU
      'com.yellowes.su',                // YellowSU
      'com.kingroot.kinguser',          // KingRoot
      'com.kingo.root',                 // KingoRoot
      'com.smedialink.oneclickroot',    // OneClickRoot
      'com.ramdroid.appquarantine',     // App Quarantine
      'com.koushikdutta.rommanager',    // ROM Manager
      'com.dimonvideo.luckypatcher',    // Lucky Patcher
      'com.chelpus.lackypatch',         // Lucky Patcher
      'com.ramdroid.appquarantine',     // App Quarantine
      'com.zachspong.temprootremovejb', // Temp Root Remove JB
    ];

    try {
      const installedApps = await DeviceInfo.getInstalledApps();
      return rootApps.some(app => installedApps.includes(app));
    } catch (error) {
      // If we can't check, assume not rooted
      return false;
    }
  }

  /**
   * Check for root binaries in common locations
   */
  private async checkForRootBinaries(): Promise<boolean> {
    const binaryPaths = [
      '/system/app/Superuser.apk',
      '/system/xbin/su',
      '/system/bin/su',
      '/sbin/su',
      '/system/su',
      '/system/bin/.ext/.su',
      '/system/usr/we-need-root/su-backup',
      '/system/xbin/mu',
      '/data/local/xbin/su',
      '/data/local/bin/su',
      '/data/local/su',
      '/system/app/SuperSU.apk',
      '/system/app/SuperSU',
      '/system/app/supersu.apk',
      '/system/xbin/daemonsu',
    ];

    if (Platform.OS === 'ios') {
      // iOS jailbreak binaries
      binaryPaths.push(
        '/Applications/Cydia.app',
        '/Library/MobileSubstrate/MobileSubstrate.dylib',
        '/bin/bash',
        '/usr/sbin/sshd',
        '/etc/apt',
        '/usr/bin/ssh',
        '/private/var/lib/apt',
        '/private/var/lib/cydia',
        '/private/var/stash',
        '/private/var/mobile/Library/SBSettings/Themes',
        '/System/Library/LaunchDaemons/com.ikey.bbot.plist',
        '/System/Library/LaunchDaemons/com.saurik.Cydia.Startup.plist',
        '/var/cache/apt',
        '/var/lib/apt',
        '/var/lib/cydia',
        '/usr/libexec/cydia',
        '/usr/bin/cycript',
        '/usr/local/bin/cycript',
        '/usr/lib/libcycript.dylib'
      );
    }

    for (const path of binaryPaths) {
      try {
        const exists = await this.fileExists(path);
        if (exists) {
          return true;
        }
      } catch (error) {
        // Continue checking other paths
      }
    }

    return false;
  }

  /**
   * Check for dangerous Android build properties
   */
  private async checkDangerousProperties(): Promise<boolean> {
    if (Platform.OS !== 'android') return false;

    try {
      const buildTags = await DeviceInfo.getBuildId();
      const fingerprint = await DeviceInfo.getFingerprint();

      // Check for test-keys (indicates custom ROM)
      if (buildTags?.includes('test-keys')) {
        return true;
      }

      // Check for common custom ROM fingerprints
      const dangerousFingerprints = [
        'generic',
        'unknown',
        'test-keys',
        'dev-keys',
      ];

      return dangerousFingerprints.some(fp =>
        fingerprint?.toLowerCase().includes(fp.toLowerCase())
      );
    } catch (error) {
      return false;
    }
  }

  /**
   * Check for modified system files
   */
  private async checkForModifiedSystem(): Promise<boolean> {
    if (Platform.OS !== 'android') return false;

    try {
      // Check if we can write to system partition (shouldn't be possible)
      const testPath = '/system/testfile';
      try {
        await RNFetchBlob.writeFile(testPath, 'test', 'utf8');
        // If we got here, system is writable (BAD)
        await RNFetchBlob.unlink(testPath); // Clean up
        return true;
      } catch (error) {
        // Good - system is read-only as it should be
        return false;
      }
    } catch (error) {
      return false;
    }
  }

  /**
   * Check if device is signed with test keys (Android)
   */
  private async checkForTestKeys(): Promise<boolean> {
    if (Platform.OS !== 'android') return false;

    try {
      const buildTags = await DeviceInfo.getBuildId();
      return buildTags?.includes('test-keys') || false;
    } catch (error) {
      return false;
    }
  }

  /**
   * Check if system partition is writable (Android)
   */
  private async checkWritableSystem(): Promise<boolean> {
    if (Platform.OS !== 'android') return false;

    const locations = ['/system', '/system/bin', '/system/xbin', '/vendor/bin'];

    for (const location of locations) {
      try {
        const testFile = `${location}/test_write_check.tmp`;
        await RNFetchBlob.writeFile(testFile, 'test', 'utf8');
        // If we can write, that's bad
        await RNFetchBlob.unlink(testFile);
        return true;
      } catch (error) {
        // Good - cannot write
        continue;
      }
    }

    return false;
  }

  /**
   * Check for Cydia and other jailbreak apps (iOS)
   */
  private async checkForCydiaApps(): Promise<boolean> {
    if (Platform.OS !== 'ios') return false;

    const cydiaApps = [
      'cydia://package/com.example.package',
      'sileo://package/com.example.package',
      'zbra://sources/add',
      'filza://view',
      'undecimus://jailbreak',
    ];

    // Try to open Cydia URLs (if they work, device is jailbroken)
    // Note: This requires linking capability in Info.plist
    // For security, we'll just check file existence instead

    const cydiaFiles = [
      '/Applications/Cydia.app',
      '/Applications/Sileo.app',
      '/Applications/Zebra.app',
      '/Applications/Installer.app',
      '/Applications/Unc0ver.app',
      '/Applications/Checkra1n.app',
    ];

    for (const file of cydiaFiles) {
      try {
        const exists = await this.fileExists(file);
        if (exists) return true;
      } catch (error) {
        continue;
      }
    }

    return false;
  }

  /**
   * Check for jailbreak-specific files (iOS)
   */
  private async checkForJailbreakFiles(): Promise<boolean> {
    if (Platform.OS !== 'ios') return false;

    const jailbreakFiles = [
      '/etc/apt',
      '/private/var/lib/apt',
      '/private/var/lib/cydia',
      '/private/var/stash',
      '/Library/MobileSubstrate/MobileSubstrate.dylib',
      '/bin/bash',
      '/usr/sbin/sshd',
      '/usr/bin/ssh',
      '/usr/libexec/sftp-server',
      '/.installed_unc0ver',
      '/.bootstrapped_electra',
      '/usr/share/jailbreak/injectme.plist',
      '/var/checkra1n.dmg',
      '/var/binpack',
    ];

    for (const file of jailbreakFiles) {
      try {
        const exists = await this.fileExists(file);
        if (exists) return true;
      } catch (error) {
        continue;
      }
    }

    return false;
  }

  /**
   * Check if app sandbox has been breached (iOS)
   */
  private async checkSandboxBreach(): Promise<boolean> {
    if (Platform.OS !== 'ios') return false;

    try {
      // Try to read file outside sandbox
      const outsideSandbox = '/private/jailbreak.txt';
      await RNFetchBlob.writeFile(outsideSandbox, 'test', 'utf8');
      // If we can write outside sandbox, it's jailbroken
      await RNFetchBlob.unlink(outsideSandbox);
      return true;
    } catch (error) {
      // Good - cannot breach sandbox
      return false;
    }
  }

  /**
   * Helper: Check if file exists
   */
  private async fileExists(path: string): Promise<boolean> {
    try {
      return await RNFetchBlob.exists(path);
    } catch (error) {
      return false;
    }
  }

  /**
   * Clear cached result to force re-check
   */
  clearCache(): void {
    this.cache = null;
    this.cacheTimestamp = 0;
  }

  /**
   * Get a human-readable security report
   */
  async getSecurityReport(): Promise<string> {
    const result = await this.isDeviceCompromised();

    if (!result.isRooted) {
      return 'Device security: ✅ SECURE\n\nNo signs of root/jailbreak detected.';
    }

    let report = `Device security: ⚠️ COMPROMISED\n\n`;
    report += `Risk Level: ${result.riskLevel.toUpperCase()}\n`;
    report += `Confidence: ${result.confidence}%\n\n`;
    report += `Detection Methods:\n`;
    result.detectionMethod.forEach(method => {
      report += `- ${method}\n`;
    });

    return report;
  }
}

export default RootDetectionService.getInstance();
export type { RootDetectionResult };

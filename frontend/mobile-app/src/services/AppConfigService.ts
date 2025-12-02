/**
 * AppConfigService - Centralized application configuration management
 * Provides app version, build info, and other configuration data
 */

import DeviceInfo from 'react-native-device-info';
import { Platform } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

interface AppConfig {
  version: string;
  buildNumber: string;
  bundleId: string;
  appName: string;
  buildType: 'debug' | 'release';
  environment: 'development' | 'staging' | 'production';
  apiBaseUrl: string;
  features: {
    [key: string]: boolean;
  };
}

interface BuildInfo {
  version: string;
  buildNumber: string;
  buildDate: string;
  gitCommit?: string;
  gitBranch?: string;
  buildEnvironment: string;
  platform: string;
  architecture: string;
}

class AppConfigService {
  private static instance: AppConfigService;
  private config: AppConfig | null = null;
  private buildInfo: BuildInfo | null = null;

  private constructor() {
    this.initializeConfig();
  }

  public static getInstance(): AppConfigService {
    if (!AppConfigService.instance) {
      AppConfigService.instance = new AppConfigService();
    }
    return AppConfigService.instance;
  }

  /**
   * Initialize app configuration
   */
  private async initializeConfig(): Promise<void> {
    try {
      // Get version information from device info
      const version = await DeviceInfo.getVersion();
      const buildNumber = await DeviceInfo.getBuildNumber();
      const bundleId = await DeviceInfo.getBundleId();
      const appName = await DeviceInfo.getApplicationName();

      // Determine build type and environment
      const buildType = __DEV__ ? 'debug' : 'release';
      const environment = this.determineEnvironment();

      // Set up feature flags based on environment
      const features = this.getFeatureFlags(environment);

      this.config = {
        version,
        buildNumber,
        bundleId,
        appName,
        buildType,
        environment,
        apiBaseUrl: this.getApiBaseUrl(environment),
        features,
      };

      // Initialize build info
      await this.initializeBuildInfo();

    } catch (error) {
      console.error('Failed to initialize app config:', error);
      // Fallback configuration
      this.config = {
        version: '1.0.0',
        buildNumber: '1',
        bundleId: 'com.waqiti.mobile',
        appName: 'Waqiti',
        buildType: __DEV__ ? 'debug' : 'release',
        environment: 'development',
        apiBaseUrl: 'http://localhost:8080/api/v1',
        features: {},
      };
    }
  }

  /**
   * Initialize build information
   */
  private async initializeBuildInfo(): Promise<void> {
    try {
      const version = await DeviceInfo.getVersion();
      const buildNumber = await DeviceInfo.getBuildNumber();
      const buildDate = await DeviceInfo.getBuildId(); // This might not be the actual build date
      const architecture = await DeviceInfo.supported64BitAbis();

      this.buildInfo = {
        version,
        buildNumber,
        buildDate: new Date().toISOString(), // Fallback to current date
        gitCommit: process.env.GIT_COMMIT || 'unknown',
        gitBranch: process.env.GIT_BRANCH || 'unknown',
        buildEnvironment: this.config?.environment || 'unknown',
        platform: Platform.OS,
        architecture: Array.isArray(architecture) ? architecture.join(', ') : 'unknown',
      };

    } catch (error) {
      console.error('Failed to initialize build info:', error);
      this.buildInfo = {
        version: '1.0.0',
        buildNumber: '1',
        buildDate: new Date().toISOString(),
        buildEnvironment: 'unknown',
        platform: Platform.OS,
        architecture: 'unknown',
      };
    }
  }

  /**
   * Determine the current environment
   */
  private determineEnvironment(): 'development' | 'staging' | 'production' {
    if (__DEV__) {
      return 'development';
    }

    // Check for staging indicators
    const bundleId = DeviceInfo.getBundleId();
    if (bundleId.includes('.staging') || bundleId.includes('.beta')) {
      return 'staging';
    }

    // Default to production for release builds
    return 'production';
  }

  /**
   * Get API base URL for environment
   */
  private getApiBaseUrl(environment: string): string {
    switch (environment) {
      case 'development':
        return Platform.OS === 'android' 
          ? 'http://10.0.2.2:8080/api/v1'  // Android emulator
          : 'http://localhost:8080/api/v1'; // iOS simulator
      case 'staging':
        return 'https://api-staging.example.com/v1';
      case 'production':
        return 'https://api.example.com/v1';
      default:
        return 'http://localhost:8080/api/v1';
    }
  }

  /**
   * Get feature flags for environment
   */
  private getFeatureFlags(environment: string): { [key: string]: boolean } {
    const baseFeatures = {
      analytics: true,
      crashReporting: true,
      biometricAuth: true,
      pushNotifications: true,
      voicePayments: false, // New feature, disabled by default
      cryptoWallet: true,
      checkDeposit: true,
      merchantPayments: true,
      internationalTransfers: false, // Coming soon
    };

    switch (environment) {
      case 'development':
        return {
          ...baseFeatures,
          debugMenu: true,
          mockData: true,
          developerTools: true,
          voicePayments: true, // Enable for testing
          internationalTransfers: true, // Enable for development
        };
      case 'staging':
        return {
          ...baseFeatures,
          debugMenu: false,
          mockData: false,
          developerTools: false,
          voicePayments: true, // Test in staging
        };
      case 'production':
        return {
          ...baseFeatures,
          debugMenu: false,
          mockData: false,
          developerTools: false,
        };
      default:
        return baseFeatures;
    }
  }

  /**
   * Get app configuration
   */
  public async getConfig(): Promise<AppConfig> {
    if (!this.config) {
      await this.initializeConfig();
    }
    return this.config!;
  }

  /**
   * Get app version
   */
  public async getVersion(): Promise<string> {
    const config = await this.getConfig();
    return config.version;
  }

  /**
   * Get build number
   */
  public async getBuildNumber(): Promise<string> {
    const config = await this.getConfig();
    return config.buildNumber;
  }

  /**
   * Get full version string (version + build)
   */
  public async getFullVersion(): Promise<string> {
    const config = await this.getConfig();
    return `${config.version} (${config.buildNumber})`;
  }

  /**
   * Get build information
   */
  public async getBuildInfo(): Promise<BuildInfo> {
    if (!this.buildInfo) {
      await this.initializeBuildInfo();
    }
    return this.buildInfo!;
  }

  /**
   * Check if feature is enabled
   */
  public async isFeatureEnabled(featureName: string): Promise<boolean> {
    const config = await this.getConfig();
    return config.features[featureName] === true;
  }

  /**
   * Get API base URL
   */
  public async getApiBaseUrl(): Promise<string> {
    const config = await this.getConfig();
    return config.apiBaseUrl;
  }

  /**
   * Get current environment
   */
  public async getEnvironment(): Promise<string> {
    const config = await this.getConfig();
    return config.environment;
  }

  /**
   * Check if app is in debug mode
   */
  public isDebugMode(): boolean {
    return __DEV__;
  }

  /**
   * Get app headers for API requests
   */
  public async getAppHeaders(): Promise<{ [key: string]: string }> {
    const config = await this.getConfig();
    const buildInfo = await this.getBuildInfo();
    
    return {
      'X-App-Version': config.version,
      'X-App-Build': config.buildNumber,
      'X-App-Platform': Platform.OS,
      'X-App-Environment': config.environment,
      'X-Build-Date': buildInfo.buildDate,
      'User-Agent': `${config.appName}/${config.version} (${Platform.OS} ${Platform.Version})`,
    };
  }

  /**
   * Update feature flag (for remote config)
   */
  public async updateFeatureFlag(featureName: string, enabled: boolean): Promise<void> {
    if (!this.config) {
      await this.initializeConfig();
    }
    
    this.config!.features[featureName] = enabled;
    
    // Store updated feature flags locally
    try {
      await AsyncStorage.setItem(
        'feature_flags',
        JSON.stringify(this.config!.features)
      );
    } catch (error) {
      console.error('Failed to store feature flags:', error);
    }
  }

  /**
   * Load feature flags from remote config
   */
  public async loadRemoteFeatureFlags(): Promise<void> {
    try {
      // This would typically fetch from a remote config service
      const storedFlags = await AsyncStorage.getItem('feature_flags');
      
      if (storedFlags && this.config) {
        const remoteFlags = JSON.parse(storedFlags);
        this.config.features = { ...this.config.features, ...remoteFlags };
      }
    } catch (error) {
      console.error('Failed to load remote feature flags:', error);
    }
  }

  /**
   * Check if app update is available
   */
  public async checkForUpdates(): Promise<{
    updateAvailable: boolean;
    latestVersion?: string;
    isForced?: boolean;
    releaseNotes?: string;
  }> {
    try {
      // This would typically call an update check service
      // For now, return a mock response
      const currentVersion = await this.getVersion();
      
      return {
        updateAvailable: false,
        latestVersion: currentVersion,
        isForced: false,
        releaseNotes: 'No updates available',
      };
    } catch (error) {
      console.error('Failed to check for updates:', error);
      return {
        updateAvailable: false,
      };
    }
  }

  /**
   * Get diagnostic information
   */
  public async getDiagnosticInfo(): Promise<{
    app: AppConfig;
    build: BuildInfo;
    device: any;
  }> {
    const config = await this.getConfig();
    const buildInfo = await this.getBuildInfo();
    
    const deviceInfo = {
      deviceId: await DeviceInfo.getUniqueId(),
      deviceName: await DeviceInfo.getDeviceName(),
      systemName: await DeviceInfo.getSystemName(),
      systemVersion: await DeviceInfo.getSystemVersion(),
      model: await DeviceInfo.getModel(),
      brand: await DeviceInfo.getBrand(),
      isEmulator: await DeviceInfo.isEmulator(),
      isTablet: await DeviceInfo.isTablet(),
    };
    
    return {
      app: config,
      build: buildInfo,
      device: deviceInfo,
    };
  }
}

export default AppConfigService.getInstance();
export { AppConfig, BuildInfo };
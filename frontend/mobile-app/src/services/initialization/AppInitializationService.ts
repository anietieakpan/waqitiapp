import VaultConfigService from '../config/VaultConfigService';
import { ApiService } from '../ApiService';
import LocationPaymentService from '../location/LocationPaymentService';

export interface InitializationResult {
  success: boolean;
  errors: string[];
  warnings: string[];
  duration: number;
}

class AppInitializationService {
  private isInitialized = false;
  private initializationPromise: Promise<InitializationResult> | null = null;

  /**
   * Initialize all app services
   * This should be called early in the app lifecycle
   */
  async initialize(): Promise<InitializationResult> {
    // Prevent multiple simultaneous initializations
    if (this.initializationPromise) {
      return this.initializationPromise;
    }

    this.initializationPromise = this.performInitialization();
    return this.initializationPromise;
  }

  /**
   * Check if app is fully initialized
   */
  isAppInitialized(): boolean {
    return this.isInitialized;
  }

  /**
   * Re-initialize services (useful after network reconnection)
   */
  async reinitialize(): Promise<InitializationResult> {
    this.isInitialized = false;
    this.initializationPromise = null;
    return this.initialize();
  }

  private async performInitialization(): Promise<InitializationResult> {
    const startTime = Date.now();
    const errors: string[] = [];
    const warnings: string[] = [];

    console.log('Starting app initialization...');

    try {
      // Step 1: Initialize API Service
      console.log('Initializing API Service...');
      await this.initializeApiService();

      // Step 2: Initialize Vault Configuration Service
      console.log('Initializing Vault Configuration Service...');
      try {
        await VaultConfigService.initialize();
        console.log('✅ Vault Configuration Service initialized');
      } catch (error) {
        const errorMsg = `Failed to initialize Vault Configuration Service: ${error}`;
        errors.push(errorMsg);
        console.error('❌', errorMsg);
      }

      // Step 3: Initialize Location Services (if enabled)
      console.log('Initializing Location Services...');
      try {
        const locationInitialized = await LocationPaymentService.initialize();
        if (locationInitialized) {
          console.log('✅ Location Services initialized');
        } else {
          warnings.push('Location Services initialization failed - continuing without location features');
          console.warn('⚠️ Location Services initialization failed');
        }
      } catch (error) {
        warnings.push(`Location Services error: ${error}`);
        console.warn('⚠️ Location Services error:', error);
      }

      // Step 4: Validate Critical Configuration
      console.log('Validating critical configuration...');
      await this.validateCriticalConfig(warnings);

      // Step 5: Perform health checks
      console.log('Performing health checks...');
      await this.performHealthChecks(warnings);

      const duration = Date.now() - startTime;
      this.isInitialized = errors.length === 0;

      const result: InitializationResult = {
        success: this.isInitialized,
        errors,
        warnings,
        duration,
      };

      if (this.isInitialized) {
        console.log(`✅ App initialization completed successfully in ${duration}ms`);
        if (warnings.length > 0) {
          console.warn(`⚠️ Initialization completed with ${warnings.length} warnings:`, warnings);
        }
      } else {
        console.error(`❌ App initialization failed with ${errors.length} errors:`, errors);
      }

      return result;

    } catch (error) {
      const duration = Date.now() - startTime;
      const errorMsg = `Critical initialization error: ${error}`;
      errors.push(errorMsg);
      console.error('❌ Critical initialization error:', error);

      return {
        success: false,
        errors,
        warnings,
        duration,
      };
    }
  }

  private async initializeApiService(): Promise<void> {
    try {
      // Initialize API service with base configuration
      await ApiService.initialize({
        baseURL: 'https://api.example.com', // This could come from environment
        timeout: 30000,
        retryAttempts: 3,
      });
      console.log('✅ API Service initialized');
    } catch (error) {
      throw new Error(`API Service initialization failed: ${error}`);
    }
  }

  private async validateCriticalConfig(warnings: string[]): Promise<void> {
    try {
      // Check Google Maps configuration
      const googleMapsConfigured = await VaultConfigService.isGoogleMapsConfigured();
      if (!googleMapsConfigured) {
        warnings.push('Google Maps API not configured - location features will be limited');
      }

      // Get debug info from VaultConfigService
      const vaultDebug = VaultConfigService.getDebugInfo();
      console.log('Vault Configuration Debug Info:', vaultDebug);

      console.log('✅ Configuration validation completed');
    } catch (error) {
      warnings.push(`Configuration validation error: ${error}`);
    }
  }

  private async performHealthChecks(warnings: string[]): Promise<void> {
    try {
      // Check API connectivity
      const apiHealthy = await this.checkApiHealth();
      if (!apiHealthy) {
        warnings.push('API health check failed - some features may not work');
      }

      // Check device capabilities
      await this.checkDeviceCapabilities(warnings);

      console.log('✅ Health checks completed');
    } catch (error) {
      warnings.push(`Health check error: ${error}`);
    }
  }

  private async checkApiHealth(): Promise<boolean> {
    try {
      const response = await ApiService.get('/api/v1/health');
      return response.success;
    } catch (error) {
      console.warn('API health check failed:', error);
      return false;
    }
  }

  private async checkDeviceCapabilities(warnings: string[]): Promise<void> {
    // Check if required device features are available
    
    // Location services
    try {
      const { Platform } = require('react-native');
      if (Platform.OS === 'ios') {
        // iOS-specific checks
      } else if (Platform.OS === 'android') {
        // Android-specific checks
      }
    } catch (error) {
      warnings.push(`Device capability check failed: ${error}`);
    }

    // Camera availability (for QR scanning)
    // Biometric availability (for secure authentication)
    // Network connectivity
    // Storage availability
  }

  /**
   * Get initialization status and debug information
   */
  getInitializationStatus(): object {
    return {
      initialized: this.isInitialized,
      timestamp: Date.now(),
      vaultDebug: this.isInitialized ? VaultConfigService.getDebugInfo() : null,
    };
  }
}

export default new AppInitializationService();
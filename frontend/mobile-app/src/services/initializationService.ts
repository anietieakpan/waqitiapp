/**
 * Initialization Service
 * Handles app initialization and setup
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import DeviceInfo from 'react-native-device-info';
import AppConfigService from './AppConfigService';
import { ApiService } from './ApiService';
import SecureNetworkingService from './SecureNetworkingService';
import CertificatePinningService from './CertificatePinningService';
import crashlyticsService from './crashlyticsService';
import analyticsService from './analyticsService';

interface InitializationResult {
  success: boolean;
  initializedServices: string[];
  failedServices: string[];
  errors: Error[];
}

/**
 * Initialize core services in the correct order
 */
export const initializeServices = async (): Promise<InitializationResult> => {
  const result: InitializationResult = {
    success: true,
    initializedServices: [],
    failedServices: [],
    errors: []
  };

  console.log('Starting core services initialization...');

  const services = [
    {
      name: 'AppConfigService',
      init: async () => {
        const config = await AppConfigService.getConfig();
        console.log('AppConfigService initialized with environment:', config.environment);
      }
    },
    {
      name: 'CrashReporting',
      init: async () => {
        await crashlyticsService.initialize();
        console.log('Crash reporting initialized');
      }
    },
    {
      name: 'Analytics',
      init: async () => {
        await analyticsService.initialize();
        console.log('Analytics service initialized');
      }
    },
    {
      name: 'CertificatePinning',
      init: async () => {
        await CertificatePinningService.initialize();
        console.log('Certificate pinning initialized');
      }
    },
    {
      name: 'SecureNetworking',
      init: async () => {
        await SecureNetworkingService.initialize();
        console.log('Secure networking initialized');
      }
    },
    {
      name: 'AsyncStorage',
      init: async () => {
        // Test AsyncStorage availability
        await AsyncStorage.setItem('init_test', 'test');
        await AsyncStorage.removeItem('init_test');
        console.log('AsyncStorage initialized and tested');
      }
    },
    {
      name: 'DeviceInfo',
      init: async () => {
        // Initialize device information
        const deviceId = await DeviceInfo.getUniqueId();
        const appVersion = await DeviceInfo.getVersion();
        await AsyncStorage.setItem('device_id', deviceId);
        console.log(`Device info initialized - ID: ${deviceId}, App Version: ${appVersion}`);
      }
    },
    {
      name: 'NetworkInterceptors',
      init: async () => {
        // Network interceptors are already set up in ApiService and SecureNetworkingService
        // Just ensure they're configured properly
        const baseUrl = ApiService.getApiBaseURL();
        console.log(`Network interceptors configured for ${baseUrl}`);
      }
    }
  ];

  // Initialize services sequentially to avoid race conditions
  for (const service of services) {
    try {
      await service.init();
      result.initializedServices.push(service.name);
    } catch (error) {
      console.error(`Failed to initialize ${service.name}:`, error);
      result.failedServices.push(service.name);
      result.errors.push(error as Error);
      result.success = false;
      
      // Continue with other services even if one fails
      continue;
    }
  }

  // Log initialization results
  console.log('Service initialization completed:');
  console.log('✅ Initialized:', result.initializedServices.join(', '));
  
  if (result.failedServices.length > 0) {
    console.warn('❌ Failed:', result.failedServices.join(', '));
  }

  // Track initialization completion
  try {
    await analyticsService.trackEvent('app_initialization_completed', {
      success: result.success,
      initializedServices: result.initializedServices.length,
      failedServices: result.failedServices.length,
      totalServices: services.length
    });
  } catch (error) {
    console.warn('Failed to track initialization event:', error);
  }

  return result;
};

/**
 * Initialize app state persistence
 */
export const initializeStatePersistence = async (): Promise<void> => {
  try {
    console.log('Initializing state persistence...');
    
    // Set up periodic state cleanup
    const cleanupOldState = async () => {
      try {
        const keys = await AsyncStorage.getAllKeys();
        const now = Date.now();
        const maxAge = 30 * 24 * 60 * 60 * 1000; // 30 days
        
        for (const key of keys) {
          if (key.startsWith('temp_') || key.startsWith('cache_')) {
            const value = await AsyncStorage.getItem(key);
            if (value) {
              try {
                const data = JSON.parse(value);
                if (data.timestamp && (now - data.timestamp) > maxAge) {
                  await AsyncStorage.removeItem(key);
                  console.log(`Cleaned up old state: ${key}`);
                }
              } catch (e) {
                // Not JSON, check if it's an old key and remove it
                await AsyncStorage.removeItem(key);
              }
            }
          }
        }
      } catch (error) {
        console.warn('State cleanup failed:', error);
      }
    };

    // Run initial cleanup
    await cleanupOldState();
    
    // Schedule periodic cleanup (every 24 hours)
    setInterval(cleanupOldState, 24 * 60 * 60 * 1000);
    
    console.log('State persistence initialized');
  } catch (error) {
    console.error('Failed to initialize state persistence:', error);
    throw error;
  }
};

/**
 * Perform health checks on critical services
 */
export const performHealthChecks = async (): Promise<{
  overall: boolean;
  checks: Array<{ service: string; healthy: boolean; error?: string }>;
}> => {
  const checks = [];
  let overall = true;

  // Check API connectivity
  try {
    await ApiService.healthCheck();
    checks.push({ service: 'API', healthy: true });
  } catch (error) {
    checks.push({ service: 'API', healthy: false, error: error.message });
    overall = false;
  }

  // Check AsyncStorage
  try {
    await AsyncStorage.setItem('health_check', 'test');
    await AsyncStorage.getItem('health_check');
    await AsyncStorage.removeItem('health_check');
    checks.push({ service: 'AsyncStorage', healthy: true });
  } catch (error) {
    checks.push({ service: 'AsyncStorage', healthy: false, error: error.message });
    overall = false;
  }

  // Check app configuration
  try {
    const config = await AppConfigService.getConfig();
    if (config.version) {
      checks.push({ service: 'AppConfig', healthy: true });
    } else {
      throw new Error('Invalid configuration');
    }
  } catch (error) {
    checks.push({ service: 'AppConfig', healthy: false, error: error.message });
    overall = false;
  }

  return { overall, checks };
};

export default {
  initializeServices,
  initializeStatePersistence,
  performHealthChecks,
};
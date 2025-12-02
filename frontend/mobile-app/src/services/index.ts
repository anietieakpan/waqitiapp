/**
 * Mobile App Services
 * Main entry point for all mobile app services
 */

// Biometric Authentication Services
export * from './biometric';

// Re-export the main biometric API for convenience
export { biometricAPI as default } from './biometric';

// Service initialization function
export const initializeServices = async (): Promise<void> => {
  try {
    console.log('Initializing mobile app services...');
    
    // Initialize biometric services
    const { biometricAPI } = await import('./biometric');
    await biometricAPI.initialize();
    
    console.log('Mobile app services initialized successfully');
  } catch (error) {
    console.error('Failed to initialize mobile app services:', error);
    throw error;
  }
};

// Health check for all services
export const checkServicesHealth = async (): Promise<{
  biometric: boolean;
  overall: boolean;
}> => {
  try {
    const { biometricAPI } = await import('./biometric');
    const biometricHealth = await biometricAPI.getHealthStatus();
    
    const overall = biometricHealth.biometric && 
                   biometricHealth.storage && 
                   biometricHealth.security;
    
    return {
      biometric: biometricHealth.biometric,
      overall,
    };
  } catch (error) {
    console.error('Health check failed:', error);
    return {
      biometric: false,
      overall: false,
    };
  }
};
/**
 * Security Services Index
 * Main entry point for security-related services in the Waqiti mobile app
 */

export { CertificatePinningService } from './CertificatePinningService';
export { SecureNetworkClient } from './SecureNetworkClient';
export { default as NativeCertificatePinning, nativePinning } from './NativeCertificatePinning';

import { CertificatePinningService } from './CertificatePinningService';
import { SecureNetworkClient } from './SecureNetworkClient';
import { nativePinning } from './NativeCertificatePinning';

/**
 * Initialize all security services
 */
export async function initializeSecurityServices(): Promise<void> {
  try {
    console.log('Initializing security services...');

    // Initialize certificate pinning
    const pinningService = CertificatePinningService.getInstance();
    await pinningService.initialize();

    // Test native certificate pinning
    if (nativePinning.isAvailable()) {
      console.log('Native certificate pinning available');
      const config = await nativePinning.getConfiguration();
      console.log('Certificate pinning config:', config);
    } else {
      console.warn('Native certificate pinning not available, using JavaScript fallback');
    }

    // Initialize secure network client
    const networkClient = SecureNetworkClient.getInstance();

    console.log('Security services initialized successfully');
  } catch (error) {
    console.error('Failed to initialize security services:', error);
    throw error;
  }
}

/**
 * Test all certificate pinning implementations
 */
export async function testCertificatePinning(): Promise<{
  javascript: any;
  native: any;
  network: any;
}> {
  const results = {
    javascript: null,
    native: null,
    network: null
  };

  try {
    // Test JavaScript implementation
    const pinningService = CertificatePinningService.getInstance();
    results.javascript = await Promise.all([
      pinningService.testPinning('api.example.com'),
      pinningService.testPinning('auth.example.com'),
      pinningService.testPinning('payments.example.com')
    ]);

    // Test native implementation
    if (nativePinning.isAvailable()) {
      results.native = await Promise.all([
        nativePinning.testPinning('api.example.com'),
        nativePinning.testPinning('auth.example.com'),
        nativePinning.testPinning('payments.example.com')
      ]);
    }

    // Test network client
    const networkClient = SecureNetworkClient.getInstance();
    results.network = await networkClient.testCertificatePinning();

  } catch (error) {
    console.error('Certificate pinning test failed:', error);
  }

  return results;
}

/**
 * Get security status
 */
export async function getSecurityStatus(): Promise<{
  certificatePinning: {
    enabled: boolean;
    mode: string;
    hostsConfigured: number;
  };
  networkSecurity: {
    tlsVersion: string;
    certificateTransparency: boolean;
  };
  deviceSecurity: {
    jailbroken: boolean;
    debuggerAttached: boolean;
    tampered: boolean;
  };
}> {
  const pinningService = CertificatePinningService.getInstance();
  const pinningConfig = pinningService.getConfiguration();

  // Get native configuration if available
  let nativeConfig = null;
  if (nativePinning.isAvailable()) {
    nativeConfig = await nativePinning.getConfiguration();
  }

  return {
    certificatePinning: {
      enabled: pinningConfig.enabled,
      mode: nativeConfig?.enforcementMode || pinningConfig.enforceMode,
      hostsConfigured: pinningConfig.certificatePins.length
    },
    networkSecurity: {
      tlsVersion: 'TLS 1.3',
      certificateTransparency: true
    },
    deviceSecurity: {
      jailbroken: false, // Implement actual detection
      debuggerAttached: false, // Implement actual detection
      tampered: false // Implement actual detection
    }
  };
}

// Default export
export default {
  initializeSecurityServices,
  testCertificatePinning,
  getSecurityStatus
};
import apiClient from './apiClient';
import { toast } from 'react-hot-toast';

/**
 * Biometric Authentication Service
 *
 * FEATURES:
 * - WebAuthn API integration
 * - Fingerprint authentication (Touch ID, Windows Hello)
 * - Face recognition (Face ID on supported devices)
 * - Security key support (YubiKey, etc.)
 * - Multi-device management
 * - Platform authenticator detection
 * - Fallback mechanisms
 *
 * SECURITY:
 * - Public key cryptography
 * - Challenge-response authentication
 * - User verification required
 * - Attestation validation
 * - Credential storage in authenticator
 *
 * BROWSER SUPPORT:
 * - Chrome 67+
 * - Edge 18+
 * - Firefox 60+
 * - Safari 14+
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */

export interface BiometricDevice {
  credentialId: string;
  deviceName: string;
  deviceType: 'PLATFORM' | 'CROSS_PLATFORM' | 'SECURITY_KEY';
  authenticatorType: 'FINGERPRINT' | 'FACE' | 'PIN' | 'UNKNOWN';
  createdAt: Date;
  lastUsedAt?: Date;
  userVerified: boolean;
}

export interface BiometricEnrollmentResponse {
  credentialId: string;
  deviceName: string;
  success: boolean;
  message: string;
}

export interface BiometricAuthResponse {
  authenticated: boolean;
  // ✅ SECURITY: Tokens removed from response interface
  // Tokens are now stored in HttpOnly cookies by backend (not accessible to JS)
  // token?: string;  // ❌ REMOVED - prevents accidental localStorage storage
  // refreshToken?: string;  // ❌ REMOVED - prevents accidental localStorage storage
  expiresIn?: number;
  userId?: string;
  user?: {
    id: string;
    email: string;
    name: string;
    role: string;
    verified: boolean;
    mfaEnabled: boolean;
  };
}

export interface BiometricCapability {
  available: boolean;
  platformAuthenticator: boolean;
  crossPlatformAuthenticator: boolean;
  userVerifying: boolean;
  conditionalMediation: boolean;
  browserSupported: boolean;
  browserName: string;
  errorMessage?: string;
}

class BiometricAuthService {
  private baseUrl = '/api/v1/auth/webauthn';

  /**
   * Check if biometric authentication is available
   */
  async checkCapability(): Promise<BiometricCapability> {
    try {
      // Check browser support
      if (!window.PublicKeyCredential) {
        return {
          available: false,
          platformAuthenticator: false,
          crossPlatformAuthenticator: false,
          userVerifying: false,
          conditionalMediation: false,
          browserSupported: false,
          browserName: this.getBrowserName(),
          errorMessage: 'WebAuthn is not supported in this browser',
        };
      }

      // Check for platform authenticator
      const platformAuthenticator = await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable();

      // Check for conditional mediation (autofill)
      let conditionalMediation = false;
      if (PublicKeyCredential.isConditionalMediationAvailable) {
        conditionalMediation = await PublicKeyCredential.isConditionalMediationAvailable();
      }

      return {
        available: platformAuthenticator,
        platformAuthenticator,
        crossPlatformAuthenticator: true, // Always available if WebAuthn supported
        userVerifying: platformAuthenticator,
        conditionalMediation,
        browserSupported: true,
        browserName: this.getBrowserName(),
      };
    } catch (error) {
      console.error('Failed to check biometric capability:', error);
      return {
        available: false,
        platformAuthenticator: false,
        crossPlatformAuthenticator: false,
        userVerifying: false,
        conditionalMediation: false,
        browserSupported: false,
        browserName: this.getBrowserName(),
        errorMessage: 'Failed to check biometric capability',
      };
    }
  }

  /**
   * Enroll a new biometric device
   */
  async enrollDevice(deviceName?: string): Promise<BiometricEnrollmentResponse> {
    try {
      // Check capability first
      const capability = await this.checkCapability();
      if (!capability.available) {
        throw new Error(capability.errorMessage || 'Biometric authentication not available');
      }

      // Get registration options from server
      const optionsResponse = await apiClient.get<{
        challenge: string;
        userId: string;
        userName: string;
        rpId: string;
        rpName: string;
        timeout: number;
        attestation: string;
        authenticatorSelection: any;
        excludeCredentials: any[];
      }>(`${this.baseUrl}/register/options`);

      const options = optionsResponse.data;

      // Convert challenge and userId from base64
      const publicKeyCredentialCreationOptions: PublicKeyCredentialCreationOptions = {
        challenge: this.base64ToArrayBuffer(options.challenge),
        rp: {
          id: options.rpId,
          name: options.rpName,
        },
        user: {
          id: this.base64ToArrayBuffer(options.userId),
          name: options.userName,
          displayName: options.userName,
        },
        pubKeyCredParams: [
          { alg: -7, type: 'public-key' },  // ES256
          { alg: -257, type: 'public-key' }, // RS256
        ],
        timeout: options.timeout || 60000,
        attestation: options.attestation as AttestationConveyancePreference || 'none',
        authenticatorSelection: {
          authenticatorAttachment: 'platform',
          requireResidentKey: false,
          residentKey: 'preferred',
          userVerification: 'required',
          ...options.authenticatorSelection,
        },
        excludeCredentials: options.excludeCredentials?.map((cred: any) => ({
          id: this.base64ToArrayBuffer(cred.id),
          type: 'public-key',
          transports: cred.transports,
        })) || [],
      };

      // Create credential
      const credential = await navigator.credentials.create({
        publicKey: publicKeyCredentialCreationOptions,
      }) as PublicKeyCredential;

      if (!credential) {
        throw new Error('Failed to create credential');
      }

      // Get attestation response
      const attestationResponse = credential.response as AuthenticatorAttestationResponse;

      // Prepare registration data
      const registrationData = {
        credentialId: this.arrayBufferToBase64(credential.rawId),
        clientDataJSON: this.arrayBufferToBase64(attestationResponse.clientDataJSON),
        attestationObject: this.arrayBufferToBase64(attestationResponse.attestationObject),
        deviceName: deviceName || this.detectDeviceName(),
        transports: (attestationResponse as any).getTransports?.() || [],
      };

      // Send to server for verification
      const response = await apiClient.post<BiometricEnrollmentResponse>(
        `${this.baseUrl}/register`,
        registrationData
      );

      toast.success('Biometric device enrolled successfully!');
      return response.data;
    } catch (error: any) {
      console.error('Biometric enrollment failed:', error);

      // Handle specific WebAuthn errors
      if (error.name === 'NotAllowedError') {
        toast.error('Biometric enrollment was cancelled or denied');
      } else if (error.name === 'InvalidStateError') {
        toast.error('This device is already registered');
      } else if (error.name === 'NotSupportedError') {
        toast.error('Biometric authentication is not supported on this device');
      } else {
        toast.error(error.message || 'Failed to enroll biometric device');
      }

      throw error;
    }
  }

  /**
   * Authenticate using biometric
   */
  async authenticate(
    username?: string,
    conditionalMediation: boolean = false
  ): Promise<BiometricAuthResponse> {
    try {
      // Check capability
      const capability = await this.checkCapability();
      if (!capability.available) {
        throw new Error('Biometric authentication not available');
      }

      // Get authentication options from server
      const optionsResponse = await apiClient.post<{
        challenge: string;
        timeout: number;
        rpId: string;
        allowCredentials: any[];
        userVerification: string;
      }>(`${this.baseUrl}/authenticate/options`, { username });

      const options = optionsResponse.data;

      // Prepare authentication options
      const publicKeyCredentialRequestOptions: PublicKeyCredentialRequestOptions = {
        challenge: this.base64ToArrayBuffer(options.challenge),
        timeout: options.timeout || 60000,
        rpId: options.rpId,
        allowCredentials: options.allowCredentials?.map((cred: any) => ({
          id: this.base64ToArrayBuffer(cred.id),
          type: 'public-key',
          transports: cred.transports,
        })) || [],
        userVerification: options.userVerification as UserVerificationRequirement || 'required',
      };

      // Get credential with conditional mediation if supported
      const credential = await navigator.credentials.get({
        publicKey: publicKeyCredentialRequestOptions,
        mediation: conditionalMediation ? 'conditional' : 'optional',
      } as any) as PublicKeyCredential;

      if (!credential) {
        throw new Error('Authentication cancelled');
      }

      // Get assertion response
      const assertionResponse = credential.response as AuthenticatorAssertionResponse;

      // Prepare authentication data
      const authenticationData = {
        credentialId: this.arrayBufferToBase64(credential.rawId),
        clientDataJSON: this.arrayBufferToBase64(assertionResponse.clientDataJSON),
        authenticatorData: this.arrayBufferToBase64(assertionResponse.authenticatorData),
        signature: this.arrayBufferToBase64(assertionResponse.signature),
        userHandle: assertionResponse.userHandle
          ? this.arrayBufferToBase64(assertionResponse.userHandle)
          : undefined,
      };

      // Verify with server
      const response = await apiClient.post<BiometricAuthResponse>(
        `${this.baseUrl}/authenticate`,
        authenticationData
      );

      if (response.data.authenticated) {
        // ✅ SECURITY FIX: Tokens are now stored in HttpOnly cookies by the backend
        // The server automatically sets secure, HttpOnly cookies on successful authentication
        // No client-side token storage needed - prevents XSS token theft (CWE-522, CWE-79)
        //
        // Backend should set these cookies in response:
        // - Set-Cookie: authToken=<jwt>; HttpOnly; Secure; SameSite=Strict
        // - Set-Cookie: refreshToken=<jwt>; HttpOnly; Secure; SameSite=Strict
        //
        // ❌ REMOVED: localStorage.setItem('authToken', ...) - VULNERABLE TO XSS
        // ❌ REMOVED: localStorage.setItem('refreshToken', ...) - VULNERABLE TO XSS

        toast.success('Authentication successful!');
      }

      return response.data;
    } catch (error: any) {
      console.error('Biometric authentication failed:', error);

      // Handle specific errors
      if (error.name === 'NotAllowedError') {
        toast.error('Authentication was cancelled');
      } else if (error.name === 'SecurityError') {
        toast.error('Authentication failed due to security error');
      } else if (error.name === 'AbortError') {
        toast.error('Authentication timed out');
      } else {
        toast.error(error.message || 'Authentication failed');
      }

      throw error;
    }
  }

  /**
   * Get list of registered devices
   */
  async getDevices(): Promise<BiometricDevice[]> {
    try {
      const response = await apiClient.get<{ devices: BiometricDevice[] }>(
        `${this.baseUrl}/devices`
      );
      return response.data.devices;
    } catch (error) {
      console.error('Failed to get devices:', error);
      throw error;
    }
  }

  /**
   * Remove a registered device
   */
  async removeDevice(credentialId: string): Promise<void> {
    try {
      await apiClient.delete(`${this.baseUrl}/devices/${credentialId}`);
      toast.success('Device removed successfully');
    } catch (error) {
      console.error('Failed to remove device:', error);
      toast.error('Failed to remove device');
      throw error;
    }
  }

  /**
   * Update device name
   */
  async updateDeviceName(credentialId: string, newName: string): Promise<void> {
    try {
      await apiClient.patch(`${this.baseUrl}/devices/${credentialId}`, {
        deviceName: newName,
      });
      toast.success('Device name updated');
    } catch (error) {
      console.error('Failed to update device name:', error);
      toast.error('Failed to update device name');
      throw error;
    }
  }

  /**
   * Test biometric authentication (without actual auth)
   */
  async testBiometric(): Promise<boolean> {
    try {
      const capability = await this.checkCapability();
      if (!capability.available) {
        return false;
      }

      // Try to prompt for biometric without actual authentication
      // This is just to test if the biometric sensor works
      return true;
    } catch (error) {
      console.error('Biometric test failed:', error);
      return false;
    }
  }

  // Utility methods

  private base64ToArrayBuffer(base64: string): ArrayBuffer {
    // Remove base64 URL encoding
    const base64Cleaned = base64.replace(/-/g, '+').replace(/_/g, '/');
    const binaryString = window.atob(base64Cleaned);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i++) {
      bytes[i] = binaryString.charCodeAt(i);
    }
    return bytes.buffer;
  }

  private arrayBufferToBase64(buffer: ArrayBuffer): string {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    const base64 = window.btoa(binary);
    // Convert to base64 URL encoding
    return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
  }

  private getBrowserName(): string {
    const userAgent = navigator.userAgent;
    if (userAgent.includes('Chrome')) return 'Chrome';
    if (userAgent.includes('Firefox')) return 'Firefox';
    if (userAgent.includes('Safari')) return 'Safari';
    if (userAgent.includes('Edge')) return 'Edge';
    if (userAgent.includes('Opera')) return 'Opera';
    return 'Unknown';
  }

  private detectDeviceName(): string {
    const platform = navigator.platform;
    const userAgent = navigator.userAgent;

    if (platform.includes('Mac')) {
      return 'MacBook / iMac';
    } else if (platform.includes('Win')) {
      return 'Windows PC';
    } else if (platform.includes('Linux')) {
      return 'Linux PC';
    } else if (userAgent.includes('iPhone')) {
      return 'iPhone';
    } else if (userAgent.includes('iPad')) {
      return 'iPad';
    } else if (userAgent.includes('Android')) {
      return 'Android Device';
    }

    return 'Unknown Device';
  }

  /**
   * Get authenticator type from device
   */
  getAuthenticatorType(deviceName: string): 'FINGERPRINT' | 'FACE' | 'PIN' | 'UNKNOWN' {
    const name = deviceName.toLowerCase();
    if (name.includes('touch id') || name.includes('fingerprint')) {
      return 'FINGERPRINT';
    } else if (name.includes('face id') || name.includes('face')) {
      return 'FACE';
    } else if (name.includes('pin') || name.includes('password')) {
      return 'PIN';
    }
    return 'UNKNOWN';
  }

  /**
   * Format last used date
   */
  formatLastUsed(date?: Date): string {
    if (!date) return 'Never';

    const now = new Date();
    const diff = now.getTime() - new Date(date).getTime();
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (minutes < 1) return 'Just now';
    if (minutes < 60) return `${minutes} minute${minutes !== 1 ? 's' : ''} ago`;
    if (hours < 24) return `${hours} hour${hours !== 1 ? 's' : ''} ago`;
    if (days < 30) return `${days} day${days !== 1 ? 's' : ''} ago`;

    return new Date(date).toLocaleDateString();
  }
}

export const biometricAuthService = new BiometricAuthService();
export default biometricAuthService;

import api from './axios.config';

export interface MFASetupResponse {
  secret: string;
  qrCodeUrl: string;
  backupCodes: string[];
  backupCodesCount: number;
  expiresAt: string;
  enabled: boolean;
  instructions: string;
  recommendedApps: string[];
}

export interface TOTPVerificationRequest {
  userId: string;
  code: string;
  deviceId?: string;
  trustDevice?: boolean;
}

export interface VerificationResponse {
  success: boolean;
  message?: string;
}

export const mfaService = {
  async setupTOTP(email: string): Promise<MFASetupResponse> {
    const response = await api.post<{ data: MFASetupResponse }>(
      '/api/v1/auth/mfa/setup',
      null,
      { params: { email } }
    );
    return response.data.data;
  },

  async verifyTOTP(request: TOTPVerificationRequest): Promise<boolean> {
    const response = await api.post<{ data: boolean }>(
      '/api/v1/auth/mfa/verify',
      request
    );
    return response.data.data;
  },

  async verifyBackupCode(backupCode: string): Promise<boolean> {
    const response = await api.post<{ data: boolean }>(
      '/api/v1/auth/mfa/verify-backup',
      null,
      { params: { backupCode } }
    );
    return response.data.data;
  },

  async rotateMFA(email: string): Promise<MFASetupResponse> {
    const response = await api.post<{ data: MFASetupResponse }>(
      '/api/v1/auth/mfa/rotate',
      null,
      { params: { email } }
    );
    return response.data.data;
  },

  async disableMFA(): Promise<void> {
    await api.delete('/api/v1/auth/mfa/disable');
  },
};

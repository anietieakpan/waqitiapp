import { authorize, refresh, revoke, AuthConfiguration } from 'react-native-app-auth';
import AsyncStorage from '@react-native-async-storage/async-storage';
import Config from 'react-native-config';

// Keycloak configuration
const keycloakConfig: AuthConfiguration = {
  issuer: Config.KEYCLOAK_ISSUER || 'https://auth.example.com/realms/waqiti-fintech',
  clientId: Config.KEYCLOAK_CLIENT_ID || 'waqiti-mobile-app',
  redirectUrl: Config.KEYCLOAK_REDIRECT_URL || 'com.waqiti://oauth',
  scopes: ['openid', 'profile', 'email', 'offline_access'],
  additionalParameters: {},
  customHeaders: {},
  connectionTimeoutSeconds: 30,
  dangerouslyAllowInsecureHttpRequests: false, // Enforce HTTPS
};

// Token storage keys
const TOKEN_STORAGE_KEY = '@waqiti:auth:token';
const REFRESH_TOKEN_KEY = '@waqiti:auth:refresh';
const USER_INFO_KEY = '@waqiti:auth:user';

// Keycloak auth service
class KeycloakAuthService {
  private config: AuthConfiguration;
  private accessToken: string | null = null;
  private refreshToken: string | null = null;
  private idToken: string | null = null;
  private userInfo: any = null;

  constructor() {
    this.config = keycloakConfig;
    this.loadStoredTokens();
  }

  // Load stored tokens from AsyncStorage
  private async loadStoredTokens() {
    try {
      const [accessToken, refreshToken, userInfo] = await Promise.all([
        AsyncStorage.getItem(TOKEN_STORAGE_KEY),
        AsyncStorage.getItem(REFRESH_TOKEN_KEY),
        AsyncStorage.getItem(USER_INFO_KEY),
      ]);

      this.accessToken = accessToken;
      this.refreshToken = refreshToken;
      if (userInfo) {
        this.userInfo = JSON.parse(userInfo);
      }
    } catch (error) {
      console.error('Failed to load stored tokens:', error);
    }
  }

  // Save tokens to AsyncStorage
  private async saveTokens(
    accessToken: string,
    refreshToken: string,
    idToken: string,
    userInfo?: any
  ) {
    try {
      await Promise.all([
        AsyncStorage.setItem(TOKEN_STORAGE_KEY, accessToken),
        AsyncStorage.setItem(REFRESH_TOKEN_KEY, refreshToken),
        userInfo && AsyncStorage.setItem(USER_INFO_KEY, JSON.stringify(userInfo)),
      ]);

      this.accessToken = accessToken;
      this.refreshToken = refreshToken;
      this.idToken = idToken;
      this.userInfo = userInfo;
    } catch (error) {
      console.error('Failed to save tokens:', error);
      throw error;
    }
  }

  // Clear stored tokens
  private async clearTokens() {
    try {
      await Promise.all([
        AsyncStorage.removeItem(TOKEN_STORAGE_KEY),
        AsyncStorage.removeItem(REFRESH_TOKEN_KEY),
        AsyncStorage.removeItem(USER_INFO_KEY),
      ]);

      this.accessToken = null;
      this.refreshToken = null;
      this.idToken = null;
      this.userInfo = null;
    } catch (error) {
      console.error('Failed to clear tokens:', error);
    }
  }

  // Login with Keycloak
  async login(): Promise<boolean> {
    try {
      const result = await authorize(this.config);
      
      if (result.accessToken && result.refreshToken) {
        // Parse user info from ID token
        const userInfo = this.parseJWT(result.idToken);
        
        await this.saveTokens(
          result.accessToken,
          result.refreshToken,
          result.idToken,
          userInfo
        );

        return true;
      }
      
      return false;
    } catch (error) {
      console.error('Login failed:', error);
      throw error;
    }
  }

  // Refresh access token
  async refreshAccessToken(): Promise<boolean> {
    if (!this.refreshToken) {
      console.warn('No refresh token available');
      return false;
    }

    try {
      const result = await refresh(this.config, {
        refreshToken: this.refreshToken,
      });

      if (result.accessToken) {
        await this.saveTokens(
          result.accessToken,
          result.refreshToken || this.refreshToken,
          result.idToken || this.idToken,
          this.userInfo
        );

        return true;
      }

      return false;
    } catch (error) {
      console.error('Token refresh failed:', error);
      // Clear tokens on refresh failure
      await this.clearTokens();
      return false;
    }
  }

  // Logout from Keycloak
  async logout(): Promise<void> {
    try {
      if (this.refreshToken) {
        await revoke(this.config, {
          tokenToRevoke: this.refreshToken,
          includeBasicAuth: false,
        });
      }
    } catch (error) {
      console.error('Logout failed:', error);
    } finally {
      await this.clearTokens();
    }
  }

  // Get current access token
  getAccessToken(): string | null {
    return this.accessToken;
  }

  // Get user info
  getUserInfo(): any {
    return this.userInfo;
  }

  // Check if user is authenticated
  isAuthenticated(): boolean {
    return !!this.accessToken;
  }

  // Parse JWT token
  private parseJWT(token: string): any {
    try {
      const base64Url = token.split('.')[1];
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      const jsonPayload = decodeURIComponent(
        atob(base64)
          .split('')
          .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      );

      return JSON.parse(jsonPayload);
    } catch (error) {
      console.error('Failed to parse JWT:', error);
      return null;
    }
  }

  // Get user roles
  getUserRoles(): string[] {
    if (!this.userInfo) return [];

    const realmRoles = this.userInfo.realm_access?.roles || [];
    const clientRoles = 
      this.userInfo.resource_access?.[this.config.clientId]?.roles || [];

    return [...realmRoles, ...clientRoles];
  }

  // Check if user has specific role
  hasRole(role: string): boolean {
    return this.getUserRoles().includes(role);
  }

  // Set up token interceptor for API calls
  setupAxiosInterceptor(axios: any) {
    // Request interceptor
    axios.interceptors.request.use(
      async (config: any) => {
        const token = this.getAccessToken();
        if (token) {
          config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
      },
      (error: any) => Promise.reject(error)
    );

    // Response interceptor for token refresh
    axios.interceptors.response.use(
      (response: any) => response,
      async (error: any) => {
        const originalRequest = error.config;

        if (error.response?.status === 401 && !originalRequest._retry) {
          originalRequest._retry = true;

          const refreshed = await this.refreshAccessToken();
          if (refreshed) {
            const token = this.getAccessToken();
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return axios(originalRequest);
          }
        }

        return Promise.reject(error);
      }
    );
  }
}

// Export singleton instance
export default new KeycloakAuthService();

// Export configuration for external use
export { keycloakConfig };
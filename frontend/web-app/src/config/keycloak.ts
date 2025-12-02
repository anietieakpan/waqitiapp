import Keycloak from 'keycloak-js';

// Keycloak configuration
const keycloakConfig = {
  url: process.env.REACT_APP_KEYCLOAK_URL || 'https://auth.waqiti.com',
  realm: process.env.REACT_APP_KEYCLOAK_REALM || 'waqiti-fintech',
  clientId: process.env.REACT_APP_KEYCLOAK_CLIENT_ID || 'waqiti-web-app',
};

// Initialize Keycloak instance
const keycloak = new Keycloak(keycloakConfig);

// Keycloak initialization options
export const keycloakInitOptions = {
  onLoad: 'check-sso' as const,
  checkLoginIframe: true,
  pkceMethod: 'S256',
  silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
  enableLogging: process.env.NODE_ENV === 'development',
};

// Helper function to get user roles
export const getUserRoles = (): string[] => {
  if (!keycloak.tokenParsed) return [];
  
  const realmRoles = keycloak.tokenParsed.realm_access?.roles || [];
  const resourceRoles = keycloak.tokenParsed.resource_access?.[keycloakConfig.clientId]?.roles || [];
  
  return [...realmRoles, ...resourceRoles];
};

// Helper function to check if user has a specific role
export const hasRole = (role: string): boolean => {
  return getUserRoles().includes(role);
};

// Helper function to get user profile
export const getUserProfile = () => {
  if (!keycloak.tokenParsed) return null;
  
  return {
    id: keycloak.tokenParsed.sub,
    username: keycloak.tokenParsed.preferred_username,
    email: keycloak.tokenParsed.email,
    firstName: keycloak.tokenParsed.given_name,
    lastName: keycloak.tokenParsed.family_name,
    fullName: keycloak.tokenParsed.name,
    emailVerified: keycloak.tokenParsed.email_verified,
    roles: getUserRoles(),
  };
};

// Helper function to refresh token
export const refreshToken = async (minValidity = 30): Promise<boolean> => {
  try {
    const refreshed = await keycloak.updateToken(minValidity);
    if (refreshed) {
      console.log('Token refreshed successfully');
    }
    return refreshed;
  } catch (error) {
    console.error('Failed to refresh token:', error);
    return false;
  }
};

// Export the Keycloak instance
export default keycloak;
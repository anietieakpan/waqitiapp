/**
 * Theme Configuration
 * Material Design 3 theme for the mobile app
 */

import { MD3LightTheme, MD3DarkTheme } from 'react-native-paper';

// Custom colors
const customColors = {
  primary: '#6750A4',
  primaryContainer: '#EADDFF',
  secondary: '#625B71',
  secondaryContainer: '#E8DEF8',
  tertiary: '#7D5260',
  tertiaryContainer: '#FFD8E4',
  success: '#4CAF50',
  warning: '#FF9800',
  error: '#F44336',
  info: '#2196F3',
};

// Light theme
export const lightTheme = {
  ...MD3LightTheme,
  colors: {
    ...MD3LightTheme.colors,
    ...customColors,
    background: '#FFFBFE',
    surface: '#FFFBFE',
    surfaceVariant: '#F4EFF4',
    onSurface: '#1C1B1F',
    onSurfaceVariant: '#49454F',
    outline: '#79747E',
    outlineVariant: '#CAC4D0',
  },
};

// Dark theme
export const darkTheme = {
  ...MD3DarkTheme,
  colors: {
    ...MD3DarkTheme.colors,
    ...customColors,
    background: '#1C1B1F',
    surface: '#1C1B1F',
    surfaceVariant: '#49454F',
    onSurface: '#E6E1E5',
    onSurfaceVariant: '#CAC4D0',
    outline: '#938F99',
    outlineVariant: '#49454F',
  },
};

// Default theme (light)
export const theme = lightTheme;

export default theme;
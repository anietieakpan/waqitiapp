/**
 * Waqiti Design System - Color Tokens
 * Comprehensive color palette with semantic meanings
 */

export const primitiveColors = {
  // Primary Brand Colors
  blue: {
    50: '#E3F2FD',
    100: '#BBDEFB',
    200: '#90CAF9',
    300: '#64B5F6',
    400: '#42A5F5',
    500: '#2196F3', // Primary
    600: '#1E88E5',
    700: '#1976D2',
    800: '#1565C0',
    900: '#0D47A1',
  },
  
  // Secondary Brand Colors
  purple: {
    50: '#F3E5F5',
    100: '#E1BEE7',
    200: '#CE93D8',
    300: '#BA68C8',
    400: '#AB47BC',
    500: '#9C27B0', // Secondary
    600: '#8E24AA',
    700: '#7B1FA2',
    800: '#6A1B9A',
    900: '#4A148C',
  },
  
  // Accent Colors
  teal: {
    50: '#E0F2F1',
    100: '#B2DFDB',
    200: '#80CBC4',
    300: '#4DB6AC',
    400: '#26A69A',
    500: '#009688', // Accent
    600: '#00897B',
    700: '#00796B',
    800: '#00695C',
    900: '#004D40',
  },
  
  // Semantic Colors
  green: {
    50: '#E8F5E9',
    100: '#C8E6C9',
    200: '#A5D6A7',
    300: '#81C784',
    400: '#66BB6A',
    500: '#4CAF50', // Success
    600: '#43A047',
    700: '#388E3C',
    800: '#2E7D32',
    900: '#1B5E20',
  },
  
  red: {
    50: '#FFEBEE',
    100: '#FFCDD2',
    200: '#EF9A9A',
    300: '#E57373',
    400: '#EF5350',
    500: '#F44336', // Error
    600: '#E53935',
    700: '#D32F2F',
    800: '#C62828',
    900: '#B71C1C',
  },
  
  amber: {
    50: '#FFF8E1',
    100: '#FFECB3',
    200: '#FFE082',
    300: '#FFD54F',
    400: '#FFCA28',
    500: '#FFC107', // Warning
    600: '#FFB300',
    700: '#FFA000',
    800: '#FF8F00',
    900: '#FF6F00',
  },
  
  // Neutral Colors
  grey: {
    50: '#FAFAFA',
    100: '#F5F5F5',
    200: '#EEEEEE',
    300: '#E0E0E0',
    400: '#BDBDBD',
    500: '#9E9E9E',
    600: '#757575',
    700: '#616161',
    800: '#424242',
    900: '#212121',
  },
  
  // Special Colors
  white: '#FFFFFF',
  black: '#000000',
  transparent: 'transparent',
};

export const semanticColors = {
  // Background Colors
  background: {
    primary: primitiveColors.white,
    secondary: primitiveColors.grey[50],
    tertiary: primitiveColors.grey[100],
    inverse: primitiveColors.grey[900],
    paper: primitiveColors.white,
    default: primitiveColors.grey[50],
  },
  
  // Surface Colors
  surface: {
    primary: primitiveColors.white,
    secondary: primitiveColors.grey[50],
    tertiary: primitiveColors.grey[100],
    disabled: primitiveColors.grey[200],
    overlay: 'rgba(0, 0, 0, 0.5)',
  },
  
  // Text Colors
  text: {
    primary: 'rgba(0, 0, 0, 0.87)',
    secondary: 'rgba(0, 0, 0, 0.6)',
    tertiary: 'rgba(0, 0, 0, 0.38)',
    disabled: 'rgba(0, 0, 0, 0.38)',
    inverse: primitiveColors.white,
    hint: 'rgba(0, 0, 0, 0.38)',
    link: primitiveColors.blue[600],
  },
  
  // Action Colors
  action: {
    active: 'rgba(0, 0, 0, 0.54)',
    hover: 'rgba(0, 0, 0, 0.04)',
    selected: 'rgba(0, 0, 0, 0.08)',
    disabled: 'rgba(0, 0, 0, 0.26)',
    disabledBackground: 'rgba(0, 0, 0, 0.12)',
    focus: 'rgba(0, 0, 0, 0.12)',
  },
  
  // State Colors
  primary: {
    main: primitiveColors.blue[500],
    light: primitiveColors.blue[300],
    dark: primitiveColors.blue[700],
    contrastText: primitiveColors.white,
  },
  
  secondary: {
    main: primitiveColors.purple[500],
    light: primitiveColors.purple[300],
    dark: primitiveColors.purple[700],
    contrastText: primitiveColors.white,
  },
  
  success: {
    main: primitiveColors.green[500],
    light: primitiveColors.green[300],
    dark: primitiveColors.green[700],
    contrastText: primitiveColors.white,
    background: primitiveColors.green[50],
  },
  
  error: {
    main: primitiveColors.red[500],
    light: primitiveColors.red[300],
    dark: primitiveColors.red[700],
    contrastText: primitiveColors.white,
    background: primitiveColors.red[50],
  },
  
  warning: {
    main: primitiveColors.amber[500],
    light: primitiveColors.amber[300],
    dark: primitiveColors.amber[700],
    contrastText: 'rgba(0, 0, 0, 0.87)',
    background: primitiveColors.amber[50],
  },
  
  info: {
    main: primitiveColors.blue[500],
    light: primitiveColors.blue[300],
    dark: primitiveColors.blue[700],
    contrastText: primitiveColors.white,
    background: primitiveColors.blue[50],
  },
  
  // Payment Specific Colors
  payment: {
    sent: primitiveColors.red[500],
    received: primitiveColors.green[500],
    pending: primitiveColors.amber[500],
    failed: primitiveColors.red[700],
    instant: primitiveColors.purple[500],
  },
  
  // Social Colors
  social: {
    like: primitiveColors.red[500],
    comment: primitiveColors.blue[500],
    share: primitiveColors.green[500],
    public: primitiveColors.blue[500],
    friends: primitiveColors.purple[500],
    private: primitiveColors.grey[600],
  },
  
  // Chart Colors
  chart: {
    primary: primitiveColors.blue[500],
    secondary: primitiveColors.purple[500],
    tertiary: primitiveColors.teal[500],
    quaternary: primitiveColors.amber[500],
    quinary: primitiveColors.green[500],
    senary: primitiveColors.red[500],
  },
  
  // Gradient Colors
  gradient: {
    primary: `linear-gradient(135deg, ${primitiveColors.blue[400]} 0%, ${primitiveColors.blue[600]} 100%)`,
    secondary: `linear-gradient(135deg, ${primitiveColors.purple[400]} 0%, ${primitiveColors.purple[600]} 100%)`,
    success: `linear-gradient(135deg, ${primitiveColors.green[400]} 0%, ${primitiveColors.green[600]} 100%)`,
    sunset: `linear-gradient(135deg, #FF6B6B 0%, #FFE66D 100%)`,
    ocean: `linear-gradient(135deg, #4ECDC4 0%, #44A08D 100%)`,
    purple: `linear-gradient(135deg, #667EEA 0%, #764BA2 100%)`,
  },
};

// Dark Mode Colors
export const darkModeColors = {
  background: {
    primary: '#121212',
    secondary: '#1E1E1E',
    tertiary: '#2C2C2C',
    paper: '#1E1E1E',
    default: '#121212',
  },
  
  surface: {
    primary: '#1E1E1E',
    secondary: '#2C2C2C',
    tertiary: '#383838',
    disabled: '#2C2C2C',
    overlay: 'rgba(255, 255, 255, 0.12)',
  },
  
  text: {
    primary: 'rgba(255, 255, 255, 0.87)',
    secondary: 'rgba(255, 255, 255, 0.6)',
    tertiary: 'rgba(255, 255, 255, 0.38)',
    disabled: 'rgba(255, 255, 255, 0.38)',
    inverse: primitiveColors.black,
    hint: 'rgba(255, 255, 255, 0.38)',
    link: primitiveColors.blue[300],
  },
  
  action: {
    active: 'rgba(255, 255, 255, 0.54)',
    hover: 'rgba(255, 255, 255, 0.08)',
    selected: 'rgba(255, 255, 255, 0.12)',
    disabled: 'rgba(255, 255, 255, 0.26)',
    disabledBackground: 'rgba(255, 255, 255, 0.12)',
    focus: 'rgba(255, 255, 255, 0.12)',
  },
};

// Accessibility Colors
export const a11yColors = {
  focus: {
    outline: primitiveColors.blue[500],
    outlineOffset: '2px',
    outlineWidth: '3px',
  },
  
  contrast: {
    high: {
      text: primitiveColors.black,
      background: primitiveColors.white,
    },
    medium: {
      text: primitiveColors.grey[800],
      background: primitiveColors.grey[100],
    },
  },
};

// Export type definitions
export type PrimitiveColors = typeof primitiveColors;
export type SemanticColors = typeof semanticColors;
export type DarkModeColors = typeof darkModeColors;
export type A11yColors = typeof a11yColors;

export type ColorToken = 
  | keyof typeof primitiveColors
  | `${keyof typeof primitiveColors}.${string}`
  | keyof typeof semanticColors
  | `${keyof typeof semanticColors}.${string}`;

// Helper function to get color value
export function getColor(token: ColorToken, darkMode = false): string {
  const parts = token.split('.');
  const category = parts[0];
  const shade = parts[1];
  
  if (darkMode && darkModeColors[category as keyof typeof darkModeColors]) {
    const darkCategory = darkModeColors[category as keyof typeof darkModeColors];
    if (shade && typeof darkCategory === 'object') {
      return darkCategory[shade as keyof typeof darkCategory] || '';
    }
  }
  
  if (semanticColors[category as keyof typeof semanticColors]) {
    const semanticCategory = semanticColors[category as keyof typeof semanticColors];
    if (shade && typeof semanticCategory === 'object') {
      return semanticCategory[shade as keyof typeof semanticCategory] || '';
    }
  }
  
  if (primitiveColors[category as keyof typeof primitiveColors]) {
    const primitiveCategory = primitiveColors[category as keyof typeof primitiveColors];
    if (shade && typeof primitiveCategory === 'object') {
      return primitiveCategory[shade as keyof typeof primitiveCategory] || '';
    }
  }
  
  return '';
}
/**
 * Waqiti Design System - Typography Tokens
 * Comprehensive typography system with responsive scaling
 */

// Font Families
export const fontFamilies = {
  // Primary font stack
  sans: {
    primary: '-apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "Helvetica Neue", Arial, sans-serif',
    secondary: '"Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
  },
  
  // Monospace for numbers and codes
  mono: {
    primary: '"SF Mono", "Monaco", "Cascadia Code", "Roboto Mono", monospace',
    numbers: '"Roboto Mono", "SF Mono", "Monaco", monospace',
  },
  
  // Display fonts for marketing
  display: {
    primary: '"SF Pro Display", -apple-system, BlinkMacSystemFont, sans-serif',
  },
};

// Font Weights
export const fontWeights = {
  thin: 100,
  extraLight: 200,
  light: 300,
  regular: 400,
  medium: 500,
  semiBold: 600,
  bold: 700,
  extraBold: 800,
  black: 900,
} as const;

// Font Sizes - Base scale
export const fontSizes = {
  // Text sizes
  '2xs': '0.625rem',    // 10px
  xs: '0.75rem',        // 12px
  sm: '0.875rem',       // 14px
  base: '1rem',         // 16px
  lg: '1.125rem',       // 18px
  xl: '1.25rem',        // 20px
  '2xl': '1.5rem',      // 24px
  '3xl': '1.875rem',    // 30px
  '4xl': '2.25rem',     // 36px
  '5xl': '3rem',        // 48px
  '6xl': '3.75rem',     // 60px
  '7xl': '4.5rem',      // 72px
  '8xl': '6rem',        // 96px
  '9xl': '8rem',        // 128px
} as const;

// Line Heights
export const lineHeights = {
  none: 1,
  tight: 1.25,
  snug: 1.375,
  normal: 1.5,
  relaxed: 1.625,
  loose: 2,
} as const;

// Letter Spacing
export const letterSpacing = {
  tighter: '-0.05em',
  tight: '-0.025em',
  normal: '0',
  wide: '0.025em',
  wider: '0.05em',
  widest: '0.1em',
} as const;

// Typography Variants
export const typographyVariants = {
  // Display variants
  displayLarge: {
    fontFamily: fontFamilies.display.primary,
    fontSize: fontSizes['7xl'],
    fontWeight: fontWeights.bold,
    lineHeight: lineHeights.tight,
    letterSpacing: letterSpacing.tight,
  },
  displayMedium: {
    fontFamily: fontFamilies.display.primary,
    fontSize: fontSizes['5xl'],
    fontWeight: fontWeights.bold,
    lineHeight: lineHeights.tight,
    letterSpacing: letterSpacing.tight,
  },
  displaySmall: {
    fontFamily: fontFamilies.display.primary,
    fontSize: fontSizes['4xl'],
    fontWeight: fontWeights.semiBold,
    lineHeight: lineHeights.tight,
    letterSpacing: letterSpacing.normal,
  },
  
  // Heading variants
  h1: {
    fontFamily: fontFamilies.sans.primary,
    fontSize: fontSizes['5xl'],
    fontWeight: fontWeights.bold,
    lineHeight: lineHeights.tight,
    letterSpacing: letterSpacing.tight,
  },
  h2: {
    fontFamily: fontFamilies.sans.primary,
    fontSize: fontSizes['4xl'],
    fontWeight: fontWeights.bold,
    lineHeight: lineHeights.tight,
    letterSpacing: letterSpacing.tight,
  },
  h3: {
    fontFamily: fontFamilies.sans.primary,
    fontSize: fontSizes['3xl'],
    fontWeight: fontWeights.semiBold,
    lineHeight: lineHeights.snug,
    letterSpacing: letterSpacing.normal,
  },
  h4: {
    fontFamily: fontFamilies.sans.primary,
    fontSize: fontSizes['2xl'],
    fontWeight: fontWeights.semiBold,
    lineHeight: lineHeights.snug,
    letterSpacing: letterSpacing.normal,
  },
  h5: {
    fontFamily: fontFamilies.sans.primary,
    fontSize: fontSizes.xl,
    fontWeight: fontWeights.semiBold,
    lineHeight: lineHeights.normal,
    letterSpacing: letterSpacing.normal,
  },
  h6: {
    fontFamily: fontFamilies.sans.primary,
    fontSize: fontSizes.lg,
    fontWeight: fontWeights.semiBold,
    lineHeight: lineHeights.normal,
    letterSpacing: letterSpacing.wide,
  },
  
  // Body variants
  bodyLarge: {
    fontFamily: fontFamilies.sans.secondary,
    fontSize: fontSizes.lg,
    fontWeight: fontWeights.regular,
    lineHeight: lineHeights.relaxed,
    letterSpacing: letterSpacing.normal,
  },
  bodyMedium: {
    fontFamily: fontFamilies.sans.secondary,
    fontSize: fontSizes.base,
    fontWeight: fontWeights.regular,
    lineHeight: lineHeights.normal,
    letterSpacing: letterSpacing.normal,
  },
  bodySmall: {
    fontFamily: fontFamilies.sans.secondary,
    fontSize: fontSizes.sm,
    fontWeight: fontWeights.regular,
    lineHeight: lineHeights.normal,
    letterSpacing: letterSpacing.normal,
  },
  
  // Label variants
  labelLarge: {
    fontFamily: fontFamilies.sans.secondary,
    fontSize: fontSizes.base,
    fontWeight: fontWeights.medium,
    lineHeight: lineHeights.tight,
    letterSpacing: letterSpacing.wide,
  },
  labelMedium: {
    fontFamily: fontFamilies.sans.secondary,
    fontSize: fontSizes.sm,
    fontWeight: fontWeights.medium,
    lineHeight: lineHeights.tight,
    letterSpacing: letterSpacing.wide,
  },
  labelSmall: {
    fontFamily: fontFamilies.sans.secondary,
    fontSize: fontSizes.xs,
    fontWeight: fontWeights.medium,
    lineHeight: lineHeights.tight,
    letterSpacing: letterSpacing.wider,
    textTransform: 'uppercase' as const,
  },
  
  // Special variants
  button: {
    fontFamily: fontFamilies.sans.secondary,
    fontSize: fontSizes.base,
    fontWeight: fontWeights.semiBold,
    lineHeight: lineHeights.tight,
    letterSpacing: letterSpacing.wide,
    textTransform: 'none' as const,
  },
  
  caption: {
    fontFamily: fontFamilies.sans.secondary,
    fontSize: fontSizes.xs,
    fontWeight: fontWeights.regular,
    lineHeight: lineHeights.normal,
    letterSpacing: letterSpacing.normal,
  },
  
  overline: {
    fontFamily: fontFamilies.sans.secondary,
    fontSize: fontSizes['2xs'],
    fontWeight: fontWeights.semiBold,
    lineHeight: lineHeights.loose,
    letterSpacing: letterSpacing.widest,
    textTransform: 'uppercase' as const,
  },
  
  // Numeric variants
  numberLarge: {
    fontFamily: fontFamilies.mono.numbers,
    fontSize: fontSizes['4xl'],
    fontWeight: fontWeights.semiBold,
    lineHeight: lineHeights.tight,
    letterSpacing: letterSpacing.tight,
    fontFeatureSettings: '"tnum" on, "lnum" on',
  },
  numberMedium: {
    fontFamily: fontFamilies.mono.numbers,
    fontSize: fontSizes['2xl'],
    fontWeight: fontWeights.medium,
    lineHeight: lineHeights.tight,
    letterSpacing: letterSpacing.normal,
    fontFeatureSettings: '"tnum" on, "lnum" on',
  },
  numberSmall: {
    fontFamily: fontFamilies.mono.numbers,
    fontSize: fontSizes.lg,
    fontWeight: fontWeights.medium,
    lineHeight: lineHeights.tight,
    letterSpacing: letterSpacing.normal,
    fontFeatureSettings: '"tnum" on, "lnum" on',
  },
  
  // Code variants
  code: {
    fontFamily: fontFamilies.mono.primary,
    fontSize: fontSizes.sm,
    fontWeight: fontWeights.regular,
    lineHeight: lineHeights.normal,
    letterSpacing: letterSpacing.normal,
  },
  
  codeBlock: {
    fontFamily: fontFamilies.mono.primary,
    fontSize: fontSizes.sm,
    fontWeight: fontWeights.regular,
    lineHeight: lineHeights.relaxed,
    letterSpacing: letterSpacing.normal,
  },
};

// Responsive Typography Scale
export const responsiveScale = {
  mobile: 1,
  tablet: 1.125,
  desktop: 1.25,
} as const;

// Text Decoration
export const textDecoration = {
  none: 'none',
  underline: 'underline',
  overline: 'overline',
  lineThrough: 'line-through',
} as const;

// Text Transform
export const textTransform = {
  none: 'none',
  capitalize: 'capitalize',
  uppercase: 'uppercase',
  lowercase: 'lowercase',
} as const;

// Font Smoothing
export const fontSmoothing = {
  antialiased: {
    WebkitFontSmoothing: 'antialiased',
    MozOsxFontSmoothing: 'grayscale',
  },
  auto: {
    WebkitFontSmoothing: 'auto',
    MozOsxFontSmoothing: 'auto',
  },
} as const;

// Type definitions
export type FontFamily = keyof typeof fontFamilies;
export type FontWeight = keyof typeof fontWeights;
export type FontSize = keyof typeof fontSizes;
export type LineHeight = keyof typeof lineHeights;
export type LetterSpacing = keyof typeof letterSpacing;
export type TypographyVariant = keyof typeof typographyVariants;
export type TextDecoration = keyof typeof textDecoration;
export type TextTransform = keyof typeof textTransform;

// Helper functions
export function getResponsiveFontSize(
  size: FontSize,
  breakpoint: keyof typeof responsiveScale
): string {
  const baseSize = parseFloat(fontSizes[size]);
  const scale = responsiveScale[breakpoint];
  return `${baseSize * scale}rem`;
}

export function getTypographyStyles(variant: TypographyVariant) {
  return typographyVariants[variant];
}

// Fluid Typography
export function fluidTypography(
  minSize: FontSize,
  maxSize: FontSize,
  minViewport = 320,
  maxViewport = 1200
): string {
  const min = parseFloat(fontSizes[minSize]);
  const max = parseFloat(fontSizes[maxSize]);
  const slope = (max - min) / (maxViewport - minViewport);
  const yAxisIntersection = -minViewport * slope + min;
  
  return `clamp(${min}rem, ${yAxisIntersection}rem + ${slope * 100}vw, ${max}rem)`;
}
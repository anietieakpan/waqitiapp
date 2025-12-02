import React, { createContext, useContext, useEffect, useState } from 'react';
import { ThemeProvider, createTheme, useTheme } from '@mui/material/styles';

interface AccessibilitySettings {
  highContrast: boolean;
  largeText: boolean;
  reducedMotion: boolean;
  screenReaderOptimized: boolean;
  fontSize: 'small' | 'medium' | 'large' | 'extra-large';
  focusIndicatorStyle: 'default' | 'enhanced' | 'high-visibility';
}

interface AccessibilityContextType {
  settings: AccessibilitySettings;
  updateSetting: <K extends keyof AccessibilitySettings>(
    key: K,
    value: AccessibilitySettings[K]
  ) => void;
  announceToScreenReader: (message: string, priority?: 'polite' | 'assertive') => void;
}

const AccessibilityContext = createContext<AccessibilityContextType | undefined>(undefined);

export const useAccessibility = () => {
  const context = useContext(AccessibilityContext);
  if (!context) {
    throw new Error('useAccessibility must be used within AccessibilityProvider');
  }
  return context;
};

interface AccessibilityProviderProps {
  children: React.ReactNode;
}

const AccessibilityProvider: React.FC<AccessibilityProviderProps> = ({ children }) => {
  const baseTheme = useTheme();
  const [settings, setSettings] = useState<AccessibilitySettings>(() => {
    // Load from localStorage or system preferences
    const saved = localStorage.getItem('accessibility-settings');
    const defaults: AccessibilitySettings = {
      highContrast: window.matchMedia('(prefers-contrast: high)').matches,
      largeText: false,
      reducedMotion: window.matchMedia('(prefers-reduced-motion: reduce)').matches,
      screenReaderOptimized: false,
      fontSize: 'medium',
      focusIndicatorStyle: 'default',
    };
    
    return saved ? { ...defaults, ...JSON.parse(saved) } : defaults;
  });

  const [announcer, setAnnouncer] = useState<HTMLDivElement | null>(null);

  // Create accessibility-aware theme
  const accessibilityTheme = React.useMemo(() => {
    const fontSizeMultipliers = {
      small: 0.875,
      medium: 1,
      large: 1.125,
      'extra-large': 1.25,
    };

    const multiplier = fontSizeMultipliers[settings.fontSize];

    return createTheme({
      ...baseTheme,
      palette: {
        ...baseTheme.palette,
        ...(settings.highContrast && {
          mode: 'dark',
          primary: {
            main: '#ffffff',
            contrastText: '#000000',
          },
          secondary: {
            main: '#ffff00',
            contrastText: '#000000',
          },
          background: {
            default: '#000000',
            paper: '#1a1a1a',
          },
          text: {
            primary: '#ffffff',
            secondary: '#cccccc',
          },
        }),
      },
      typography: {
        ...baseTheme.typography,
        fontSize: baseTheme.typography.fontSize * multiplier,
        h1: {
          ...baseTheme.typography.h1,
          fontSize: `${parseFloat(baseTheme.typography.h1.fontSize as string) * multiplier}rem`,
        },
        h2: {
          ...baseTheme.typography.h2,
          fontSize: `${parseFloat(baseTheme.typography.h2.fontSize as string) * multiplier}rem`,
        },
        h3: {
          ...baseTheme.typography.h3,
          fontSize: `${parseFloat(baseTheme.typography.h3.fontSize as string) * multiplier}rem`,
        },
        h4: {
          ...baseTheme.typography.h4,
          fontSize: `${parseFloat(baseTheme.typography.h4.fontSize as string) * multiplier}rem`,
        },
        h5: {
          ...baseTheme.typography.h5,
          fontSize: `${parseFloat(baseTheme.typography.h5.fontSize as string) * multiplier}rem`,
        },
        h6: {
          ...baseTheme.typography.h6,
          fontSize: `${parseFloat(baseTheme.typography.h6.fontSize as string) * multiplier}rem`,
        },
        body1: {
          ...baseTheme.typography.body1,
          fontSize: `${parseFloat(baseTheme.typography.body1.fontSize as string) * multiplier}rem`,
        },
        body2: {
          ...baseTheme.typography.body2,
          fontSize: `${parseFloat(baseTheme.typography.body2.fontSize as string) * multiplier}rem`,
        },
      },
      components: {
        ...baseTheme.components,
        MuiButton: {
          ...baseTheme.components?.MuiButton,
          styleOverrides: {
            ...baseTheme.components?.MuiButton?.styleOverrides,
            root: {
              minHeight: settings.largeText ? 48 : 36,
              fontSize: settings.largeText ? '1.1rem' : '1rem',
              ...(settings.focusIndicatorStyle === 'enhanced' && {
                '&:focus': {
                  outline: '3px solid',
                  outlineColor: settings.highContrast ? '#ffff00' : '#1976d2',
                  outlineOffset: '2px',
                },
              }),
              ...(settings.focusIndicatorStyle === 'high-visibility' && {
                '&:focus': {
                  outline: '4px solid #ff0000',
                  outlineOffset: '2px',
                  backgroundColor: settings.highContrast ? '#ffff00' : '#e3f2fd',
                },
              }),
            },
          },
        },
        MuiTextField: {
          ...baseTheme.components?.MuiTextField,
          styleOverrides: {
            ...baseTheme.components?.MuiTextField?.styleOverrides,
            root: {
              '& .MuiInputBase-root': {
                minHeight: settings.largeText ? 56 : 48,
                fontSize: settings.largeText ? '1.1rem' : '1rem',
              },
              '& .MuiInputBase-input:focus': {
                ...(settings.focusIndicatorStyle !== 'default' && {
                  outline: '3px solid',
                  outlineColor: settings.highContrast ? '#ffff00' : '#1976d2',
                  outlineOffset: '2px',
                }),
              },
            },
          },
        },
        MuiLink: {
          ...baseTheme.components?.MuiLink,
          styleOverrides: {
            ...baseTheme.components?.MuiLink?.styleOverrides,
            root: {
              textDecorationThickness: '2px',
              textUnderlineOffset: '3px',
              '&:focus': {
                ...(settings.focusIndicatorStyle !== 'default' && {
                  outline: '3px solid',
                  outlineColor: settings.highContrast ? '#ffff00' : '#1976d2',
                  outlineOffset: '2px',
                }),
              },
            },
          },
        },
      },
    });
  }, [baseTheme, settings]);

  const updateSetting = <K extends keyof AccessibilitySettings>(
    key: K,
    value: AccessibilitySettings[K]
  ) => {
    const newSettings = { ...settings, [key]: value };
    setSettings(newSettings);
    localStorage.setItem('accessibility-settings', JSON.stringify(newSettings));
  };

  const announceToScreenReader = (message: string, priority: 'polite' | 'assertive' = 'polite') => {
    if (!announcer) return;

    announcer.setAttribute('aria-live', priority);
    announcer.textContent = message;

    // Clear the message after a delay to allow for re-announcement
    setTimeout(() => {
      if (announcer) {
        announcer.textContent = '';
      }
    }, 1000);
  };

  // Set up screen reader announcer
  useEffect(() => {
    const announcerElement = document.createElement('div');
    announcerElement.setAttribute('aria-live', 'polite');
    announcerElement.setAttribute('aria-atomic', 'true');
    announcerElement.style.cssText = `
      position: absolute;
      left: -10000px;
      width: 1px;
      height: 1px;
      overflow: hidden;
    `;
    document.body.appendChild(announcerElement);
    setAnnouncer(announcerElement);

    return () => {
      if (announcerElement.parentNode) {
        announcerElement.parentNode.removeChild(announcerElement);
      }
    };
  }, []);

  // Apply CSS custom properties for reduced motion
  useEffect(() => {
    const root = document.documentElement;
    if (settings.reducedMotion) {
      root.style.setProperty('--animation-duration', '0.01ms');
      root.style.setProperty('--transition-duration', '0.01ms');
    } else {
      root.style.removeProperty('--animation-duration');
      root.style.removeProperty('--transition-duration');
    }
  }, [settings.reducedMotion]);

  // Apply high contrast styles
  useEffect(() => {
    const root = document.documentElement;
    if (settings.highContrast) {
      root.classList.add('high-contrast');
    } else {
      root.classList.remove('high-contrast');
    }
  }, [settings.highContrast]);

  // Listen for system preference changes
  useEffect(() => {
    const contrastMediaQuery = window.matchMedia('(prefers-contrast: high)');
    const motionMediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');

    const handleContrastChange = (e: MediaQueryListEvent) => {
      if (!localStorage.getItem('accessibility-settings')) {
        updateSetting('highContrast', e.matches);
      }
    };

    const handleMotionChange = (e: MediaQueryListEvent) => {
      if (!localStorage.getItem('accessibility-settings')) {
        updateSetting('reducedMotion', e.matches);
      }
    };

    contrastMediaQuery.addEventListener('change', handleContrastChange);
    motionMediaQuery.addEventListener('change', handleMotionChange);

    return () => {
      contrastMediaQuery.removeEventListener('change', handleContrastChange);
      motionMediaQuery.removeEventListener('change', handleMotionChange);
    };
  }, []);

  // Skip links for keyboard navigation
  useEffect(() => {
    const skipLink = document.createElement('a');
    skipLink.href = '#main-content';
    skipLink.textContent = 'Skip to main content';
    skipLink.className = 'skip-link';
    skipLink.style.cssText = `
      position: absolute;
      top: -40px;
      left: 6px;
      background: #000;
      color: #fff;
      padding: 8px;
      text-decoration: none;
      z-index: 9999;
      border-radius: 4px;
    `;
    
    skipLink.addEventListener('focus', () => {
      skipLink.style.top = '6px';
    });
    
    skipLink.addEventListener('blur', () => {
      skipLink.style.top = '-40px';
    });

    document.body.insertBefore(skipLink, document.body.firstChild);

    return () => {
      if (skipLink.parentNode) {
        skipLink.parentNode.removeChild(skipLink);
      }
    };
  }, []);

  const value = {
    settings,
    updateSetting,
    announceToScreenReader,
  };

  return (
    <AccessibilityContext.Provider value={value}>
      <ThemeProvider theme={accessibilityTheme}>
        {children}
        
        {/* Global CSS for accessibility */}
        <style jsx global>{`
          .high-contrast {
            filter: contrast(200%);
          }
          
          .high-contrast img,
          .high-contrast video {
            filter: contrast(100%);
          }
          
          *:focus-visible {
            outline: ${settings.focusIndicatorStyle === 'high-visibility' ? '4px solid #ff0000 !important' :
                     settings.focusIndicatorStyle === 'enhanced' ? '3px solid #1976d2 !important' :
                     '2px solid #1976d2 !important'};
            outline-offset: 2px !important;
          }
          
          ${settings.reducedMotion ? `
            *,
            *::before,
            *::after {
              animation-duration: var(--animation-duration, 0.01ms) !important;
              animation-iteration-count: 1 !important;
              transition-duration: var(--transition-duration, 0.01ms) !important;
              scroll-behavior: auto !important;
            }
          ` : ''}
          
          .skip-link:focus {
            top: 6px !important;
          }
          
          /* Screen reader only content */
          .sr-only {
            position: absolute !important;
            width: 1px !important;
            height: 1px !important;
            padding: 0 !important;
            margin: -1px !important;
            overflow: hidden !important;
            clip: rect(0, 0, 0, 0) !important;
            white-space: nowrap !important;
            border: 0 !important;
          }
          
          /* Ensure interactive elements are large enough */
          ${settings.largeText ? `
            button,
            input,
            select,
            textarea,
            a {
              min-height: 44px !important;
              min-width: 44px !important;
            }
          ` : ''}
        `}</style>
      </ThemeProvider>
    </AccessibilityContext.Provider>
  );
};

export default AccessibilityProvider;
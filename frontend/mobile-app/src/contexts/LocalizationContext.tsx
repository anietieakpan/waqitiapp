/**
 * LocalizationContext - Mobile localization context
 * Provides localization state and methods for the mobile app
 */

import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { getLocales } from 'react-native-localize';

// Supported languages
export const SUPPORTED_LANGUAGES = {
  en: { name: 'English', code: 'en', flag: '🇺🇸' },
  es: { name: 'Español', code: 'es', flag: '🇪🇸' },
  fr: { name: 'Français', code: 'fr', flag: '🇫🇷' },
  de: { name: 'Deutsch', code: 'de', flag: '🇩🇪' },
  ar: { name: 'العربية', code: 'ar', flag: '🇸🇦' },
} as const;

export type LanguageCode = keyof typeof SUPPORTED_LANGUAGES;

// Translation type
type TranslationKey = string;
type TranslationValue = string;
type Translations = Record<TranslationKey, TranslationValue>;

// Localization state
interface LocalizationState {
  currentLanguage: LanguageCode;
  isRTL: boolean;
  translations: Translations;
  loading: boolean;
  error: string | null;
}

// LocalizationContext interface
interface LocalizationContextType extends LocalizationState {
  changeLanguage: (language: LanguageCode) => Promise<void>;
  t: (key: TranslationKey, params?: Record<string, string | number>) => string;
  formatCurrency: (amount: number, currency?: string) => string;
  formatDate: (date: Date | string | number, format?: 'short' | 'medium' | 'long') => string;
  formatTime: (date: Date | string | number, format?: 'short' | 'medium') => string;
  clearError: () => void;
}

// Placeholder for default translations - will be loaded from JSON files
const defaultTranslations: Translations = {};

// Default state
const defaultState: LocalizationState = {
  currentLanguage: 'en',
  isRTL: false,
  translations: defaultTranslations,
  loading: false,
  error: null,
};

// Create context
const LocalizationContext = createContext<LocalizationContextType | undefined>(undefined);

// Custom hook
export const useLocalization = () => {
  const context = useContext(LocalizationContext);
  if (!context) {
    throw new Error('useLocalization must be used within a LocalizationProvider');
  }
  return context;
};

// LocalizationProvider props
interface LocalizationProviderProps {
  children: ReactNode;
}

// Storage keys
const STORAGE_KEYS = {
  LANGUAGE: '@waqiti_language',
};

// LocalizationProvider component
export const LocalizationProvider: React.FC<LocalizationProviderProps> = ({ children }) => {
  const [state, setState] = useState<LocalizationState>(defaultState);

  // Initialize localization
  useEffect(() => {
    initializeLocalization();
  }, []);

  /**
   * Initialize localization from storage or device settings
   */
  const initializeLocalization = async () => {
    try {
      setState(prev => ({ ...prev, loading: true }));

      // Get saved language or detect device language
      const savedLanguage = await AsyncStorage.getItem(STORAGE_KEYS.LANGUAGE);
      let languageCode: LanguageCode = 'en';

      if (savedLanguage && savedLanguage in SUPPORTED_LANGUAGES) {
        languageCode = savedLanguage as LanguageCode;
      } else {
        // Detect device language
        const deviceLocales = getLocales();
        const deviceLanguage = deviceLocales[0]?.languageCode;
        
        if (deviceLanguage && deviceLanguage in SUPPORTED_LANGUAGES) {
          languageCode = deviceLanguage as LanguageCode;
        }
      }

      // Load translations for the language
      const translations = await loadTranslations(languageCode);
      const isRTL = languageCode === 'ar';

      setState({
        currentLanguage: languageCode,
        isRTL,
        translations,
        loading: false,
        error: null,
      });

      // Save the language preference
      await AsyncStorage.setItem(STORAGE_KEYS.LANGUAGE, languageCode);
    } catch (error) {
      console.error('Failed to initialize localization:', error);
      setState(prev => ({
        ...prev,
        loading: false,
        error: 'Failed to load language settings',
      }));
    }
  };

  /**
   * Load translations for a specific language
   */
  const loadTranslations = async (language: LanguageCode): Promise<Translations> => {
    try {
      let translations: any;
      
      // Dynamic import based on language
      switch (language) {
        case 'en':
          translations = require('../locales/en.json');
          break;
        case 'es':
          translations = require('../locales/es.json');
          break;
        case 'fr':
          translations = require('../locales/fr.json');
          break;
        case 'de':
          translations = require('../locales/de.json');
          break;
        case 'ar':
          translations = require('../locales/ar.json');
          break;
        default:
          translations = require('../locales/en.json');
      }
      
      // Flatten nested translations to dot notation
      const flattenTranslations = (obj: any, prefix = ''): Translations => {
        return Object.keys(obj).reduce((acc, key) => {
          const value = obj[key];
          const newKey = prefix ? `${prefix}.${key}` : key;
          
          if (typeof value === 'object' && value !== null) {
            Object.assign(acc, flattenTranslations(value, newKey));
          } else {
            acc[newKey] = value;
          }
          
          return acc;
        }, {} as Translations);
      };
      
      return flattenTranslations(translations);
    } catch (error) {
      console.error(`Failed to load translations for ${language}:`, error);
      // Fallback to default translations
      return defaultTranslations;
    }
  };

  /**
   * Change language
   */
  const changeLanguage = async (language: LanguageCode): Promise<void> => {
    try {
      setState(prev => ({ ...prev, loading: true, error: null }));

      const translations = await loadTranslations(language);
      const isRTL = language === 'ar';

      setState(prev => ({
        ...prev,
        currentLanguage: language,
        isRTL,
        translations,
        loading: false,
      }));

      // Save language preference
      await AsyncStorage.setItem(STORAGE_KEYS.LANGUAGE, language);
    } catch (error: any) {
      setState(prev => ({
        ...prev,
        loading: false,
        error: error.message || 'Failed to change language',
      }));
      throw error;
    }
  };

  /**
   * Translate function
   */
  const t = (key: TranslationKey, params?: Record<string, string | number>): string => {
    let translation = state.translations[key] || key;

    // Replace parameters in translation
    if (params) {
      Object.keys(params).forEach(paramKey => {
        const placeholder = `{{${paramKey}}}`;
        translation = translation.replace(new RegExp(placeholder, 'g'), String(params[paramKey]));
      });
    }

    return translation;
  };

  /**
   * Format currency based on current locale
   */
  const formatCurrency = (amount: number, currency: string = 'USD'): string => {
    try {
      return new Intl.NumberFormat(state.currentLanguage, {
        style: 'currency',
        currency,
      }).format(amount);
    } catch (error) {
      // Fallback formatting
      return `${currency} ${amount.toFixed(2)}`;
    }
  };

  /**
   * Format date based on current locale
   */
  const formatDate = (
    date: Date | string | number,
    format: 'short' | 'medium' | 'long' = 'medium'
  ): string => {
    try {
      const dateObj = new Date(date);
      const options: Intl.DateTimeFormatOptions = {};

      switch (format) {
        case 'short':
          options.dateStyle = 'short';
          break;
        case 'medium':
          options.dateStyle = 'medium';
          break;
        case 'long':
          options.dateStyle = 'long';
          break;
      }

      return new Intl.DateTimeFormat(state.currentLanguage, options).format(dateObj);
    } catch (error) {
      // Fallback formatting
      return new Date(date).toLocaleDateString();
    }
  };

  /**
   * Format time based on current locale
   */
  const formatTime = (
    date: Date | string | number,
    format: 'short' | 'medium' = 'short'
  ): string => {
    try {
      const dateObj = new Date(date);
      const options: Intl.DateTimeFormatOptions = {};

      switch (format) {
        case 'short':
          options.timeStyle = 'short';
          break;
        case 'medium':
          options.timeStyle = 'medium';
          break;
      }

      return new Intl.DateTimeFormat(state.currentLanguage, options).format(dateObj);
    } catch (error) {
      // Fallback formatting
      return new Date(date).toLocaleTimeString();
    }
  };

  /**
   * Clear error
   */
  const clearError = (): void => {
    setState(prev => ({ ...prev, error: null }));
  };

  const contextValue: LocalizationContextType = {
    ...state,
    changeLanguage,
    t,
    formatCurrency,
    formatDate,
    formatTime,
    clearError,
  };

  return (
    <LocalizationContext.Provider value={contextValue}>
      {children}
    </LocalizationContext.Provider>
  );
};

export default LocalizationProvider;
import i18next from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import Backend from 'i18next-http-backend';

// Import translations
import enTranslations from './locales/en.json';
import esTranslations from './locales/es.json';
import frTranslations from './locales/fr.json';
import deTranslations from './locales/de.json';
import ptTranslations from './locales/pt.json';
import arTranslations from './locales/ar.json';

// Define supported languages
export const SUPPORTED_LANGUAGES = {
  en: 'English',
  es: 'Español',
  fr: 'Français',
  de: 'Deutsch',
  pt: 'Português',
  ar: 'العربية',
};

export const RTL_LANGUAGES = ['ar'];

const resources = {
  en: { translation: enTranslations },
  es: { translation: esTranslations },
  fr: { translation: frTranslations },
  de: { translation: deTranslations },
  pt: { translation: ptTranslations },
  ar: { translation: arTranslations },
};

// Initialize i18next
i18next
  .use(Backend) // Load translations from backend
  .use(LanguageDetector) // Detect user language
  .use(initReactI18next) // Initialize react-i18next
  .init({
    resources,
    fallbackLng: 'en',
    debug: process.env.NODE_ENV === 'development',
    
    // Language detection options
    detection: {
      order: ['localStorage', 'navigator', 'htmlTag'],
      caches: ['localStorage'],
      lookupLocalStorage: 'waqiti_language',
    },

    interpolation: {
      escapeValue: false, // React already does escaping
      format: (value, format, lng) => {
        if (format === 'currency') {
          return new Intl.NumberFormat(lng, {
            style: 'currency',
            currency: 'USD', // You might want to make this dynamic
          }).format(value);
        }
        if (format === 'number') {
          return new Intl.NumberFormat(lng).format(value);
        }
        if (format === 'date') {
          return new Intl.DateTimeFormat(lng).format(new Date(value));
        }
        if (format === 'datetime') {
          return new Intl.DateTimeFormat(lng, {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
          }).format(new Date(value));
        }
        return value;
      },
    },

    backend: {
      loadPath: '/locales/{{lng}}.json',
      // Allow cross-domain requests
      crossDomain: false,
      // Parse data after it's been loaded
      parse: (data: string) => JSON.parse(data),
    },

    // React-i18next options
    react: {
      bindI18n: 'languageChanged',
      bindI18nStore: '',
      transEmptyNodeValue: '',
      transSupportBasicHtmlNodes: true,
      transKeepBasicHtmlNodesFor: ['br', 'strong', 'i', 'p'],
      useSuspense: false, // Disable suspense for now
    },
  });

// Function to change language
export const changeLanguage = (lng: string) => {
  i18next.changeLanguage(lng);
  localStorage.setItem('waqiti_language', lng);
  
  // Update document direction for RTL languages
  document.dir = RTL_LANGUAGES.includes(lng) ? 'rtl' : 'ltr';
  document.documentElement.lang = lng;
};

// Function to get current language
export const getCurrentLanguage = () => i18next.language;

// Function to check if current language is RTL
export const isRTL = () => RTL_LANGUAGES.includes(getCurrentLanguage());

// Set initial direction
document.dir = isRTL() ? 'rtl' : 'ltr';

export default i18next;
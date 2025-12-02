import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import Backend from 'i18next-http-backend';
import LanguageDetector from 'i18next-browser-languagedetector';
import { format as formatDate, isDate } from 'date-fns';
import { enUS, es, fr, de, ja, zhCN, ar, pt } from 'date-fns/locale';

// Translation resources
import enTranslation from './locales/en.json';
import esTranslation from './locales/es.json';
import frTranslation from './locales/fr.json';
import deTranslation from './locales/de.json';
import jaTranslation from './locales/ja.json';
import zhTranslation from './locales/zh.json';
import arTranslation from './locales/ar.json';
import ptTranslation from './locales/pt.json';

// Date-fns locale mapping
const dateLocales: Record<string, Locale> = {
  en: enUS,
  es: es,
  fr: fr,
  de: de,
  ja: ja,
  zh: zhCN,
  ar: ar,
  pt: pt,
};

// Number formatting for different locales
const numberFormats: Record<string, Intl.NumberFormatOptions> = {
  currency: {
    style: 'currency',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  },
  decimal: {
    style: 'decimal',
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  },
  percent: {
    style: 'percent',
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  },
};

// RTL languages
const rtlLanguages = ['ar', 'he', 'fa'];

const resources = {
  en: { translation: enTranslation },
  es: { translation: esTranslation },
  fr: { translation: frTranslation },
  de: { translation: deTranslation },
  ja: { translation: jaTranslation },
  zh: { translation: zhTranslation },
  ar: { translation: arTranslation },
  pt: { translation: ptTranslation },
};

i18n
  .use(Backend)
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: 'en',
    debug: process.env.NODE_ENV === 'development',
    
    detection: {
      order: [
        'localStorage',
        'cookie',
        'sessionStorage',
        'navigator',
        'htmlTag',
        'path',
        'subdomain'
      ],
      caches: ['localStorage', 'cookie'],
      lookupLocalStorage: 'i18nextLng',
      lookupCookie: 'i18next',
      lookupSessionStorage: 'i18nextLng',
      excludeCacheFor: ['cimode'],
    },

    interpolation: {
      escapeValue: false,
      format: (value: any, format: string, lng?: string) => {
        if (format === 'currency') {
          return formatCurrency(value, lng);
        }
        if (format === 'number') {
          return formatNumber(value, lng);
        }
        if (format === 'percent') {
          return formatPercent(value, lng);
        }
        if (format && format.startsWith('date:')) {
          const dateFormat = format.split(':')[1];
          return formatDateString(value, dateFormat, lng);
        }
        return value;
      },
    },

    backend: {
      loadPath: '/locales/{{lng}}.json',
    },
  });

// Utility functions
export const formatCurrency = (
  amount: number,
  locale?: string,
  currency = 'USD'
): string => {
  const currentLocale = locale || i18n.language || 'en';
  
  try {
    return new Intl.NumberFormat(currentLocale, {
      ...numberFormats.currency,
      currency,
    }).format(amount);
  } catch (error) {
    // Fallback to USD if currency not supported
    return new Intl.NumberFormat(currentLocale, {
      ...numberFormats.currency,
      currency: 'USD',
    }).format(amount);
  }
};

export const formatNumber = (
  value: number,
  locale?: string,
  options?: Intl.NumberFormatOptions
): string => {
  const currentLocale = locale || i18n.language || 'en';
  
  return new Intl.NumberFormat(currentLocale, {
    ...numberFormats.decimal,
    ...options,
  }).format(value);
};

export const formatPercent = (
  value: number,
  locale?: string
): string => {
  const currentLocale = locale || i18n.language || 'en';
  
  return new Intl.NumberFormat(currentLocale, numberFormats.percent).format(
    value / 100
  );
};

export const formatDateString = (
  date: Date | string | number,
  format: string,
  locale?: string
): string => {
  const currentLocale = locale || i18n.language || 'en';
  const dateValue = isDate(date) ? date : new Date(date);
  
  if (isNaN(dateValue.getTime())) {
    return '';
  }

  const dateLocale = dateLocales[currentLocale] || dateLocales.en;
  
  try {
    return formatDate(dateValue, format, { locale: dateLocale });
  } catch (error) {
    // Fallback to basic formatting
    return dateValue.toLocaleDateString(currentLocale);
  }
};

export const isRTL = (language?: string): boolean => {
  const currentLanguage = language || i18n.language || 'en';
  return rtlLanguages.includes(currentLanguage);
};

export const setDocumentDirection = (language?: string): void => {
  const direction = isRTL(language) ? 'rtl' : 'ltr';
  document.documentElement.dir = direction;
  document.documentElement.lang = language || i18n.language || 'en';
};

// Language change handler
i18n.on('languageChanged', (lng) => {
  setDocumentDirection(lng);
});

// Set initial direction
setDocumentDirection();

export default i18n;
// Currency formatting
export const formatCurrency = (
  amount: number,
  options: {
    currency?: string;
    compact?: boolean;
    showSign?: boolean;
  } = {}
) => {
  const { currency = 'USD', compact = false, showSign = false } = options;

  const formatter = new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    notation: compact ? 'compact' : 'standard',
    compactDisplay: 'short',
  });

  let formatted = formatter.format(Math.abs(amount));

  if (showSign && amount !== 0) {
    formatted = `${amount > 0 ? '+' : '-'}${formatted}`;
  } else if (amount < 0) {
    formatted = `-${formatted}`;
  }

  return formatted;
};

// Date formatting
export const formatDate = (
  date: Date | string,
  format: 'short' | 'medium' | 'long' | 'relative' | 'time' | string = 'medium'
): string => {
  const dateObj = typeof date === 'string' ? new Date(date) : date;

  if (isNaN(dateObj.getTime())) {
    return 'Invalid Date';
  }

  switch (format) {
    case 'short':
      return dateObj.toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
      });
    case 'medium':
      return dateObj.toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      });
    case 'long':
      return dateObj.toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        weekday: 'long',
      });
    case 'relative':
      return formatRelativeDate(dateObj);
    case 'time':
      return dateObj.toLocaleTimeString('en-US', {
        hour: 'numeric',
        minute: '2-digit',
        hour12: true,
      });
    case 'MMM dd':
      return dateObj.toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
      });
    case 'datetime':
      return dateObj.toLocaleString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: 'numeric',
        minute: '2-digit',
        hour12: true,
      });
    default:
      return dateObj.toLocaleDateString('en-US');
  }
};

// Relative date formatting (e.g., "2 hours ago", "Yesterday")
export const formatRelativeDate = (date: Date): string => {
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMinutes = Math.floor(diffMs / (1000 * 60));
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

  if (diffMinutes < 1) {
    return 'Just now';
  } else if (diffMinutes < 60) {
    return `${diffMinutes} minute${diffMinutes > 1 ? 's' : ''} ago`;
  } else if (diffHours < 24) {
    return `${diffHours} hour${diffHours > 1 ? 's' : ''} ago`;
  } else if (diffDays === 1) {
    return 'Yesterday';
  } else if (diffDays < 7) {
    return `${diffDays} day${diffDays > 1 ? 's' : ''} ago`;
  } else {
    return formatDate(date, 'medium');
  }
};

// Phone number formatting
export const formatPhoneNumber = (phoneNumber: string) => {
  const cleaned = phoneNumber.replace(/\D/g, '');
  
  if (cleaned.length === 10) {
    return `(${cleaned.slice(0, 3)}) ${cleaned.slice(3, 6)}-${cleaned.slice(6)}`;
  } else if (cleaned.length === 11 && cleaned[0] === '1') {
    return `+1 (${cleaned.slice(1, 4)}) ${cleaned.slice(4, 7)}-${cleaned.slice(7)}`;
  }
  
  return phoneNumber;
};

// Email masking
export const maskEmail = (email: string) => {
  const [localPart, domain] = email.split('@');
  if (localPart.length <= 2) {
    return `${localPart[0]}***@${domain}`;
  }
  return `${localPart[0]}${'*'.repeat(localPart.length - 2)}${localPart[localPart.length - 1]}@${domain}`;
};

// Phone number masking
export const maskPhoneNumber = (phoneNumber: string) => {
  const cleaned = phoneNumber.replace(/\D/g, '');
  if (cleaned.length >= 4) {
    return `***-***-${cleaned.slice(-4)}`;
  }
  return phoneNumber;
};

// Card number masking
export const maskCardNumber = (cardNumber: string) => {
  const cleaned = cardNumber.replace(/\D/g, '');
  if (cleaned.length >= 4) {
    return `**** **** **** ${cleaned.slice(-4)}`;
  }
  return cardNumber;
};

// Number formatting
export const formatNumber = (
  number: number,
  options: {
    decimals?: number;
    compact?: boolean;
    percentage?: boolean;
  } = {}
) => {
  const { decimals = 2, compact = false, percentage = false } = options;

  if (percentage) {
    return new Intl.NumberFormat('en-US', {
      style: 'percent',
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    }).format(number / 100);
  }

  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
    notation: compact ? 'compact' : 'standard',
    compactDisplay: 'short',
  }).format(number);
};

// File size formatting
export const formatFileSize = (bytes: number) => {
  if (bytes === 0) return '0 Bytes';

  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));

  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

// Transaction status formatting
export const formatTransactionStatus = (status: string) => {
  return status
    .toLowerCase()
    .replace(/_/g, ' ')
    .replace(/\b\w/g, l => l.toUpperCase());
};

// Transaction type formatting
export const formatTransactionType = (type: string) => {
  const typeMap: Record<string, string> = {
    CREDIT: 'Money Received',
    DEBIT: 'Money Sent',
    TRANSFER: 'Transfer',
    DEPOSIT: 'Deposit',
    WITHDRAWAL: 'Withdrawal',
    FEE: 'Fee',
    REFUND: 'Refund',
  };

  return typeMap[type] || formatTransactionStatus(type);
};

// Validation helpers
export const isValidEmail = (email: string) => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return emailRegex.test(email);
};

export const isValidPhoneNumber = (phoneNumber: string) => {
  const phoneRegex = /^\+?[\d\s\-\(\)]{10,}$/;
  return phoneRegex.test(phoneNumber);
};

// URL helpers
export const generateAvatarUrl = (name: string) => {
  return `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=random&color=fff&size=40`;
};

// Time helpers
export const getTimeOfDay = () => {
  const hour = new Date().getHours();
  if (hour < 12) return 'morning';
  if (hour < 17) return 'afternoon';
  return 'evening';
};

export const getGreeting = (name?: string) => {
  const timeOfDay = getTimeOfDay();
  const greeting = `Good ${timeOfDay}`;
  return name ? `${greeting}, ${name}!` : `${greeting}!`;
};

// Percentage formatting
export const formatPercentage = (
  value: number,
  options: {
    minimumFractionDigits?: number;
    maximumFractionDigits?: number;
    showSign?: boolean;
  } = {}
): string => {
  const {
    minimumFractionDigits = 1,
    maximumFractionDigits = 2,
    showSign = false,
  } = options;

  const formatted = new Intl.NumberFormat('en-US', {
    style: 'percent',
    minimumFractionDigits,
    maximumFractionDigits,
    signDisplay: showSign ? 'always' : 'auto',
  }).format(value / 100);

  return formatted;
};

// Alias for backward compatibility
export const formatPercent = formatPercentage;
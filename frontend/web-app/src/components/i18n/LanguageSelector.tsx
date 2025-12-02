import React, { useState } from 'react';
import {
  IconButton,
  Menu,
  MenuItem,
  ListItemIcon,
  ListItemText,
  Typography,
  Box,
  Tooltip,
  Chip,
} from '@mui/material';
import LanguageIcon from '@mui/icons-material/Language';
import CheckIcon from '@mui/icons-material/Check';;
import { useTranslation } from 'react-i18next';
import { SUPPORTED_LANGUAGES, changeLanguage, getCurrentLanguage, isRTL } from '../../i18n/i18n';

interface LanguageSelectorProps {
  variant?: 'icon' | 'button' | 'menu';
  showLabel?: boolean;
  size?: 'small' | 'medium' | 'large';
}

const LanguageSelector: React.FC<LanguageSelectorProps> = ({
  variant = 'icon',
  showLabel = false,
  size = 'medium',
}) => {
  const { t } = useTranslation();
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const open = Boolean(anchorEl);

  const currentLanguage = getCurrentLanguage();
  const currentLanguageName = SUPPORTED_LANGUAGES[currentLanguage as keyof typeof SUPPORTED_LANGUAGES];

  const handleClick = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleLanguageChange = (languageCode: string) => {
    changeLanguage(languageCode);
    handleClose();
    
    // Force page refresh to apply RTL changes properly
    if (isRTL()) {
      window.location.reload();
    }
  };

  const getLanguageFlag = (langCode: string): string => {
    const flags: Record<string, string> = {
      en: '🇺🇸',
      es: '🇪🇸',
      fr: '🇫🇷',
      de: '🇩🇪',
      pt: '🇧🇷',
      ar: '🇸🇦',
    };
    return flags[langCode] || '🌐';
  };

  if (variant === 'menu') {
    return (
      <Box>
        {Object.entries(SUPPORTED_LANGUAGES).map(([code, name]) => (
          <MenuItem
            key={code}
            onClick={() => handleLanguageChange(code)}
            selected={code === currentLanguage}
          >
            <ListItemIcon>
              <Typography sx={{ fontSize: '1.2rem', mr: 1 }}>
                {getLanguageFlag(code)}
              </Typography>
              {code === currentLanguage && <CheckIcon />}
            </ListItemIcon>
            <ListItemText>
              {name}
              {code === currentLanguage && (
                <Chip
                  label="Current"
                  size="small"
                  color="primary"
                  variant="outlined"
                  sx={{ ml: 1, height: 20 }}
                />
              )}
            </ListItemText>
          </MenuItem>
        ))}
      </Box>
    );
  }

  if (variant === 'button') {
    return (
      <Box>
        <Tooltip title={t('settings.language')}>
          <Box
            onClick={handleClick}
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
              cursor: 'pointer',
              px: 2,
              py: 1,
              borderRadius: 1,
              '&:hover': {
                bgcolor: 'action.hover',
              },
            }}
          >
            <Typography sx={{ fontSize: '1.2rem' }}>
              {getLanguageFlag(currentLanguage)}
            </Typography>
            {showLabel && (
              <Typography variant="body2">
                {currentLanguageName}
              </Typography>
            )}
            <LanguageIcon sx={{ fontSize: 20 }} />
          </Box>
        </Tooltip>

        <Menu
          anchorEl={anchorEl}
          open={open}
          onClose={handleClose}
          PaperProps={{
            sx: {
              minWidth: 200,
            },
          }}
        >
          {Object.entries(SUPPORTED_LANGUAGES).map(([code, name]) => (
            <MenuItem
              key={code}
              onClick={() => handleLanguageChange(code)}
              selected={code === currentLanguage}
            >
              <Box display="flex" alignItems="center" gap={1} width="100%">
                <Typography sx={{ fontSize: '1.2rem' }}>
                  {getLanguageFlag(code)}
                </Typography>
                <Typography sx={{ flex: 1 }}>{name}</Typography>
                {code === currentLanguage && (
                  <CheckIcon color="primary" sx={{ fontSize: 20 }} />
                )}
              </Box>
            </MenuItem>
          ))}
        </Menu>
      </Box>
    );
  }

  // Default: icon variant
  return (
    <Box>
      <Tooltip title={`${t('settings.language')}: ${currentLanguageName}`}>
        <IconButton
          onClick={handleClick}
          size={size}
          sx={{
            position: 'relative',
          }}
        >
          <LanguageIcon />
          <Typography
            sx={{
              position: 'absolute',
              bottom: -2,
              right: -2,
              fontSize: '0.6rem',
              background: 'white',
              borderRadius: '50%',
              minWidth: 16,
              height: 16,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: 1,
            }}
          >
            {getLanguageFlag(currentLanguage)}
          </Typography>
        </IconButton>
      </Tooltip>

      <Menu
        anchorEl={anchorEl}
        open={open}
        onClose={handleClose}
        PaperProps={{
          sx: {
            minWidth: 220,
            maxHeight: 400,
          },
        }}
        transformOrigin={{
          vertical: 'top',
          horizontal: 'right',
        }}
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'right',
        }}
      >
        <Box sx={{ px: 2, py: 1, borderBottom: 1, borderColor: 'divider' }}>
          <Typography variant="subtitle2" fontWeight="bold">
            {t('settings.language')}
          </Typography>
        </Box>
        
        {Object.entries(SUPPORTED_LANGUAGES).map(([code, name]) => (
          <MenuItem
            key={code}
            onClick={() => handleLanguageChange(code)}
            selected={code === currentLanguage}
            sx={{
              py: 1.5,
              '&.Mui-selected': {
                bgcolor: 'primary.50',
                '&:hover': {
                  bgcolor: 'primary.100',
                },
              },
            }}
          >
            <Box display="flex" alignItems="center" gap={2} width="100%">
              <Typography sx={{ fontSize: '1.5rem' }}>
                {getLanguageFlag(code)}
              </Typography>
              <Box sx={{ flex: 1 }}>
                <Typography variant="body2" fontWeight="medium">
                  {name}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {code.toUpperCase()}
                </Typography>
              </Box>
              {code === currentLanguage && (
                <CheckIcon color="primary" sx={{ fontSize: 20 }} />
              )}
            </Box>
          </MenuItem>
        ))}

        <Box sx={{ px: 2, py: 1, borderTop: 1, borderColor: 'divider' }}>
          <Typography variant="caption" color="text.secondary">
            Missing your language? {' '}
            <Typography
              component="span"
              variant="caption"
              color="primary"
              sx={{ cursor: 'pointer', textDecoration: 'underline' }}
              onClick={() => window.open('/contact', '_blank')}
            >
              Let us know
            </Typography>
          </Typography>
        </Box>
      </Menu>
    </Box>
  );
};

// Hook for using translations with better TypeScript support
export const useAppTranslation = () => {
  const { t, i18n } = useTranslation();
  
  return {
    t,
    currentLanguage: i18n.language,
    isRTL: isRTL(),
    changeLanguage,
    supportedLanguages: SUPPORTED_LANGUAGES,
  };
};

export default LanguageSelector;
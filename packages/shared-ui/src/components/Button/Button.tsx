import React, { forwardRef, ButtonHTMLAttributes } from 'react';
import { TouchableOpacity, TouchableOpacityProps, Text, ActivityIndicator, View, StyleSheet } from 'react-native';
import { clsx } from 'clsx';
import { motion, HTMLMotionProps } from 'framer-motion';
import { useTheme } from '../../hooks/useTheme';
import { ButtonVariant, ButtonSize, ButtonColor } from '../../types';

interface BaseButtonProps {
  variant?: ButtonVariant;
  size?: ButtonSize;
  color?: ButtonColor;
  fullWidth?: boolean;
  disabled?: boolean;
  loading?: boolean;
  startIcon?: React.ReactNode;
  endIcon?: React.ReactNode;
  children: React.ReactNode;
}

// Web Button Props
interface WebButtonProps extends BaseButtonProps, Omit<ButtonHTMLAttributes<HTMLButtonElement>, keyof BaseButtonProps> {
  component?: 'button' | 'a';
  href?: string;
  motionProps?: HTMLMotionProps<'button'>;
}

// Native Button Props
interface NativeButtonProps extends BaseButtonProps, Omit<TouchableOpacityProps, keyof BaseButtonProps> {}

// Type guard to check if we're in a web environment
const isWeb = typeof document !== 'undefined';

/**
 * Cross-platform Button component with consistent styling and behavior
 */
export const Button = forwardRef<
  HTMLButtonElement | TouchableOpacity,
  WebButtonProps | NativeButtonProps
>((props, ref) => {
  const theme = useTheme();
  
  if (isWeb) {
    return <WebButton {...(props as WebButtonProps)} ref={ref as React.Ref<HTMLButtonElement>} />;
  }
  
  return <NativeButton {...(props as NativeButtonProps)} ref={ref as React.Ref<TouchableOpacity>} />;
});

Button.displayName = 'Button';

// Web implementation
const WebButton = forwardRef<HTMLButtonElement, WebButtonProps>(({
  variant = 'contained',
  size = 'medium',
  color = 'primary',
  fullWidth = false,
  disabled = false,
  loading = false,
  startIcon,
  endIcon,
  children,
  className,
  component = 'button',
  href,
  motionProps,
  ...rest
}, ref) => {
  const theme = useTheme();
  
  const classes = clsx(
    'wq-button',
    `wq-button--${variant}`,
    `wq-button--${size}`,
    `wq-button--${color}`,
    {
      'wq-button--fullWidth': fullWidth,
      'wq-button--disabled': disabled || loading,
      'wq-button--loading': loading,
    },
    className
  );
  
  const content = (
    <>
      {loading && (
        <span className="wq-button__loader">
          <svg className="wq-button__spinner" viewBox="0 0 24 24">
            <circle
              className="wq-button__spinner-circle"
              cx="12"
              cy="12"
              r="10"
              fill="none"
              strokeWidth="3"
            />
          </svg>
        </span>
      )}
      {startIcon && <span className="wq-button__icon wq-button__icon--start">{startIcon}</span>}
      <span className="wq-button__label">{children}</span>
      {endIcon && <span className="wq-button__icon wq-button__icon--end">{endIcon}</span>}
    </>
  );
  
  if (component === 'a' && href) {
    return (
      <motion.a
        ref={ref as any}
        href={href}
        className={classes}
        aria-disabled={disabled || loading}
        {...motionProps}
        {...rest}
      >
        {content}
      </motion.a>
    );
  }
  
  return (
    <motion.button
      ref={ref}
      className={classes}
      disabled={disabled || loading}
      type="button"
      whileHover={{ scale: disabled || loading ? 1 : 1.02 }}
      whileTap={{ scale: disabled || loading ? 1 : 0.98 }}
      transition={{ duration: 0.2 }}
      {...motionProps}
      {...rest}
    >
      {content}
    </motion.button>
  );
});

WebButton.displayName = 'WebButton';

// Native implementation
const NativeButton = forwardRef<TouchableOpacity, NativeButtonProps>(({
  variant = 'contained',
  size = 'medium',
  color = 'primary',
  fullWidth = false,
  disabled = false,
  loading = false,
  startIcon,
  endIcon,
  children,
  style,
  ...rest
}, ref) => {
  const theme = useTheme();
  
  const buttonStyles = [
    styles.button,
    styles[variant],
    styles[size],
    styles[`${variant}${color.charAt(0).toUpperCase() + color.slice(1)}`],
    fullWidth && styles.fullWidth,
    disabled && styles.disabled,
    style,
  ];
  
  const textStyles = [
    styles.text,
    styles[`text${size.charAt(0).toUpperCase() + size.slice(1)}`],
    styles[`text${variant.charAt(0).toUpperCase() + variant.slice(1)}`],
    styles[`text${variant}${color.charAt(0).toUpperCase() + color.slice(1)}`],
  ];
  
  return (
    <TouchableOpacity
      ref={ref}
      style={buttonStyles}
      disabled={disabled || loading}
      activeOpacity={0.8}
      {...rest}
    >
      <View style={styles.content}>
        {loading && (
          <ActivityIndicator
            size="small"
            color={variant === 'contained' ? '#fff' : theme.colors[color]}
            style={styles.loader}
          />
        )}
        {startIcon && <View style={styles.iconStart}>{startIcon}</View>}
        <Text style={textStyles}>{children}</Text>
        {endIcon && <View style={styles.iconEnd}>{endIcon}</View>}
      </View>
    </TouchableOpacity>
  );
});

NativeButton.displayName = 'NativeButton';

// Native styles
const styles = StyleSheet.create({
  button: {
    borderRadius: 8,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  content: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  text: {
    fontWeight: '600',
  },
  // Variants
  contained: {
    backgroundColor: '#1976d2',
  },
  outlined: {
    backgroundColor: 'transparent',
    borderWidth: 1,
    borderColor: '#1976d2',
  },
  text: {
    backgroundColor: 'transparent',
  },
  // Sizes
  small: {
    paddingVertical: 6,
    paddingHorizontal: 16,
  },
  medium: {
    paddingVertical: 8,
    paddingHorizontal: 20,
  },
  large: {
    paddingVertical: 12,
    paddingHorizontal: 24,
  },
  // Text sizes
  textSmall: {
    fontSize: 13,
  },
  textMedium: {
    fontSize: 15,
  },
  textLarge: {
    fontSize: 17,
  },
  // Colors
  containedPrimary: {
    backgroundColor: '#1976d2',
  },
  containedSecondary: {
    backgroundColor: '#dc004e',
  },
  containedSuccess: {
    backgroundColor: '#2e7d32',
  },
  containedError: {
    backgroundColor: '#d32f2f',
  },
  containedWarning: {
    backgroundColor: '#ed6c02',
  },
  containedInfo: {
    backgroundColor: '#0288d1',
  },
  // Text colors
  textContained: {
    color: '#fff',
  },
  textOutlined: {
    color: '#1976d2',
  },
  textText: {
    color: '#1976d2',
  },
  // States
  fullWidth: {
    width: '100%',
  },
  disabled: {
    opacity: 0.6,
  },
  loader: {
    marginRight: 8,
  },
  iconStart: {
    marginRight: 8,
  },
  iconEnd: {
    marginLeft: 8,
  },
});
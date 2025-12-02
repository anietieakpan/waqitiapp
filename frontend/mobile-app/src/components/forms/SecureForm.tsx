/**
 * Secure Form Component - Form with built-in validation and security
 * Integrates validation service with real-time feedback and security checks
 */

import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  View,
  Text,
  TextInput,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Animated,
  KeyboardAvoidingView,
  Platform,
  ActivityIndicator,
} from 'react-native';
import {
  Card,
  Title,
  Button,
  HelperText,
  ProgressBar,
  Chip,
  IconButton,
  List,
  Divider,
} from 'react-native-paper';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { debounce } from 'lodash';

import ValidationService, {
  ValidationSchema,
  ValidationResult,
  ValidationError,
  ValidationWarning,
  ValidationType,
} from '../../services/ValidationService';
import ErrorHandlingService from '../../services/ErrorHandlingService';
import { useAppSelector } from '../../hooks/redux';

// Field component props
export interface FormFieldConfig {
  name: string;
  label: string;
  type: 'text' | 'email' | 'password' | 'phone' | 'number' | 'pin' | 'card' | 'date' | 'select' | 'checkbox';
  placeholder?: string;
  icon?: string;
  secure?: boolean;
  autoComplete?: string;
  keyboardType?: 'default' | 'email-address' | 'numeric' | 'phone-pad' | 'decimal-pad';
  multiline?: boolean;
  numberOfLines?: number;
  options?: Array<{ label: string; value: any }>;
  disabled?: boolean;
  helperText?: string;
  mask?: string; // e.g., "9999-9999-9999-9999" for card number
  formatter?: (value: string) => string;
  parser?: (value: string) => string;
  validateOnBlur?: boolean;
  validateOnChange?: boolean;
  showStrengthIndicator?: boolean;
  dependsOn?: string[]; // Field dependencies
  visible?: (values: Record<string, any>) => boolean;
  transform?: (value: any) => any;
}

interface SecureFormProps {
  fields: FormFieldConfig[];
  validationSchema: ValidationSchema;
  onSubmit: (values: Record<string, any>, isValid: boolean) => void | Promise<void>;
  initialValues?: Record<string, any>;
  submitLabel?: string;
  showProgress?: boolean;
  enableAutoSave?: boolean;
  autoSaveInterval?: number;
  enableSecurityChecks?: boolean;
  enableRealTimeValidation?: boolean;
  customValidation?: (values: Record<string, any>) => Promise<ValidationResult>;
  onFieldChange?: (name: string, value: any) => void;
  style?: any;
}

const SecureForm: React.FC<SecureFormProps> = ({
  fields,
  validationSchema,
  onSubmit,
  initialValues = {},
  submitLabel = 'Submit',
  showProgress = true,
  enableAutoSave = false,
  autoSaveInterval = 30000,
  enableSecurityChecks = true,
  enableRealTimeValidation = true,
  customValidation,
  onFieldChange,
  style,
}) => {
  const { user } = useAppSelector((state) => state.auth);
  
  // State
  const [values, setValues] = useState<Record<string, any>>(initialValues);
  const [errors, setErrors] = useState<Record<string, ValidationError[]>>({});
  const [warnings, setWarnings] = useState<Record<string, ValidationWarning[]>>({});
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isValidating, setIsValidating] = useState(false);
  const [formProgress, setFormProgress] = useState(0);
  const [showPasswords, setShowPasswords] = useState<Record<string, boolean>>({});
  const [fieldFocus, setFieldFocus] = useState<string | null>(null);
  
  // Animations
  const shakeAnim = useRef(new Animated.Value(0)).current;
  const pulseAnim = useRef(new Animated.Value(1)).current;
  
  // Refs
  const fieldRefs = useRef<Record<string, TextInput | null>>({});
  const autoSaveTimer = useRef<NodeJS.Timeout | null>(null);

  // Calculate form progress
  useEffect(() => {
    const requiredFields = fields.filter((f) => 
      validationSchema[f.name]?.required || 
      (Array.isArray(validationSchema[f.name]) && 
       validationSchema[f.name].some((r: any) => r.required))
    );
    
    const filledFields = requiredFields.filter((f) => values[f.name]);
    const progress = requiredFields.length > 0 
      ? filledFields.length / requiredFields.length 
      : 0;
    
    setFormProgress(progress);
  }, [values, fields, validationSchema]);

  // Auto-save functionality
  useEffect(() => {
    if (enableAutoSave) {
      if (autoSaveTimer.current) {
        clearInterval(autoSaveTimer.current);
      }
      
      autoSaveTimer.current = setInterval(() => {
        saveFormData();
      }, autoSaveInterval);
      
      return () => {
        if (autoSaveTimer.current) {
          clearInterval(autoSaveTimer.current);
        }
      };
    }
  }, [enableAutoSave, autoSaveInterval, values]);

  // Debounced validation
  const debouncedValidation = useCallback(
    debounce(async (fieldName: string, value: any) => {
      if (!enableRealTimeValidation) return;
      
      setIsValidating(true);
      
      try {
        const result = await ValidationService.validateForm(
          { ...values, [fieldName]: value },
          { [fieldName]: validationSchema[fieldName] },
          user?.id
        );
        
        setErrors((prev) => ({
          ...prev,
          [fieldName]: result.errors.filter(e => e.field === fieldName),
        }));
        
        setWarnings((prev) => ({
          ...prev,
          [fieldName]: result.warnings.filter(w => w.field === fieldName),
        }));
      } catch (error) {
        console.error('Validation error:', error);
      } finally {
        setIsValidating(false);
      }
    }, 500),
    [values, validationSchema, user, enableRealTimeValidation]
  );

  /**
   * Handle field value change
   */
  const handleFieldChange = (name: string, rawValue: any) => {
    const field = fields.find(f => f.name === name);
    
    // Apply parser if available
    let value = field?.parser ? field.parser(rawValue) : rawValue;
    
    // Apply transform if available
    value = field?.transform ? field.transform(value) : value;
    
    setValues((prev) => ({ ...prev, [name]: value }));
    
    // Clear errors when user starts typing
    if (errors[name]) {
      setErrors((prev) => ({ ...prev, [name]: [] }));
    }
    
    // Real-time validation
    if (enableRealTimeValidation && touched[name]) {
      debouncedValidation(name, value);
    }
    
    // Custom field change handler
    if (onFieldChange) {
      onFieldChange(name, value);
    }
  };

  /**
   * Handle field blur
   */
  const handleFieldBlur = async (name: string) => {
    setTouched((prev) => ({ ...prev, [name]: true }));
    setFieldFocus(null);
    
    const field = fields.find(f => f.name === name);
    if (field?.validateOnBlur !== false) {
      await validateField(name);
    }
  };

  /**
   * Handle field focus
   */
  const handleFieldFocus = (name: string) => {
    setFieldFocus(name);
    
    // Pulse animation for focused field
    Animated.sequence([
      Animated.timing(pulseAnim, {
        toValue: 1.05,
        duration: 200,
        useNativeDriver: true,
      }),
      Animated.timing(pulseAnim, {
        toValue: 1,
        duration: 200,
        useNativeDriver: true,
      }),
    ]).start();
  };

  /**
   * Validate single field
   */
  const validateField = async (name: string): Promise<boolean> => {
    try {
      const result = await ValidationService.validateForm(
        { [name]: values[name] },
        { [name]: validationSchema[name] },
        user?.id
      );
      
      setErrors((prev) => ({
        ...prev,
        [name]: result.errors.filter(e => e.field === name),
      }));
      
      setWarnings((prev) => ({
        ...prev,
        [name]: result.warnings.filter(w => w.field === name),
      }));
      
      return result.isValid;
    } catch (error) {
      console.error('Field validation error:', error);
      return false;
    }
  };

  /**
   * Validate entire form
   */
  const validateForm = async (): Promise<boolean> => {
    setIsValidating(true);
    
    try {
      // Standard validation
      const result = await ValidationService.validateForm(
        values,
        validationSchema,
        user?.id
      );
      
      // Custom validation
      let customResult: ValidationResult | null = null;
      if (customValidation) {
        customResult = await customValidation(values);
      }
      
      // Combine results
      const allErrors: Record<string, ValidationError[]> = {};
      const allWarnings: Record<string, ValidationWarning[]> = {};
      
      [...result.errors, ...(customResult?.errors || [])].forEach((error) => {
        if (!allErrors[error.field]) {
          allErrors[error.field] = [];
        }
        allErrors[error.field].push(error);
      });
      
      [...result.warnings, ...(customResult?.warnings || [])].forEach((warning) => {
        if (!allWarnings[warning.field]) {
          allWarnings[warning.field] = [];
        }
        allWarnings[warning.field].push(warning);
      });
      
      setErrors(allErrors);
      setWarnings(allWarnings);
      
      // Mark all fields as touched
      const touchedFields: Record<string, boolean> = {};
      fields.forEach((field) => {
        touchedFields[field.name] = true;
      });
      setTouched(touchedFields);
      
      const isValid = result.isValid && (!customResult || customResult.isValid);
      
      if (!isValid) {
        // Shake animation for errors
        Animated.sequence([
          Animated.timing(shakeAnim, {
            toValue: 10,
            duration: 100,
            useNativeDriver: true,
          }),
          Animated.timing(shakeAnim, {
            toValue: -10,
            duration: 100,
            useNativeDriver: true,
          }),
          Animated.timing(shakeAnim, {
            toValue: 10,
            duration: 100,
            useNativeDriver: true,
          }),
          Animated.timing(shakeAnim, {
            toValue: 0,
            duration: 100,
            useNativeDriver: true,
          }),
        ]).start();
      }
      
      return isValid;
    } catch (error) {
      await ErrorHandlingService.handleError(error, {
        component: 'SecureForm',
        action: 'validateForm',
      });
      return false;
    } finally {
      setIsValidating(false);
    }
  };

  /**
   * Handle form submission
   */
  const handleSubmit = async () => {
    if (isSubmitting) return;
    
    setIsSubmitting(true);
    
    try {
      // Check for duplicate submission
      const isDuplicate = await ValidationService.checkDuplicateSubmission(
        values,
        'form_submission',
        5000
      );
      
      if (isDuplicate) {
        console.log('Duplicate submission detected');
        return;
      }
      
      // Validate form
      const isValid = await validateForm();
      
      if (isValid) {
        // Get sanitized values
        const result = await ValidationService.validateForm(
          values,
          validationSchema,
          user?.id
        );
        
        await onSubmit(result.sanitizedValue || values, true);
      } else {
        // Focus first error field
        const firstErrorField = Object.keys(errors).find(
          (field) => errors[field] && errors[field].length > 0
        );
        
        if (firstErrorField && fieldRefs.current[firstErrorField]) {
          fieldRefs.current[firstErrorField]?.focus();
        }
        
        await onSubmit(values, false);
      }
    } catch (error) {
      await ErrorHandlingService.handleError(error, {
        component: 'SecureForm',
        action: 'handleSubmit',
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  /**
   * Save form data (for auto-save)
   */
  const saveFormData = async () => {
    try {
      // Implementation would save to AsyncStorage or server
      console.log('Auto-saving form data...');
    } catch (error) {
      console.error('Auto-save failed:', error);
    }
  };

  /**
   * Format field value for display
   */
  const formatFieldValue = (field: FormFieldConfig, value: string): string => {
    if (field.formatter) {
      return field.formatter(value);
    }
    
    // Built-in formatters
    switch (field.type) {
      case 'phone':
        return formatPhoneNumber(value);
      case 'card':
        return formatCardNumber(value);
      default:
        return value;
    }
  };

  /**
   * Format phone number
   */
  const formatPhoneNumber = (value: string): string => {
    const cleaned = value.replace(/\D/g, '');
    const match = cleaned.match(/^(\d{3})(\d{3})(\d{4})$/);
    if (match) {
      return `(${match[1]}) ${match[2]}-${match[3]}`;
    }
    return value;
  };

  /**
   * Format card number
   */
  const formatCardNumber = (value: string): string => {
    const cleaned = value.replace(/\s/g, '');
    const chunks = cleaned.match(/.{1,4}/g) || [];
    return chunks.join(' ');
  };

  /**
   * Get field error message
   */
  const getFieldError = (name: string): string | null => {
    const fieldErrors = errors[name];
    if (!fieldErrors || fieldErrors.length === 0) return null;
    
    // Return highest severity error
    const sortedErrors = [...fieldErrors].sort((a, b) => {
      const severityOrder = { critical: 4, high: 3, medium: 2, low: 1 };
      return severityOrder[b.severity] - severityOrder[a.severity];
    });
    
    return sortedErrors[0].message;
  };

  /**
   * Get field warning message
   */
  const getFieldWarning = (name: string): string | null => {
    const fieldWarnings = warnings[name];
    if (!fieldWarnings || fieldWarnings.length === 0) return null;
    return fieldWarnings[0].message;
  };

  /**
   * Check if field should be visible
   */
  const isFieldVisible = (field: FormFieldConfig): boolean => {
    if (field.visible) {
      return field.visible(values);
    }
    return true;
  };

  /**
   * Render field based on type
   */
  const renderField = (field: FormFieldConfig) => {
    if (!isFieldVisible(field)) return null;
    
    const hasError = touched[field.name] && errors[field.name]?.length > 0;
    const hasWarning = touched[field.name] && warnings[field.name]?.length > 0;
    const errorMessage = getFieldError(field.name);
    const warningMessage = getFieldWarning(field.name);
    
    return (
      <Animated.View
        key={field.name}
        style={[
          styles.fieldContainer,
          fieldFocus === field.name && { transform: [{ scale: pulseAnim }] },
          hasError && { transform: [{ translateX: shakeAnim }] },
        ]}
      >
        <View style={styles.fieldHeader}>
          {field.icon && (
            <Icon name={field.icon} size={20} color="#666" style={styles.fieldIcon} />
          )}
          <Text style={styles.fieldLabel}>{field.label}</Text>
          {validationSchema[field.name]?.required && (
            <Text style={styles.requiredIndicator}>*</Text>
          )}
        </View>
        
        {field.type === 'password' ? (
          <View style={styles.passwordContainer}>
            <TextInput
              ref={(ref) => (fieldRefs.current[field.name] = ref)}
              style={[
                styles.input,
                hasError && styles.inputError,
                hasWarning && styles.inputWarning,
              ]}
              value={values[field.name] || ''}
              onChangeText={(text) => handleFieldChange(field.name, text)}
              onBlur={() => handleFieldBlur(field.name)}
              onFocus={() => handleFieldFocus(field.name)}
              placeholder={field.placeholder}
              secureTextEntry={!showPasswords[field.name]}
              autoComplete={field.autoComplete}
              keyboardType={field.keyboardType}
              editable={!field.disabled && !isSubmitting}
            />
            <IconButton
              icon={showPasswords[field.name] ? 'eye-off' : 'eye'}
              size={20}
              onPress={() => 
                setShowPasswords((prev) => ({
                  ...prev,
                  [field.name]: !prev[field.name],
                }))
              }
              style={styles.passwordToggle}
            />
          </View>
        ) : (
          <TextInput
            ref={(ref) => (fieldRefs.current[field.name] = ref)}
            style={[
              styles.input,
              hasError && styles.inputError,
              hasWarning && styles.inputWarning,
              field.multiline && styles.textArea,
            ]}
            value={
              field.formatter && values[field.name]
                ? formatFieldValue(field, values[field.name])
                : values[field.name] || ''
            }
            onChangeText={(text) => handleFieldChange(field.name, text)}
            onBlur={() => handleFieldBlur(field.name)}
            onFocus={() => handleFieldFocus(field.name)}
            placeholder={field.placeholder}
            secureTextEntry={field.secure}
            autoComplete={field.autoComplete}
            keyboardType={field.keyboardType}
            multiline={field.multiline}
            numberOfLines={field.numberOfLines}
            editable={!field.disabled && !isSubmitting}
          />
        )}
        
        {field.type === 'password' && field.showStrengthIndicator && values[field.name] && (
          <PasswordStrengthIndicator password={values[field.name]} />
        )}
        
        {field.helperText && !errorMessage && !warningMessage && (
          <HelperText type="info" visible>
            {field.helperText}
          </HelperText>
        )}
        
        {errorMessage && (
          <HelperText type="error" visible={hasError}>
            {errorMessage}
          </HelperText>
        )}
        
        {warningMessage && !errorMessage && (
          <HelperText type="info" visible={hasWarning}>
            ⚠️ {warningMessage}
          </HelperText>
        )}
      </Animated.View>
    );
  };

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      style={styles.container}
    >
      <ScrollView style={[styles.scrollView, style]}>
        <Card style={styles.card}>
          <Card.Content>
            {showProgress && (
              <View style={styles.progressContainer}>
                <View style={styles.progressHeader}>
                  <Text style={styles.progressLabel}>Form Progress</Text>
                  <Text style={styles.progressPercentage}>
                    {Math.round(formProgress * 100)}%
                  </Text>
                </View>
                <ProgressBar 
                  progress={formProgress} 
                  color="#4CAF50"
                  style={styles.progressBar}
                />
              </View>
            )}
            
            {fields.map(renderField)}
            
            {isValidating && (
              <View style={styles.validatingContainer}>
                <ActivityIndicator size="small" color="#2196F3" />
                <Text style={styles.validatingText}>Validating...</Text>
              </View>
            )}
            
            <Button
              mode="contained"
              onPress={handleSubmit}
              disabled={isSubmitting || isValidating}
              loading={isSubmitting}
              style={styles.submitButton}
            >
              {submitLabel}
            </Button>
            
            {enableAutoSave && (
              <Text style={styles.autoSaveText}>
                Form auto-saves every {autoSaveInterval / 1000} seconds
              </Text>
            )}
          </Card.Content>
        </Card>
      </ScrollView>
    </KeyboardAvoidingView>
  );
};

// Password strength indicator component
const PasswordStrengthIndicator: React.FC<{ password: string }> = ({ password }) => {
  const getStrength = (): { level: number; label: string; color: string } => {
    let strength = 0;
    
    if (password.length >= 8) strength++;
    if (password.length >= 12) strength++;
    if (/[a-z]/.test(password)) strength++;
    if (/[A-Z]/.test(password)) strength++;
    if (/\d/.test(password)) strength++;
    if (/[@$!%*?&]/.test(password)) strength++;
    
    if (strength <= 2) return { level: 1, label: 'Weak', color: '#F44336' };
    if (strength <= 4) return { level: 2, label: 'Fair', color: '#FF9800' };
    if (strength <= 5) return { level: 3, label: 'Good', color: '#2196F3' };
    return { level: 4, label: 'Strong', color: '#4CAF50' };
  };
  
  const strength = getStrength();
  
  return (
    <View style={styles.strengthContainer}>
      <View style={styles.strengthBars}>
        {[1, 2, 3, 4].map((level) => (
          <View
            key={level}
            style={[
              styles.strengthBar,
              { backgroundColor: level <= strength.level ? strength.color : '#E0E0E0' },
            ]}
          />
        ))}
      </View>
      <Text style={[styles.strengthLabel, { color: strength.color }]}>
        {strength.label}
      </Text>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollView: {
    flex: 1,
  },
  card: {
    margin: 16,
  },
  progressContainer: {
    marginBottom: 20,
  },
  progressHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  progressLabel: {
    fontSize: 14,
    color: '#666',
  },
  progressPercentage: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#4CAF50',
  },
  progressBar: {
    height: 6,
    borderRadius: 3,
  },
  fieldContainer: {
    marginBottom: 16,
  },
  fieldHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  fieldIcon: {
    marginRight: 8,
  },
  fieldLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#333',
  },
  requiredIndicator: {
    color: '#F44336',
    marginLeft: 4,
  },
  input: {
    borderWidth: 1,
    borderColor: '#DDD',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 16,
    backgroundColor: 'white',
  },
  inputError: {
    borderColor: '#F44336',
  },
  inputWarning: {
    borderColor: '#FF9800',
  },
  textArea: {
    minHeight: 100,
    textAlignVertical: 'top',
  },
  passwordContainer: {
    position: 'relative',
  },
  passwordToggle: {
    position: 'absolute',
    right: 0,
    top: 0,
  },
  strengthContainer: {
    marginTop: 8,
  },
  strengthBars: {
    flexDirection: 'row',
    gap: 4,
  },
  strengthBar: {
    flex: 1,
    height: 4,
    borderRadius: 2,
  },
  strengthLabel: {
    fontSize: 12,
    marginTop: 4,
  },
  validatingContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginVertical: 8,
  },
  validatingText: {
    marginLeft: 8,
    color: '#2196F3',
  },
  submitButton: {
    marginTop: 20,
  },
  autoSaveText: {
    fontSize: 12,
    color: '#666',
    textAlign: 'center',
    marginTop: 12,
  },
});

export default SecureForm;
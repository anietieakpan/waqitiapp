import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  Alert,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useSelector, useDispatch } from 'react-redux';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { RootState } from '../store';
import Header from '../components/Header';
import { AnalyticsService } from '../services/AnalyticsService';

/**
 * ChangePasswordScreen
 *
 * Screen for changing user's account password
 *
 * Features:
 * - Current password verification
 * - New password with strength validation
 * - Password confirmation
 * - Security requirements display
 * - Analytics tracking
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
const ChangePasswordScreen: React.FC = () => {
  const navigation = useNavigation();
  const dispatch = useDispatch();
  const { user } = useSelector((state: RootState) => state.auth);

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);

  const validatePasswordStrength = (password: string) => {
    const checks = {
      minLength: password.length >= 8,
      hasUpperCase: /[A-Z]/.test(password),
      hasLowerCase: /[a-z]/.test(password),
      hasNumber: /\d/.test(password),
      hasSpecialChar: /[!@#$%^&*(),.?":{}|<>]/.test(password),
    };

    const score = Object.values(checks).filter(Boolean).length;

    return {
      checks,
      score,
      strength: score <= 2 ? 'weak' : score <= 3 ? 'fair' : score <= 4 ? 'good' : 'strong',
      isValid: Object.values(checks).every(Boolean),
    };
  };

  const handleChangePassword = async () => {
    if (!currentPassword) {
      Alert.alert('Error', 'Please enter your current password');
      return;
    }

    const validation = validatePasswordStrength(newPassword);
    if (!validation.isValid) {
      Alert.alert('Weak Password', 'Please meet all password requirements');
      return;
    }

    if (newPassword !== confirmPassword) {
      Alert.alert('Error', 'New password and confirmation do not match');
      return;
    }

    if (currentPassword === newPassword) {
      Alert.alert('Error', 'New password must be different from current password');
      return;
    }

    setIsProcessing(true);

    try {
      // TODO: Call API to change password
      // await dispatch(changePassword({ currentPassword, newPassword })).unwrap();

      AnalyticsService.trackEvent('password_changed', {
        userId: user?.id,
      });

      Alert.alert(
        'Success',
        'Your password has been changed successfully. Please login again with your new password.',
        [
          {
            text: 'OK',
            onPress: () => {
              // TODO: Logout user and navigate to login
              navigation.navigate('Login' as never);
            },
          },
        ]
      );
    } catch (error: any) {
      Alert.alert('Error', error.message || 'Failed to change password');

      AnalyticsService.trackEvent('password_change_failed', {
        userId: user?.id,
        error: error.message,
      });
    } finally {
      setIsProcessing(false);
    }
  };

  const getStrengthColor = (strength: string): string => {
    switch (strength) {
      case 'weak':
        return '#F44336';
      case 'fair':
        return '#FF9800';
      case 'good':
        return '#8BC34A';
      case 'strong':
        return '#4CAF50';
      default:
        return '#E0E0E0';
    }
  };

  const renderPasswordInput = (
    label: string,
    value: string,
    onChangeText: (text: string) => void,
    show: boolean,
    onToggleShow: () => void,
    placeholder: string
  ) => (
    <View style={styles.inputContainer}>
      <Text style={styles.inputLabel}>{label}</Text>
      <View style={styles.inputWrapper}>
        <TextInput
          style={styles.input}
          value={value}
          onChangeText={onChangeText}
          placeholder={placeholder}
          placeholderTextColor="#999"
          secureTextEntry={!show}
          autoCapitalize="none"
          autoCorrect={false}
        />
        <TouchableOpacity onPress={onToggleShow} style={styles.eyeButton}>
          <Icon name={show ? 'eye-off' : 'eye'} size={24} color="#666" />
        </TouchableOpacity>
      </View>
    </View>
  );

  const validation = validatePasswordStrength(newPassword);

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <Header title="Change Password" showBack />

      <ScrollView style={styles.content} keyboardShouldPersistTaps="handled">
        <View style={styles.infoCard}>
          <Icon name="shield-lock" size={24} color="#6200EE" />
          <Text style={styles.infoText}>
            Choose a strong password to protect your account. We recommend using a password manager.
          </Text>
        </View>

        {renderPasswordInput(
          'Current Password',
          currentPassword,
          setCurrentPassword,
          showCurrentPassword,
          () => setShowCurrentPassword(!showCurrentPassword),
          'Enter current password'
        )}

        {renderPasswordInput(
          'New Password',
          newPassword,
          setNewPassword,
          showNewPassword,
          () => setShowNewPassword(!showNewPassword),
          'Enter new password'
        )}

        {newPassword.length > 0 && (
          <View style={styles.strengthIndicator}>
            <View style={styles.strengthBars}>
              {[1, 2, 3, 4, 5].map((bar) => (
                <View
                  key={bar}
                  style={[
                    styles.strengthBar,
                    {
                      backgroundColor:
                        bar <= validation.score
                          ? getStrengthColor(validation.strength)
                          : '#E0E0E0',
                    },
                  ]}
                />
              ))}
            </View>
            <Text
              style={[
                styles.strengthText,
                { color: getStrengthColor(validation.strength) },
              ]}
            >
              {validation.strength.charAt(0).toUpperCase() +
                validation.strength.slice(1)}{' '}
              Password
            </Text>
          </View>
        )}

        {renderPasswordInput(
          'Confirm New Password',
          confirmPassword,
          setConfirmPassword,
          showConfirmPassword,
          () => setShowConfirmPassword(!showConfirmPassword),
          'Re-enter new password'
        )}

        {confirmPassword.length > 0 && confirmPassword !== newPassword && (
          <View style={styles.errorContainer}>
            <Icon name="alert-circle" size={16} color="#F44336" />
            <Text style={styles.errorText}>Passwords do not match</Text>
          </View>
        )}

        <View style={styles.requirementsCard}>
          <Text style={styles.requirementsTitle}>Password Requirements:</Text>

          <View style={styles.requirementItem}>
            <Icon
              name={validation.checks.minLength ? 'check-circle' : 'circle-outline'}
              size={20}
              color={validation.checks.minLength ? '#4CAF50' : '#9E9E9E'}
            />
            <Text style={styles.requirementText}>At least 8 characters</Text>
          </View>

          <View style={styles.requirementItem}>
            <Icon
              name={validation.checks.hasUpperCase ? 'check-circle' : 'circle-outline'}
              size={20}
              color={validation.checks.hasUpperCase ? '#4CAF50' : '#9E9E9E'}
            />
            <Text style={styles.requirementText}>One uppercase letter (A-Z)</Text>
          </View>

          <View style={styles.requirementItem}>
            <Icon
              name={validation.checks.hasLowerCase ? 'check-circle' : 'circle-outline'}
              size={20}
              color={validation.checks.hasLowerCase ? '#4CAF50' : '#9E9E9E'}
            />
            <Text style={styles.requirementText}>One lowercase letter (a-z)</Text>
          </View>

          <View style={styles.requirementItem}>
            <Icon
              name={validation.checks.hasNumber ? 'check-circle' : 'circle-outline'}
              size={20}
              color={validation.checks.hasNumber ? '#4CAF50' : '#9E9E9E'}
            />
            <Text style={styles.requirementText}>One number (0-9)</Text>
          </View>

          <View style={styles.requirementItem}>
            <Icon
              name={validation.checks.hasSpecialChar ? 'check-circle' : 'circle-outline'}
              size={20}
              color={validation.checks.hasSpecialChar ? '#4CAF50' : '#9E9E9E'}
            />
            <Text style={styles.requirementText}>One special character (!@#$%^&*)</Text>
          </View>
        </View>

        <View style={styles.securityTip}>
          <Icon name="lightbulb-on" size={20} color="#FF9800" />
          <Text style={styles.securityTipText}>
            <Text style={styles.securityTipBold}>Security Tip: </Text>
            Use a unique password that you don't use anywhere else. Consider using a passphrase or password manager.
          </Text>
        </View>
      </ScrollView>

      <View style={styles.footer}>
        <TouchableOpacity
          style={[
            styles.changeButton,
            (isProcessing ||
              !currentPassword ||
              !newPassword ||
              !confirmPassword ||
              newPassword !== confirmPassword ||
              !validation.isValid) &&
              styles.changeButtonDisabled,
          ]}
          onPress={handleChangePassword}
          disabled={
            isProcessing ||
            !currentPassword ||
            !newPassword ||
            !confirmPassword ||
            newPassword !== confirmPassword ||
            !validation.isValid
          }
        >
          <Text style={styles.changeButtonText}>
            {isProcessing ? 'Changing Password...' : 'Change Password'}
          </Text>
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  content: {
    flex: 1,
    paddingHorizontal: 16,
    paddingTop: 16,
  },
  infoCard: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    backgroundColor: '#E8EAF6',
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 8,
    marginBottom: 24,
  },
  infoText: {
    fontSize: 14,
    color: '#666',
    marginLeft: 12,
    flex: 1,
    lineHeight: 20,
  },
  inputContainer: {
    marginBottom: 20,
  },
  inputLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: '#212121',
    marginBottom: 8,
  },
  inputWrapper: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderWidth: 2,
    borderColor: '#E0E0E0',
    borderRadius: 8,
    paddingHorizontal: 16,
  },
  input: {
    flex: 1,
    fontSize: 16,
    color: '#212121',
    paddingVertical: 14,
  },
  eyeButton: {
    padding: 8,
  },
  strengthIndicator: {
    marginTop: -12,
    marginBottom: 20,
  },
  strengthBars: {
    flexDirection: 'row',
    marginBottom: 8,
  },
  strengthBar: {
    flex: 1,
    height: 4,
    borderRadius: 2,
    marginHorizontal: 2,
  },
  strengthText: {
    fontSize: 13,
    fontWeight: '600',
  },
  errorContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: -12,
    marginBottom: 20,
  },
  errorText: {
    fontSize: 14,
    color: '#F44336',
    marginLeft: 6,
  },
  requirementsCard: {
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    borderRadius: 8,
    marginTop: 8,
    marginBottom: 16,
  },
  requirementsTitle: {
    fontSize: 15,
    fontWeight: 'bold',
    color: '#212121',
    marginBottom: 16,
  },
  requirementItem: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  requirementText: {
    fontSize: 14,
    color: '#666',
    marginLeft: 12,
  },
  securityTip: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    backgroundColor: '#FFF3E0',
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 8,
    marginBottom: 16,
  },
  securityTipText: {
    fontSize: 13,
    color: '#666',
    marginLeft: 12,
    flex: 1,
    lineHeight: 20,
  },
  securityTipBold: {
    fontWeight: 'bold',
    color: '#FF9800',
  },
  footer: {
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    borderTopWidth: 1,
    borderTopColor: '#E0E0E0',
  },
  changeButton: {
    backgroundColor: '#6200EE',
    paddingVertical: 16,
    borderRadius: 8,
    alignItems: 'center',
  },
  changeButtonDisabled: {
    backgroundColor: '#BDBDBD',
  },
  changeButtonText: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: 'bold',
  },
});

export default ChangePasswordScreen;

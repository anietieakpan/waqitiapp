import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  StatusBar,
  Platform,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';

/**
 * Header Component
 *
 * Reusable header component for consistent navigation UI
 *
 * Features:
 * - Back button support
 * - Custom title
 * - Right action buttons
 * - Safe area handling
 * - Customizable styling
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */

interface HeaderAction {
  icon: string;
  onPress: () => void;
  label?: string;
}

interface HeaderProps {
  title?: string;
  subtitle?: string;
  showBack?: boolean;
  onBack?: () => void;
  rightActions?: HeaderAction[];
  backgroundColor?: string;
  textColor?: string;
  statusBarStyle?: 'default' | 'light-content' | 'dark-content';
}

const Header: React.FC<HeaderProps> = ({
  title,
  subtitle,
  showBack = false,
  onBack,
  rightActions = [],
  backgroundColor = '#6200EE',
  textColor = '#FFFFFF',
  statusBarStyle = 'light-content',
}) => {
  const navigation = useNavigation();

  const handleBack = () => {
    if (onBack) {
      onBack();
    } else if (navigation.canGoBack()) {
      navigation.goBack();
    }
  };

  return (
    <>
      <StatusBar
        barStyle={statusBarStyle}
        backgroundColor={backgroundColor}
        translucent={Platform.OS === 'android'}
      />
      <View style={[styles.container, { backgroundColor }]}>
        <View style={styles.content}>
          {/* Left Section - Back Button */}
          <View style={styles.leftSection}>
            {showBack && (
              <TouchableOpacity
                style={styles.backButton}
                onPress={handleBack}
                hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
              >
                <Icon name="arrow-left" size={24} color={textColor} />
              </TouchableOpacity>
            )}
          </View>

          {/* Center Section - Title */}
          <View style={styles.centerSection}>
            {title && (
              <Text
                style={[styles.title, { color: textColor }]}
                numberOfLines={1}
                ellipsizeMode="tail"
              >
                {title}
              </Text>
            )}
            {subtitle && (
              <Text
                style={[styles.subtitle, { color: textColor, opacity: 0.8 }]}
                numberOfLines={1}
                ellipsizeMode="tail"
              >
                {subtitle}
              </Text>
            )}
          </View>

          {/* Right Section - Action Buttons */}
          <View style={styles.rightSection}>
            {rightActions.map((action, index) => (
              <TouchableOpacity
                key={index}
                style={styles.actionButton}
                onPress={action.onPress}
                hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
              >
                <Icon name={action.icon} size={24} color={textColor} />
              </TouchableOpacity>
            ))}
          </View>
        </View>
      </View>
    </>
  );
};

const styles = StyleSheet.create({
  container: {
    paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight || 0 : 44,
  },
  content: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    height: 56,
    paddingHorizontal: 8,
  },
  leftSection: {
    width: 56,
    justifyContent: 'center',
    alignItems: 'flex-start',
  },
  centerSection: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 8,
  },
  rightSection: {
    flexDirection: 'row',
    width: 56,
    justifyContent: 'flex-end',
    alignItems: 'center',
  },
  backButton: {
    width: 40,
    height: 40,
    justifyContent: 'center',
    alignItems: 'center',
  },
  title: {
    fontSize: 18,
    fontWeight: 'bold',
  },
  subtitle: {
    fontSize: 12,
    marginTop: 2,
  },
  actionButton: {
    width: 40,
    height: 40,
    justifyContent: 'center',
    alignItems: 'center',
    marginLeft: 4,
  },
});

export default Header;

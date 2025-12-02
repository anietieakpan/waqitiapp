import React from 'react';
import {
  View,
  Image,
  Text,
  StyleSheet,
  ViewStyle,
} from 'react-native';
import { useTheme } from '../contexts/ThemeContext';

interface UserAvatarProps {
  user: {
    avatar?: string;
    firstName?: string;
    lastName?: string;
    displayName?: string;
  };
  size?: number;
  style?: ViewStyle;
  fontSize?: number;
}

const UserAvatar: React.FC<UserAvatarProps> = ({
  user,
  size = 40,
  style,
  fontSize,
}) => {
  const { theme } = useTheme();

  const getInitials = () => {
    if (user.firstName && user.lastName) {
      return `${user.firstName[0]}${user.lastName[0]}`.toUpperCase();
    }
    if (user.displayName) {
      const parts = user.displayName.split(' ');
      if (parts.length >= 2) {
        return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
      }
      return user.displayName.substring(0, 2).toUpperCase();
    }
    return '??';
  };

  const getBackgroundColor = () => {
    // Generate a consistent color based on the user's name
    const name = user.displayName || `${user.firstName} ${user.lastName}` || '';
    let hash = 0;
    for (let i = 0; i < name.length; i++) {
      hash = name.charCodeAt(i) + ((hash << 5) - hash);
    }
    
    const colors = [
      '#FF6B6B', '#4ECDC4', '#45B7D1', '#FFA07A', '#98D8C8',
      '#F7DC6F', '#BB8FCE', '#85C1E2', '#F8C471', '#82E0AA'
    ];
    
    return colors[Math.abs(hash) % colors.length];
  };

  const textSize = fontSize || size * 0.4;

  if (user.avatar) {
    return (
      <Image
        source={{ uri: user.avatar }}
        style={[
          {
            width: size,
            height: size,
            borderRadius: size / 2,
          },
          style,
        ]}
      />
    );
  }

  return (
    <View
      style={[
        {
          width: size,
          height: size,
          borderRadius: size / 2,
          backgroundColor: getBackgroundColor(),
          justifyContent: 'center',
          alignItems: 'center',
        },
        style,
      ]}
    >
      <Text
        style={{
          color: '#FFFFFF',
          fontSize: textSize,
          fontWeight: '600',
        }}
      >
        {getInitials()}
      </Text>
    </View>
  );
};

export default UserAvatar;
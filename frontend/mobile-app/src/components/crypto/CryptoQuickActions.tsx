/**
 * Crypto Quick Actions Component
 * Provides quick access buttons for common cryptocurrency operations
 */
import React from 'react';
import { View, Text, TouchableOpacity, ScrollView, StyleSheet } from 'react-native';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import { theme } from '../../theme';

interface QuickAction {
  id: string;
  title: string;
  icon: string;
  iconLibrary: 'ionicons' | 'material';
  color: string;
  backgroundColor: string;
}

interface CryptoQuickActionsProps {
  onActionPress: (action: string) => void;
}

export const CryptoQuickActions: React.FC<CryptoQuickActionsProps> = ({
  onActionPress,
}) => {
  const quickActions: QuickAction[] = [
    {
      id: 'buy',
      title: 'Buy',
      icon: 'add-circle',
      iconLibrary: 'ionicons',
      color: theme.colors.success,
      backgroundColor: theme.colors.successLight,
    },
    {
      id: 'sell',
      title: 'Sell',
      icon: 'remove-circle',
      iconLibrary: 'ionicons',
      color: theme.colors.error,
      backgroundColor: theme.colors.errorLight,
    },
    {
      id: 'send',
      title: 'Send',
      icon: 'arrow-up-circle',
      iconLibrary: 'ionicons',
      color: theme.colors.primary,
      backgroundColor: theme.colors.primaryLight,
    },
    {
      id: 'receive',
      title: 'Receive',
      icon: 'arrow-down-circle',
      iconLibrary: 'ionicons',
      color: theme.colors.info,
      backgroundColor: theme.colors.infoLight,
    },
    {
      id: 'convert',
      title: 'Convert',
      icon: 'swap-horizontal',
      iconLibrary: 'ionicons',
      color: theme.colors.warning,
      backgroundColor: theme.colors.warningLight,
    },
    {
      id: 'scan',
      title: 'Scan QR',
      icon: 'qr-code-scanner',
      iconLibrary: 'material',
      color: theme.colors.secondary,
      backgroundColor: theme.colors.secondaryLight,
    },
  ];

  const renderActionButton = (action: QuickAction) => {
    const IconComponent = action.iconLibrary === 'ionicons' ? Ionicons : MaterialIcons;

    return (
      <TouchableOpacity
        key={action.id}
        style={styles.actionButton}
        onPress={() => onActionPress(action.id)}
        activeOpacity={0.7}
      >
        <View
          style={[
            styles.iconContainer,
            { backgroundColor: action.backgroundColor },
          ]}
        >
          <IconComponent
            name={action.icon as any}
            size={24}
            color={action.color}
          />
        </View>
        <Text style={styles.actionTitle}>{action.title}</Text>
      </TouchableOpacity>
    );
  };

  return (
    <View style={styles.container}>
      <Text style={styles.sectionTitle}>Quick Actions</Text>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.actionsContainer}
      >
        {quickActions.map(renderActionButton)}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    paddingVertical: 20,
    paddingHorizontal: 20,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: theme.colors.text,
    marginBottom: 16,
  },
  actionsContainer: {
    flexDirection: 'row',
    paddingHorizontal: 4,
  },
  actionButton: {
    alignItems: 'center',
    marginHorizontal: 8,
    minWidth: 70,
  },
  iconContainer: {
    width: 56,
    height: 56,
    borderRadius: 28,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 8,
    elevation: 2,
    shadowColor: theme.colors.black,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  actionTitle: {
    fontSize: 12,
    fontWeight: '500',
    color: theme.colors.text,
    textAlign: 'center',
  },
});
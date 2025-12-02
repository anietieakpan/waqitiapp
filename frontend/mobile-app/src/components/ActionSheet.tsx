import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Modal,
  Pressable,
  Animated,
  Dimensions,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialIcons';
import { useTheme } from '../contexts/ThemeContext';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

interface ActionSheetOption {
  label: string;
  icon?: string;
  onPress: () => void;
  destructive?: boolean;
}

interface ActionSheetProps {
  visible: boolean;
  onClose: () => void;
  options: ActionSheetOption[];
  title?: string;
  message?: string;
  cancelLabel?: string;
}

const ActionSheet: React.FC<ActionSheetProps> = ({
  visible,
  onClose,
  options,
  title,
  message,
  cancelLabel = 'Cancel',
}) => {
  const { theme } = useTheme();
  const insets = useSafeAreaInsets();
  const slideAnim = React.useRef(new Animated.Value(300)).current;

  React.useEffect(() => {
    if (visible) {
      Animated.spring(slideAnim, {
        toValue: 0,
        useNativeDriver: true,
        tension: 65,
        friction: 11,
      }).start();
    } else {
      Animated.timing(slideAnim, {
        toValue: 300,
        duration: 200,
        useNativeDriver: true,
      }).start();
    }
  }, [visible]);

  const handleOptionPress = (option: ActionSheetOption) => {
    onClose();
    // Delay action to allow animation to complete
    setTimeout(() => {
      option.onPress();
    }, 200);
  };

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={onClose}
    >
      <Pressable style={styles.backdrop} onPress={onClose}>
        <Animated.View
          style={[
            styles.container,
            {
              backgroundColor: theme.colors.surface,
              paddingBottom: insets.bottom + 16,
              transform: [{ translateY: slideAnim }],
            },
          ]}
        >
          {(title || message) && (
            <View style={styles.header}>
              {title && (
                <Text style={[styles.title, { color: theme.colors.text }]}>
                  {title}
                </Text>
              )}
              {message && (
                <Text style={[styles.message, { color: theme.colors.textSecondary }]}>
                  {message}
                </Text>
              )}
            </View>
          )}

          <View style={styles.options}>
            {options.map((option, index) => (
              <TouchableOpacity
                key={index}
                style={[
                  styles.option,
                  index > 0 && styles.optionBorder,
                  { borderTopColor: theme.colors.divider },
                ]}
                onPress={() => handleOptionPress(option)}
                activeOpacity={0.7}
              >
                {option.icon && (
                  <Icon
                    name={option.icon}
                    size={24}
                    color={option.destructive ? theme.colors.error : theme.colors.text}
                    style={styles.optionIcon}
                  />
                )}
                <Text
                  style={[
                    styles.optionText,
                    {
                      color: option.destructive ? theme.colors.error : theme.colors.text,
                    },
                  ]}
                >
                  {option.label}
                </Text>
              </TouchableOpacity>
            ))}
          </View>

          <TouchableOpacity
            style={[
              styles.cancelButton,
              { backgroundColor: theme.colors.surfaceVariant },
            ]}
            onPress={onClose}
            activeOpacity={0.7}
          >
            <Text style={[styles.cancelText, { color: theme.colors.primary }]}>
              {cancelLabel}
            </Text>
          </TouchableOpacity>
        </Animated.View>
      </Pressable>
    </Modal>
  );
};

const { height } = Dimensions.get('window');

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'flex-end',
  },
  container: {
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    maxHeight: height * 0.8,
  },
  header: {
    paddingHorizontal: 16,
    paddingVertical: 20,
    alignItems: 'center',
  },
  title: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 4,
  },
  message: {
    fontSize: 14,
    textAlign: 'center',
  },
  options: {
    paddingHorizontal: 16,
  },
  option: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 16,
  },
  optionBorder: {
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  optionIcon: {
    marginRight: 12,
  },
  optionText: {
    fontSize: 16,
  },
  cancelButton: {
    marginHorizontal: 16,
    marginTop: 8,
    paddingVertical: 16,
    borderRadius: 12,
    alignItems: 'center',
  },
  cancelText: {
    fontSize: 16,
    fontWeight: '600',
  },
});

export default ActionSheet;
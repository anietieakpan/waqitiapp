import React, { useRef } from 'react';
import {
  Animated,
  StyleSheet,
  Text,
  View,
  I18nManager,
  TouchableOpacity,
} from 'react-native';
import { RectButton, Swipeable } from 'react-native-gesture-handler';
import Icon from 'react-native-vector-icons/MaterialIcons';

interface SwipeAction {
  text: string;
  backgroundColor: string;
  onPress: () => void;
  icon?: string;
}

interface SwipeableRowProps {
  children: React.ReactNode;
  rightActions?: SwipeAction[];
  leftActions?: SwipeAction[];
  enabled?: boolean;
}

const SwipeableRow: React.FC<SwipeableRowProps> = ({
  children,
  rightActions = [],
  leftActions = [],
  enabled = true,
}) => {
  const swipeableRef = useRef<Swipeable>(null);

  const renderRightAction = (
    action: SwipeAction,
    progress: Animated.AnimatedInterpolation,
    dragX: Animated.AnimatedInterpolation,
    index: number,
    total: number
  ) => {
    const trans = progress.interpolate({
      inputRange: [0, 1],
      outputRange: [64 * (total - index), 0],
    });

    const pressHandler = () => {
      action.onPress();
      swipeableRef.current?.close();
    };

    return (
      <Animated.View
        key={index}
        style={{ flex: 1, transform: [{ translateX: trans }] }}
      >
        <RectButton
          style={[styles.rightAction, { backgroundColor: action.backgroundColor }]}
          onPress={pressHandler}
        >
          {action.icon && (
            <Icon name={action.icon} size={24} color="#FFFFFF" />
          )}
          <Text style={styles.actionText}>{action.text}</Text>
        </RectButton>
      </Animated.View>
    );
  };

  const renderRightActions = (
    progress: Animated.AnimatedInterpolation,
    dragX: Animated.AnimatedInterpolation
  ) => (
    <View
      style={{
        width: 64 * rightActions.length,
        flexDirection: I18nManager.isRTL ? 'row-reverse' : 'row',
      }}
    >
      {rightActions.map((action, index) =>
        renderRightAction(action, progress, dragX, index, rightActions.length)
      )}
    </View>
  );

  const renderLeftAction = (
    action: SwipeAction,
    progress: Animated.AnimatedInterpolation,
    dragX: Animated.AnimatedInterpolation,
    index: number
  ) => {
    const trans = progress.interpolate({
      inputRange: [0, 1],
      outputRange: [-64 * (index + 1), 0],
    });

    const pressHandler = () => {
      action.onPress();
      swipeableRef.current?.close();
    };

    return (
      <Animated.View
        key={index}
        style={{ flex: 1, transform: [{ translateX: trans }] }}
      >
        <RectButton
          style={[styles.leftAction, { backgroundColor: action.backgroundColor }]}
          onPress={pressHandler}
        >
          {action.icon && (
            <Icon name={action.icon} size={24} color="#FFFFFF" />
          )}
          <Text style={styles.actionText}>{action.text}</Text>
        </RectButton>
      </Animated.View>
    );
  };

  const renderLeftActions = (
    progress: Animated.AnimatedInterpolation,
    dragX: Animated.AnimatedInterpolation
  ) => (
    <View
      style={{
        width: 64 * leftActions.length,
        flexDirection: I18nManager.isRTL ? 'row-reverse' : 'row',
      }}
    >
      {leftActions.map((action, index) =>
        renderLeftAction(action, progress, dragX, index)
      )}
    </View>
  );

  if (!enabled || (rightActions.length === 0 && leftActions.length === 0)) {
    return <>{children}</>;
  }

  return (
    <Swipeable
      ref={swipeableRef}
      friction={2}
      enableTrackpadTwoFingerGesture
      leftThreshold={30}
      rightThreshold={40}
      renderLeftActions={leftActions.length > 0 ? renderLeftActions : undefined}
      renderRightActions={rightActions.length > 0 ? renderRightActions : undefined}
    >
      {children}
    </Swipeable>
  );
};

const styles = StyleSheet.create({
  leftAction: {
    flex: 1,
    backgroundColor: '#497AFC',
    justifyContent: 'center',
    alignItems: 'flex-end',
    paddingRight: 20,
  },
  rightAction: {
    alignItems: 'center',
    flex: 1,
    justifyContent: 'center',
  },
  actionText: {
    color: 'white',
    fontSize: 16,
    backgroundColor: 'transparent',
    marginTop: 4,
  },
});

export default SwipeableRow;
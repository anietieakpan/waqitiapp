/**
 * Offline Indicator Component
 * Shows offline status and sync progress
 */
import React, { useEffect, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Animated,
  TouchableOpacity,
  ActivityIndicator,
} from 'react-native';
import { useSelector } from 'react-redux';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { 
  selectIsOffline, 
  selectPendingChanges, 
  selectSyncStatus 
} from '../store/offline/offlineSlice';
import { useOfflineSync } from '../hooks/useOfflineSync';
import { colors, typography } from '../theme';

export const OfflineIndicator: React.FC = () => {
  const isOffline = useSelector(selectIsOffline);
  const pendingChanges = useSelector(selectPendingChanges);
  const syncStatus = useSelector(selectSyncStatus);
  const { triggerSync } = useOfflineSync();
  
  const slideAnim = useRef(new Animated.Value(-100)).current;
  const fadeAnim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (isOffline || pendingChanges > 0 || syncStatus.status === 'syncing') {
      // Slide in
      Animated.parallel([
        Animated.timing(slideAnim, {
          toValue: 0,
          duration: 300,
          useNativeDriver: true,
        }),
        Animated.timing(fadeAnim, {
          toValue: 1,
          duration: 300,
          useNativeDriver: true,
        }),
      ]).start();
    } else {
      // Slide out
      Animated.parallel([
        Animated.timing(slideAnim, {
          toValue: -100,
          duration: 300,
          useNativeDriver: true,
        }),
        Animated.timing(fadeAnim, {
          toValue: 0,
          duration: 300,
          useNativeDriver: true,
        }),
      ]).start();
    }
  }, [isOffline, pendingChanges, syncStatus.status, slideAnim, fadeAnim]);

  const getBackgroundColor = () => {
    if (isOffline) return colors.error;
    if (syncStatus.status === 'syncing') return colors.primary;
    if (pendingChanges > 0) return colors.warning;
    return colors.success;
  };

  const getMessage = () => {
    if (isOffline) {
      return pendingChanges > 0 
        ? `Offline - ${pendingChanges} changes pending`
        : 'You are offline';
    }
    
    if (syncStatus.status === 'syncing') {
      return `Syncing... ${Math.round(syncStatus.progress)}%`;
    }
    
    if (pendingChanges > 0) {
      return `${pendingChanges} changes to sync`;
    }
    
    return 'All changes synced';
  };

  const getIcon = () => {
    if (isOffline) return 'wifi-off';
    if (syncStatus.status === 'syncing') return 'sync';
    if (pendingChanges > 0) return 'cloud-upload';
    return 'cloud-check';
  };

  const canTriggerSync = !isOffline && pendingChanges > 0 && syncStatus.status !== 'syncing';

  return (
    <Animated.View
      style={[
        styles.container,
        {
          backgroundColor: getBackgroundColor(),
          transform: [{ translateY: slideAnim }],
          opacity: fadeAnim,
        },
      ]}
    >
      <TouchableOpacity
        style={styles.content}
        onPress={canTriggerSync ? triggerSync : undefined}
        disabled={!canTriggerSync}
        activeOpacity={canTriggerSync ? 0.7 : 1}
      >
        <View style={styles.leftContent}>
          {syncStatus.status === 'syncing' ? (
            <ActivityIndicator size="small" color={colors.white} />
          ) : (
            <Icon name={getIcon()} size={20} color={colors.white} />
          )}
          <Text style={styles.message}>{getMessage()}</Text>
        </View>
        
        {canTriggerSync && (
          <View style={styles.syncButton}>
            <Text style={styles.syncButtonText}>Sync Now</Text>
            <Icon name="chevron-right" size={16} color={colors.white} />
          </View>
        )}
      </TouchableOpacity>
      
      {syncStatus.status === 'syncing' && (
        <View style={styles.progressBar}>
          <View
            style={[
              styles.progressFill,
              { width: `${syncStatus.progress}%` },
            ]}
          />
        </View>
      )}
    </Animated.View>
  );
};

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    zIndex: 1000,
    shadowColor: colors.black,
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.25,
    shadowRadius: 3.84,
    elevation: 5,
  },
  content: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    paddingTop: 40, // Account for status bar
  },
  leftContent: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  message: {
    color: colors.white,
    fontSize: 14,
    fontFamily: typography.medium,
    marginLeft: 8,
  },
  syncButton: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.2)',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
  },
  syncButtonText: {
    color: colors.white,
    fontSize: 12,
    fontFamily: typography.medium,
    marginRight: 4,
  },
  progressBar: {
    height: 3,
    backgroundColor: 'rgba(255, 255, 255, 0.3)',
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
  },
  progressFill: {
    height: '100%',
    backgroundColor: colors.white,
  },
});
import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  Alert,
  Platform,
  Linking,
  ActivityIndicator
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialIcons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import WidgetConfiguration from '../../components/widgets/WidgetConfiguration';
import WidgetPreview from '../../components/widgets/WidgetPreview';
import WidgetService, { WidgetConfig } from '../../services/widgets/WidgetService';

interface WidgetInfo {
  supportsWidgets: boolean;
  platform: string;
  version?: string;
  activeWidgets?: number;
  reason?: string;
}

const WidgetSettingsScreen: React.FC = () => {
  const insets = useSafeAreaInsets();
  const [activeTab, setActiveTab] = useState<'preview' | 'settings'>('preview');
  const [widgetInfo, setWidgetInfo] = useState<WidgetInfo | null>(null);
  const [configurations, setConfigurations] = useState<Record<string, WidgetConfig>>({});
  const [loading, setLoading] = useState(true);
  const [errors, setErrors] = useState<any[]>([]);

  useEffect(() => {
    initializeScreen();
  }, []);

  const initializeScreen = async () => {
    try {
      setLoading(true);
      
      // Initialize widget service
      await WidgetService.initialize();
      
      // Get widget info and configurations
      const [info, configs, errorList] = await Promise.all([
        getWidgetInfo(),
        WidgetService.getConfiguration(),
        WidgetService.getErrors()
      ]);
      
      setWidgetInfo(info);
      setConfigurations(configs);
      setErrors(errorList);
      
    } catch (error) {
      console.error('Failed to initialize widget screen:', error);
      Alert.alert('Error', 'Failed to load widget information');
    } finally {
      setLoading(false);
    }
  };

  const getWidgetInfo = async (): Promise<WidgetInfo> => {
    try {
      // This would normally call the native module
      // For now, return platform-specific info
      if (Platform.OS === 'ios') {
        const iosVersion = parseInt(Platform.Version as string, 10);
        return {
          supportsWidgets: iosVersion >= 14,
          platform: 'iOS',
          version: Platform.Version as string,
          activeWidgets: 0,
          reason: iosVersion < 14 ? 'iOS 14.0 or later required' : undefined
        };
      } else {
        const androidVersion = parseInt(Platform.Version as string, 10);
        return {
          supportsWidgets: androidVersion >= 11,
          platform: 'Android',
          version: Platform.Version as string,
          activeWidgets: 0,
          reason: androidVersion < 11 ? 'Android 3.0 or later required' : undefined
        };
      }
    } catch (error) {
      console.error('Failed to get widget info:', error);
      return {
        supportsWidgets: false,
        platform: Platform.OS,
        reason: 'Unable to determine widget support'
      };
    }
  };

  const handleQuickAction = async (actionId: string) => {
    try {
      await WidgetService.handleWidgetTap('quick_action', actionId);
    } catch (error) {
      console.error('Failed to handle quick action:', error);
    }
  };

  const handleWidgetTap = async (widgetType: string) => {
    try {
      await WidgetService.handleWidgetTap(widgetType);
    } catch (error) {
      console.error('Failed to handle widget tap:', error);
    }
  };

  const refreshAllWidgets = async () => {
    try {
      await WidgetService.refreshWidgets();
      Alert.alert('Success', 'All widgets have been refreshed');
    } catch (error) {
      console.error('Failed to refresh widgets:', error);
      Alert.alert('Error', 'Failed to refresh widgets');
    }
  };

  const openWidgetSettings = () => {
    if (Platform.OS === 'ios') {
      Alert.alert(
        'Add Widgets',
        'To add Waqiti widgets to your home screen:\n\n1. Long press on your home screen\n2. Tap the "+" button\n3. Search for "Waqiti"\n4. Choose your preferred widget size\n5. Tap "Add Widget"',
        [{ text: 'OK' }]
      );
    } else {
      Alert.alert(
        'Add Widgets',
        'To add Waqiti widgets to your home screen:\n\n1. Long press on your home screen\n2. Tap "Widgets"\n3. Find "Waqiti" in the list\n4. Drag the widget to your home screen',
        [{ text: 'OK' }]
      );
    }
  };

  const clearErrors = async () => {
    try {
      await WidgetService.clearErrors();
      setErrors([]);
      Alert.alert('Success', 'Error log cleared');
    } catch (error) {
      console.error('Failed to clear errors:', error);
      Alert.alert('Error', 'Failed to clear error log');
    }
  };

  const renderUnsupportedMessage = () => (
    <View style={styles.unsupportedContainer}>
      <Icon name="widgets" size={64} color="#CCCCCC" />
      <Text style={styles.unsupportedTitle}>Widgets Not Supported</Text>
      <Text style={styles.unsupportedMessage}>
        {widgetInfo?.reason || 'Widgets are not supported on this device'}
      </Text>
    </View>
  );

  const renderPreviewTab = () => (
    <ScrollView style={styles.previewContainer}>
      <View style={styles.previewHeader}>
        <Text style={styles.previewTitle}>Widget Previews</Text>
        <Text style={styles.previewSubtitle}>
          See how your widgets will look on the home screen
        </Text>
      </View>

      {/* Balance Widget Previews */}
      <View style={styles.widgetTypeSection}>
        <Text style={styles.widgetTypeTitle}>Balance Widget</Text>
        <View style={styles.previewRow}>
          <View style={styles.previewItem}>
            <Text style={styles.previewLabel}>Small</Text>
            <WidgetPreview
              widgetType="balance"
              size="small"
              onTap={() => handleWidgetTap('balance')}
              onQuickAction={handleQuickAction}
            />
          </View>
        </View>
        <View style={styles.previewRow}>
          <WidgetPreview
            widgetType="balance"
            size="medium"
            onTap={() => handleWidgetTap('balance')}
            onQuickAction={handleQuickAction}
          />
        </View>
        <View style={styles.previewRow}>
          <WidgetPreview
            widgetType="balance"
            size="large"
            onTap={() => handleWidgetTap('balance')}
            onQuickAction={handleQuickAction}
          />
        </View>
      </View>

      {/* Quick Actions Widget */}
      <View style={styles.widgetTypeSection}>
        <Text style={styles.widgetTypeTitle}>Quick Actions Widget</Text>
        <View style={styles.previewRow}>
          <View style={styles.previewItem}>
            <Text style={styles.previewLabel}>Small</Text>
            <WidgetPreview
              widgetType="quick_actions"
              size="small"
              onTap={() => handleWidgetTap('quick_actions')}
              onQuickAction={handleQuickAction}
            />
          </View>
        </View>
      </View>

      {/* Recent Transactions Widget */}
      <View style={styles.widgetTypeSection}>
        <Text style={styles.widgetTypeTitle}>Recent Transactions Widget</Text>
        <View style={styles.previewRow}>
          <WidgetPreview
            widgetType="recent_transactions"
            size="medium"
            onTap={() => handleWidgetTap('recent_transactions')}
            onQuickAction={handleQuickAction}
          />
        </View>
      </View>
    </ScrollView>
  );

  const renderSettingsTab = () => (
    <WidgetConfiguration
      onConfigurationChange={setConfigurations}
    />
  );

  const renderErrorSection = () => {
    if (errors.length === 0) return null;

    return (
      <View style={styles.errorSection}>
        <View style={styles.errorHeader}>
          <Icon name="error" size={20} color="#FF5252" />
          <Text style={styles.errorTitle}>Recent Errors ({errors.length})</Text>
          <TouchableOpacity onPress={clearErrors}>
            <Text style={styles.clearErrorsText}>Clear</Text>
          </TouchableOpacity>
        </View>
        <ScrollView style={styles.errorList} nestedScrollEnabled>
          {errors.slice(0, 3).map((error, index) => (
            <View key={index} style={styles.errorItem}>
              <Text style={styles.errorCode}>{error.code}</Text>
              <Text style={styles.errorMessage}>{error.message}</Text>
              <Text style={styles.errorTime}>
                {new Date(error.timestamp).toLocaleString()}
              </Text>
            </View>
          ))}
        </ScrollView>
      </View>
    );
  };

  if (loading) {
    return (
      <View style={[styles.container, { paddingTop: insets.top }]}>
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color="#007AFF" />
          <Text style={styles.loadingText}>Loading widget settings...</Text>
        </View>
      </View>
    );
  }

  if (!widgetInfo?.supportsWidgets) {
    return (
      <View style={[styles.container, { paddingTop: insets.top }]}>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>Widgets</Text>
        </View>
        {renderUnsupportedMessage()}
      </View>
    );
  }

  return (
    <View style={[styles.container, { paddingTop: insets.top }]}>
      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Widgets</Text>
        <View style={styles.headerButtons}>
          <TouchableOpacity
            style={styles.headerButton}
            onPress={refreshAllWidgets}
          >
            <Icon name="refresh" size={24} color="#007AFF" />
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.headerButton}
            onPress={openWidgetSettings}
          >
            <Icon name="add" size={24} color="#007AFF" />
          </TouchableOpacity>
        </View>
      </View>

      {/* Widget Info Banner */}
      <View style={styles.infoBanner}>
        <Icon name="info" size={20} color="#007AFF" />
        <Text style={styles.infoBannerText}>
          {widgetInfo.activeWidgets || 0} active widgets • {widgetInfo.platform} {widgetInfo.version}
        </Text>
      </View>

      {/* Tab Navigation */}
      <View style={styles.tabContainer}>
        <TouchableOpacity
          style={[styles.tab, activeTab === 'preview' && styles.activeTab]}
          onPress={() => setActiveTab('preview')}
        >
          <Text style={[styles.tabText, activeTab === 'preview' && styles.activeTabText]}>
            Preview
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.tab, activeTab === 'settings' && styles.activeTab]}
          onPress={() => setActiveTab('settings')}
        >
          <Text style={[styles.tabText, activeTab === 'settings' && styles.activeTabText]}>
            Settings
          </Text>
        </TouchableOpacity>
      </View>

      {/* Tab Content */}
      <View style={styles.tabContent}>
        {activeTab === 'preview' ? renderPreviewTab() : renderSettingsTab()}
      </View>

      {/* Error Section */}
      {renderErrorSection()}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F8F9FA',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 16,
    fontSize: 16,
    color: '#666',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 16,
    backgroundColor: '#FFFFFF',
    borderBottomWidth: 1,
    borderBottomColor: '#E1E5E9',
  },
  headerTitle: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#1F2937',
  },
  headerButtons: {
    flexDirection: 'row',
    gap: 12,
  },
  headerButton: {
    padding: 8,
  },
  infoBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#E3F2FD',
    paddingHorizontal: 20,
    paddingVertical: 12,
    gap: 8,
  },
  infoBannerText: {
    color: '#1976D2',
    fontSize: 14,
    fontWeight: '500',
  },
  tabContainer: {
    flexDirection: 'row',
    backgroundColor: '#FFFFFF',
    borderBottomWidth: 1,
    borderBottomColor: '#E1E5E9',
  },
  tab: {
    flex: 1,
    paddingVertical: 16,
    alignItems: 'center',
  },
  activeTab: {
    borderBottomWidth: 2,
    borderBottomColor: '#007AFF',
  },
  tabText: {
    fontSize: 16,
    color: '#6B7280',
    fontWeight: '500',
  },
  activeTabText: {
    color: '#007AFF',
    fontWeight: '600',
  },
  tabContent: {
    flex: 1,
  },
  unsupportedContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 40,
  },
  unsupportedTitle: {
    fontSize: 22,
    fontWeight: 'bold',
    color: '#374151',
    marginTop: 16,
    marginBottom: 8,
    textAlign: 'center',
  },
  unsupportedMessage: {
    fontSize: 16,
    color: '#6B7280',
    textAlign: 'center',
    lineHeight: 24,
  },
  previewContainer: {
    flex: 1,
  },
  previewHeader: {
    padding: 20,
    backgroundColor: '#FFFFFF',
    borderBottomWidth: 1,
    borderBottomColor: '#E5E7EB',
  },
  previewTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#1F2937',
    marginBottom: 4,
  },
  previewSubtitle: {
    fontSize: 14,
    color: '#6B7280',
  },
  widgetTypeSection: {
    marginBottom: 24,
  },
  widgetTypeTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#374151',
    paddingHorizontal: 20,
    paddingVertical: 16,
    backgroundColor: '#F9FAFB',
  },
  previewRow: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    alignItems: 'center',
  },
  previewItem: {
    alignItems: 'center',
    marginBottom: 8,
  },
  previewLabel: {
    fontSize: 14,
    color: '#6B7280',
    marginBottom: 8,
    fontWeight: '500',
  },
  errorSection: {
    backgroundColor: '#FFFFFF',
    borderTopWidth: 1,
    borderTopColor: '#E5E7EB',
    maxHeight: 150,
  },
  errorHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 12,
    backgroundColor: '#FEF2F2',
    gap: 8,
  },
  errorTitle: {
    flex: 1,
    fontSize: 14,
    fontWeight: '600',
    color: '#DC2626',
  },
  clearErrorsText: {
    fontSize: 14,
    color: '#007AFF',
    fontWeight: '500',
  },
  errorList: {
    maxHeight: 100,
  },
  errorItem: {
    paddingHorizontal: 20,
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: '#F3F4F6',
  },
  errorCode: {
    fontSize: 12,
    fontWeight: '600',
    color: '#DC2626',
  },
  errorMessage: {
    fontSize: 13,
    color: '#374151',
    marginVertical: 2,
  },
  errorTime: {
    fontSize: 11,
    color: '#9CA3AF',
  },
});

export default WidgetSettingsScreen;
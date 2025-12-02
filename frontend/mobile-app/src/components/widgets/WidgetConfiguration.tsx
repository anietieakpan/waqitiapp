import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Switch,
  TouchableOpacity,
  Alert,
  ScrollView,
  ActivityIndicator
} from 'react-native';
import { Picker } from '@react-native-picker/picker';
import Icon from 'react-native-vector-icons/MaterialIcons';
import WidgetService, { WidgetConfig } from '../../services/widgets/WidgetService';

interface WidgetConfigurationProps {
  onConfigurationChange?: (configs: Record<string, WidgetConfig>) => void;
}

const WidgetConfiguration: React.FC<WidgetConfigurationProps> = ({
  onConfigurationChange
}) => {
  const [configurations, setConfigurations] = useState<Record<string, WidgetConfig>>({});
  const [loading, setLoading] = useState(true);
  const [updating, setUpdating] = useState<Record<string, boolean>>({});

  useEffect(() => {
    loadConfigurations();
  }, []);

  const loadConfigurations = async () => {
    try {
      setLoading(true);
      const configs = await WidgetService.getConfiguration();
      setConfigurations(configs);
    } catch (error) {
      console.error('Failed to load widget configurations:', error);
      Alert.alert('Error', 'Failed to load widget settings');
    } finally {
      setLoading(false);
    }
  };

  const updateConfiguration = async (type: string, updates: Partial<WidgetConfig>) => {
    try {
      setUpdating(prev => ({ ...prev, [type]: true }));
      
      const currentConfig = configurations[type];
      const newConfig = { ...currentConfig, ...updates };
      
      await WidgetService.configureWidget(type, newConfig);
      
      const updatedConfigs = { ...configurations, [type]: newConfig };
      setConfigurations(updatedConfigs);
      
      onConfigurationChange?.(updatedConfigs);
      
    } catch (error) {
      console.error('Failed to update widget configuration:', error);
      Alert.alert('Error', 'Failed to update widget settings');
    } finally {
      setUpdating(prev => ({ ...prev, [type]: false }));
    }
  };

  const toggleWidget = async (type: string, enabled: boolean) => {
    await updateConfiguration(type, { enabled });
  };

  const updateTheme = async (type: string, theme: 'light' | 'dark' | 'auto') => {
    const currentConfig = configurations[type];
    const customization = { ...currentConfig.customization, theme };
    await updateConfiguration(type, { customization });
  };

  const updateUpdateInterval = async (type: string, interval: number) => {
    await updateConfiguration(type, { updateInterval: interval });
  };

  const toggleBalanceVisibility = async (type: string, showBalance: boolean) => {
    const currentConfig = configurations[type];
    const customization = { ...currentConfig.customization, showBalance };
    await updateConfiguration(type, { customization });
  };

  const toggleTransactionVisibility = async (type: string, showRecentTransaction: boolean) => {
    const currentConfig = configurations[type];
    const customization = { ...currentConfig.customization, showRecentTransaction };
    await updateConfiguration(type, { customization });
  };

  const refreshWidgets = async () => {
    try {
      await WidgetService.refreshWidgets();
      Alert.alert('Success', 'Widgets refreshed successfully');
    } catch (error) {
      console.error('Failed to refresh widgets:', error);
      Alert.alert('Error', 'Failed to refresh widgets');
    }
  };

  const renderWidgetSection = (type: string, config: WidgetConfig, title: string, description: string) => {
    const isUpdating = updating[type];
    
    return (
      <View key={type} style={styles.widgetSection}>
        <View style={styles.sectionHeader}>
          <View style={styles.titleContainer}>
            <Text style={styles.sectionTitle}>{title}</Text>
            <Text style={styles.sectionDescription}>{description}</Text>
          </View>
          <View style={styles.switchContainer}>
            {isUpdating && <ActivityIndicator size="small" color="#007AFF" />}
            <Switch
              value={config.enabled}
              onValueChange={(enabled) => toggleWidget(type, enabled)}
              disabled={isUpdating}
              trackColor={{ false: '#767577', true: '#81b0ff' }}
              thumbColor={config.enabled ? '#007AFF' : '#f4f3f4'}
            />
          </View>
        </View>

        {config.enabled && (
          <View style={styles.configOptions}>
            {/* Theme Selection */}
            <View style={styles.optionRow}>
              <Text style={styles.optionLabel}>Theme</Text>
              <Picker
                selectedValue={config.customization?.theme || 'auto'}
                style={styles.picker}
                onValueChange={(theme) => updateTheme(type, theme as any)}
                enabled={!isUpdating}
              >
                <Picker.Item label="Auto" value="auto" />
                <Picker.Item label="Light" value="light" />
                <Picker.Item label="Dark" value="dark" />
              </Picker>
            </View>

            {/* Update Interval */}
            <View style={styles.optionRow}>
              <Text style={styles.optionLabel}>Update Interval</Text>
              <Picker
                selectedValue={config.updateInterval}
                style={styles.picker}
                onValueChange={(interval) => updateUpdateInterval(type, interval)}
                enabled={!isUpdating}
              >
                <Picker.Item label="5 minutes" value={5} />
                <Picker.Item label="15 minutes" value={15} />
                <Picker.Item label="30 minutes" value={30} />
                <Picker.Item label="1 hour" value={60} />
              </Picker>
            </View>

            {/* Balance Visibility (for balance widgets) */}
            {type === 'balance' && (
              <View style={styles.optionRow}>
                <Text style={styles.optionLabel}>Show Balance</Text>
                <Switch
                  value={config.customization?.showBalance ?? true}
                  onValueChange={(showBalance) => toggleBalanceVisibility(type, showBalance)}
                  disabled={isUpdating}
                  trackColor={{ false: '#767577', true: '#81b0ff' }}
                  thumbColor={config.customization?.showBalance ? '#007AFF' : '#f4f3f4'}
                />
              </View>
            )}

            {/* Recent Transaction Visibility */}
            {type === 'balance' && (
              <View style={styles.optionRow}>
                <Text style={styles.optionLabel}>Show Recent Transaction</Text>
                <Switch
                  value={config.customization?.showRecentTransaction ?? true}
                  onValueChange={(show) => toggleTransactionVisibility(type, show)}
                  disabled={isUpdating}
                  trackColor={{ false: '#767577', true: '#81b0ff' }}
                  thumbColor={config.customization?.showRecentTransaction ? '#007AFF' : '#f4f3f4'}
                />
              </View>
            )}

            {/* Widget Size */}
            <View style={styles.optionRow}>
              <Text style={styles.optionLabel}>Widget Size</Text>
              <Picker
                selectedValue={config.size}
                style={styles.picker}
                onValueChange={(size) => updateConfiguration(type, { size: size as any })}
                enabled={!isUpdating}
              >
                <Picker.Item label="Small" value="small" />
                <Picker.Item label="Medium" value="medium" />
                <Picker.Item label="Large" value="large" />
              </Picker>
            </View>
          </View>
        )}
      </View>
    );
  };

  if (loading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#007AFF" />
        <Text style={styles.loadingText}>Loading widget settings...</Text>
      </View>
    );
  }

  return (
    <ScrollView style={styles.container}>
      <View style={styles.header}>
        <Icon name="widgets" size={32} color="#007AFF" />
        <Text style={styles.headerTitle}>Widget Settings</Text>
        <Text style={styles.headerSubtitle}>
          Configure your home screen widgets to display Waqiti information
        </Text>
      </View>

      {Object.entries(configurations).map(([type, config]) => {
        const titles: Record<string, string> = {
          balance: 'Balance Widget',
          quick_actions: 'Quick Actions Widget',
          recent_transactions: 'Transactions Widget',
          crypto_prices: 'Crypto Widget'
        };

        const descriptions: Record<string, string> = {
          balance: 'Shows your current balance and recent transaction',
          quick_actions: 'Provides quick access to common actions',
          recent_transactions: 'Displays your recent transaction history',
          crypto_prices: 'Shows cryptocurrency prices and your portfolio'
        };

        return renderWidgetSection(
          type,
          config,
          titles[type] || type,
          descriptions[type] || 'Widget configuration'
        );
      })}

      <TouchableOpacity style={styles.refreshButton} onPress={refreshWidgets}>
        <Icon name="refresh" size={24} color="#FFFFFF" />
        <Text style={styles.refreshButtonText}>Refresh All Widgets</Text>
      </TouchableOpacity>

      <View style={styles.footer}>
        <Text style={styles.footerText}>
          Note: Widgets update automatically based on your configured interval. 
          You can also pull down on widgets to refresh manually.
        </Text>
      </View>
    </ScrollView>
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
    backgroundColor: '#F8F9FA',
  },
  loadingText: {
    marginTop: 16,
    fontSize: 16,
    color: '#666',
  },
  header: {
    padding: 20,
    backgroundColor: '#FFFFFF',
    borderBottomWidth: 1,
    borderBottomColor: '#E1E5E9',
    alignItems: 'center',
  },
  headerTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#1F2937',
    marginTop: 8,
  },
  headerSubtitle: {
    fontSize: 14,
    color: '#6B7280',
    textAlign: 'center',
    marginTop: 4,
    lineHeight: 20,
  },
  widgetSection: {
    backgroundColor: '#FFFFFF',
    margin: 16,
    borderRadius: 12,
    padding: 16,
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.1,
    shadowRadius: 3.84,
    elevation: 5,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  titleContainer: {
    flex: 1,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1F2937',
  },
  sectionDescription: {
    fontSize: 14,
    color: '#6B7280',
    marginTop: 2,
  },
  switchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  configOptions: {
    borderTopWidth: 1,
    borderTopColor: '#E5E7EB',
    paddingTop: 16,
  },
  optionRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
  },
  optionLabel: {
    fontSize: 16,
    color: '#374151',
    flex: 1,
  },
  picker: {
    width: 150,
    height: 50,
  },
  refreshButton: {
    backgroundColor: '#007AFF',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 16,
    margin: 16,
    borderRadius: 12,
    gap: 8,
  },
  refreshButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
  footer: {
    padding: 20,
    backgroundColor: '#FFFFFF',
    margin: 16,
    borderRadius: 12,
  },
  footerText: {
    fontSize: 14,
    color: '#6B7280',
    textAlign: 'center',
    lineHeight: 20,
  },
});

export default WidgetConfiguration;
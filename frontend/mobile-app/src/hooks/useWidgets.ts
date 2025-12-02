import { useState, useEffect, useCallback } from 'react';
import { AppState, AppStateStatus } from 'react-native';
import WidgetService, { WidgetData, WidgetConfig } from '../services/widgets/WidgetService';

interface UseWidgetsReturn {
  widgetData: WidgetData | null;
  configurations: Record<string, WidgetConfig>;
  isLoading: boolean;
  error: string | null;
  refreshWidgets: () => Promise<void>;
  updateConfiguration: (type: string, config: WidgetConfig) => Promise<void>;
  toggleWidget: (type: string, enabled: boolean) => Promise<void>;
}

/**
 * Custom hook for managing widget state and operations
 */
export const useWidgets = (): UseWidgetsReturn => {
  const [widgetData, setWidgetData] = useState<WidgetData | null>(null);
  const [configurations, setConfigurations] = useState<Record<string, WidgetConfig>>({});
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Load initial widget data and configurations
  const loadWidgetState = useCallback(async () => {
    try {
      setIsLoading(true);
      setError(null);

      const [data, configs] = await Promise.all([
        WidgetService.getWidgetData(),
        WidgetService.getConfiguration()
      ]);

      setWidgetData(data);
      setConfigurations(configs);
    } catch (err) {
      console.error('Failed to load widget state:', err);
      setError(err instanceof Error ? err.message : 'Failed to load widgets');
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Refresh widget data
  const refreshWidgets = useCallback(async () => {
    try {
      setError(null);
      await WidgetService.refreshWidgets();
      
      // Reload data after refresh
      const data = await WidgetService.getWidgetData();
      setWidgetData(data);
    } catch (err) {
      console.error('Failed to refresh widgets:', err);
      setError(err instanceof Error ? err.message : 'Failed to refresh widgets');
      throw err;
    }
  }, []);

  // Update widget configuration
  const updateConfiguration = useCallback(async (type: string, config: WidgetConfig) => {
    try {
      setError(null);
      await WidgetService.configureWidget(type, config);
      
      // Update local state
      setConfigurations(prev => ({
        ...prev,
        [type]: config
      }));
    } catch (err) {
      console.error('Failed to update widget configuration:', err);
      setError(err instanceof Error ? err.message : 'Failed to update configuration');
      throw err;
    }
  }, []);

  // Toggle widget enabled state
  const toggleWidget = useCallback(async (type: string, enabled: boolean) => {
    try {
      setError(null);
      await WidgetService.setWidgetEnabled(type, enabled);
      
      // Update local state
      setConfigurations(prev => ({
        ...prev,
        [type]: {
          ...prev[type],
          enabled
        }
      }));
    } catch (err) {
      console.error('Failed to toggle widget:', err);
      setError(err instanceof Error ? err.message : 'Failed to toggle widget');
      throw err;
    }
  }, []);

  // Handle app state changes
  useEffect(() => {
    const handleAppStateChange = (nextAppState: AppStateStatus) => {
      if (nextAppState === 'active') {
        // Refresh widgets when app becomes active
        refreshWidgets().catch(console.error);
      }
    };

    const subscription = AppState.addEventListener('change', handleAppStateChange);
    
    return () => {
      subscription?.remove();
    };
  }, [refreshWidgets]);

  // Load initial state
  useEffect(() => {
    loadWidgetState();
  }, [loadWidgetState]);

  return {
    widgetData,
    configurations,
    isLoading,
    error,
    refreshWidgets,
    updateConfiguration,
    toggleWidget
  };
};

/**
 * Hook for widget data that automatically refreshes
 */
export const useWidgetData = (refreshInterval?: number) => {
  const [data, setData] = useState<WidgetData | null>(null);
  const [lastUpdated, setLastUpdated] = useState<number>(0);

  const refreshData = useCallback(async () => {
    try {
      const widgetData = await WidgetService.getWidgetData();
      setData(widgetData);
      setLastUpdated(Date.now());
    } catch (error) {
      console.error('Failed to refresh widget data:', error);
    }
  }, []);

  useEffect(() => {
    // Initial load
    refreshData();

    // Set up periodic refresh if interval is provided
    if (refreshInterval && refreshInterval > 0) {
      const interval = setInterval(refreshData, refreshInterval * 1000);
      return () => clearInterval(interval);
    }
  }, [refreshData, refreshInterval]);

  return {
    data,
    lastUpdated,
    refresh: refreshData
  };
};

/**
 * Hook for widget configuration management
 */
export const useWidgetConfiguration = (widgetType: string) => {
  const [config, setConfig] = useState<WidgetConfig | null>(null);
  const [isUpdating, setIsUpdating] = useState(false);

  const loadConfiguration = useCallback(async () => {
    try {
      const configurations = await WidgetService.getConfiguration();
      setConfig(configurations[widgetType]);
    } catch (error) {
      console.error('Failed to load widget configuration:', error);
    }
  }, [widgetType]);

  const updateConfig = useCallback(async (updates: Partial<WidgetConfig>) => {
    if (!config) return;

    try {
      setIsUpdating(true);
      const newConfig = { ...config, ...updates };
      await WidgetService.configureWidget(widgetType, newConfig);
      setConfig(newConfig);
    } catch (error) {
      console.error('Failed to update widget configuration:', error);
      throw error;
    } finally {
      setIsUpdating(false);
    }
  }, [config, widgetType]);

  useEffect(() => {
    loadConfiguration();
  }, [loadConfiguration]);

  return {
    config,
    isUpdating,
    updateConfig,
    reload: loadConfiguration
  };
};

export default useWidgets;
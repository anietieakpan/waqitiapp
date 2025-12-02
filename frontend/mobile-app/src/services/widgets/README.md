# Waqiti Mobile App Widgets

Comprehensive home screen widget system for iOS and Android that provides users with quick access to balance information, recent transactions, and common actions directly from their device's home screen.

## Features

### 🏠 Home Screen Integration
- **iOS Widgets**: Native SwiftUI widgets for iOS 14+ with WidgetKit
- **Android Widgets**: Native App Widget providers for Android 3.0+
- **Multiple Sizes**: Small, medium, and large widget configurations
- **Dynamic Updates**: Real-time data synchronization with configurable intervals

### 💰 Widget Types

#### 1. Balance Widget
- **Current Balance**: Display total account balance
- **Recent Transaction**: Show latest transaction with amount and time
- **Quick Actions**: Direct access to send, request, scan QR (large widget)
- **Customizable**: Toggle balance visibility, transaction display

#### 2. Quick Actions Widget
- **Four Action Buttons**: Send money, request money, scan QR, view history
- **One-Tap Access**: Direct deep linking to app features
- **Compact Design**: Fits in small widget slots
- **Customizable Actions**: Configure which actions to display

#### 3. Recent Transactions Widget
- **Transaction History**: Latest transactions with descriptions and amounts
- **Balance Display**: Current balance at the top
- **Time Stamps**: Relative time indicators (2h, 1d, etc.)
- **Color Coding**: Visual distinction for sent/received/pending transactions

### 🎨 Customization Options
- **Themes**: Light, dark, and auto (system) themes
- **Update Intervals**: 5 minutes to 1 hour refresh rates
- **Privacy Controls**: Hide sensitive information
- **Layout Options**: Choose what information to display

## Architecture

### Core Components

```
┌─────────────────────────────────────────────────────────┐
│                    Widget System                        │
├─────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────┐ │
│  │ Widget Service  │  │ Configuration   │  │ Preview  │ │
│  │ (Core Logic)    │  │ Management      │  │ System   │ │
│  └─────────────────┘  └─────────────────┘  └──────────┘ │
├─────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────┐ │
│  │ iOS Native      │  │ Android Native  │  │ React    │ │
│  │ (SwiftUI)       │  │ (AppWidget)     │  │ Native   │ │
│  └─────────────────┘  └─────────────────┘  └──────────┘ │
└─────────────────────────────────────────────────────────┘
```

### Data Flow

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│              │    │              │    │              │
│  React Native│───▶│ Widget Service│───▶│ Native Module│
│     App      │    │              │    │              │
└──────────────┘    └──────────────┘    └──────────────┘
                           │                     │
                           ▼                     ▼
                    ┌──────────────┐    ┌──────────────┐
                    │              │    │              │
                    │ Shared Storage│    │ Widget Provider│
                    │              │    │              │
                    └──────────────┘    └──────────────┘
                           │                     │
                           └─────────────────────┘
                                     │
                                     ▼
                            ┌──────────────┐
                            │              │
                            │ Home Screen  │
                            │   Widget     │
                            └──────────────┘
```

## Quick Start

### Basic Setup

```typescript
import WidgetService from '@/services/widgets';

// Initialize widget service
useEffect(() => {
  const initializeWidgets = async () => {
    try {
      await WidgetService.initialize();
      console.log('Widgets initialized successfully');
    } catch (error) {
      console.error('Failed to initialize widgets:', error);
    }
  };
  
  initializeWidgets();
}, []);
```

### Using Widget Hooks

```typescript
import { useWidgets } from '@/services/widgets';

const MyComponent = () => {
  const {
    widgetData,
    configurations,
    isLoading,
    error,
    refreshWidgets,
    updateConfiguration,
    toggleWidget
  } = useWidgets();

  const handleRefresh = async () => {
    try {
      await refreshWidgets();
      console.log('Widgets refreshed');
    } catch (error) {
      console.error('Refresh failed:', error);
    }
  };

  if (isLoading) return <LoadingSpinner />;
  if (error) return <ErrorMessage message={error} />;

  return (
    <View>
      <Text>Balance: {widgetData?.balance}</Text>
      <Button title="Refresh" onPress={handleRefresh} />
    </View>
  );
};
```

### Configuration Management

```typescript
import { WidgetConfiguration } from '@/services/widgets';

const SettingsScreen = () => {
  const handleConfigChange = (configs) => {
    console.log('Widget configurations updated:', configs);
  };

  return (
    <WidgetConfiguration
      onConfigurationChange={handleConfigChange}
    />
  );
};
```

## Widget Configuration

### Default Configuration

```typescript
const defaultConfig = {
  balance: {
    type: 'balance',
    size: 'medium',
    updateInterval: 15, // minutes
    enabled: true,
    customization: {
      showBalance: true,
      showRecentTransaction: true,
      quickActionIds: ['send_money', 'request_money', 'scan_qr'],
      theme: 'auto'
    }
  },
  quick_actions: {
    type: 'quick_actions',
    size: 'small',
    updateInterval: 60, // minutes
    enabled: true,
    customization: {
      quickActionIds: ['send_money', 'request_money', 'scan_qr', 'pay_merchant'],
      theme: 'auto'
    }
  }
};
```

### Updating Configuration

```typescript
// Configure specific widget
await WidgetService.configureWidget('balance', {
  type: 'balance',
  size: 'large',
  updateInterval: 30,
  enabled: true,
  customization: {
    showBalance: false, // Hide balance for privacy
    showRecentTransaction: true,
    theme: 'dark'
  }
});

// Toggle widget on/off
await WidgetService.setWidgetEnabled('balance', false);
```

## Platform-Specific Implementation

### iOS (SwiftUI + WidgetKit)

#### Widget Timeline Provider
```swift
struct WaqitiWidgetProvider: TimelineProvider {
    func getTimeline(in context: Context, completion: @escaping (Timeline<WaqitiWidgetEntry>) -> Void) {
        let currentData = loadWidgetData() ?? WaqitiWidgetData.placeholder
        let entry = WaqitiWidgetEntry(date: Date(), data: currentData)
        
        // Update every 15 minutes
        let nextUpdate = Calendar.current.date(byAdding: .minute, value: 15, to: Date()) ?? Date()
        let timeline = Timeline(entries: [entry], policy: .after(nextUpdate))
        
        completion(timeline)
    }
}
```

#### Widget Views
```swift
struct WaqitiBalanceWidgetView: View {
    let entry: WaqitiWidgetEntry
    
    var body: some View {
        ZStack {
            LinearGradient(/* gradient configuration */)
            
            VStack {
                // Header with app icon and title
                // Balance display
                // Recent transaction (if enabled)
                // Quick actions (large widget only)
            }
        }
        .widgetURL(URL(string: "waqiti://home"))
    }
}
```

### Android (App Widget Provider)

#### Widget Provider
```java
public class WaqitiWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }
    
    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        WidgetData widgetData = loadWidgetData(context);
        RemoteViews views = createWidgetViews(context, widgetData);
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
```

#### Widget Layouts
```xml
<!-- Balance Widget Layout -->
<LinearLayout android:orientation="vertical">
    <!-- Header with app icon -->
    <!-- Balance display -->
    <!-- Recent transaction -->
    <!-- Quick actions grid -->
</LinearLayout>
```

## Data Management

### Widget Data Structure

```typescript
interface WidgetData {
  balance: string;
  recentTransaction?: {
    description: string;
    amount: string;
    type: 'sent' | 'received' | 'pending';
    timestamp: number;
  };
  quickActions: Array<{
    id: string;
    title: string;
    icon: string;
    deeplink: string;
  }>;
  lastUpdated: number;
}
```

### Data Fetching

```typescript
private async fetchWidgetData(): Promise<WidgetData> {
  const [balanceData, transactionData, actionsData] = await Promise.all([
    this.fetchBalanceData(),
    this.fetchRecentTransaction(),
    this.fetchQuickActions()
  ]);

  return {
    balance: balanceData.balance,
    recentTransaction: transactionData,
    quickActions: actionsData,
    lastUpdated: Date.now()
  };
}
```

### Caching Strategy

- **Local Storage**: Widget data cached in AsyncStorage
- **Shared Preferences**: iOS uses shared UserDefaults, Android uses SharedPreferences
- **Update Intervals**: Configurable refresh rates (5min - 1hour)
- **Fallback Data**: Placeholder content when offline or unauthenticated

## Deep Link Integration

### Widget Tap Handling

```typescript
// Main widget tap - opens app home
await WidgetService.handleWidgetTap('balance');

// Quick action tap - specific feature
await WidgetService.handleWidgetTap('quick_action', 'send_money');
```

### Deep Link Routing

```typescript
// Widget actions route through the deep link system
const deeplinks: Record<string, string> = {
  send_money: 'waqiti://send',
  request_money: 'waqiti://request',
  scan_qr: 'waqiti://scan',
  pay_merchant: 'waqiti://pay',
  recent_transactions: 'waqiti://transactions'
};
```

## Analytics & Monitoring

### Tracked Events

```typescript
// Widget usage analytics
await AnalyticsService.track('widget_tapped', {
  widget_type: 'balance',
  action_id: 'send_money',
  source: 'home_screen',
  timestamp: Date.now()
});

// Widget performance metrics
await AnalyticsService.track('widget_updated', {
  data_size: jsonString.length,
  update_duration_ms: updateTime,
  success: true
});

// Error tracking
await AnalyticsService.track('widget_error', {
  error_code: 'UPDATE_FAILED',
  error_message: error.message,
  widget_type: 'balance'
});
```

### Performance Monitoring

- **Update Latency**: Time to fetch and display new data
- **Success Rates**: Percentage of successful widget updates
- **User Engagement**: Widget tap rates and action usage
- **Error Rates**: Frequency and types of widget errors

## Error Handling

### Error Types

```typescript
interface WidgetError {
  code: string;
  message: string;
  timestamp: number;
}

// Common error codes
const ERROR_CODES = {
  INITIALIZATION_FAILED: 'Failed to initialize widget service',
  UPDATE_FAILED: 'Failed to update widget data',
  CONFIGURATION_FAILED: 'Failed to save widget configuration',
  TAP_HANDLING_FAILED: 'Failed to handle widget interaction',
  AUTHENTICATION_REQUIRED: 'User authentication required'
};
```

### Error Recovery

```typescript
// Graceful degradation
private async updateWidgetsWithCachedData(): Promise<void> {
  try {
    const cachedData = await this.getWidgetData();
    if (cachedData && this.widgetNativeModule) {
      await this.widgetNativeModule.updateWidgets(cachedData);
    }
  } catch (error) {
    console.error('Failed to update widgets with cached data:', error);
  }
}

// Error logging and tracking
private async logError(code: string, message: string): Promise<void> {
  const error: WidgetError = { code, message, timestamp: Date.now() };
  const errors = await this.getErrors();
  errors.push(error);
  
  // Keep only last 50 errors
  const recentErrors = errors.slice(-50);
  await AsyncStorage.setItem(this.WIDGET_ERROR_KEY, JSON.stringify(recentErrors));
}
```

## Testing

### Unit Tests

```typescript
describe('WidgetService', () => {
  test('should initialize successfully', async () => {
    await expect(WidgetService.initialize()).resolves.not.toThrow();
  });

  test('should update widget data', async () => {
    mockApiResponses();
    await WidgetService.updateAllWidgets();
    expect(AsyncStorage.setItem).toHaveBeenCalledWith(
      '@widget_data',
      expect.stringContaining('$1,234.56')
    );
  });

  test('should handle configuration changes', async () => {
    const config = { type: 'balance', enabled: false };
    await WidgetService.configureWidget('balance', config);
    expect(AnalyticsService.track).toHaveBeenCalledWith('widget_configured');
  });
});
```

### Integration Tests

```typescript
describe('Widget Integration', () => {
  test('should update widgets when app becomes active', async () => {
    const { result } = renderHook(() => useWidgets());
    
    // Simulate app state change
    act(() => {
      AppState.currentState = 'active';
    });
    
    await waitFor(() => {
      expect(result.current.widgetData).toBeTruthy();
    });
  });
});
```

## Security Considerations

### Data Privacy
- **Balance Hiding**: Option to hide sensitive financial information
- **Authentication**: Widget data only available when user is authenticated
- **Encryption**: Sensitive data encrypted in shared storage
- **Permissions**: Widget configuration requires app authentication

### Deep Link Security
- **URL Validation**: All deep links validated before processing
- **Parameter Sanitization**: Input validation on all widget interactions
- **Rate Limiting**: Prevent excessive widget tap events
- **Audit Logging**: Track all widget-initiated actions

## Performance Optimization

### Data Fetching
```typescript
// Parallel API calls for faster data loading
const [balanceData, transactionData, actionsData] = await Promise.all([
  this.fetchBalanceData(),
  this.fetchRecentTransaction(),
  this.fetchQuickActions()
]);
```

### Memory Management
```typescript
// Cleanup resources on app termination
async cleanup(): Promise<void> {
  if (this.updateTimer) {
    clearInterval(this.updateTimer);
    this.updateTimer = null;
  }
  
  if (this.eventEmitter) {
    this.eventEmitter.removeAllListeners();
  }
}
```

### Update Optimization
- **Delta Updates**: Only update changed data
- **Batched Operations**: Combine multiple widget updates
- **Background Processing**: Use background tasks for data fetching
- **Intelligent Scheduling**: Adjust update frequency based on usage

## Troubleshooting

### Common Issues

**Widgets not updating**
```typescript
// Check authentication status
const isAuth = await AuthService.isAuthenticated();
if (!isAuth) {
  console.log('User not authenticated - widgets will show placeholder');
}

// Verify native module availability
if (!this.widgetNativeModule) {
  console.error('Widget native module not available');
}

// Check update intervals
const config = await WidgetService.getConfiguration();
console.log('Update interval:', config.balance.updateInterval);
```

**Configuration not saving**
```typescript
// Verify AsyncStorage permissions
try {
  await AsyncStorage.setItem('test', 'value');
  await AsyncStorage.removeItem('test');
  console.log('AsyncStorage working');
} catch (error) {
  console.error('AsyncStorage permission denied:', error);
}
```

**Deep links not working**
```typescript
// Test deep link routing
const testResult = DeepLinkManager.testURL('waqiti://send');
console.log('Deep link test:', testResult);
```

### Debug Mode

```typescript
// Enable debug logging
if (__DEV__) {
  console.log('Widget data:', widgetData);
  console.log('Widget configuration:', configuration);
  console.log('Native module available:', !!this.widgetNativeModule);
}
```

## Future Enhancements

### Planned Features
- **Interactive Widgets**: iOS 17+ interactive elements
- **Smart Stacks**: Intelligent widget rotation based on usage
- **Complications**: Apple Watch complication support
- **Live Activities**: iOS Live Activities for ongoing transactions
- **Dynamic Island**: iPhone 14 Pro Dynamic Island integration

### Advanced Configurations
- **Conditional Display**: Show different content based on time/location
- **Multi-Account Support**: Switch between different user accounts
- **Custom Themes**: User-created color schemes and layouts
- **Widget Shortcuts**: Siri Shortcuts integration for voice control

## Contributing

### Adding New Widget Types

1. **Define Widget Type**
```typescript
interface CryptoWidgetConfig extends WidgetConfig {
  type: 'crypto_prices';
  customization: {
    cryptoIds: string[];
    showPortfolio: boolean;
    priceChangeTimeframe: '24h' | '7d' | '30d';
  };
}
```

2. **Implement Data Fetching**
```typescript
private async fetchCryptoData(): Promise<CryptoWidgetData> {
  const response = await ApiService.get('/api/crypto/portfolio');
  return {
    prices: response.data.prices,
    portfolioValue: response.data.totalValue,
    change24h: response.data.change24h
  };
}
```

3. **Create Native Views**
- Add SwiftUI view for iOS
- Create Android layout XML
- Implement widget provider logic

4. **Add Configuration UI**
```typescript
const CryptoWidgetConfiguration = () => {
  // Configuration form for crypto-specific settings
};
```

5. **Write Tests**
```typescript
describe('CryptoWidget', () => {
  test('should fetch crypto prices', async () => {
    // Test implementation
  });
});
```

## License

This widget system is part of the Waqiti mobile application and follows the same licensing terms.
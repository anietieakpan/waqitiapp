package com.waqiti.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;

import com.waqiti.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Waqiti Widget Provider for Android Home Screen Widgets
 * Supports balance display, recent transactions, and quick actions
 */
public class WaqitiWidgetProvider extends AppWidgetProvider {
    
    private static final String TAG = "WaqitiWidgetProvider";
    private static final String WIDGET_DATA_PREFS = "widget_data_prefs";
    private static final String WIDGET_CONFIG_PREFS = "widget_config_prefs";
    
    // Widget actions
    private static final String ACTION_WIDGET_TAP = "com.waqiti.WIDGET_TAP";
    private static final String ACTION_QUICK_ACTION = "com.waqiti.QUICK_ACTION";
    private static final String ACTION_REFRESH = "com.waqiti.REFRESH_WIDGET";
    
    // Intent extras
    private static final String EXTRA_WIDGET_TYPE = "widget_type";
    private static final String EXTRA_ACTION_ID = "action_id";
    private static final String EXTRA_DEEPLINK = "deeplink";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);
        
        Log.d(TAG, "Updating widgets: " + appWidgetIds.length);
        
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
                                          int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        updateWidget(context, appWidgetManager, appWidgetId);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        
        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);
        
        if (ACTION_WIDGET_TAP.equals(action)) {
            handleWidgetTap(context, intent);
        } else if (ACTION_QUICK_ACTION.equals(action)) {
            handleQuickAction(context, intent);
        } else if (ACTION_REFRESH.equals(action)) {
            handleRefresh(context);
        }
    }

    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        try {
            WidgetData widgetData = loadWidgetData(context);
            WidgetConfig config = loadWidgetConfig(context, appWidgetId);
            
            RemoteViews views = createWidgetViews(context, widgetData, config, appWidgetId);
            setupClickListeners(context, views, appWidgetId);
            
            appWidgetManager.updateAppWidget(appWidgetId, views);
            
            Log.d(TAG, "Widget updated successfully: " + appWidgetId);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to update widget: " + appWidgetId, e);
            
            // Show error widget
            RemoteViews errorViews = new RemoteViews(context.getPackageName(), R.layout.widget_error);
            errorViews.setTextViewText(R.id.error_message, "Unable to load data");
            appWidgetManager.updateAppWidget(appWidgetId, errorViews);
        }
    }

    private RemoteViews createWidgetViews(Context context, WidgetData data, WidgetConfig config, int appWidgetId) {
        String widgetType = config.getType();
        
        switch (widgetType) {
            case "balance":
                return createBalanceWidget(context, data, config);
            case "quick_actions":
                return createQuickActionsWidget(context, data, config);
            case "recent_transactions":
                return createTransactionsWidget(context, data, config);
            default:
                return createBalanceWidget(context, data, config);
        }
    }

    private RemoteViews createBalanceWidget(Context context, WidgetData data, WidgetConfig config) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_balance);
        
        // Set balance
        views.setTextViewText(R.id.balance_amount, data.getBalance());
        
        // Set last updated time
        String lastUpdated = formatLastUpdated(data.getLastUpdated());
        views.setTextViewText(R.id.last_updated, lastUpdated);
        
        // Set recent transaction if available
        WidgetData.RecentTransaction transaction = data.getRecentTransaction();
        if (transaction != null && config.shouldShowRecentTransaction()) {
            views.setViewVisibility(R.id.recent_transaction_container, RemoteViews.VISIBLE);
            views.setTextViewText(R.id.transaction_description, transaction.getDescription());
            views.setTextViewText(R.id.transaction_amount, transaction.getAmount());
            
            // Set amount color based on transaction type
            int amountColor = getTransactionColor(transaction.getType());
            views.setTextColor(R.id.transaction_amount, amountColor);
        } else {
            views.setViewVisibility(R.id.recent_transaction_container, RemoteViews.GONE);
        }
        
        // Apply theme
        applyTheme(views, config.getTheme());
        
        return views;
    }

    private RemoteViews createQuickActionsWidget(Context context, WidgetData data, WidgetConfig config) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_quick_actions);
        
        // Clear existing action views
        views.removeAllViews(R.id.actions_container);
        
        // Add quick action buttons
        for (int i = 0; i < Math.min(data.getQuickActions().size(), 4); i++) {
            WidgetData.QuickAction action = data.getQuickActions().get(i);
            
            RemoteViews actionView = new RemoteViews(context.getPackageName(), R.layout.widget_action_button);
            actionView.setTextViewText(R.id.action_title, action.getTitle());
            actionView.setImageViewResource(R.id.action_icon, getActionIconResource(action.getIcon()));
            
            // Set click intent
            Intent actionIntent = new Intent(context, WaqitiWidgetProvider.class);
            actionIntent.setAction(ACTION_QUICK_ACTION);
            actionIntent.putExtra(EXTRA_ACTION_ID, action.getId());
            actionIntent.putExtra(EXTRA_DEEPLINK, action.getDeeplink());
            actionIntent.setData(Uri.parse("action://" + action.getId() + "/" + System.currentTimeMillis()));
            
            PendingIntent actionPendingIntent = PendingIntent.getBroadcast(
                context, 0, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            actionView.setOnClickPendingIntent(R.id.action_button, actionPendingIntent);
            
            views.addView(R.id.actions_container, actionView);
        }
        
        applyTheme(views, config.getTheme());
        
        return views;
    }

    private RemoteViews createTransactionsWidget(Context context, WidgetData data, WidgetConfig config) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_transactions);
        
        // Show balance at top
        views.setTextViewText(R.id.balance_amount, data.getBalance());
        
        // Show recent transaction
        WidgetData.RecentTransaction transaction = data.getRecentTransaction();
        if (transaction != null) {
            views.setTextViewText(R.id.transaction_description, transaction.getDescription());
            views.setTextViewText(R.id.transaction_amount, transaction.getAmount());
            views.setTextViewText(R.id.transaction_time, formatTransactionTime(transaction.getTimestamp()));
            
            int amountColor = getTransactionColor(transaction.getType());
            views.setTextColor(R.id.transaction_amount, amountColor);
        }
        
        applyTheme(views, config.getTheme());
        
        return views;
    }

    private void setupClickListeners(Context context, RemoteViews views, int appWidgetId) {
        // Main widget tap
        Intent mainIntent = new Intent(context, WaqitiWidgetProvider.class);
        mainIntent.setAction(ACTION_WIDGET_TAP);
        mainIntent.putExtra(EXTRA_WIDGET_TYPE, "main");
        mainIntent.setData(Uri.parse("widget://" + appWidgetId + "/" + System.currentTimeMillis()));
        
        PendingIntent mainPendingIntent = PendingIntent.getBroadcast(
            context, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_container, mainPendingIntent);
        
        // Refresh button
        Intent refreshIntent = new Intent(context, WaqitiWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH);
        refreshIntent.setData(Uri.parse("refresh://" + appWidgetId + "/" + System.currentTimeMillis()));
        
        PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
            context, 0, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.refresh_button, refreshPendingIntent);
    }

    private void handleWidgetTap(Context context, Intent intent) {
        String widgetType = intent.getStringExtra(EXTRA_WIDGET_TYPE);
        Log.d(TAG, "Widget tapped: " + widgetType);
        
        // Open main app
        Intent appIntent = new Intent();
        appIntent.setAction(Intent.ACTION_VIEW);
        appIntent.setData(Uri.parse("waqiti://home"));
        appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        try {
            context.startActivity(appIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open app from widget tap", e);
        }
        
        // Notify React Native module
        WaqitiWidgetModule.notifyWidgetTap(widgetType, null);
    }

    private void handleQuickAction(Context context, Intent intent) {
        String actionId = intent.getStringExtra(EXTRA_ACTION_ID);
        String deeplink = intent.getStringExtra(EXTRA_DEEPLINK);
        
        Log.d(TAG, "Quick action tapped: " + actionId + ", deeplink: " + deeplink);
        
        if (deeplink != null) {
            Intent appIntent = new Intent();
            appIntent.setAction(Intent.ACTION_VIEW);
            appIntent.setData(Uri.parse(deeplink));
            appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            try {
                context.startActivity(appIntent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to open deeplink from widget: " + deeplink, e);
            }
        }
        
        // Notify React Native module
        WaqitiWidgetModule.notifyWidgetTap("quick_action", actionId);
    }

    private void handleRefresh(Context context) {
        Log.d(TAG, "Widget refresh requested");
        
        // Notify React Native to refresh data
        WaqitiWidgetModule.notifyDataRequested();
        
        // Update all widgets
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
            new android.content.ComponentName(context, WaqitiWidgetProvider.class));
        
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private WidgetData loadWidgetData(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(WIDGET_DATA_PREFS, Context.MODE_PRIVATE);
        String dataJson = prefs.getString("widget_data", null);
        
        if (dataJson != null) {
            try {
                return WidgetData.fromJson(dataJson);
            } catch (JSONException e) {
                Log.e(TAG, "Failed to parse widget data", e);
            }
        }
        
        // Return placeholder data
        return WidgetData.createPlaceholder();
    }

    private WidgetConfig loadWidgetConfig(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(WIDGET_CONFIG_PREFS, Context.MODE_PRIVATE);
        String configJson = prefs.getString("config_" + appWidgetId, null);
        
        if (configJson != null) {
            try {
                return WidgetConfig.fromJson(configJson);
            } catch (JSONException e) {
                Log.e(TAG, "Failed to parse widget config", e);
            }
        }
        
        // Return default config
        return WidgetConfig.createDefault();
    }

    private void applyTheme(RemoteViews views, String theme) {
        if ("dark".equals(theme)) {
            views.setInt(R.id.widget_container, "setBackgroundColor", Color.parseColor("#1A1A1A"));
            views.setTextColor(R.id.balance_amount, Color.WHITE);
        } else if ("light".equals(theme)) {
            views.setInt(R.id.widget_container, "setBackgroundColor", Color.parseColor("#F5F5F5"));
            views.setTextColor(R.id.balance_amount, Color.BLACK);
        }
        // "auto" theme uses system default
    }

    private int getTransactionColor(String type) {
        switch (type) {
            case "sent":
                return Color.parseColor("#FF5252");
            case "received":
                return Color.parseColor("#4CAF50");
            case "pending":
                return Color.parseColor("#FF9800");
            default:
                return Color.parseColor("#757575");
        }
    }

    private int getActionIconResource(String iconName) {
        switch (iconName) {
            case "arrow-up-circle":
                return R.drawable.ic_arrow_up_circle;
            case "arrow-down-circle":
                return R.drawable.ic_arrow_down_circle;
            case "qr-code-scanner":
                return R.drawable.ic_qr_code;
            case "credit-card":
                return R.drawable.ic_credit_card;
            case "history":
                return R.drawable.ic_history;
            case "bitcoin":
                return R.drawable.ic_bitcoin;
            case "group":
                return R.drawable.ic_group;
            case "add-circle":
                return R.drawable.ic_add_circle;
            default:
                return R.drawable.ic_help_circle;
        }
    }

    private String formatLastUpdated(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        
        if (diff < 60000) { // Less than 1 minute
            return "now";
        } else if (diff < 3600000) { // Less than 1 hour
            return (diff / 60000) + "m ago";
        } else if (diff < 86400000) { // Less than 1 day
            return (diff / 3600000) + "h ago";
        } else {
            return (diff / 86400000) + "d ago";
        }
    }

    private String formatTransactionTime(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        
        if (diff < 3600000) { // Less than 1 hour
            return (diff / 60000) + "m";
        } else if (diff < 86400000) { // Less than 1 day
            return (diff / 3600000) + "h";
        } else {
            return (diff / 86400000) + "d";
        }
    }

    // Static methods for external access
    public static void updateAllWidgets(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
            new android.content.ComponentName(context, WaqitiWidgetProvider.class));
        
        Intent updateIntent = new Intent(context, WaqitiWidgetProvider.class);
        updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        context.sendBroadcast(updateIntent);
    }

    public static void saveWidgetData(Context context, String dataJson) {
        SharedPreferences prefs = context.getSharedPreferences(WIDGET_DATA_PREFS, Context.MODE_PRIVATE);
        prefs.edit()
            .putString("widget_data", dataJson)
            .putLong("last_updated", System.currentTimeMillis())
            .apply();
        
        updateAllWidgets(context);
    }

    public static void saveWidgetConfig(Context context, int appWidgetId, String configJson) {
        SharedPreferences prefs = context.getSharedPreferences(WIDGET_CONFIG_PREFS, Context.MODE_PRIVATE);
        prefs.edit()
            .putString("config_" + appWidgetId, configJson)
            .apply();
    }
}
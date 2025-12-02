package com.waqiti.widgets;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Configuration model for Waqiti widgets
 */
public class WidgetConfig {
    private String type;
    private String size;
    private int updateInterval;
    private boolean enabled;
    private String theme;
    private boolean showBalance;
    private boolean showRecentTransaction;

    public WidgetConfig(String type, String size, int updateInterval, boolean enabled, 
                       String theme, boolean showBalance, boolean showRecentTransaction) {
        this.type = type;
        this.size = size;
        this.updateInterval = updateInterval;
        this.enabled = enabled;
        this.theme = theme;
        this.showBalance = showBalance;
        this.showRecentTransaction = showRecentTransaction;
    }

    // Getters
    public String getType() { return type; }
    public String getSize() { return size; }
    public int getUpdateInterval() { return updateInterval; }
    public boolean isEnabled() { return enabled; }
    public String getTheme() { return theme; }
    public boolean shouldShowBalance() { return showBalance; }
    public boolean shouldShowRecentTransaction() { return showRecentTransaction; }

    // Setters
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setTheme(String theme) { this.theme = theme; }
    public void setShowBalance(boolean showBalance) { this.showBalance = showBalance; }
    public void setShowRecentTransaction(boolean showRecentTransaction) { 
        this.showRecentTransaction = showRecentTransaction; 
    }

    // JSON serialization
    public static WidgetConfig fromJson(String json) throws JSONException {
        JSONObject obj = new JSONObject(json);
        
        String type = obj.optString("type", "balance");
        String size = obj.optString("size", "medium");
        int updateInterval = obj.optInt("updateInterval", 15);
        boolean enabled = obj.optBoolean("enabled", true);
        
        JSONObject customization = obj.optJSONObject("customization");
        String theme = "auto";
        boolean showBalance = true;
        boolean showRecentTransaction = true;
        
        if (customization != null) {
            theme = customization.optString("theme", "auto");
            showBalance = customization.optBoolean("showBalance", true);
            showRecentTransaction = customization.optBoolean("showRecentTransaction", true);
        }
        
        return new WidgetConfig(type, size, updateInterval, enabled, theme, showBalance, showRecentTransaction);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("type", type);
        obj.put("size", size);
        obj.put("updateInterval", updateInterval);
        obj.put("enabled", enabled);
        
        JSONObject customization = new JSONObject();
        customization.put("theme", theme);
        customization.put("showBalance", showBalance);
        customization.put("showRecentTransaction", showRecentTransaction);
        obj.put("customization", customization);
        
        return obj;
    }

    // Create default configuration
    public static WidgetConfig createDefault() {
        return new WidgetConfig("balance", "medium", 15, true, "auto", true, true);
    }
}
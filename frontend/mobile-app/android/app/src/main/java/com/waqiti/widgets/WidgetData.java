package com.waqiti.widgets;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Data model for Waqiti widget content
 */
public class WidgetData {
    private String balance;
    private RecentTransaction recentTransaction;
    private List<QuickAction> quickActions;
    private long lastUpdated;

    public WidgetData(String balance, RecentTransaction recentTransaction, 
                     List<QuickAction> quickActions, long lastUpdated) {
        this.balance = balance;
        this.recentTransaction = recentTransaction;
        this.quickActions = quickActions;
        this.lastUpdated = lastUpdated;
    }

    // Getters
    public String getBalance() { return balance; }
    public RecentTransaction getRecentTransaction() { return recentTransaction; }
    public List<QuickAction> getQuickActions() { return quickActions; }
    public long getLastUpdated() { return lastUpdated; }

    // JSON serialization
    public static WidgetData fromJson(String json) throws JSONException {
        JSONObject obj = new JSONObject(json);
        
        String balance = obj.getString("balance");
        long lastUpdated = obj.getLong("lastUpdated");
        
        RecentTransaction recentTransaction = null;
        if (obj.has("recentTransaction") && !obj.isNull("recentTransaction")) {
            JSONObject transactionObj = obj.getJSONObject("recentTransaction");
            recentTransaction = RecentTransaction.fromJson(transactionObj);
        }
        
        List<QuickAction> quickActions = new ArrayList<>();
        if (obj.has("quickActions")) {
            JSONArray actionsArray = obj.getJSONArray("quickActions");
            for (int i = 0; i < actionsArray.length(); i++) {
                JSONObject actionObj = actionsArray.getJSONObject(i);
                quickActions.add(QuickAction.fromJson(actionObj));
            }
        }
        
        return new WidgetData(balance, recentTransaction, quickActions, lastUpdated);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("balance", balance);
        obj.put("lastUpdated", lastUpdated);
        
        if (recentTransaction != null) {
            obj.put("recentTransaction", recentTransaction.toJson());
        }
        
        if (quickActions != null && !quickActions.isEmpty()) {
            JSONArray actionsArray = new JSONArray();
            for (QuickAction action : quickActions) {
                actionsArray.put(action.toJson());
            }
            obj.put("quickActions", actionsArray);
        }
        
        return obj;
    }

    // Create placeholder data
    public static WidgetData createPlaceholder() {
        List<QuickAction> placeholderActions = new ArrayList<>();
        placeholderActions.add(new QuickAction("send", "Send", "arrow-up-circle", "waqiti://send"));
        placeholderActions.add(new QuickAction("request", "Request", "arrow-down-circle", "waqiti://request"));
        placeholderActions.add(new QuickAction("scan", "Scan", "qr-code-scanner", "waqiti://scan"));
        placeholderActions.add(new QuickAction("history", "History", "history", "waqiti://transactions"));
        
        RecentTransaction placeholderTransaction = new RecentTransaction(
            "Placeholder Transaction", "-$0.00", "sent", System.currentTimeMillis() - 3600000);
        
        return new WidgetData("$0.00", placeholderTransaction, placeholderActions, System.currentTimeMillis());
    }

    // Inner classes
    public static class RecentTransaction {
        private String description;
        private String amount;
        private String type;
        private long timestamp;

        public RecentTransaction(String description, String amount, String type, long timestamp) {
            this.description = description;
            this.amount = amount;
            this.type = type;
            this.timestamp = timestamp;
        }

        // Getters
        public String getDescription() { return description; }
        public String getAmount() { return amount; }
        public String getType() { return type; }
        public long getTimestamp() { return timestamp; }

        public static RecentTransaction fromJson(JSONObject obj) throws JSONException {
            return new RecentTransaction(
                obj.getString("description"),
                obj.getString("amount"),
                obj.getString("type"),
                obj.getLong("timestamp")
            );
        }

        public JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("description", description);
            obj.put("amount", amount);
            obj.put("type", type);
            obj.put("timestamp", timestamp);
            return obj;
        }
    }

    public static class QuickAction {
        private String id;
        private String title;
        private String icon;
        private String deeplink;

        public QuickAction(String id, String title, String icon, String deeplink) {
            this.id = id;
            this.title = title;
            this.icon = icon;
            this.deeplink = deeplink;
        }

        // Getters
        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getIcon() { return icon; }
        public String getDeeplink() { return deeplink; }

        public static QuickAction fromJson(JSONObject obj) throws JSONException {
            return new QuickAction(
                obj.getString("id"),
                obj.getString("title"),
                obj.getString("icon"),
                obj.getString("deeplink")
            );
        }

        public JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("title", title);
            obj.put("icon", icon);
            obj.put("deeplink", deeplink);
            return obj;
        }
    }
}
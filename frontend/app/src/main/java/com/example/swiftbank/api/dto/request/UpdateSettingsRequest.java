package com.example.swiftbank.api.dto.request;

import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.Map;

public class UpdateSettingsRequest {

    @SerializedName("settings")
    private Map<String, Object> settings;

    public UpdateSettingsRequest() {
        this.settings = new HashMap<>();
    }

    public UpdateSettingsRequest setSecurityBiometric(boolean enabled) {
        Map<String, Object> security = getOrCreateMap("security");
        security.put("biometric_enabled", enabled);
        return this;
    }

    public UpdateSettingsRequest setNotificationsPush(boolean enabled) {
        Map<String, Object> notifications = getOrCreateMap("notifications");
        notifications.put("push_enabled", enabled);
        return this;
    }

    public UpdateSettingsRequest setNotificationsEmail(boolean enabled) {
        Map<String, Object> notifications = getOrCreateMap("notifications");
        notifications.put("email_enabled", enabled);
        return this;
    }

    public UpdateSettingsRequest setNotificationsTransactionAlerts(boolean enabled) {
        Map<String, Object> notifications = getOrCreateMap("notifications");
        notifications.put("transaction_alerts", enabled);
        return this;
    }

    public UpdateSettingsRequest setDisplayHideBalance(boolean hidden) {
        Map<String, Object> display = getOrCreateMap("display");
        display.put("hide_balance", hidden);
        return this;
    }

    public UpdateSettingsRequest setDisplayLanguage(String language) {
        Map<String, Object> display = getOrCreateMap("display");
        display.put("language", language);
        return this;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrCreateMap(String key) {
        if (!settings.containsKey(key)) {
            settings.put(key, new HashMap<String, Object>());
        }
        return (Map<String, Object>) settings.get(key);
    }

    public Map<String, Object> getSettings() {
        return settings;
    }
}

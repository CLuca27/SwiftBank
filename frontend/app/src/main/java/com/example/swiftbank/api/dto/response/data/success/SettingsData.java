package com.example.swiftbank.api.dto.response.data.success;

import com.google.gson.annotations.SerializedName;

public class SettingsData {

    @SerializedName("settings")
    private UserSettings settings;

    public UserSettings getSettings() {
        return settings;
    }

    public static class UserSettings {
        @SerializedName("security")
        private SecuritySettings security;

        @SerializedName("notifications")
        private NotificationSettings notifications;

        public SecuritySettings getSecurity() {
            return security;
        }

        public NotificationSettings getNotifications() {
            return notifications;
        }
    }

    public static class SecuritySettings {
        @SerializedName("biometric_enabled")
        private boolean biometricEnabled;

        public boolean isBiometricEnabled() {
            return biometricEnabled;
        }
    }

    public static class NotificationSettings {
        @SerializedName("push_enabled")
        private boolean pushEnabled;

        public boolean isPushEnabled() {
            return pushEnabled;
        }
    }
}

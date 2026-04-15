package com.example.swiftbank.api.dto.response.data;

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

        @SerializedName("display")
        private DisplaySettings display;

        public SecuritySettings getSecurity() {
            return security;
        }

        public NotificationSettings getNotifications() {
            return notifications;
        }

        public DisplaySettings getDisplay() {
            return display;
        }
    }

    public static class SecuritySettings {
        @SerializedName("biometric_enabled")
        private boolean biometricEnabled;

        public boolean isBiometricEnabled() {
            return biometricEnabled;
        }

        public void setBiometricEnabled(boolean biometricEnabled) {
            this.biometricEnabled = biometricEnabled;
        }
    }

    public static class NotificationSettings {
        @SerializedName("push_enabled")
        private boolean pushEnabled;

        @SerializedName("email_enabled")
        private boolean emailEnabled;

        @SerializedName("transaction_alerts")
        private boolean transactionAlerts;

        public boolean isPushEnabled() {
            return pushEnabled;
        }

        public boolean isEmailEnabled() {
            return emailEnabled;
        }

        public boolean isTransactionAlerts() {
            return transactionAlerts;
        }
    }

    public static class DisplaySettings {
        @SerializedName("hide_balance")
        private boolean hideBalance;

        @SerializedName("language")
        private String language;

        public boolean isHideBalance() {
            return hideBalance;
        }

        public String getLanguage() {
            return language;
        }
    }
}

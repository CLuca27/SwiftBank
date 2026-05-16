package com.example.swiftbank;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.swiftbank.activities.login.SessionRevokedActivity;
import com.example.swiftbank.activities.welcome.WelcomeActivity;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.managers.AuthTokenManager;

public class SwiftBankApplication extends Application {

    private static final String TAG = "SwiftBankApplication";
    public static final String ACTION_SESSION_EXPIRED = "com.example.swiftbank.SESSION_EXPIRED";
    public static final String EXTRA_SESSION_EXPIRED_REASON = "session_expired_reason";
    public static final String REASON_NEW_LOGIN = "NEW_LOGIN";

    private final BroadcastReceiver sessionExpiredReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Sesiune expirată - redirecționez la login");

            AuthTokenManager.getInstance(context).clearTokens();

            String reason = intent.getStringExtra(EXTRA_SESSION_EXPIRED_REASON);
            Class<?> targetActivity = REASON_NEW_LOGIN.equals(reason)
                    ? SessionRevokedActivity.class
                    : WelcomeActivity.class;

            Intent loginIntent = new Intent(context, targetActivity);
            loginIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(loginIntent);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        // Aplică tema salvată
        applyTheme();

        // Inițializează ApiClient cu context
        ApiClient.init(this);

        // Înregistrează receiver-ul pentru sesiune expirată
        IntentFilter filter = new IntentFilter(ACTION_SESSION_EXPIRED);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sessionExpiredReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(sessionExpiredReceiver, filter);
        }

        Log.d(TAG, "Application inițializată");
    }

    private void applyTheme() {
        SharedPreferences prefs = getSharedPreferences("SwiftBankSettings", MODE_PRIVATE);
        String theme = prefs.getString("theme", "dark");

        int nightMode = theme.equals("dark")
            ? AppCompatDelegate.MODE_NIGHT_YES
            : AppCompatDelegate.MODE_NIGHT_NO;
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }
}

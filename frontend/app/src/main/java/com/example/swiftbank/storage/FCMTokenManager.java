package com.example.swiftbank.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.request.RegisterDeviceRequest;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.utils.DeviceDetails;
import com.google.firebase.messaging.FirebaseMessaging;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FCMTokenManager {

    private static final String TAG = "FCMTokenManager";
    private static final String PREFS_NAME = "SwiftBankFCM";
    private static final String KEY_TOKEN = "fcm_token";
    private static final String KEY_TOKEN_SENT = "fcm_token_sent";

    private static FCMTokenManager instance;
    private final Context context;
    private final SharedPreferences prefs;

    private FCMTokenManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized FCMTokenManager getInstance(Context context) {
        if (instance == null) {
            instance = new FCMTokenManager(context);
        }
        return instance;
    }

    /**
     * Obține token-ul FCM și îl trimite la server
     * Apelează această metodă după login
     */
    public void registerDevice() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Fetching FCM token failed", task.getException());
                        return;
                    }

                    String token = task.getResult();
                    Log.d(TAG, "FCM Token: " + token);

                    // Salvează local
                    prefs.edit().putString(KEY_TOKEN, token).apply();

                    // Trimite la server
                    sendTokenToServer(token);
                });
    }

    /**
     * Trimite token-ul la server
     */
    private void sendTokenToServer(String fcmToken) {
        String deviceId = DeviceDetails.getDeviceId(context);
        String deviceName = DeviceDetails.getDeviceModel();

        RegisterDeviceRequest request = new RegisterDeviceRequest(fcmToken, deviceId, deviceName);

        ApiClient.getUserService().registerDevice(request).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "FCM token registered successfully");
                    prefs.edit().putBoolean(KEY_TOKEN_SENT, true).apply();
                } else {
                    Log.e(TAG, "Failed to register FCM token: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                Log.e(TAG, "Failed to register FCM token", t);
            }
        });
    }

    /**
     * Șterge token-ul la logout
     */
    public void unregisterDevice() {
        String deviceId = DeviceDetails.getDeviceId(context);

        ApiClient.getUserService().unregisterDevice(deviceId).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                Log.d(TAG, "Device unregistered");
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                Log.e(TAG, "Failed to unregister device", t);
            }
        });

        prefs.edit()
                .remove(KEY_TOKEN_SENT)
                .apply();
    }

    /**
     * Obține token-ul salvat
     */
    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }
}

package com.example.swiftbank.api.interceptors;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.swiftbank.api.dto.request.RefreshRequest;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.RefreshData;
import com.example.swiftbank.managers.AuthTokenManager;
import com.example.swiftbank.utils.DeviceDetails;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;

import okhttp3.Authenticator;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Route;

public class TokenAuthenticator implements Authenticator {

    private static final String TAG = "TokenAuthenticator";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final Context context;
    private final AuthTokenManager tokenManager;
    private final String baseUrl;
    private final Gson gson;

    // Lock pentru a evita refresh-uri simultane de la mai multe request-uri
    private static final Object LOCK = new Object();

    public TokenAuthenticator(Context context, AuthTokenManager tokenManager, String baseUrl) {
        this.context = context.getApplicationContext();
        this.tokenManager = tokenManager;
        this.baseUrl = baseUrl;
        this.gson = new Gson();
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, @NonNull Response response) throws IOException {
        if (responseCount(response) >= 2) {
            return null;
        }
        // Dacă request-ul original nu avea Authorization header, nu încercăm refresh
        String authHeader = response.request().header("Authorization");
        if (authHeader == null) {
            if (!isProtectedApiRequest(response.request())) {
                return null;
            }

            synchronized (LOCK) {
                RefreshData refreshData = doRefresh();

                if (refreshData != null) {
                    Log.d(TAG, "Refresh reusit pentru request fara access token");
                    tokenManager.saveTokens(
                            refreshData.getAccessToken(),
                            refreshData.getRefreshToken()
                    );

                    return response.request().newBuilder()
                            .header("Authorization", "Bearer " + refreshData.getAccessToken())
                            .build();
                }

                Log.e(TAG, "Token lipsa pe ruta protejata, sesiune expirata");
                handleSessionExpired();
                return null;
            }
        }

        synchronized (LOCK) {
            // Verifică dacă token-ul a fost deja refresh-uit de alt thread
            String currentToken = tokenManager.getAccessToken();
            String requestToken = authHeader.replace("Bearer ", "");

            // Dacă token-ul s-a schimbat între timp, retry cu noul token
            if (currentToken != null && !currentToken.equals(requestToken)) {
                Log.d(TAG, "Token deja refresh-uit, retry cu noul token");
                return response.request().newBuilder()
                        .header("Authorization", "Bearer " + currentToken)
                        .build();
            }

            // Încearcă refresh
            Log.d(TAG, "Încerc refresh token...");
            RefreshData refreshData = doRefresh();

            if (refreshData != null) {
                Log.d(TAG, "Refresh reușit");
                tokenManager.saveTokens(
                        refreshData.getAccessToken(),
                        refreshData.getRefreshToken()
                );

                // Retry request-ul original cu noul token
                return response.request().newBuilder()
                        .header("Authorization", "Bearer " + refreshData.getAccessToken())
                        .build();
            } else {
                Log.e(TAG, "Refresh eșuat, sesiune expirată");
                handleSessionExpired();
                return null;
            }
        }
    }

    private RefreshData doRefresh() {
        String refreshToken = tokenManager.getRefreshToken();
        String deviceId = DeviceDetails.getDeviceId(context);

        if (refreshToken == null || deviceId == null) {
            Log.e(TAG, "Lipsește refresh token sau device ID");
            return null;
        }

        // Client SEPARAT - fără Authenticator (evităm orice risc de loop)
        OkHttpClient refreshClient = new OkHttpClient.Builder().build();

        RefreshRequest refreshRequest = new RefreshRequest(refreshToken, deviceId);
        String jsonBody = gson.toJson(refreshRequest);

        Request request = new Request.Builder()
                .url(baseUrl + "/auth/refresh")
                .post(RequestBody.create(jsonBody, JSON))
                .header("Content-Type", "application/json")
                .build();

        try {
            Response refreshResponse = refreshClient.newCall(request).execute();

            if (refreshResponse.isSuccessful() && refreshResponse.body() != null) {
                String responseBody = refreshResponse.body().string();
                Type type = new TypeToken<ApiResponse<RefreshData>>() {}.getType();
                ApiResponse<RefreshData> apiResponse = gson.fromJson(responseBody, type);

                if (apiResponse != null && apiResponse.getData() != null) {
                    return apiResponse.getData();
                }
            } else {
                Log.e(TAG, "Refresh failed: " + refreshResponse.code());
            }
        } catch (IOException e) {
            Log.e(TAG, "Eroare rețea la refresh: " + e.getMessage());
        }

        return null;
    }

    private void handleSessionExpired() {
        // Șterge token-urile locale
        tokenManager.clearTokens();

        // Notifică aplicația prin broadcast
        Intent intent = new Intent("com.example.swiftbank.SESSION_EXPIRED");
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    private boolean isProtectedApiRequest(Request request) {
        String path = request.url().encodedPath();
        return path != null && path.startsWith("/api/");
    }

    private int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) {
            result++;
        }
        return result;
    }
}

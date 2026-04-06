package com.example.swiftbank.api.interceptors;

import androidx.annotation.NonNull;

import com.example.swiftbank.storage.TokenManager;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {

    private final TokenManager tokenManager;

    public AuthInterceptor(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request originalRequest = chain.request();
        String url = originalRequest.url().toString();

        // Doar rutele /api/* necesită Authorization header
        if (!url.contains("/api/")) {
            return chain.proceed(originalRequest);
        }

        String accessToken = tokenManager.getAccessToken();

        // Dacă nu avem token, continuăm fără header (backend va returna 401)
        if (accessToken == null || accessToken.isEmpty()) {
            return chain.proceed(originalRequest);
        }

        // Adaugă Authorization header
        Request authenticatedRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer " + accessToken)
                .build();

        return chain.proceed(authenticatedRequest);
    }
}

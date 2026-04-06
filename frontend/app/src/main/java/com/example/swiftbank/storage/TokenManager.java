package com.example.swiftbank.storage;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class TokenManager {

    private static TokenManager instance;
    private static final String PREFS_NAME = "tokens";
    private static final String REFRESH_TOKEN = "REFRESH_TOKEN";

    private SharedPreferences prefs;
    private String accessToken;

    private TokenManager(Context context)
    {
        Context appContext = context.getApplicationContext();
        try{
            MasterKey key =  new MasterKey.Builder(appContext).
                    setKeyScheme(MasterKey.KeyScheme.AES256_GCM).
                    build();

            prefs = EncryptedSharedPreferences.create(
                    appContext,
                    PREFS_NAME,
                    key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    public static TokenManager getInstance(Context context)
    {
        if(instance == null)
            instance = new TokenManager(context);

        return instance;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken){
        this.accessToken = accessToken;
    }

    public String getRefreshToken(){
        return prefs.getString(REFRESH_TOKEN, null);
    }

    public void setRefreshToken(String token){
            prefs.edit().putString(REFRESH_TOKEN, token).apply();
    }

    public void saveTokens(String accessToken, String refreshToken)
    {
        setAccessToken(accessToken);
        setRefreshToken(refreshToken);
    }

    public void clearTokens() {
        accessToken = null;
        prefs.edit().remove(REFRESH_TOKEN).apply();
    }

    public boolean hasRefreshToken() {
        return getRefreshToken() != null;
    }

}


package com.example.swiftbank.api;

import android.content.Context;

import com.example.swiftbank.api.interceptors.AuthInterceptor;
import com.example.swiftbank.api.interceptors.TokenAuthenticator;
import com.example.swiftbank.api.services.AccountService;
import com.example.swiftbank.api.services.AuthService;
import com.example.swiftbank.api.services.BillService;
import com.example.swiftbank.api.services.CardService;
import com.example.swiftbank.api.services.CardPaymentService;
import com.example.swiftbank.api.services.PlacesService;
import com.example.swiftbank.api.services.RatesService;
import com.example.swiftbank.api.services.TransactionService;
import com.example.swiftbank.api.services.TransferService;
import com.example.swiftbank.api.services.UserService;
import com.example.swiftbank.api.services.StatisticsService;
import com.example.swiftbank.managers.AuthTokenManager;
import com.example.swiftbank.config.GsonProvider;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    //Pentru conectare la server: 16.170.238.26
    //Pentru conectare la server local: 10.0.2.2
    private static final String BASE_URL = "http://10.0.2.2:8618";

    private static Retrofit retrofit = null;
    private static boolean isInitialized = false;

    // Apelat in SwiftBankApplication.onCreate()
    public static void init(Context context) {
        if (isInitialized) {
            return;
        }

        Context appContext = context.getApplicationContext();
        AuthTokenManager authTokenManager = AuthTokenManager.getInstance(appContext);

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.level(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor(new AuthInterceptor(authTokenManager))
                .authenticator(new TokenAuthenticator(appContext, authTokenManager, BASE_URL))
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        retrofit = new Retrofit.Builder()
                .client(client)
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(GsonProvider.getGson()))
                .build();

        isInitialized = true;
    }

    public static Retrofit getRetrofit() {
        if (!isInitialized) {
            throw new IllegalStateException("ApiClient nu a fost initializat! Apeleaza ApiClient.init(context) in Application.onCreate()");
        }
        return retrofit;
    }

    public static String getBaseUrl() {
        return BASE_URL;
    }

    public static AuthService getAuthService() {
        return getRetrofit().create(AuthService.class);
    }

    public static PlacesService getPlacesService() {
        return getRetrofit().create(PlacesService.class);
    }

    public static AccountService getAccountService() {
        return getRetrofit().create(AccountService.class);
    }

    public static TransactionService getTransactionService() {
        return getRetrofit().create(TransactionService.class);
    }

    public static UserService getUserService() {
        return getRetrofit().create(UserService.class);
    }

    public static RatesService getRatesService() {
        return getRetrofit().create(RatesService.class);
    }

    public static TransferService getTransferService() {
        return getRetrofit().create(TransferService.class);
    }

    public static CardService getCardService() {
        return getRetrofit().create(CardService.class);
    }

    public static CardPaymentService getCardPaymentService() {
        return getRetrofit().create(CardPaymentService.class);
    }

    public static BillService getBillService() {
        return getRetrofit().create(BillService.class);
    }

    public static StatisticsService getStatisticsService() {
        return getRetrofit().create(StatisticsService.class);
    }
}

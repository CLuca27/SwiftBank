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

public final class ApiClient {

    //Pentru conectare la server: 16.170.229.137
    //Pentru conectare la server local: 10.0.2.2
    private static final String BASE_URL = "http://16.170.229.137:8080";

    private static volatile boolean isInitialized = false;
    private static Retrofit retrofit;

    private static AuthService authService;
    private static PlacesService placesService;
    private static AccountService accountService;
    private static TransactionService transactionService;
    private static UserService userService;
    private static RatesService ratesService;
    private static TransferService transferService;
    private static CardService cardService;
    private static CardPaymentService cardPaymentService;
    private static BillService billService;
    private static StatisticsService statisticsService;

    private ApiClient() {
    }

    // Apelat in SwiftBankApplication.onCreate()
    public static synchronized void init(Context context) {
        if (isInitialized) {
            return;
        }
        if (context == null) {
            throw new IllegalArgumentException("Contextul nu poate fi null la initializarea ApiClient");
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

        authService = createService(AuthService.class);
        placesService = createService(PlacesService.class);
        accountService = createService(AccountService.class);
        transactionService = createService(TransactionService.class);
        userService = createService(UserService.class);
        ratesService = createService(RatesService.class);
        transferService = createService(TransferService.class);
        cardService = createService(CardService.class);
        cardPaymentService = createService(CardPaymentService.class);
        billService = createService(BillService.class);
        statisticsService = createService(StatisticsService.class);

        isInitialized = true;
    }
    private static Retrofit getRetrofit() {
        if (!isInitialized) {
            throw new IllegalStateException("ApiClient nu a fost initializat! Apeleaza ApiClient.init(context) in Application.onCreate()");
        }
        return retrofit;
    }
    private static <T> T createService(Class<T> serviceClass) {
        return retrofit.create(serviceClass);
    }

    public static String getBaseUrl() {
        return BASE_URL;
    }

    public static AuthService getAuthService() {
        getRetrofit();
        return authService;
    }

    public static PlacesService getPlacesService() {
        getRetrofit();
        return placesService;
    }

    public static AccountService getAccountService() {
        getRetrofit();
        return accountService;
    }

    public static TransactionService getTransactionService() {
        getRetrofit();
        return transactionService;
    }

    public static UserService getUserService() {
        getRetrofit();
        return userService;
    }

    public static RatesService getRatesService() {
        getRetrofit();
        return ratesService;
    }

    public static TransferService getTransferService() {
        getRetrofit();
        return transferService;
    }

    public static CardService getCardService() {
        getRetrofit();
        return cardService;
    }

    public static CardPaymentService getCardPaymentService() {
        getRetrofit();
        return cardPaymentService;
    }

    public static BillService getBillService() {
        getRetrofit();
        return billService;
    }

    public static StatisticsService getStatisticsService() {
        getRetrofit();
        return statisticsService;
    }
}

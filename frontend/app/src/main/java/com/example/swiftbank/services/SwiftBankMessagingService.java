package com.example.swiftbank.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.example.swiftbank.R;
import com.example.swiftbank.activities.dashboard.DashboardActivity;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.storage.AuthTokenManager;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SwiftBankMessagingService extends FirebaseMessagingService {

    private static final String TAG = "SwiftBankFCM";
    private static final String CHANNEL_ID = "swiftbank_transactions";
    private static final String CHANNEL_NAME = "Tranzacții";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token: " + token);

        // Salvează token-ul local
        getSharedPreferences("SwiftBankFCM", MODE_PRIVATE)
                .edit()
                .putString("fcm_token", token)
                .apply();

        // Trimite la server dacă utilizatorul e autentificat
        sendTokenToServer(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "Message received from: " + remoteMessage.getFrom());

        // Verifică dacă mesajul are payload de notificare
        if (remoteMessage.getNotification() != null) {
            String title = remoteMessage.getNotification().getTitle();
            String body = remoteMessage.getNotification().getBody();
            showNotification(title, body);
        }

        // Verifică dacă mesajul are payload de date
        if (!remoteMessage.getData().isEmpty()) {
            String type = remoteMessage.getData().get("type");

            if ("TRANSFER_RECEIVED".equals(type)) {
                String amount = remoteMessage.getData().get("amount");
                String currency = remoteMessage.getData().get("currency");
                String senderName = remoteMessage.getData().get("sender_name");

                String title = "Ai primit bani!";
                String body = String.format("%s %s de la %s", amount, currency, senderName);
                showNotification(title, body);

                // Trimite broadcast pentru a actualiza UI-ul dacă app-ul e deschis
                Intent updateIntent = new Intent("com.example.swiftbank.REFRESH_DATA");
                sendBroadcast(updateIntent);
            } else if ("TRANSFER_SENT".equals(type)) {
                String amount = remoteMessage.getData().get("amount");
                String currency = remoteMessage.getData().get("currency");
                String recipientName = remoteMessage.getData().get("recipient_name");

                String title = "Transfer efectuat";
                String body = String.format("%s %s către %s", amount, currency, recipientName);
                showNotification(title, body);

                // Trimite broadcast pentru a actualiza UI-ul
                Intent updateIntent = new Intent("com.example.swiftbank.REFRESH_DATA");
                sendBroadcast(updateIntent);
            }
        }
    }

    private void showNotification(String title, String body) {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);

        // Creează canalul de notificare (necesar pentru Android 8+)
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Notificări pentru tranzacții SwiftBank");
        notificationManager.createNotificationChannel(channel);

        // Intent pentru deschiderea aplicației
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        // Construiește notificarea
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        // Afișează notificarea
        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void sendTokenToServer(String token) {
        AuthTokenManager authTokenManager = AuthTokenManager.getInstance(this);
        if (!authTokenManager.hasValidToken()) {
            Log.d(TAG, "User not logged in, skipping token upload");
            return;
        }

        // Re-înregistrează token-ul nou la server
        com.example.swiftbank.storage.FCMTokenManager.getInstance(this).registerDevice();
    }

    /**
     * Obține token-ul FCM salvat local
     */
    public static String getToken(android.content.Context context) {
        return context.getSharedPreferences("SwiftBankFCM", MODE_PRIVATE)
                .getString("fcm_token", null);
    }
}

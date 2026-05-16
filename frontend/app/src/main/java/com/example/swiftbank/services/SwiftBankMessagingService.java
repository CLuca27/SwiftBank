package com.example.swiftbank.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.example.swiftbank.R;
import com.example.swiftbank.SwiftBankApplication;
import com.example.swiftbank.activities.cards.CardPaymentApprovalActivity;
import com.example.swiftbank.activities.dashboard.DashboardActivity;
import com.example.swiftbank.activities.login.SessionRevokedActivity;
import com.example.swiftbank.managers.AuthTokenManager;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

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
        String dataType = remoteMessage.getData().get("type");

        // Verifică dacă mesajul are payload de notificare
        if (remoteMessage.getNotification() != null && remoteMessage.getData().isEmpty()) {
            String title = remoteMessage.getNotification().getTitle();
            String body = remoteMessage.getNotification().getBody();
            showNotification(title, body);
        }

        // Verifică dacă mesajul are payload de date
        if (!remoteMessage.getData().isEmpty()) {
            String type = dataType;

            if ("SESSION_REVOKED".equals(type)) {
                handleSessionRevoked(remoteMessage);
            } else if ("TRANSFER_RECEIVED".equals(type)) {
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
            } else if ("CARD_PAYMENT_APPROVAL".equals(type)) {
                String sessionId = remoteMessage.getData().get("session_id");
                String amount = remoteMessage.getData().get("amount");
                String currency = remoteMessage.getData().get("currency");
                String merchantName = remoteMessage.getData().get("merchant_name");
                String merchantLocation = remoteMessage.getData().get("merchant_location");
                String maskedCard = remoteMessage.getData().get("masked_card");
                String expiresAt = remoteMessage.getData().get("expires_at");

                String title = "Aprobare plata";
                String body = String.format("%s %s la %s", amount, currency, merchantName);

                Intent intent = new Intent(this, CardPaymentApprovalActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.putExtra(CardPaymentApprovalActivity.EXTRA_SESSION_ID, parseInt(sessionId, -1));
                intent.putExtra(CardPaymentApprovalActivity.EXTRA_MERCHANT_NAME, merchantName);
                intent.putExtra(CardPaymentApprovalActivity.EXTRA_MERCHANT_LOCATION, merchantLocation);
                intent.putExtra(CardPaymentApprovalActivity.EXTRA_AMOUNT, parseDouble(amount, 0));
                intent.putExtra(CardPaymentApprovalActivity.EXTRA_CURRENCY, currency);
                intent.putExtra(CardPaymentApprovalActivity.EXTRA_MASKED_CARD, maskedCard);
                intent.putExtra(CardPaymentApprovalActivity.EXTRA_EXPIRES_AT, expiresAt);
                showNotification(title, body, intent);

                Intent updateIntent = new Intent("com.example.swiftbank.REFRESH_DATA");
                sendBroadcast(updateIntent);
            } else if ("BILL_PAYMENT_COMPLETED".equals(type)) {
                String amount = remoteMessage.getData().get("amount");
                String currency = remoteMessage.getData().get("currency");
                String billerName = remoteMessage.getData().get("biller_name");

                String title = "Factura platita";
                String body = String.format("%s %s catre %s",
                        displayValue(amount),
                        displayValue(currency),
                        displayValue(billerName));
                showNotification(title, body);
                sendRefreshBroadcast();
            } else if ("CARD_PAYMENT_APPROVED".equals(type)) {
                String amount = remoteMessage.getData().get("amount");
                String currency = remoteMessage.getData().get("currency");
                String merchantName = remoteMessage.getData().get("merchant_name");

                String title = "Plata aprobata";
                String body = String.format("%s %s blocati pentru %s",
                        displayValue(amount),
                        displayValue(currency),
                        displayValue(merchantName));
                showNotification(title, body);
                sendRefreshBroadcast();
            } else if ("CARD_PAYMENT_DECLINED".equals(type)) {
                String amount = remoteMessage.getData().get("amount");
                String currency = remoteMessage.getData().get("currency");
                String merchantName = remoteMessage.getData().get("merchant_name");

                String title = "Plata refuzata";
                String body = String.format("%s %s la %s",
                        displayValue(amount),
                        displayValue(currency),
                        displayValue(merchantName));
                showNotification(title, body);
                sendRefreshBroadcast();
            } else if (remoteMessage.getNotification() != null) {
                showNotification(
                        remoteMessage.getNotification().getTitle(),
                        remoteMessage.getNotification().getBody()
                );
            }
        }
    }

    private void sendRefreshBroadcast() {
        Intent updateIntent = new Intent("com.example.swiftbank.REFRESH_DATA");
        sendBroadcast(updateIntent);
    }

    private void handleSessionRevoked(RemoteMessage remoteMessage) {
        Log.d(TAG, "Session revoked by login on another device");

        AuthTokenManager.getInstance(this).clearTokens();
        getSharedPreferences("SwiftBankFCM", MODE_PRIVATE)
                .edit()
                .remove("fcm_token_sent")
                .apply();

        Intent expiredIntent = new Intent(SwiftBankApplication.ACTION_SESSION_EXPIRED);
        expiredIntent.putExtra(
                SwiftBankApplication.EXTRA_SESSION_EXPIRED_REASON,
                SwiftBankApplication.REASON_NEW_LOGIN
        );
        expiredIntent.setPackage(getPackageName());
        sendBroadcast(expiredIntent);

        Intent revokedIntent = new Intent(this, SessionRevokedActivity.class);
        revokedIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        showNotification(
                "Sesiune inchisa",
                "Te-ai autentificat pe un alt dispozitiv.",
                revokedIntent
        );
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

    private void showNotification(String title, String body, Intent intent) {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Notificari pentru tranzactii SwiftBank");
        notificationManager.createNotificationChannel(channel);

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, (int) System.currentTimeMillis(), intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private double parseDouble(String value, double fallback) {
        try {
            return value == null ? fallback : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String displayValue(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private void sendTokenToServer(String token) {
        AuthTokenManager authTokenManager = AuthTokenManager.getInstance(this);
        if (!authTokenManager.hasValidToken()) {
            Log.d(TAG, "User not logged in, skipping token upload");
            return;
        }

        // Re-înregistrează token-ul nou la server
        com.example.swiftbank.managers.FCMTokenManager.getInstance(this).registerDevice();
    }

    /**
     * Obține token-ul FCM salvat local
     */
    public static String getToken(android.content.Context context) {
        return context.getSharedPreferences("SwiftBankFCM", MODE_PRIVATE)
                .getString("fcm_token", null);
    }
}

package com.example.swiftbank.managers;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.example.swiftbank.config.SupabaseConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Manager pentru Supabase Realtime folosind WebSocket direct.
 * Bazat pe protocolul Phoenix Channels.
 */
public class RealtimeManager {

    private static final String TAG = "RealtimeManager";


    private static RealtimeManager instance;

    private final OkHttpClient client;
    private final Gson gson;
    private final Handler mainHandler;
    private final Handler heartbeatHandler;

    private WebSocket webSocket;
    private boolean isConnected = false;
    private int messageRef = 0;

    private final Map<String, Set<RealtimeListener>> channelListeners = new HashMap<>();
    private final Set<String> joinedChannels = new HashSet<>();

    // Stocăm info pentru fiecare subscription (pentru reconectare)
    private static class SubscriptionInfo {
        String table;
        String filterColumn;
        String filterOp;
        String filterValue;

        SubscriptionInfo(String table, String filterColumn, String filterOp, String filterValue) {
            this.table = table;
            this.filterColumn = filterColumn;
            this.filterOp = filterOp;
            this.filterValue = filterValue;
        }
    }
    private final Map<String, SubscriptionInfo> channelSubscriptions = new HashMap<>();

    // Heartbeat
    private static final long HEARTBEAT_INTERVAL = 30000; // 30 secunde
    private final Runnable heartbeatRunnable = this::sendHeartbeat;

    // Reconnect
    private static final long RECONNECT_DELAY = 3000; // 3 secunde
    private boolean shouldReconnect = true;

    public interface RealtimeListener {
        void onInsert(String table, JsonObject newRecord);
        void onUpdate(String table, JsonObject oldRecord, JsonObject newRecord);
        void onDelete(String table, JsonObject oldRecord);
    }

    private RealtimeManager() {
        client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket
                .build();
        gson = new Gson();
        mainHandler = new Handler(Looper.getMainLooper());
        heartbeatHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized RealtimeManager getInstance() {
        if (instance == null) {
            instance = new RealtimeManager();
        }
        return instance;
    }

    /**
     * Conectează la Supabase Realtime
     */
    public void connect() {
        if (isConnected || webSocket != null) {
            Log.d(TAG, "Already connected or connecting");
            return;
        }

        shouldReconnect = true;

        String url = SupabaseConfig.getRealtimeUrl();

        Request request = new Request.Builder()
                .url(url)
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                Log.d(TAG, "WebSocket connected");
                isConnected = true;

                // Pornește heartbeat
                startHeartbeat();

                // Re-join channels existente cu filtrele corecte
                mainHandler.post(() -> {
                    for (String channel : channelListeners.keySet()) {
                        SubscriptionInfo info = channelSubscriptions.get(channel);
                        if (info != null && info.filterColumn != null) {
                            // Channel cu filtru
                            joinChannelWithFilter(channel, info.table, info.filterColumn, info.filterOp, info.filterValue);
                        } else {
                            // Channel simplu
                            joinChannel(channel);
                        }
                    }
                });
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                handleMessage(text);
            }

            @Override
            public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                Log.d(TAG, "WebSocket closing: " + reason);
            }

            @Override
            public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                Log.d(TAG, "WebSocket closed: " + reason);
                handleDisconnect();
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
                Log.e(TAG, "WebSocket error: " + t.getMessage());
                handleDisconnect();
            }
        });
    }

    /**
     * Deconectează de la Supabase Realtime
     */
    public void disconnect() {
        shouldReconnect = false;
        stopHeartbeat();
        joinedChannels.clear();

        if (webSocket != null) {
            webSocket.close(1000, "Client disconnect");
            webSocket = null;
        }
        isConnected = false;
    }

    /**
     * Abonează la schimbări pe un tabel
     */
    public void subscribe(String table, RealtimeListener listener) {
        String channel = "realtime:public:" + table;

        // Adaugă listener
        channelListeners.computeIfAbsent(channel, k -> new HashSet<>()).add(listener);

        // Join channel dacă suntem conectați
        if (isConnected && !joinedChannels.contains(channel)) {
            joinChannel(channel);
        }
    }

    /**
     * Dezabonează de la un tabel
     */
    public void unsubscribe(String table, RealtimeListener listener) {
        String channel = "realtime:public:" + table;

        Set<RealtimeListener> listeners = channelListeners.get(channel);
        if (listeners != null) {
            listeners.remove(listener);

            // Dacă nu mai sunt listeners, leave channel
            if (listeners.isEmpty()) {
                channelListeners.remove(channel);
                leaveChannel(channel);
            }
        }
    }

    /**
     * Dezabonează de la schimbări filtrate după user_id
     */
    public void unsubscribeFromUserChanges(String table, String userId, RealtimeListener listener) {
        unsubscribeFromColumnChanges(table, "user_id", userId, listener);
    }

    public void unsubscribeFromColumnChanges(String table, String column, String value, RealtimeListener listener) {
        String channel = filteredChannelName(table, column, value);

        Set<RealtimeListener> listeners = channelListeners.get(channel);
        if (listeners != null) {
            listeners.remove(listener);

            if (listeners.isEmpty()) {
                channelListeners.remove(channel);
                channelSubscriptions.remove(channel);
                leaveChannel(channel);
            }
        }
    }

    /**
     * Abonează la schimbări filtrate după user_id
     */
    public void subscribeToUserChanges(String table, String userId, RealtimeListener listener) {
        subscribeToColumnChanges(table, "user_id", userId, listener);
    }

    public void subscribeToColumnChanges(String table, String column, String value, RealtimeListener listener) {
        String channel = filteredChannelName(table, column, value);

        channelListeners.computeIfAbsent(channel, k -> new HashSet<>()).add(listener);

        // Stocăm info pentru reconectare
        channelSubscriptions.put(channel, new SubscriptionInfo(table, column, "eq", value));

        if (isConnected && !joinedChannels.contains(channel)) {
            joinChannelWithFilter(channel, table, column, "eq", value);
        }
    }

    private String filteredChannelName(String table, String column, String value) {
        return "realtime:" + table + "-" + column + "-" + value;
    }

    private void joinChannel(String channel) {
        if (webSocket == null || !isConnected) return;

        JsonObject payload = new JsonObject();
        payload.addProperty("user_token", SupabaseConfig.ANON_KEY);

        JsonObject config = new JsonObject();

        // Broadcast config
        JsonObject broadcast = new JsonObject();
        broadcast.addProperty("self", false);
        config.add("broadcast", broadcast);

        // Presence config
        JsonObject presence = new JsonObject();
        presence.addProperty("key", "");
        config.add("presence", presence);

        payload.add("config", config);

        sendMessage(channel, "phx_join", payload);
        joinedChannels.add(channel);

        Log.d(TAG, "Joining channel: " + channel);
    }

    private void joinChannelWithFilter(String channel, String table, String column, String op, String value) {
        if (webSocket == null || !isConnected) return;

        JsonObject payload = new JsonObject();
        payload.addProperty("user_token", SupabaseConfig.ANON_KEY);

        JsonObject config = new JsonObject();

        // Broadcast config
        JsonObject broadcast = new JsonObject();
        broadcast.addProperty("self", false);
        config.add("broadcast", broadcast);

        // Presence config
        JsonObject presence = new JsonObject();
        presence.addProperty("key", "");
        config.add("presence", presence);

        // Postgres changes config - MUST be an array for Supabase Realtime v2
        JsonArray pgChangesArray = new JsonArray();
        JsonObject pgChange = new JsonObject();
        pgChange.addProperty("event", "*");
        pgChange.addProperty("schema", "public");
        pgChange.addProperty("table", table);
        pgChange.addProperty("filter", column + "=" + op + "." + value);
        pgChangesArray.add(pgChange);

        config.add("postgres_changes", pgChangesArray);
        payload.add("config", config);

        sendMessage(channel, "phx_join", payload);
        joinedChannels.add(channel);

        Log.d(TAG, "Joining filtered channel: " + channel);
    }

    private void leaveChannel(String channel) {
        if (webSocket == null || !isConnected) return;

        sendMessage(channel, "phx_leave", new JsonObject());
        joinedChannels.remove(channel);

        Log.d(TAG, "Leaving channel: " + channel);
    }

    private void sendMessage(String topic, String event, JsonObject payload) {
        if (webSocket == null) return;

        JsonObject message = new JsonObject();
        message.addProperty("topic", topic);
        message.addProperty("event", event);
        message.add("payload", payload);
        message.addProperty("ref", String.valueOf(++messageRef));

        String json = gson.toJson(message);
        webSocket.send(json);

        Log.v(TAG, "Sent: " + json);
    }

    private void handleMessage(String text) {
        Log.v(TAG, "Received: " + text);

        try {
            JsonObject message = gson.fromJson(text, JsonObject.class);
            String event = message.get("event").getAsString();
            String topic = message.get("topic").getAsString();

            switch (event) {
                case "phx_reply":
                    // Răspuns la join/leave
                    handleReply(message);
                    break;

                case "postgres_changes":
                    // Schimbare în baza de date
                    handlePostgresChange(topic, message.getAsJsonObject("payload"));
                    break;

                case "phx_close":
                    Log.d(TAG, "Channel closed: " + topic);
                    break;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing message: " + e.getMessage());
        }
    }

    private void handleReply(JsonObject message) {
        JsonObject payload = message.getAsJsonObject("payload");
        String status = payload.get("status").getAsString();

        if ("ok".equals(status)) {
            Log.d(TAG, "Join successful for: " + message.get("topic").getAsString());
        } else {
            Log.e(TAG, "Join failed: " + payload);
        }
    }

    private void handlePostgresChange(String topic, JsonObject payload) {
        try {
            JsonObject data = payload.getAsJsonObject("data");
            String type = data.get("type").getAsString();
            String table = data.get("table").getAsString();
            String schema = data.get("schema").getAsString();

            JsonObject record = data.has("record") ? data.getAsJsonObject("record") : null;
            JsonObject oldRecord = data.has("old_record") ? data.getAsJsonObject("old_record") : null;

            Log.d(TAG, "Postgres change: " + type + " on " + table);

            // Notifică listeners pe main thread
            mainHandler.post(() -> {
                Set<RealtimeListener> listeners = channelListeners.get(topic);
                if (listeners == null) return;

                for (RealtimeListener listener : listeners) {
                    switch (type) {
                        case "INSERT":
                            listener.onInsert(table, record);
                            break;
                        case "UPDATE":
                            listener.onUpdate(table, oldRecord, record);
                            break;
                        case "DELETE":
                            listener.onDelete(table, oldRecord);
                            break;
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error handling postgres change: " + e.getMessage());
        }
    }

    private void startHeartbeat() {
        heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL);
    }

    private void stopHeartbeat() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable);
    }

    private void sendHeartbeat() {
        if (webSocket != null && isConnected) {
            sendMessage("phoenix", "heartbeat", new JsonObject());
            heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL);
        }
    }

    private void handleDisconnect() {
        isConnected = false;
        webSocket = null;
        joinedChannels.clear();
        stopHeartbeat();

        // Reconnect dacă trebuie
        if (shouldReconnect) {
            Log.d(TAG, "Reconnecting in " + RECONNECT_DELAY + "ms...");
            mainHandler.postDelayed(this::connect, RECONNECT_DELAY);
        }
    }

    public boolean isConnected() {
        return isConnected;
    }
}

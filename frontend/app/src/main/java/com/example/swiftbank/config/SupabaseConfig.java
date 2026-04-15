package com.example.swiftbank.config;

/**
 * Configurare Supabase pentru Android.
 * IMPORTANT: Înlocuiește valorile cu cele din proiectul tău Supabase.
 *
 * Le găsești în Supabase Dashboard → Settings → API
 */
public class SupabaseConfig {

    // Project URL (fără https://)
    // Exemplu: "abcdefghijklmnop.supabase.co"
    public static final String PROJECT_URL = "ytpoattgkohchzarveal.supabase.co";

    // Anon/Public key (safe to use in client)
    // Exemplu: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
    public static final String ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inl0cG9hdHRna29oY2h6YXJ2ZWFsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzE0MTc4NzgsImV4cCI6MjA4Njk5Mzg3OH0.v2Kxb3SMeFyQvZItMnWTZGd29yBHsfvowNy4xfHkaJU";

    // WebSocket URL pentru Realtime
    public static String getRealtimeUrl() {
        return String.format("wss://%s/realtime/v1/websocket?apikey=%s&vsn=1.0.0",
                PROJECT_URL, ANON_KEY);
    }
}

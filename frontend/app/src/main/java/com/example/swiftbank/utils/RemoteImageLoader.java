package com.example.swiftbank.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RemoteImageLoader {

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private RemoteImageLoader() {
    }

    public static boolean load(String imageUrl, ImageView imageView, Runnable onError) {
        String cleanUrl = imageUrl == null ? "" : imageUrl.trim();
        if (cleanUrl.isEmpty()) {
            return false;
        }

        imageView.setTag(cleanUrl);

        Bitmap cached = CACHE.get(cleanUrl);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return true;
        }

        imageView.setImageDrawable(null);

        EXECUTOR.execute(() -> {
            Bitmap bitmap = downloadBitmap(cleanUrl);
            if (bitmap != null) {
                CACHE.put(cleanUrl, bitmap);
            }

            MAIN_HANDLER.post(() -> {
                Object tag = imageView.getTag();
                if (!cleanUrl.equals(tag)) {
                    return;
                }

                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                } else if (onError != null) {
                    onError.run();
                }
            });
        });

        return true;
    }

    private static Bitmap downloadBitmap(String imageUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(imageUrl).openConnection();
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(7000);
            connection.setInstanceFollowRedirects(true);

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                return null;
            }

            String contentType = connection.getContentType();
            if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
                return null;
            }

            try (InputStream stream = connection.getInputStream()) {
                return BitmapFactory.decodeStream(stream);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}

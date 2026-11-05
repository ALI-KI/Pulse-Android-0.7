package com.pulseplayer.mobile;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Artwork {
    private static final ExecutorService WORK = Executors.newFixedThreadPool(2);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };

    static void show(ImageView view, String url) {
        if (url.equals(view.getTag())) return;
        view.setTag(url);
        view.setImageResource(R.drawable.pulse_logo);
        if (!Track.https(url)) return;
        Bitmap cached = CACHE.get(url);
        if (cached != null) { view.setImageBitmap(cached); return; }
        WORK.execute(() -> {
            Bitmap image = fetch(url);
            if (image == null) return;
            CACHE.put(url, image);
            view.post(() -> { if (url.equals(view.getTag())) view.setImageBitmap(image); });
        });
    }

    private static Bitmap fetch(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                for (int size; (size = input.read(buffer)) != -1;) {
                    if (output.size() + size > 2 * 1024 * 1024) return null;
                    output.write(buffer, 0, size);
                }
                byte[] data = output.toByteArray();
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, options);
                options.inSampleSize = 1;
                while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 768)
                    options.inSampleSize *= 2;
                options.inJustDecodeBounds = false;
                return BitmapFactory.decodeByteArray(data, 0, data.length, options);
            }
        } catch (Exception ignored) { return null; }
        finally { if (connection != null) connection.disconnect(); }
    }
}

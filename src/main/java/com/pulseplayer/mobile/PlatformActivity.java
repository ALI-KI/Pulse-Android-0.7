package com.pulseplayer.mobile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.view.WindowInsets;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.concurrent.FutureTask;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

public final class PlatformActivity extends Activity {
    static final String PROVIDER = "provider", SEARCH = "search", MEDIA = "media";
    static final String TITLE = "title", AUTHOR = "author", PLAY = "play", ARTWORK = "artwork";
    static final String QUEUE = "queue", QUEUE_INDEX = "queue_index";
    private LinearLayout results;
    private FutureTask<Void> task;
    private List<StreamInfoItem> currentResults = java.util.Collections.emptyList();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String provider = getIntent().getStringExtra(PROVIDER);
        String query = getIntent().getStringExtra(SEARCH);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left + dp(18), bars.top + dp(20),
                    bars.right + dp(18), bars.bottom + dp(12));
            } else view.setPadding(dp(18), dp(20), dp(18), dp(12));
            return insets;
        });
        root.setBackgroundColor(0xff0d1322);
        TextView heading = label(provider + "  ·  " + query, 22, Color.WHITE);
        root.addView(heading);
        Button back = new Button(this);
        back.setText("رجوع");
        back.setOnClickListener(view -> finish());
        root.addView(back);
        ScrollView scroll = new ScrollView(this);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        root.requestApplyInsets();
        if (query == null || query.trim().isEmpty()) { finish(); return; }
        results.addView(label("جارٍ البحث…", 16, 0xff8ce8ff));
        task = new FutureTask<>(() -> {
            try {
                List<StreamInfoItem> items = StreamResolver.search(provider, query);
                runOnUiThread(() -> showResults(items));
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    results.removeAllViews();
                    results.addView(label("تعذّر البحث. تحقّق من الاتصال وجرّب مرة أخرى.", 16, 0xfff8c8c8));
                });
            }
            return null;
        });
        new Thread(task, "pulse-search").start();
    }

    private void showResults(List<StreamInfoItem> items) {
        if (isFinishing() || isDestroyed()) return;
        results.removeAllViews();
        currentResults = items;
        if (items.isEmpty()) results.addView(label("لا توجد نتائج صوتية لهذا البحث.", 16, Color.WHITE));
        for (StreamInfoItem item : items) {
            String media = PlatformLinks.media(item.getUrl());
            if (media.isEmpty()) continue;
            LinearLayout entry = new LinearLayout(this);
            entry.setGravity(Gravity.CENTER_VERTICAL);
            entry.setPadding(dp(6), dp(12), dp(6), 0);
            ImageView art = new ImageView(this);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            entry.addView(art, new LinearLayout.LayoutParams(dp(62), dp(62)));
            Artwork.show(art, StreamResolver.artwork(item.getThumbnails()));
            LinearLayout detail = new LinearLayout(this);
            detail.setOrientation(LinearLayout.VERTICAL);
            detail.setPadding(dp(10), 0, dp(10), 0);
            TextView track = label(item.getName(), 17, Color.WHITE);
            track.setMaxLines(2);
            detail.addView(track);
            detail.addView(label(item.getUploaderName(), 14, 0xffaec1e5));
            entry.addView(detail, new LinearLayout.LayoutParams(0, -2, 1));
            results.addView(entry);
            LinearLayout actions = new LinearLayout(this);
            Button play = new Button(this);
            play.setText("تشغيل");
            play.setOnClickListener(view -> choose(item, media, true));
            Button save = new Button(this);
            save.setText("حفظ");
            save.setOnClickListener(view -> choose(item, media, false));
            actions.addView(play);
            actions.addView(save);
            results.addView(actions);
        }
    }

    private void choose(StreamInfoItem item, String media, boolean play) {
        Intent result = new Intent().putExtra(MEDIA, media).putExtra(TITLE, item.getName())
            .putExtra(AUTHOR, item.getUploaderName()).putExtra(PLAY, play)
            .putExtra(ARTWORK, StreamResolver.artwork(item.getThumbnails()));
        if (play) {
            // A result plays through the remaining search list, not a one-item queue.
            JSONArray queue = new JSONArray();
            int selectedIndex = -1;
            for (StreamInfoItem found : currentResults) {
                String link = PlatformLinks.media(found.getUrl());
                if (link.isEmpty()) continue;
                Track track = new Track(link, found.getName());
                track.author = found.getUploaderName() == null ? "" : found.getUploaderName();
                track.artwork = StreamResolver.artwork(found.getThumbnails());
                try {
                    if (link.equals(media) && selectedIndex == -1) selectedIndex = queue.length();
                    queue.put(track.write());
                } catch (JSONException error) { /* Ignore only this malformed search entry. */ }
            }
            if (selectedIndex >= 0) result.putExtra(QUEUE, queue.toString()).putExtra(QUEUE_INDEX, selectedIndex);
        }
        setResult(RESULT_OK, result);
        finish();
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.START);
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        if (task != null) task.cancel(true);
        super.onDestroy();
    }
}

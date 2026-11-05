package com.pulseplayer.mobile;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

public final class MainActivity extends Activity {
    private static final int PICK = 9;
    private static final int PLATFORM = 10;
    private static final String OWNER = "Mahmoud Elgamsy";
    private static final int BG = Color.rgb(12, 15, 27);
    private static final int CARD = Color.rgb(27, 33, 53);
    private static final int INK = Color.rgb(240, 244, 255);
    private static final int MUTED = Color.rgb(171, 184, 212);
    private static final int CYAN = Color.rgb(103, 217, 235);
    private final List<Track> tracks = new ArrayList<>();
    private final List<Playlist> playlists = new ArrayList<>();
    private AlertDialog playlistDialog;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout list, tabs;
    private TextView now, timing, count;
    private Button toggle, nextTrack, previousTrack;
    private AlertDialog playerDialog;
    private TextView expandedTitle, expandedStatus;
    private Button expandedToggle;
    private ImageView expandedArtwork;
    private SeekBar seek;
    private int tab;
    private boolean dragging;
    private DownloadManager manager;
    private final Set<Track> resolvingDownloads = new HashSet<>();
    private static final java.util.concurrent.ExecutorService DOWNLOAD_RESOLVER =
        java.util.concurrent.Executors.newFixedThreadPool(2);
    private final Map<Long, Integer> downloadStates = new HashMap<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        manager = getSystemService(DownloadManager.class);
        tab = state == null ? 0 : state.getInt("tab");
        load();
        build();
        refreshDownloads();
        render();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("tab", tab);
    }
    @Override protected void onStart() { super.onStart(); handler.post(tick); }
    @Override protected void onStop() { handler.removeCallbacks(tick); super.onStop(); }

    private final Runnable tick = new Runnable() {
        private int ticks;
        @Override public void run() {
            updatePlayer();
            if (++ticks % 4 == 0) refreshDownloads();
            handler.postDelayed(this, 500);
        }
    };

    private void load() {
        try {
            JSONArray data = new JSONArray(getPreferences(MODE_PRIVATE).getString("tracks", "[]"));
            for (int i = 0; i < data.length(); i++) tracks.add(Track.read(data.getJSONObject(i)));
        } catch (JSONException error) { toast("تعذّر قراءة المكتبة المحفوظة"); }
        try {
            JSONArray stored = new JSONArray(getPreferences(MODE_PRIVATE).getString("playlists", "[]"));
            for (int i = 0; i < stored.length(); i++) playlists.add(Playlist.read(stored.getJSONObject(i)));
        } catch (JSONException error) { toast("تعذّر قراءة قوائم التشغيل"); }
    }
    private void savePlaylists() {
        JSONArray data = new JSONArray();
        try {
            for (Playlist playlist : playlists) data.put(playlist.write());
            getPreferences(MODE_PRIVATE).edit().putString("playlists", data.toString()).apply();
        } catch (JSONException error) { toast("تعذّر حفظ قوائم التشغيل"); }
    }
    private void save() {
        JSONArray data = new JSONArray();
        try {
            for (Track track : tracks) data.put(track.write());
            getPreferences(MODE_PRIVATE).edit().putString("tracks", data.toString()).apply();
        } catch (JSONException error) { toast("تعذّر حفظ المكتبة"); }
    }

    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }
    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }
    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }
    private Button button(String caption, boolean accent, Runnable click) {
        Button view = new Button(this);
        view.setAllCaps(false);
        view.setText(caption);
        view.setTextColor(accent ? BG : INK);
        view.setTextSize(14);
        view.setMinWidth(0);
        view.setMinimumWidth(0);
        view.setMinHeight(dp(48));
        view.setMinimumHeight(dp(48));
        view.setPadding(dp(9), 0, dp(9), 0);
        view.setBackground(new android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(0x408795ad), shape(accent ? CYAN : CARD, 14), null));
        view.setOnClickListener(v -> click.run());
        return view;
    }
    private void weighted(LinearLayout parent, View child, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMarginStart(dp(margin));
        parent.addView(child, params);
    }
    private void gap(LinearLayout layout, int height) {
        layout.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }
    private void build() {
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        LinearLayout root = vertical();
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top, bottom, left, right;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets pad = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout());
                top = pad.top; bottom = pad.bottom; left = pad.left; right = pad.right;
            } else {
                top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft(); right = insets.getSystemWindowInsetRight();
            }
            view.setPadding(left + dp(16), top + dp(10), right + dp(16), bottom + dp(10));
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();

        LinearLayout header = row();
        ImageView logo = new ImageView(this);
        logo.setImageResource(com.pulseplayer.mobile.R.drawable.pulse_logo);
        header.addView(logo, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout name = vertical();
        name.setPadding(dp(12), 0, dp(12), 0);
        TextView title = text("Pulse", 27, INK);
        title.setTypeface(null, Typeface.BOLD);
        name.addView(title);
        name.addView(text("مشغّل الصوت • " + OWNER, 12, MUTED));
        weighted(header, name, 0);
        header.addView(button("ⓘ", false, this::about));
        root.addView(header);
        gap(root, 12);
        LinearLayout discovery = vertical();
        GradientDrawable panel = new GradientDrawable(GradientDrawable.Orientation.TR_BL,
            new int[] {0xff2d2252, 0xff192842, 0xff102635});
        panel.setCornerRadius(dp(24));
        panel.setStroke(dp(1), 0xff3d4d70);
        discovery.setBackground(panel);
        discovery.setPadding(dp(13), dp(10), dp(13), dp(12));
        TextView discoverTitle = text("اكتشف صوتك", 20, INK);
        discoverTitle.setTypeface(null, Typeface.BOLD);
        discovery.addView(discoverTitle);
        discovery.addView(text("ابحث أونلاين، أو شغّل ملفاتك المحفوظة", 12, MUTED));
        gap(discovery, 7);
        LinearLayout search = row();
        weighted(search, button("YouTube", true, () -> searchPlatform("YouTube")), 0);
        weighted(search, button("SoundCloud", false, () -> searchPlatform("SoundCloud")), 8);
        discovery.addView(search);
        root.addView(discovery);
        gap(root, 7);
        LinearLayout add = row();
        weighted(add, button("＋ ملفات الهاتف", false, this::pick), 0);
        weighted(add, button("＋ رابط", false, this::linkDialog), 8);
        root.addView(add);
        gap(root, 9);
        count = text("", 13, MUTED);
        root.addView(count);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        list = vertical();
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        gap(root, 8);

        LinearLayout player = vertical();
        player.setBackground(shape(CARD, 18));
        player.setPadding(dp(12), dp(10), dp(12), dp(9));
        LinearLayout controls = row();
        now = text("لا يوجد مقطع قيد التشغيل", 15, INK);
        now.setSingleLine(true);
        now.setEllipsize(TextUtils.TruncateAt.END);
        weighted(controls, now, 0);
        previousTrack = button("⏮", false, () -> changeTrack(-1));
        previousTrack.setContentDescription("الأغنية السابقة");
        controls.addView(previousTrack, new LinearLayout.LayoutParams(dp(44), dp(48)));
        toggle = button("▶", true, () -> {
            if (PlaybackService.active != null) PlaybackService.active.toggle();
            updatePlayer();
        });
        toggle.setContentDescription("تشغيل أو إيقاف مؤقت");
        controls.addView(toggle, new LinearLayout.LayoutParams(dp(48), dp(48)));
        nextTrack = button("⏭", false, () -> changeTrack(1));
        nextTrack.setContentDescription("الأغنية التالية");
        controls.addView(nextTrack, new LinearLayout.LayoutParams(dp(44), dp(48)));
        Button stop = button("■", false, () -> stopService(new Intent(this, PlaybackService.class)));
        stop.setContentDescription("إيقاف التشغيل");
        controls.addView(stop, new LinearLayout.LayoutParams(dp(48), dp(48)));
        player.addView(controls);
        seek = new SeekBar(this);
        seek.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (user) timing.setText(clock(value) + " / " + clock(bar.getMax()));
            }
            public void onStartTrackingTouch(SeekBar bar) { dragging = true; }
            public void onStopTrackingTouch(SeekBar bar) {
                if (PlaybackService.active != null) PlaybackService.active.seek(bar.getProgress());
                dragging = false;
            }
        });
        player.addView(seek);
        timing = text("0:00 / 0:00", 12, MUTED);
        timing.setTextDirection(View.TEXT_DIRECTION_LTR);
        timing.setGravity(Gravity.CENTER);
        player.addView(timing);
        LinearLayout tools = row();
        weighted(tools, button("المشغّل الآن", false, this::showPlayer), 0);
        weighted(tools, button("السرعة", false, this::chooseSpeed), 8);
        player.addView(tools);
        root.addView(player);
        gap(root, 8);
        tabs = row();
        root.addView(tabs);
    }

    private void render() {
        tabs.removeAllViews();
        String[] labels = {"المكتبة", "المفضلة", "بدون نت", "قوائمي"};
        for (int i = 0; i < labels.length; i++) {
            int index = i;
            Button tabButton = button(labels[i], i == tab, () -> { tab = index; render(); });
            tabButton.setTextSize(12);
            tabButton.setSelected(i == tab);
            weighted(tabs, tabButton, i == 0 ? 0 : 6);
        }
        list.removeAllViews();
        if (tab == 3) { renderPlaylists(); return; }
        int shown = 0;
        for (Track track : tracks) {
            if ((tab == 1 && !track.favorite) ||
                (tab == 2 && !availableOffline(track) && track.downloadId == 0 && !resolvingDownloads.contains(track))) continue;
            shown++;
            list.addView(trackCard(track));
        }
        count.setText(labels[tab] + "  •  " + shown);
        if (shown == 0) {
            TextView empty = text(tab == 0 ? "أضف ملف صوت أو رابط للبدء"
                : tab == 1 ? "اضغط على القلب بجانب أي مقطع لإضافته هنا"
                : "نزّل مقطعًا من قائمة الخيارات للاستماع من غير إنترنت", 15, MUTED);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(8), dp(28), dp(8), dp(20));
            list.addView(empty);
        }
    }

    private void renderPlaylists() {
        count.setText("قوائم التشغيل  •  " + playlists.size());
        list.addView(button("＋ إنشاء قائمة تشغيل", true, () -> createPlaylist(null, null)));
        if (playlists.isEmpty()) {
            TextView empty = text("أنشئ أول قائمة، أو أضف أغنية لقائمة من زر ⋮ بجانبها.", 14, MUTED);
            empty.setPadding(dp(8), dp(20), dp(8), dp(12));
            list.addView(empty);
            return;
        }
        for (Playlist playlist : playlists) {
            LinearLayout card = row();
            card.setPadding(dp(12), dp(8), dp(12), dp(8));
            card.setBackground(shape(CARD, 16));
            LinearLayout detail = vertical();
            TextView name = text(playlist.name, 16, INK);
            name.setTypeface(null, Typeface.BOLD);
            detail.addView(name);
            detail.addView(text(playlistTracks(playlist, false).size() + " أغنية", 12, MUTED));
            weighted(card, detail, 0);
            detail.setOnClickListener(v -> showPlaylist(playlist));
            card.addView(button("▶", true, () -> playPlaylist(playlist, false)));
            card.addView(button("⋮", false, () -> playlistOptions(playlist)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(8);
            list.addView(card, params);
        }
    }

    private List<Track> playlistTracks(Playlist playlist, boolean offlineOnly) {
        List<Track> result = new ArrayList<>();
        for (String uri : playlist.uris) {
            Track track = findTrack(uri);
            if (track != null && (!offlineOnly || availableOffline(track))) result.add(track);
        }
        return result;
    }

    private void playPlaylist(Playlist playlist, boolean offlineOnly) {
        List<Track> order = playlistTracks(playlist, offlineOnly);
        if (order.isEmpty()) {
            toast(offlineOnly ? "لا توجد أغاني متاحة بدون إنترنت في هذه القائمة" : "هذه القائمة فارغة");
            return;
        }
        if (playlistDialog != null) playlistDialog.dismiss();
        playQueue(order, order.get(0));
    }

    private void addToPlaylist(Track track) {
        List<String> labels = new ArrayList<>();
        labels.add("＋ إنشاء قائمة جديدة");
        for (Playlist playlist : playlists) labels.add(playlist.name);
        new AlertDialog.Builder(this).setTitle("إضافة إلى قائمة تشغيل")
            .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                if (which == 0) { createPlaylist(track, null); return; }
                Playlist playlist = playlists.get(which - 1);
                if (!playlist.uris.contains(track.uri)) playlist.uris.add(track.uri);
                savePlaylists();
                toast("تمت الإضافة إلى " + playlist.name);
                if (tab == 3) render();
            }).show();
    }

    private void createPlaylist(Track initial, Runnable after) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("مثال: المفضلة في السيارة");
        input.setPadding(dp(20), dp(14), dp(20), dp(14));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("إنشاء قائمة تشغيل")
            .setView(input).setPositiveButton("إنشاء", null)
            .setNegativeButton("إلغاء", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty() || name.length() > 80) {
                input.setError("اكتب اسمًا من 1 إلى 80 حرفًا"); return;
            }
            Playlist playlist = new Playlist(name);
            if (initial != null) playlist.uris.add(initial.uri);
            playlists.add(playlist);
            savePlaylists();
            dialog.dismiss();
            if (tab == 3) render();
            toast("تم إنشاء قائمة " + name);
            if (after != null) after.run();
        }));
        dialog.show();
    }

    private void playlistOptions(Playlist playlist) {
        String[] options = {"فتح القائمة", "تشغيل الكل", "تشغيل بدون نت", "إضافة / حذف أغاني", "تغيير الاسم", "حذف القائمة"};
        new AlertDialog.Builder(this).setTitle(playlist.name).setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: showPlaylist(playlist); break;
                case 1: playPlaylist(playlist, false); break;
                case 2: playPlaylist(playlist, true); break;
                case 3: editPlaylistTracks(playlist); break;
                case 4: renamePlaylist(playlist); break;
                case 5: deletePlaylist(playlist); break;
            }
        }).show();
    }

    private void renamePlaylist(Playlist playlist) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(playlist.name);
        input.selectAll();
        new AlertDialog.Builder(this).setTitle("تغيير اسم القائمة").setView(input)
            .setPositiveButton("حفظ", (dialog, which) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty() || name.length() > 80) { toast("اسم القائمة غير صالح"); return; }
                playlist.name = name;
                savePlaylists(); render();
            }).setNegativeButton("إلغاء", null).show();
    }

    private void deletePlaylist(Playlist playlist) {
        new AlertDialog.Builder(this).setTitle("حذف القائمة؟")
            .setMessage("سيتم حذف قائمة «" + playlist.name + "» فقط، ولن تُحذف الأغاني من المكتبة أو الهاتف.")
            .setPositiveButton("حذف", (dialog, which) -> {
                if (playlistDialog != null) playlistDialog.dismiss();
                playlists.remove(playlist);
                savePlaylists(); render();
            }).setNegativeButton("إلغاء", null).show();
    }

    private void editPlaylistTracks(Playlist playlist) {
        if (tracks.isEmpty()) { toast("أضف أغاني للمكتبة أولًا"); return; }
        String[] names = new String[tracks.size()];
        boolean[] selected = new boolean[tracks.size()];
        for (int i = 0; i < tracks.size(); i++) {
            Track track = tracks.get(i);
            names[i] = track.title + (availableOffline(track) ? " • بدون نت" : "");
            selected[i] = playlist.uris.contains(track.uri);
        }
        new AlertDialog.Builder(this).setTitle("أغاني " + playlist.name)
            .setMultiChoiceItems(names, selected, (dialog, which, checked) -> selected[which] = checked)
            .setPositiveButton("حفظ", (dialog, which) -> {
                // Preserve existing order; newly selected songs are appended in library order.
                Set<String> retained = new HashSet<>();
                for (int i = 0; i < tracks.size(); i++) if (selected[i]) retained.add(tracks.get(i).uri);
                playlist.uris.removeIf(uri -> !retained.contains(uri));
                for (int i = 0; i < tracks.size(); i++) {
                    if (selected[i] && !playlist.uris.contains(tracks.get(i).uri))
                        playlist.uris.add(tracks.get(i).uri);
                }
                savePlaylists(); render();
                if (playlistDialog != null && playlistDialog.isShowing()) showPlaylist(playlist);
            }).setNegativeButton("إلغاء", null).show();
    }

    private void movePlaylistSong(Playlist playlist, int position, int direction) {
        int target = position + direction;
        if (target < 0 || target >= playlist.uris.size()) return;
        Collections.swap(playlist.uris, position, target);
        savePlaylists();
        showPlaylist(playlist);
    }

    private void removePlaylistSong(Playlist playlist, Track track) {
        playlist.uris.remove(track.uri);
        savePlaylists();
        showPlaylist(playlist);
    }

    private void showPlaylist(Playlist playlist) {
        if (playlistDialog != null) playlistDialog.dismiss();
        LinearLayout content = vertical();
        content.setPadding(dp(12), dp(10), dp(12), dp(10));
        TextView title = text(playlist.name, 19, INK);
        title.setTypeface(null, Typeface.BOLD);
        content.addView(title);
        gap(content, 7);
        LinearLayout actions = row();
        weighted(actions, button("▶ تشغيل الكل", true, () -> playPlaylist(playlist, false)), 0);
        weighted(actions, button("▶ بدون نت", false, () -> playPlaylist(playlist, true)), 6);
        content.addView(actions);
        gap(content, 6);
        content.addView(button("＋ إضافة / تعديل الأغاني", false, () -> editPlaylistTracks(playlist)));
        gap(content, 12);
        List<Track> songs = playlistTracks(playlist, false);
        if (songs.isEmpty()) content.addView(text("القائمة فارغة. أضف أغاني من المكتبة.", 14, MUTED));
        for (int index = 0; index < playlist.uris.size(); index++) {
            Track track = findTrack(playlist.uris.get(index));
            if (track == null) continue;
            final int position = index;
            LinearLayout entry = row();
            TextView name = text((index + 1) + ". " + track.title, 13, INK);
            name.setMaxLines(2);
            weighted(entry, name, 0);
            name.setOnClickListener(v -> {
                List<Track> order = playlistTracks(playlist, false);
                playQueue(order, track);
                if (playlistDialog != null) playlistDialog.dismiss();
            });
            entry.addView(button("↑", false, () -> movePlaylistSong(playlist, position, -1)));
            entry.addView(button("↓", false, () -> movePlaylistSong(playlist, position, 1)));
            entry.addView(button("×", false, () -> removePlaylistSong(playlist, track)));
            content.addView(entry);
            gap(content, 5);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        playlistDialog = new AlertDialog.Builder(this).setView(scroll)
            .setNegativeButton("إغلاق", null).create();
        AlertDialog shown = playlistDialog;
        shown.setOnDismissListener(dialog -> { if (playlistDialog == shown) playlistDialog = null; });
        shown.show();
    }

    private View trackCard(Track track) {
        LinearLayout card = row();
        card.setBackground(shape(CARD, 16));
        card.setPadding(dp(8), dp(5), dp(8), dp(5));
        ImageView art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setBackground(shape(0xff26344f, 12));
        art.setClipToOutline(true);
        art.setContentDescription("تشغيل " + track.title);
        art.setOnClickListener(v -> play(track));
        card.addView(art, new LinearLayout.LayoutParams(dp(52), dp(52)));
        Artwork.show(art, track.artwork);
        LinearLayout title = vertical();
        title.setPadding(dp(9), 0, dp(4), 0);
        TextView name = text(track.title, 15, INK);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        title.addView(name);
        int status = downloadStates.getOrDefault(track.downloadId, 0);
        String provider = Track.provider(track.uri);
        String label = resolvingDownloads.contains(track) ? "جارٍ تجهيز التنزيل"
            : status == DownloadManager.STATUS_SUCCESSFUL ? "محفوظ على الهاتف"
            : status == DownloadManager.STATUS_FAILED ? "التنزيل غير مكتمل"
            : track.downloadId != 0 ? "جارٍ التنزيل"
            : !provider.isEmpty() ? provider : Track.https(track.uri) ? "بث مباشر" : "ملف على الهاتف";
        title.addView(text(track.author.isEmpty() ? label : label + " • " + track.author, 12, MUTED));
        weighted(card, title, 0);
        title.setOnClickListener(v -> play(track));
        Button heart = button(track.favorite ? "♥" : "♡", false,
            () -> { track.favorite = !track.favorite; save(); render(); });
        heart.setContentDescription(track.favorite ? "إزالة من المفضلة" : "إضافة للمفضلة");
        card.addView(heart, new LinearLayout.LayoutParams(dp(48), dp(48)));
        Button options = button("⋮", false, () -> options(track));
        options.setContentDescription("خيارات المقطع");
        card.addView(options, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        card.setLayoutParams(params);
        return card;
    }

    private void options(Track track) {
        List<String> choices = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (Track.downloadable(track.uri) || !Track.provider(track.uri).isEmpty()) {
            choices.add("تنزيل ملف الصوت");
            actions.add(() -> download(track));
        }
        if (track.downloadId != 0) {
            choices.add("حذف التنزيل");
            actions.add(() -> new AlertDialog.Builder(this).setMessage("حذف النسخة المنزّلة من الهاتف؟")
                .setPositiveButton("حذف", (dialog, which) -> {
                    manager.remove(track.downloadId);
                    track.downloadId = 0;
                    save();
                    render();
                }).setNegativeButton("إلغاء", null).show());
        }
        choices.add("إضافة إلى قائمة تشغيل");
        actions.add(() -> addToPlaylist(track));
        if (track.downloadId == 0) {
            choices.add("إزالة من المكتبة");
            actions.add(() -> {
                tracks.remove(track);
                for (Playlist playlist : playlists) playlist.uris.remove(track.uri);
                savePlaylists();
                save();
                render();
            });
        }
        new AlertDialog.Builder(this).setTitle(track.title)
            .setItems(choices.toArray(new String[0]), (dialog, which) -> actions.get(which).run()).show();
    }

    private void pick() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, PICK); }
        catch (ActivityNotFoundException error) { toast("منتقي الملفات غير متاح"); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) return;
        if (request == PLATFORM) {
            String link = data.getStringExtra(PlatformActivity.MEDIA);
            String title = data.getStringExtra(PlatformActivity.TITLE);
            if (link != null) {
                Track track = savePlatform(link, title, data.getStringExtra(PlatformActivity.AUTHOR));
                String art = data.getStringExtra(PlatformActivity.ARTWORK);
                if (art != null && !art.isEmpty()) { track.artwork = art; save(); render(); }
                if (data.getBooleanExtra(PlatformActivity.PLAY, false)) playSearchResults(track, data);
            }
            return;
        }
        if (request != PICK) return;
        ClipData files = data.getClipData();
        if (files != null) {
            for (int i = 0; i < files.getItemCount(); i++) importFile(files.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            importFile(data.getData());
        }
        tab = 0;
        save();
        render();
    }
    private void importFile(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException error) { toast("تعذّر حفظ صلاحية قراءة الملف"); return; }
        String name = "ملف صوتي";
        try (Cursor cursor = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && cursor.getString(0) != null) name = cursor.getString(0);
        } catch (RuntimeException ignored) {}
        add(uri.toString(), name);
    }
    private void add(String uri, String title) {
        if (tracks.stream().noneMatch(track -> track.uri.equals(uri))) tracks.add(new Track(uri, title));
    }
    private void searchPlatform(String provider) {
        EditText query = new EditText(this);
        query.setHint("اسم المقطع أو القناة");
        query.setSingleLine(true);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("بحث " + provider).setView(query)
            .setPositiveButton("بحث", null).setNegativeButton("إلغاء", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = query.getText().toString().trim();
            if (value.isEmpty()) { query.setError("اكتب كلمة للبحث"); return; }
            openPlatform(new Intent(this, PlatformActivity.class)
                .putExtra(PlatformActivity.PROVIDER, provider).putExtra(PlatformActivity.SEARCH, value));
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void openPlatform(Intent intent) {
        startActivityForResult(intent, PLATFORM);
    }

    private Track savePlatform(String link, String title, String author) {
        String fallback = Track.provider(link);
        Track track = tracks.stream().filter(item -> link.equals(PlatformLinks.media(item.uri))).findFirst()
            .orElseGet(() -> {
                Track added = new Track(link, title == null || title.isEmpty() ? fallback : title);
                tracks.add(added);
                return added;
            });
        if (author != null && !author.isEmpty()) track.author = author;
        if (title != null && !title.isEmpty() && !title.equals(fallback)) track.title = title;
        tab = 0;
        save();
        render();
        refreshPlatformTitle(track, link);
        return track;
    }

    private void refreshPlatformTitle(Track track, String link) {
        if (!track.title.equals(Track.provider(link))) return;
        PlatformMetadata.lookup(link, (name, creator) -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || !tracks.contains(track) || name.isEmpty()) return;
            track.title = name;
            track.author = creator;
            save();
            render();
        }));
    }

    private void chooseSpeed() {
        PlaybackService service = PlaybackService.active;
        if (service == null) { toast("شغّل ملفًا صوتيًا أولًا"); return; }
        float[] speeds = {0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
        String[] labels = {"0.75×", "1×", "1.25×", "1.5×", "1.75×", "2×"};
        int selected = 1;
        for (int i = 0; i < speeds.length; i++) if (speeds[i] == service.speed()) selected = i;
        new AlertDialog.Builder(this).setTitle("سرعة تشغيل الصوت")
            .setSingleChoiceItems(labels, selected, (dialog, index) -> {
                if (PlaybackService.active != null) PlaybackService.active.setSpeed(speeds[index]);
                updatePlayer();
                dialog.dismiss();
            }).setNegativeButton("إلغاء", null).show();
    }

    private void showPlayer() {
        LinearLayout panel = vertical();
        panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[] {0xff253b63, BG, 0xff181e34});
        panel.setBackground(background);
        panel.setPadding(dp(18), dp(18), dp(18), dp(18));
        TextView label = text("PULSE   /   المشغّل الآن", 15, CYAN);
        label.setGravity(Gravity.CENTER);
        panel.addView(label);
        gap(panel, 14);
        expandedArtwork = new ImageView(this);
        expandedArtwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        expandedArtwork.setBackground(shape(0xff202c49, 24));
        expandedArtwork.setClipToOutline(true);
        LinearLayout.LayoutParams artworkSize = new LinearLayout.LayoutParams(-1, dp(220));
        artworkSize.gravity = Gravity.CENTER;
        panel.addView(expandedArtwork, artworkSize);
        gap(panel, 12);
        expandedTitle = text("", 22, INK);
        expandedTitle.setGravity(Gravity.CENTER);
        expandedTitle.setMaxLines(2);
        expandedTitle.setEllipsize(TextUtils.TruncateAt.END);
        panel.addView(expandedTitle);
        expandedStatus = text("", 14, MUTED);
        expandedStatus.setGravity(Gravity.CENTER);
        panel.addView(expandedStatus);
        gap(panel, 16);
        LinearLayout controls = row();
        weighted(controls, button("⏮ السابق", false, () -> changeTrack(-1)), 0);
        expandedToggle = button("تشغيل", true, () -> {
            if (PlaybackService.active != null) PlaybackService.active.toggle();
            updatePlayer();
        });
        weighted(controls, expandedToggle, 6);
        weighted(controls, button("التالي ⏭", false, () -> changeTrack(1)), 6);
        panel.addView(controls);
        gap(panel, 8);
        LinearLayout seekTools = row();
        weighted(seekTools, button("−15 ثانية", false, () -> jump(-15000)), 0);
        weighted(seekTools, button("+15 ثانية", false, () -> jump(15000)), 6);
        panel.addView(seekTools);
        gap(panel, 8);
        panel.addView(button("سرعة التشغيل", false, this::chooseSpeed));
        ScrollView details = new ScrollView(this);
        details.addView(panel);
        playerDialog = new AlertDialog.Builder(this).setView(details).setPositiveButton("إغلاق", null).create();
        playerDialog.setOnDismissListener(dialog -> {
            playerDialog = null;
            expandedTitle = expandedStatus = null;
            expandedToggle = null;
            expandedArtwork = null;
        });
        playerDialog.show();
        updatePlayer();
    }

    private void jump(int delta) {
        PlaybackService service = PlaybackService.active;
        if (service != null) service.seek(service.position() + delta);
        updatePlayer();
    }

    private void linkDialog() {
        LinearLayout fields = vertical();
        fields.setPadding(dp(20), 0, dp(20), 0);
        EditText url = new EditText(this);
        url.setHint("https://…");
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setTextDirection(View.TEXT_DIRECTION_LTR);
        url.setSingleLine();
        fields.addView(url);
        EditText name = new EditText(this);
        name.setHint("اسم المقطع (اختياري)");
        name.setSingleLine();
        fields.addView(name);
        fields.addView(text("رابط صوت مباشر، أو رابط مقطع YouTube / SoundCloud.", 13, MUTED));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("إضافة رابط").setView(fields)
            .setPositiveButton("حفظ", null).setNegativeButton("إلغاء", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String link = url.getText().toString().trim();
            if (!Track.https(link)) { url.setError("اكتب رابط HTTPS صالح"); return; }
            String label = name.getText().toString().trim();
            if (label.isEmpty()) {
                String provider = Track.provider(link);
                String file = Uri.parse(link).getLastPathSegment();
                label = !provider.isEmpty() ? provider : file != null ? file : Uri.parse(link).getHost();
            }
            if (!Track.provider(link).isEmpty()) {
                String media = PlatformLinks.media(link);
                if (media.isEmpty()) { url.setError("اكتب رابط مقطع محدد أو استخدم البحث"); return; }
                savePlatform(media, label, null);
            } else {
                add(link, label);
                tab = 0;
                save();
                render();
            }
            dialog.dismiss();
        }));
        dialog.show();
    }
    private void changeTrack(int direction) {
        PlaybackService service = PlaybackService.active;
        if (service == null) return;
        service.skip(direction);
        updatePlayer();
    }

    private boolean availableOffline(Track track) {
        if (!Track.https(track.uri)) return true;
        if (track.downloadId == 0) return false;
        try { return manager.getUriForDownloadedFile(track.downloadId) != null; }
        catch (RuntimeException unavailable) { return false; }
    }

    private List<Track> currentOrder() {
        List<Track> order = new ArrayList<>();
        for (Track item : tracks) {
            if (tab == 1 && !item.favorite) continue;
            if (tab == 2 && !availableOffline(item)) continue;
            order.add(item);
        }
        return order;
    }

    private void play(Track track) {
        List<Track> order = currentOrder();
        if (tab == 2 && !availableOffline(track)) {
            toast("التنزيل لم يكتمل بعد. اختَر الملف بعد اكتماله."); return;
        }
        if (!order.contains(track)) order.add(track);
        playQueue(order, track);
    }

    private void playSearchResults(Track selected, Intent result) {
        String encoded = result.getStringExtra(PlatformActivity.QUEUE);
        if (encoded == null || encoded.isEmpty()) { play(selected); return; }
        List<Track> order = new ArrayList<>();
        try {
            JSONArray entries = new JSONArray(encoded);
            for (int i = 0; i < entries.length(); i++) {
                Track item = Track.read(entries.getJSONObject(i));
                Track libraryItem = findTrack(item.uri);
                order.add(libraryItem == null ? item : libraryItem);
            }
        } catch (JSONException error) { play(selected); return; }
        int index = result.getIntExtra(PlatformActivity.QUEUE_INDEX, -1);
        if (index < 0 || index >= order.size()) { play(selected); return; }
        playQueue(order, order.get(index));
    }

    private void playQueue(List<Track> order, Track selected) {
        if (order.isEmpty() || selected == null) return;
        JSONArray entries = new JSONArray();
        int index = -1;
        try {
            for (Track item : order) {
                if (item == selected) index = entries.length();
                entries.put(item.write());
            }
        } catch (JSONException error) { toast("تعذّر تجهيز قائمة التشغيل"); return; }
        if (index < 0) { toast("المقطع غير موجود في القائمة"); return; }
        // Pass a short token rather than a potentially oversized Android Binder Intent.
        String token = UUID.randomUUID().toString();
        boolean stored = getSharedPreferences("pulse_queue_handoff", MODE_PRIVATE).edit()
            .putString(token, entries.toString()).commit();
        if (!stored) { toast("تعذّر تجهيز طابور التشغيل"); return; }
        startForegroundService(new Intent(this, PlaybackService.class).setAction("play")
            .putExtra("queue_token", token).putExtra("index", index));
    }

    private Track findTrack(String uri) {
        for (Track item : tracks) if (item.uri.equals(uri)) return item;
        return null;
    }

    private void download(Track track) {
        int status = downloadStates.getOrDefault(track.downloadId, 0);
        if (resolvingDownloads.contains(track) || track.downloadId != 0 && status != DownloadManager.STATUS_FAILED) {
            toast("التنزيل مسجّل بالفعل"); return;
        }
        if (!Track.provider(track.uri).isEmpty()) {
            String media = PlatformLinks.media(track.uri);
            if (media.isEmpty()) { toast("الرابط لا يشير إلى مقطع محدد"); return; }
            resolvingDownloads.add(track);
            tab = 2;
            render();
            DOWNLOAD_RESOLVER.execute(() -> {
                StreamResolver.DownloadSource source = null;
                try { source = StreamResolver.downloadSource(media); }
                catch (Exception error) { android.util.Log.w("Pulse", "Offline resolver: " + error.getClass().getSimpleName()); }
                StreamResolver.DownloadSource resolved = source;
                runOnUiThread(() -> {
                    resolvingDownloads.remove(track);
                    if (isDestroyed() || !tracks.contains(track)) return;
                    if (resolved == null) {
                        toast("لا يتوفر ملف صوت قابل للتنزيل لهذا المقطع حاليًا");
                        render();
                    } else enqueueDownload(track, resolved.url, resolved.extension, true);
                });
            });
        } else if (Track.downloadable(track.uri)) {
            String file = Uri.parse(track.uri).getLastPathSegment();
            enqueueDownload(track, track.uri, file.substring(file.lastIndexOf('.')), false);
        }
    }

    private void enqueueDownload(Track track, String url, String extension, boolean platform) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                .setTitle(track.title)
                .setDescription("Pulse · الاستماع من غير إنترنت")
                .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS,
                    UUID.randomUUID() + extension)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            if (platform) request.addRequestHeader("User-Agent", StreamResolver.AGENT);
            long previous = track.downloadId;
            track.downloadId = manager.enqueue(request);
            save();
            if (previous != 0) manager.remove(previous);
            refreshDownloads();
            render();
            toast("بدأ تنزيل المقطع");
        } catch (RuntimeException error) {
            toast("تعذّر بدء التنزيل");
            render();
        }
    }

    private void refreshDownloads() {
        long[] ids = tracks.stream().mapToLong(track -> track.downloadId).filter(id -> id != 0).toArray();
        Map<Long, Integer> latest = new HashMap<>();
        for (long id : ids) latest.put(id, DownloadManager.STATUS_FAILED);
        if (ids.length > 0) {
            try (Cursor result = manager.query(new DownloadManager.Query().setFilterById(ids))) {
                while (result != null && result.moveToNext()) {
                    latest.put(result.getLong(result.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)),
                        result.getInt(result.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)));
                }
            } catch (RuntimeException ignored) { return; }
        }
        if (!latest.equals(downloadStates)) {
            downloadStates.clear();
            downloadStates.putAll(latest);
            render();
        }
    }
    private void updatePlayer() {
        PlaybackService service = PlaybackService.active;
        if (!PlaybackService.lastError.isEmpty()) {
            toast("تعذّر تشغيل " + PlaybackService.lastError);
            PlaybackService.lastError = "";
        }
        boolean running = service != null && (service.playing() || service.pending());
        toggle.setEnabled(service != null);
        previousTrack.setEnabled(service != null && service.hasPrevious());
        nextTrack.setEnabled(service != null && service.hasNext());
        toggle.setText(running ? "Ⅱ" : "▶");
        now.setText(service == null ? "لا يوجد مقطع قيد التشغيل"
            : service.pending() ? "جارٍ التشغيل… " + service.title : service.title);
        int length = service == null ? 0 : service.duration();
        int progress = service == null ? 0 : service.position();
        if (expandedTitle != null) {
            Artwork.show(expandedArtwork, service == null ? "" : service.artwork);
            expandedTitle.setText(service == null ? "اختَر مقطعًا من المكتبة" : service.title);
            expandedStatus.setText(clock(progress) + " / " + clock(length)
                + "   •   " + (service == null ? "1" : String.valueOf(service.speed())) + "×"
                + (service == null ? "" : "   •   " + (service.queuePosition() + 1) + "/" + service.queueSize()));
            expandedToggle.setEnabled(service != null);
            expandedToggle.setText(running ? "إيقاف مؤقت" : "تشغيل");
        }
        if (!dragging) {
            seek.setEnabled(length > 0);
            seek.setMax(Math.max(1, length));
            seek.setProgress(progress);
            timing.setText(clock(progress) + " / " + clock(length));
        }
    }
    private static String clock(int ms) {
        int sec = ms / 1000;
        return String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60);
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    private void about() {
        new AlertDialog.Builder(this).setTitle("Pulse · " + OWNER)
            .setMessage("Pulse Android 0.7 Beta\nتصميم وتطوير محمود الجمصي\n© 2026 Mahmoud Elgamsy\n\nالسعودية  \u200e0562284194\nمصر  \u200e01017230727\n\nالبحث في YouTube وSoundCloud وتشغيل الصوت داخل Pulse مع التحكم من الإشعار.")
            .setPositiveButton("تمام", null).show();
    }
}

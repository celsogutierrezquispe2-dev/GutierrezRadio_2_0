package com.gutierrez.radio;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/** Interfaz completamente nativa: no necesita internet, WebView ni bibliotecas externas. */
public class MainActivity extends Activity {
    private static final int OPEN_MUSIC = 101;
    private static final int BG = Color.rgb(2, 12, 32);
    private static final int PANEL = Color.rgb(6, 28, 64);
    private static final int PANEL_ALT = Color.rgb(9, 40, 84);
    private static final int CYAN = Color.rgb(9, 219, 245);
    private static final int BLUE = Color.rgb(28, 123, 255);
    private static final int WHITE = Color.WHITE;
    private static final int DIM = Color.rgb(177, 202, 228);

    private SharedPreferences prefs;
    private TextView currentTitle, currentState, trackCount, elapsed, total, intervalState;
    private Button playButton, shuffleButton;
    private SeekBar progress, volume;
    private final Button[] intervals = new Button[4];
    private LinearLayout playlistView;
    private ScrollView pageScroll;
    private TextView toggleAnnouncement;
    private boolean touchingProgress;
    private int lastIndex = Integer.MIN_VALUE;
    private boolean showingPlaylist = false;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            refreshState();
            uiHandler.postDelayed(this, 800);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(RadioService.PREFS_NAME, MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setupScreen();
        drawPlaylist();
        refreshState();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 42);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        uiHandler.removeCallbacks(refreshTask);
        uiHandler.post(refreshTask);
    }

    @Override protected void onPause() {
        uiHandler.removeCallbacks(refreshTask);
        super.onPause();
    }

    private void setupScreen() {
        pageScroll = new ScrollView(this);
        pageScroll.setBackgroundColor(BG);
        pageScroll.setFillViewport(true);
        LinearLayout page = column();
        page.setPadding(dp(15), dp(18), dp(15), dp(35));
        pageScroll.addView(page);
        setContentView(pageScroll);

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(header);
        ImageView badge = new ImageView(this);
        badge.setImageResource(R.drawable.ic_launcher);
        badge.setScaleType(ImageView.ScaleType.CENTER_CROP);
        badge.setContentDescription("Logotipo de Gutiérrez Radio");
        header.addView(badge, new LinearLayout.LayoutParams(dp(70), dp(70)));
        LinearLayout labels = column();
        labels.setPadding(dp(12), 0, 0, 0);
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        labels.addView(label("Gutiérrez Radio", 23, WHITE, true));
        currentState = label("●  Listo para reproducir", 12, CYAN, false);
        labels.addView(currentState);
        gap(page, 15);

        LinearLayout player = panel();
        page.addView(player);
        LinearLayout trackRow = row();
        trackRow.setGravity(Gravity.CENTER_VERTICAL);
        player.addView(trackRow);
        ImageView cover = new ImageView(this);
        cover.setImageResource(R.drawable.ic_launcher);
        cover.setContentDescription("Portada de Gutiérrez Radio");
        trackRow.addView(cover, new LinearLayout.LayoutParams(dp(102), dp(102)));
        LinearLayout trackLabels = column();
        trackLabels.setPadding(dp(14), 0, 0, 0);
        trackRow.addView(trackLabels, new LinearLayout.LayoutParams(0, -2, 1));
        trackLabels.addView(label("♫  EN REPRODUCCIÓN", 11, CYAN, true));
        gap(trackLabels, 10);
        currentTitle = label("Elige tus canciones", 18, WHITE, true);
        currentTitle.setMaxLines(3);
        trackLabels.addView(currentTitle);
        gap(trackLabels, 8);
        trackLabels.addView(label("Tu música • sin internet", 12, DIM, false));
        gap(player, 15);

        LinearLayout wave = row();
        wave.setGravity(Gravity.CENTER);
        int[] heights = {12,27,42,22,31,50,15,36,27,54,35,17,43,26,38,14,44,26,11,35,47,25};
        for (int h : heights) {
            View bar = new View(this);
            bar.setBackground(shape(h % 2 == 0 ? CYAN : BLUE, 3, 0));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(h), 1);
            bp.leftMargin = dp(2);
            bp.rightMargin = dp(2);
            wave.addView(bar, bp);
        }
        player.addView(wave, new LinearLayout.LayoutParams(-1, dp(56)));
        gap(player, 6);
        progress = new SeekBar(this);
        progress.setMax(1000);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));
        progress.setThumbTintList(android.content.res.ColorStateList.valueOf(CYAN));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL_ALT));
        progress.setContentDescription("Posición de la canción");
        player.addView(progress);
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar bar) { touchingProgress = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                int dur = prefs.getInt(RadioService.KEY_DURATION, 0);
                if (dur > 0) command(RadioService.ACTION_SEEK, dur * bar.getProgress() / 1000);
                touchingProgress = false;
            }
        });
        LinearLayout times = row();
        player.addView(times);
        elapsed = label("0:00", 12, DIM, false);
        total = label("0:00", 12, DIM, false);
        times.addView(elapsed, new LinearLayout.LayoutParams(0, -2, 1));
        times.addView(total);
        gap(player, 15);
        LinearLayout controls = row();
        controls.setGravity(Gravity.CENTER);
        player.addView(controls);
        addTransport(controls, "⏮", "Anterior", false, () -> command(RadioService.ACTION_PREVIOUS));
        addTransport(controls, "▶", "Reproducir", false, () -> command(RadioService.ACTION_TOGGLE));
        playButton = lastTransportButton;
        addTransport(controls, "Ⅱ", "Pausar", true, () -> command(RadioService.ACTION_TOGGLE));
        addTransport(controls, "⏭", "Siguiente", false, () -> command(RadioService.ACTION_NEXT));
        addTransport(controls, "■", "Detener", false, () -> command(RadioService.ACTION_STOP));
        gap(page, 13);

        LinearLayout library = panel();
        page.addView(library);
        library.addView(label("▣  Biblioteca musical", 18, WHITE, true));
        gap(library, 12);
        Button choose = actionButton("＋  Agregar canciones del celular");
        choose.setOnClickListener(v -> pickSongs());
        library.addView(choose);
        gap(library, 8);
        LinearLayout actions = row();
        library.addView(actions);
        Button playlistButton = miniButton("☷  Ver playlist");
        actions.addView(playlistButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        playlistButton.setOnClickListener(v -> {
            showingPlaylist = !showingPlaylist;
            playlistView.setVisibility(showingPlaylist ? View.VISIBLE : View.GONE);
            playlistButton.setText(showingPlaylist ? "▲  Ocultar lista" : "☷  Ver playlist");
            if (showingPlaylist) {
                uiHandler.postDelayed(() -> pageScroll.smoothScrollTo(0, playlistView.getTop()), 150);
            }
        });
        horizGap(actions, 8);
        shuffleButton = miniButton("⇄  Aleatorio: NO");
        actions.addView(shuffleButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        shuffleButton.setOnClickListener(v -> {
            boolean enabled = !prefs.getBoolean(RadioService.KEY_SHUFFLE, false);
            prefs.edit().putBoolean(RadioService.KEY_SHUFFLE, enabled).apply();
            updateSettingLabels();
        });
        gap(library, 10);
        trackCount = label("0 canciones guardadas", 12, DIM, false);
        library.addView(trackCount);
        playlistView = column();
        playlistView.setVisibility(View.GONE);
        gap(library, 6);
        library.addView(playlistView);
        gap(page, 13);

        LinearLayout announcer = panel();
        page.addView(announcer);
        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        announcer.addView(top);
        top.addView(label("◷  Anuncio automático de hora", 16, WHITE, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        toggleAnnouncement = label("ACTIVO", 12, CYAN, true);
        toggleAnnouncement.setGravity(Gravity.CENTER);
        toggleAnnouncement.setPadding(dp(8), dp(10), dp(8), dp(10));
        toggleAnnouncement.setBackground(shape(PANEL_ALT, 12, CYAN));
        top.addView(toggleAnnouncement);
        toggleAnnouncement.setOnClickListener(v -> {
            int current = prefs.getInt(RadioService.KEY_INTERVAL, 20);
            prefs.edit().putInt(RadioService.KEY_INTERVAL, current == 0 ? 20 : 0).apply();
            updateSettingLabels();
            if (prefs.getBoolean(RadioService.KEY_ACTIVE, false)) command(RadioService.ACTION_CONFIG);
        });
        gap(announcer, 10);
        intervalState = label("Anuncia la hora en los minutos exactos del reloj", 12, DIM, false);
        announcer.addView(intervalState);
        gap(announcer, 12);
        LinearLayout chips = row();
        announcer.addView(chips);
        int[] minutes = {15,20,30,0};
        String[] titles = {"15 min","20 min","30 min","OFF"};
        for (int i = 0; i < minutes.length; i++) {
            final int selected = minutes[i];
            Button chip = new Button(this);
            chip.setText(titles[i]);
            chip.setTextSize(12);
            chip.setAllCaps(false);
            chip.setMinHeight(0);
            chip.setMinimumHeight(0);
            chip.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(45), 1);
            if (i > 0) cp.leftMargin = dp(5);
            chips.addView(chip, cp);
            intervals[i] = chip;
            chip.setOnClickListener(v -> {
                prefs.edit().putInt(RadioService.KEY_INTERVAL, selected).apply();
                updateSettingLabels();
                if (prefs.getBoolean(RadioService.KEY_ACTIVE, false)) command(RadioService.ACTION_CONFIG);
            });
        }
        gap(announcer, 12);
        LinearLayout announcerActions = row();
        announcer.addView(announcerActions);
        Button speakNow = miniButton("♫  Probar voz");
        announcerActions.addView(speakNow, new LinearLayout.LayoutParams(0, dp(49), 1));
        speakNow.setOnClickListener(v -> command(RadioService.ACTION_ANNOUNCE));
        horizGap(announcerActions, 8);
        Button intro = miniButton("✎  Locución propia");
        announcerActions.addView(intro, new LinearLayout.LayoutParams(0, dp(49), 1));
        intro.setOnClickListener(v -> editIntro());
        gap(page, 13);

        LinearLayout settings = panel();
        page.addView(settings);
        settings.addView(label("◖))  Volumen de la música", 16, WHITE, true));
        volume = new SeekBar(this);
        volume.setMax(100);
        volume.setProgress(prefs.getInt(RadioService.KEY_VOLUME, 80));
        volume.setContentDescription("Volumen de la música");
        volume.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));
        volume.setThumbTintList(android.content.res.ColorStateList.valueOf(CYAN));
        settings.addView(volume);
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser) {
                    prefs.edit().putInt(RadioService.KEY_VOLUME, value).apply();
                    if (prefs.getBoolean(RadioService.KEY_ACTIVE, false)) command(RadioService.ACTION_VOLUME);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        gap(settings, 5);
        settings.addView(label("✓  Sigue sonando con la pantalla apagada", 13, CYAN, true));
        gap(settings, 6);
        settings.addView(label("La app usa una notificación de reproducción. Permite las notificaciones y evita restricciones de batería para que funcione en segundo plano.", 12, DIM, false));
        gap(page, 16);
        page.addView(label("GUTIÉRREZ RADIO  •  VERSIÓN 2.0", 11, DIM, true));
        updateSettingLabels();
    }

    private Button lastTransportButton;
    private void addTransport(LinearLayout container, String icon, String title, boolean highlighted,
                              Runnable action) {
        LinearLayout cell = column();
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        container.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
        Button b = new Button(this);
        b.setText(icon);
        b.setTextSize(highlighted ? 23 : 20);
        b.setAllCaps(false);
        b.setTextColor(highlighted ? BG : WHITE);
        b.setBackground(shape(highlighted ? CYAN : PANEL_ALT, 40, highlighted ? CYAN : BLUE));
        b.setPadding(0,0,0,0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        cell.addView(b, new LinearLayout.LayoutParams(dp(51), dp(51)));
        gap(cell, 6);
        TextView caption = label(title, 10, DIM, false);
        caption.setGravity(Gravity.CENTER);
        cell.addView(caption);
        b.setContentDescription(title);
        b.setOnClickListener(v -> action.run());
        if ("Reproducir".equals(title)) lastTransportButton = b;
    }

    private void pickSongs() {
        Intent select = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        select.addCategory(Intent.CATEGORY_OPENABLE);
        select.setType("audio/*");
        select.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        select.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(select, OPEN_MUSIC);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != OPEN_MUSIC || result != RESULT_OK || data == null) return;
        JSONArray songs = RadioService.loadSongs(prefs);
        int added = 0;
        ClipData files = data.getClipData();
        if (files != null) {
            for (int i = 0; i < files.getItemCount(); i++) {
                if (insertSong(songs, files.getItemAt(i).getUri())) added++;
            }
        } else if (data.getData() != null) {
            if (insertSong(songs, data.getData())) added++;
        }
        prefs.edit().putString(RadioService.KEY_TRACKS, songs.toString()).apply();
        drawPlaylist();
        Toast.makeText(this, added + " canción(es) añadida(s)", Toast.LENGTH_SHORT).show();
    }

    private boolean insertSong(JSONArray songs, Uri uri) {
        String address = uri.toString();
        for (int i = 0; i < songs.length(); i++) {
            JSONObject s = songs.optJSONObject(i);
            if (s != null && address.equals(s.optString("uri"))) return false;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) { }
        JSONObject item = new JSONObject();
        try {
            item.put("uri", address);
            item.put("title", displayName(uri));
            songs.put(item);
            return true;
        } catch (JSONException ignored) { return false; }
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Exception ignored) { }
        return uri.getLastPathSegment() == null ? "Audio sin nombre" : uri.getLastPathSegment();
    }

    private void drawPlaylist() {
        if (playlistView == null) return;
        playlistView.removeAllViews();
        JSONArray songs = RadioService.loadSongs(prefs);
        int current = prefs.getInt(RadioService.KEY_INDEX, -1);
        trackCount.setText(songs.length() + " canciones guardadas");
        if (songs.length() == 0) {
            playlistView.addView(label("Aún no hay canciones. Pulsa 'Agregar canciones del celular'.", 12, DIM, false));
            return;
        }
        for (int i = 0; i < songs.length(); i++) {
            JSONObject song = songs.optJSONObject(i);
            if (song == null) continue;
            final int position = i;
            TextView item = label((i + 1) + "  ♫  " + song.optString("title", "Audio"),
                    13, i == current ? CYAN : WHITE, i == current);
            item.setPadding(dp(11), dp(14), dp(11), dp(14));
            item.setBackground(shape(PANEL_ALT, 10, 0));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(6);
            playlistView.addView(item, params);
            item.setOnClickListener(v -> command(RadioService.ACTION_PLAY_INDEX, position));
            item.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Quitar canción")
                        .setMessage("¿Quitar '" + song.optString("title") + "' de la lista?")
                        .setNegativeButton("Cancelar", (d, w) -> { })
                        .setPositiveButton("Quitar", (d, w) -> {
                            if (prefs.getBoolean(RadioService.KEY_ACTIVE, false)) command(RadioService.ACTION_STOP);
                            JSONArray cleaned = new JSONArray();
                            JSONArray before = RadioService.loadSongs(prefs);
                            for (int j = 0; j < before.length(); j++) {
                                if (j != position) cleaned.put(before.opt(j));
                            }
                            prefs.edit().putString(RadioService.KEY_TRACKS, cleaned.toString())
                                    .putInt(RadioService.KEY_INDEX, -1)
                                    .putString(RadioService.KEY_TITLE, "Elige tus canciones")
                                    .putString(RadioService.KEY_STATUS, "Lista actualizada")
                                    .putInt(RadioService.KEY_POSITION, 0)
                                    .putInt(RadioService.KEY_DURATION, 0)
                                    .apply();
                            lastIndex = Integer.MIN_VALUE;
                            drawPlaylist();
                            refreshState();
                        }).show();
                return true;
            });
        }
    }

    private void editIntro() {
        EditText field = new EditText(this);
        field.setSingleLine(false);
        field.setText(prefs.getString(RadioService.KEY_INTRO, "Gutiérrez Radio informa"));
        field.setSelection(field.length());
        LinearLayout wrapper = column();
        wrapper.setPadding(dp(22), dp(5), dp(22), 0);
        wrapper.addView(field);
        new AlertDialog.Builder(this)
                .setTitle("Personalizar la locución")
                .setMessage("Escribe cómo quieres que la voz presente la hora. El teléfono leerá este texto antes de decir la hora.")
                .setView(wrapper)
                .setNegativeButton("Cancelar", (d,w) -> { })
                .setPositiveButton("Guardar", (d,w) -> {
                    String phrase = field.getText().toString().trim();
                    if (phrase.length() > 120) phrase = phrase.substring(0, 120);
                    prefs.edit().putString(RadioService.KEY_INTRO,
                            phrase.isEmpty() ? "Gutiérrez Radio informa" : phrase).apply();
                    Toast.makeText(this, "Locución guardada", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void refreshState() {
        if (currentTitle == null) return;
        currentTitle.setText(prefs.getString(RadioService.KEY_TITLE, "Elige tus canciones"));
        String status = prefs.getString(RadioService.KEY_STATUS, "Listo para comenzar");
        currentState.setText("●  " + status);
        boolean playing = prefs.getBoolean(RadioService.KEY_PLAYING, false);
        playButton.setText(playing ? "Ⅱ" : "▶");
        int pos = prefs.getInt(RadioService.KEY_POSITION, 0);
        int duration = prefs.getInt(RadioService.KEY_DURATION, 0);
        if (!touchingProgress) progress.setProgress(duration > 0 ? (int)(1000L * pos / duration) : 0);
        elapsed.setText(formatTime(pos));
        total.setText(formatTime(duration));
        int current = prefs.getInt(RadioService.KEY_INDEX, -1);
        if (lastIndex != current) {
            lastIndex = current;
            drawPlaylist();
        }
    }

    private String formatTime(int millis) {
        long seconds = Math.max(0, millis) / 1000;
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }

    private void command(String action) { command(action, 0); }

    private void command(String action, int value) {
        if (RadioService.ACTION_TOGGLE.equals(action) && RadioService.loadSongs(prefs).length() == 0) {
            Toast.makeText(this, "Primero añade canciones del celular", Toast.LENGTH_SHORT).show();
            return;
        }
        if ((RadioService.ACTION_STOP.equals(action) || RadioService.ACTION_SEEK.equals(action)) &&
                !prefs.getBoolean(RadioService.KEY_ACTIVE, false)) return;
        Intent intent = new Intent(this, RadioService.class);
        intent.setAction(action);
        if (RadioService.ACTION_PLAY_INDEX.equals(action)) intent.putExtra(RadioService.EXTRA_INDEX, value);
        if (RadioService.ACTION_SEEK.equals(action)) intent.putExtra(RadioService.EXTRA_SEEK_MS, value);
        if (RadioService.ACTION_STOP.equals(action) || RadioService.ACTION_CONFIG.equals(action) ||
                RadioService.ACTION_SEEK.equals(action) || RadioService.ACTION_VOLUME.equals(action)) {
            startService(intent);
        } else {
            startForegroundService(intent);
        }
        uiHandler.postDelayed(this::refreshState, 400);
    }

    private void updateSettingLabels() {
        int current = prefs.getInt(RadioService.KEY_INTERVAL, 20);
        toggleAnnouncement.setText(current == 0 ? "APAGADO" : "ACTIVO");
        intervalState.setText(current == 0 ? "Anuncios desactivados" :
                "La hora se anuncia cada " + current + " minutos de reloj");
        int[] options = {15, 20, 30, 0};
        for (int i = 0; i < options.length; i++) {
            boolean selected = current == options[i];
            intervals[i].setBackground(shape(selected ? CYAN : PANEL_ALT, 16, selected ? CYAN : BLUE));
            intervals[i].setTextColor(selected ? BG : WHITE);
        }
        shuffleButton.setText(prefs.getBoolean(RadioService.KEY_SHUFFLE, false) ?
                "⇄  Aleatorio: SÍ" : "⇄  Aleatorio: NO");
    }

    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(1); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(0); return l; }
    private LinearLayout panel() {
        LinearLayout p = column();
        p.setPadding(dp(15), dp(16), dp(15), dp(17));
        p.setBackground(shape(PANEL, 20, BLUE));
        return p;
    }
    private TextView label(String text, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return v;
    }
    private Button actionButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(BG);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(shape(CYAN, 12, CYAN));
        b.setMinHeight(dp(50));
        return b;
    }
    private Button miniButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(WHITE);
        b.setBackground(shape(PANEL_ALT, 12, BLUE));
        b.setPadding(dp(4),0,dp(4),0);
        b.setMinHeight(0);
        return b;
    }
    private GradientDrawable shape(int color, int radius, int border) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (border != 0) d.setStroke(dp(1), border);
        return d;
    }
    private void gap(LinearLayout container, int height) {
        container.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }
    private void horizGap(LinearLayout container, int width) {
        container.addView(new View(this), new LinearLayout.LayoutParams(dp(width), 1));
    }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}

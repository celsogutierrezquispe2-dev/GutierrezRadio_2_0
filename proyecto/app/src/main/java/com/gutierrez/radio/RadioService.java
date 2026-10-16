package com.gutierrez.radio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

public class RadioService extends Service {
    public static final String PREFS_NAME = "gutierrez_radio_settings";
    public static final String KEY_TRACKS = "tracks";
    public static final String KEY_INDEX = "current_index";
    public static final String KEY_TITLE = "current_title";
    public static final String KEY_STATUS = "status_text";
    public static final String KEY_PLAYING = "is_playing";
    public static final String KEY_ACTIVE = "service_active";
    public static final String KEY_INTERVAL = "announce_interval";
    public static final String KEY_SHUFFLE = "shuffle";
    public static final String KEY_POSITION = "track_position_ms";
    public static final String KEY_DURATION = "track_duration_ms";
    public static final String KEY_VOLUME = "music_volume";
    public static final String KEY_INTRO = "custom_intro";

    public static final String ACTION_TOGGLE = "com.gutierrez.radio.TOGGLE";
    public static final String ACTION_NEXT = "com.gutierrez.radio.NEXT";
    public static final String ACTION_PREVIOUS = "com.gutierrez.radio.PREVIOUS";
    public static final String ACTION_STOP = "com.gutierrez.radio.STOP";
    public static final String ACTION_CONFIG = "com.gutierrez.radio.CONFIG";
    public static final String ACTION_ANNOUNCE = "com.gutierrez.radio.ANNOUNCE";
    public static final String ACTION_PLAY_INDEX = "com.gutierrez.radio.PLAY_INDEX";
    public static final String ACTION_SEEK = "com.gutierrez.radio.SEEK";
    public static final String ACTION_VOLUME = "com.gutierrez.radio.VOLUME";
    public static final String EXTRA_SEEK_MS = "position_ms";
    public static final String EXTRA_INDEX = "index";

    private static final String CHANNEL_ID = "radio_playback";
    private static final int NOTIFICATION_ID = 2006;
    private SharedPreferences prefs;
    private MediaPlayer player;
    private boolean prepared;
    private boolean ttsReady;
    private boolean voicePending;
    private boolean resumeAfterSpeech;
    private int currentIndex = -1;
    private int failureStreak = 0;
    private long utteranceSerial = 0;
    private String currentUtterance = "";
    private String currentTitle = "Gutiérrez Radio";
    private String statusMessage = "Listo para reproducir";
    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timeRunnable;
    private final Runnable positionRunnable = new Runnable() {
        @Override public void run() {
            if (player != null && prepared) {
                try {
                    prefs.edit().putInt(KEY_POSITION, player.getCurrentPosition())
                            .putInt(KEY_DURATION, player.getDuration()).apply();
                } catch (IllegalStateException ignored) { }
            }
            handler.postDelayed(this, 750);
        }
    };
    private TextToSpeech speaker;
    private NotificationManager notificationManager;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentIndex = prefs.getInt(KEY_INDEX, -1);
        notificationManager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Reproducción de Gutiérrez Radio", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Controles de música y locuciones de la hora");
        notificationManager.createNotificationChannel(channel);
        startForeground(NOTIFICATION_ID, notification());
        handler.post(positionRunnable);
        saveState("Listo para reproducir");
        speaker = new TextToSpeech(this, result -> handler.post(() -> {
            if (result == TextToSpeech.SUCCESS && speaker != null) {
                Locale spanish = Locale.forLanguageTag("es-PE");
                int supported = speaker.setLanguage(spanish);
                if (supported == TextToSpeech.LANG_MISSING_DATA ||
                        supported == TextToSpeech.LANG_NOT_SUPPORTED) {
                    supported = speaker.setLanguage(new Locale("es", "ES"));
                }
                ttsReady = supported != TextToSpeech.LANG_MISSING_DATA &&
                        supported != TextToSpeech.LANG_NOT_SUPPORTED;
                if (ttsReady) {
                    speaker.setSpeechRate(0.92f);
                    speaker.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String utteranceId) { }
                        @Override public void onDone(String utteranceId) {
                            handler.post(() -> finishSpeech(utteranceId));
                        }
                        @Override public void onError(String utteranceId) {
                            handler.post(() -> finishSpeech(utteranceId));
                        }
                    });
                }
            }
            if (voicePending) {
                voicePending = false;
                announceTime();
            }
        }));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_TOGGLE : intent.getAction();
        if (action == null) action = ACTION_TOGGLE;
        switch (action) {
            case ACTION_TOGGLE: togglePlayback(); break;
            case ACTION_NEXT: playNext(); break;
            case ACTION_PREVIOUS: playPrevious(); break;
            case ACTION_PLAY_INDEX:
                if (intent != null) playIndex(intent.getIntExtra(EXTRA_INDEX, 0));
                break;
            case ACTION_CONFIG:
                scheduleNextAnnouncement();
                saveState(statusMessage);
                break;
            case ACTION_VOLUME:
                applyVolume();
                break;
            case ACTION_SEEK:
                if (player != null && prepared && intent != null) {
                    try {
                        int desired = intent.getIntExtra(EXTRA_SEEK_MS, 0);
                        player.seekTo(Math.max(0, Math.min(desired, player.getDuration())));
                    } catch (IllegalStateException ignored) { }
                }
                break;
            case ACTION_ANNOUNCE:
                announceTime();
                break;
            case ACTION_STOP:
                stopPlayback();
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
                return START_NOT_STICKY;
            default: togglePlayback(); break;
        }
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    public static JSONArray loadSongs(SharedPreferences preferences) {
        try {
            return new JSONArray(preferences.getString(KEY_TRACKS, "[]"));
        } catch (Exception invalid) {
            return new JSONArray();
        }
    }

    private void togglePlayback() {
        if (player != null && prepared) {
            if (player.isPlaying()) {
                resumeAfterSpeech = false;
                player.pause();
                cancelAnnouncement();
                saveState("En pausa");
            } else {
                player.start();
                saveState("Reproduciendo");
                scheduleNextAnnouncement();
            }
            return;
        }
        JSONArray songs = loadSongs(prefs);
        if (songs.length() == 0) {
            saveState("Agrega canciones para comenzar");
            return;
        }
        int index = currentIndex >= 0 && currentIndex < songs.length() ? currentIndex : 0;
        playIndex(index);
    }

    private void playNext() {
        JSONArray songs = loadSongs(prefs);
        if (songs.length() == 0) {
            saveState("Agrega canciones para comenzar");
            return;
        }
        int index;
        if (prefs.getBoolean(KEY_SHUFFLE, false) && songs.length() > 1) {
            index = random.nextInt(songs.length() - 1);
            if (index >= currentIndex) index++;
        } else {
            index = (currentIndex + 1) % songs.length();
        }
        playIndex(index);
    }

    private void playPrevious() {
        JSONArray songs = loadSongs(prefs);
        if (songs.length() == 0) return;
        playIndex((currentIndex - 1 + songs.length()) % songs.length());
    }

    private void playIndex(int index) {
        JSONArray songs = loadSongs(prefs);
        if (index < 0 || index >= songs.length()) {
            saveState("No se encontró la canción");
            return;
        }
        JSONObject song = songs.optJSONObject(index);
        if (song == null || song.optString("uri").isEmpty()) {
            saveState("Archivo de audio no válido");
            return;
        }
        releasePlayer();
        cancelAnnouncement();
        prefs.edit().putInt(KEY_POSITION, 0).putInt(KEY_DURATION, 0).apply();
        currentIndex = index;
        currentTitle = song.optString("title", "Audio " + (index + 1));
        statusMessage = "Preparando audio...";
        saveState(statusMessage);
        MediaPlayer fresh = new MediaPlayer();
        player = fresh;
        try {
            fresh.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA).build());
            fresh.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            applyVolume();
            fresh.setDataSource(getApplicationContext(), Uri.parse(song.optString("uri")));
            fresh.setOnPreparedListener(mp -> {
                if (player != mp) return;
                prepared = true;
                failureStreak = 0;
                applyVolume();
                prefs.edit().putInt(KEY_DURATION, mp.getDuration()).putInt(KEY_POSITION, 0).apply();
                mp.start();
                saveState("Reproduciendo");
                scheduleNextAnnouncement();
            });
            fresh.setOnCompletionListener(mp -> {
                if (player == mp) playNext();
            });
            fresh.setOnErrorListener((mp, what, extra) -> {
                if (player == mp) skipBrokenAudio();
                return true;
            });
            fresh.prepareAsync();
        } catch (IOException | IllegalArgumentException | SecurityException error) {
            skipBrokenAudio();
        }
    }

    private void skipBrokenAudio() {
        releasePlayer();
        failureStreak++;
        int size = loadSongs(prefs).length();
        if (failureStreak >= size || size == 0) {
            failureStreak = 0;
            cancelAnnouncement();
            saveState("No se pueden abrir los audios. Vuelve a añadirlos.");
        } else {
            saveState("Saltando archivo inaccesible...");
            handler.postDelayed(this::playNext, 250);
        }
    }

    private void scheduleNextAnnouncement() {
        cancelAnnouncement();
        int interval = prefs.getInt(KEY_INTERVAL, 20);
        if (interval != 15 && interval != 20 && interval != 30) return;
        if (player == null || !prepared || !player.isPlaying()) return;
        Calendar now = Calendar.getInstance();
        Calendar next = (Calendar) now.clone();
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        int remainder = now.get(Calendar.MINUTE) % interval;
        next.add(Calendar.MINUTE, interval - remainder);
        long delay = Math.max(1000L, next.getTimeInMillis() - System.currentTimeMillis());
        timeRunnable = () -> {
            if (player != null && prepared && player.isPlaying()) announceTime();
            else scheduleNextAnnouncement();
        };
        handler.postDelayed(timeRunnable, delay);
    }

    private void cancelAnnouncement() {
        if (timeRunnable != null) handler.removeCallbacks(timeRunnable);
        timeRunnable = null;
    }

    private void announceTime() {
        if (!ttsReady) {
            if (speaker != null && !voicePending) {
                saveState("La voz en español no está lista en tu celular");
                scheduleNextAnnouncement();
            } else voicePending = true;
            return;
        }
        cancelAnnouncement();
        boolean wasPlaying = player != null && prepared && player.isPlaying();
        resumeAfterSpeech = wasPlaying;
        if (wasPlaying) player.pause();
        Calendar now = Calendar.getInstance();
        int hour24 = now.get(Calendar.HOUR_OF_DAY);
        int hour12 = now.get(Calendar.HOUR);
        if (hour12 == 0) hour12 = 12;
        int minute = now.get(Calendar.MINUTE);
        String partOfDay = hour24 < 12 ? "de la mañana" :
                hour24 < 19 ? "de la tarde" : "de la noche";
        String intro = prefs.getString(KEY_INTRO, "Gutiérrez Radio informa");
        if (intro == null || intro.trim().isEmpty()) intro = "Gutiérrez Radio informa";
        String phrase = intro + ". Son las " + hour12 +
                (minute == 0 ? " en punto " : " y " + minute + " minutos ") + partOfDay + ".";
        currentUtterance = "hora_" + (++utteranceSerial);
        Bundle options = new Bundle();
        int outcome = speaker.speak(phrase, TextToSpeech.QUEUE_FLUSH, options, currentUtterance);
        if (outcome == TextToSpeech.ERROR) {
            finishSpeech(currentUtterance);
        } else {
            saveState("Anunciando la hora...");
        }
    }

    private void finishSpeech(String utteranceId) {
        if (!utteranceId.equals(currentUtterance)) return;
        currentUtterance = "";
        if (resumeAfterSpeech && player != null && prepared) {
            resumeAfterSpeech = false;
            try {
                player.start();
                saveState("Reproduciendo");
            } catch (IllegalStateException ignored) {
                saveState("Reproducción interrumpida");
            }
        } else {
            resumeAfterSpeech = false;
            saveState(player != null && prepared && player.isPlaying() ? "Reproduciendo" : "En pausa");
        }
        scheduleNextAnnouncement();
    }

    private void applyVolume() {
        if (player != null) {
            float level = Math.max(0, Math.min(100, prefs.getInt(KEY_VOLUME, 80))) / 100f;
            try { player.setVolume(level, level); } catch (IllegalStateException ignored) { }
        }
    }

    private void releasePlayer() {
        prepared = false;
        resumeAfterSpeech = false;
        if (player != null) {
            try { player.reset(); } catch (IllegalStateException ignored) { }
            player.release();
            player = null;
        }
    }

    private void stopPlayback() {
        cancelAnnouncement();
        if (speaker != null) speaker.stop();
        currentUtterance = "";
        releasePlayer();
        prefs.edit().putInt(KEY_POSITION, 0).putInt(KEY_DURATION, 0).apply();
        statusMessage = "Detenido";
        saveState(statusMessage);
    }

    private void saveState(String status) {
        statusMessage = status;
        boolean playing = player != null && prepared && player.isPlaying();
        prefs.edit()
                .putInt(KEY_INDEX, currentIndex)
                .putString(KEY_TITLE, currentTitle)
                .putString(KEY_STATUS, status)
                .putBoolean(KEY_PLAYING, playing)
                .putBoolean(KEY_ACTIVE, true)
                .apply();
        notificationManager.notify(NOTIFICATION_ID, notification());
    }

    private PendingIntent pending(String action, int request) {
        Intent intent = new Intent(this, RadioService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, request, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification notification() {
        boolean playing = player != null && prepared && player.isPlaying();
        Intent launch = new Intent(this, MainActivity.class);
        launch.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openApp = PendingIntent.getActivity(this, 99, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(currentTitle)
                .setContentText(statusMessage)
                .setSmallIcon(R.drawable.ic_stat_radio)
                .setContentIntent(openApp)
                .setColor(0xFF22CEB1)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setOngoing(playing)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous,
                        "Anterior", pending(ACTION_PREVIOUS, 1)).build())
                .addAction(new Notification.Action.Builder(playing ?
                        android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? "Pausa" : "Reproducir", pending(ACTION_TOGGLE, 2)).build())
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next,
                        "Siguiente", pending(ACTION_NEXT, 3)).build())
                .build();
    }

    @Override public void onDestroy() {
        cancelAnnouncement();
        releasePlayer();
        handler.removeCallbacks(positionRunnable);
        if (speaker != null) {
            speaker.stop();
            speaker.shutdown();
        }
        prefs.edit().putBoolean(KEY_ACTIVE, false).putBoolean(KEY_PLAYING, false).apply();
        super.onDestroy();
    }
}

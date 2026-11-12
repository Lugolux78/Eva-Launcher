package nl.eva.radio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import java.util.HashMap;
import java.util.Map;

/** Geïsoleerde radioplayer: werkt onafhankelijk van Activity/skins. */
public final class RadioService extends Service {
    public static final String PLAY = "nl.eva.radio.PLAY";
    public static final String TOGGLE = "nl.eva.radio.TOGGLE";
    public static final String STOP = "nl.eva.radio.STOP";
    public static final String STATUS = "nl.eva.radio.STATUS";
    public static final String STATE_CHANGED = "nl.eva.radio.STATE_CHANGED";
    private static final String CHANNEL = "eva_radio_playback";
    private static final int NOTIFICATION_ID = 1201;

    private AudioManager audioManager;
    private AudioFocusRequest audioRequest;
    private boolean hasAudioFocus;
    private MediaPlayer player;
    private MediaSession mediaSession;
    private String stationName = "Evadio";
    private String stationUrl = "";
    private String status = "Gestopt";
    private boolean playing;
    private boolean buffering;
    private boolean foreground;

    @Override public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(
                new NotificationChannel(CHANNEL, "Radio afspelen", NotificationManager.IMPORTANCE_LOW));
        mediaSession = new MediaSession(this, "Evadio");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { togglePlayback(true); }
            @Override public void onPause() { pausePlayback(); }
            @Override public void onStop() { stopPlayback(); }
        });
        mediaSession.setActive(true);
        // Eén audiofocusverzoek per servicesessie: geen pauze bij zenderwissels.
        AudioAttributes focusAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        audioRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(focusAttributes)
                .setOnAudioFocusChangeListener(change -> {
                    if (change == AudioManager.AUDIOFOCUS_LOSS ||
                            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        if (change == AudioManager.AUDIOFOCUS_LOSS) hasAudioFocus = false;
                        pausePlayback();
                    } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
                        hasAudioFocus = true;
                    }
                }).build();
        publish();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? STATUS : intent.getAction();
        if (PLAY.equals(action)) {
            String url = intent.getStringExtra("url");
            String name = intent.getStringExtra("name");
            if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
                status = "Ongeldige stream-URL"; publish(); stopSelf(); return START_NOT_STICKY;
            }
            stationName = name == null || name.isEmpty() ? "Radio" : name;
            stationUrl = url;
            status = "Verbinden…";
            buffering = true;
            playing = false;
            ensureForeground();
            playNewStream();
        } else if (TOGGLE.equals(action)) {
            togglePlayback(false);
        } else if (STOP.equals(action)) {
            stopPlayback();
        } else {
            publish();
        }
        return START_NOT_STICKY;
    }

    private void togglePlayback(boolean fromSession) {
        if (playing || buffering) {
            pausePlayback();
        } else if (player != null) {
            try {
                if (!requestFocus()) { status = "Geen audiofocus"; publish(); return; }
                player.start(); playing = true; status = "LIVE";
                ensureForeground(); publish();
            } catch (Exception e) { playNewStream(); }
        } else if (!stationUrl.isEmpty()) {
            status = "Verbinden…"; buffering = true; ensureForeground(); playNewStream();
        } else {
            publish();
        }
    }

    private boolean requestFocus() {
        if (hasAudioFocus) return true;
        hasAudioFocus = audioManager.requestAudioFocus(audioRequest)
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return hasAudioFocus;
    }

    private void playNewStream() {
        releasePlayer();
        if (!requestFocus()) {
            buffering = false; status = "Audio is in gebruik door een andere app"; publish(); return;
        }
        try {
            MediaPlayer next = new MediaPlayer();
            player = next;
            next.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            next.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Evadio/0.2 (Android)");
            next.setDataSource(getApplicationContext(), android.net.Uri.parse(stationUrl), headers);
            next.setOnPreparedListener(mp -> {
                if (mp != player) return;
                buffering = false;
                try { mp.start(); playing = true; status = "LIVE"; }
                catch (Exception e) { playing = false; status = "Kan stream niet starten"; }
                publish();
            });
            next.setOnInfoListener((mp, what, extra) -> {
                if (mp != player) return true;
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) { status = "Bufferen…"; publish(); }
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END && playing) { status = "LIVE"; publish(); }
                return false;
            });
            next.setOnErrorListener((mp, what, extra) -> {
                if (mp != player) return true;
                playing = false; buffering = false;
                status = "Stream niet bereikbaar (" + what + "). Kies een ander station.";
                releasePlayer(); publish();
                return true;
            });
            next.prepareAsync();
            publish();
        } catch (Exception e) {
            releasePlayer(); buffering = false; playing = false;
            status = "Stream mislukt: " + e.getClass().getSimpleName();
            publish();
        }
    }

    private void pausePlayback() {
        if (buffering) { releasePlayer(); buffering = false; }
        else if (player != null && playing) {
            try { player.pause(); } catch (Exception ignored) { releasePlayer(); }
        }
        playing = false; status = "Gepauzeerd";
        publish();
    }

    private void stopPlayback() {
        releasePlayer();
        playing = false; buffering = false; status = "Gestopt";
        if (audioRequest != null) audioManager.abandonAudioFocusRequest(audioRequest);
        hasAudioFocus = false;
        publish();
        if (foreground) { stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; }
        stopSelf();
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.reset(); player.release(); } catch (Exception ignored) {}
            player = null;
        }
    }

    private void ensureForeground() {
        Notification n = buildNotification();
        startForeground(NOTIFICATION_ID, n);
        foreground = true;
    }

    private void publish() {
        if (mediaSession != null) {
            mediaSession.setMetadata(new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, stationName)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "Evadio • Live").build());
            int state = buffering ? PlaybackState.STATE_BUFFERING :
                    playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
            mediaSession.setPlaybackState(new PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE |
                            PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP)
                    .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, playing ? 1f : 0f).build());
        }
        if (foreground) ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .notify(NOTIFICATION_ID, buildNotification());
        Intent state = new Intent(STATE_CHANGED).setPackage(getPackageName());
        state.putExtra("name", stationName).putExtra("status", status)
                .putExtra("playing", playing).putExtra("buffering", buffering);
        sendBroadcast(state);
    }

    private PendingIntent serviceIntent(String action, int requestCode) {
        return PendingIntent.getService(this, requestCode,
                new Intent(this, RadioService.class).setAction(action),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification buildNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 1,
                new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Action toggle = new Notification.Action.Builder(
                android.R.drawable.ic_media_play, playing || buffering ? "Pauze" : "Afspelen",
                serviceIntent(TOGGLE, 2)).build();
        Notification.Action stop = new Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel, "Stop",
                serviceIntent(STOP, 3)).build();
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_radio)
                .setContentTitle(stationName)
                .setContentText(status)
                .setContentIntent(open)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(playing || buffering)
                .addAction(toggle).addAction(stop)
                .setStyle(new Notification.MediaStyle().setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1))
                .build();
    }

    @Override public void onDestroy() {
        releasePlayer();
        if (audioRequest != null) audioManager.abandonAudioFocusRequest(audioRequest);
        hasAudioFocus = false;
        if (mediaSession != null) { mediaSession.setActive(false); mediaSession.release(); }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

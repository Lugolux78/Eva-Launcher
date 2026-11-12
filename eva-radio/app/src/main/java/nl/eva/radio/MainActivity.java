package nl.eva.radio;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.WindowManager;
import android.view.View;
import android.app.AlertDialog;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/** Evadio Walkman: alleen UI en bediening, geen audiocode. */
public final class MainActivity extends Activity implements WalkmanView.Controller {
    private WalkmanView walkman;
    private SharedPreferences prefs;
    private StationRepository repository;
    private final List<Station> stations = new ArrayList<>();
    private Station selected;
    private boolean playing;
    private boolean buffering;
    private boolean registered;
    private AudioManager audio;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            playing = intent.getBooleanExtra("playing", false);
            buffering = intent.getBooleanExtra("buffering", false);
            String name = intent.getStringExtra("name");
            if (selected != null && (name == null || name.equals("Evadio"))) name = selected.name;
            walkman.setPlayback(name, intent.getStringExtra("status"), playing, buffering);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN);
        prefs = getSharedPreferences("radio", MODE_PRIVATE);
        repository = new StationRepository();
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        walkman = new WalkmanView(this, this);
        setContentView(walkman);
        refreshSelected();
        refreshFavorites();
        refreshVolume();
        repository.search("NL", "", new StationRepository.Callback() {
            @Override public void onResult(List<Station> results) {
                runOnUiThread(() -> { stations.clear(); stations.addAll(results); });
            }
            @Override public void onError(String reason) {
                // De zenderbrowser is altijd beschikbaar als de lijst niet laadt.
            }
        });
    }

    private void refreshSelected() {
        String raw = prefs.getString("lastStation", "");
        if (raw != null && !raw.isEmpty()) {
            try {
                selected = Station.fromJson(new JSONObject(raw));
                walkman.setPlayback(selected.name, "Gereed", playing, buffering);
            } catch (Exception ignored) {}
        }
    }

    private void refreshFavorites() {
        try {
            JSONObject items = new JSONObject(prefs.getString("favorites", "{}"));
            walkman.setFavoritesCount(items.length());
        } catch (Exception ignored) { walkman.setFavoritesCount(0); }
    }

    private void refreshVolume() {
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        walkman.setVolume(max <= 0 ? 0.5f :
                audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max);
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(RadioService.STATE_CHANGED);
        if (android.os.Build.VERSION.SDK_INT >= 33)
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(stateReceiver, filter);
        registered = true;
        startService(new Intent(this, RadioService.class).setAction(RadioService.STATUS));
    }

    @Override protected void onResume() {
        super.onResume();
        refreshSelected(); refreshFavorites(); refreshVolume();
    }

    @Override protected void onStop() {
        if (registered) { unregisterReceiver(stateReceiver); registered = false; }
        super.onStop();
    }

    @Override protected void onDestroy() {
        repository.shutdown();
        super.onDestroy();
    }

    private void play(Station st) {
        if (st == null || !st.playable()) return;
        selected = st;
        prefs.edit().putString("lastStation", st.toJson().toString()).apply();
        walkman.setPlayback(st.name, "Verbinden…", false, true);
        Intent intent = new Intent(this, RadioService.class).setAction(RadioService.PLAY);
        intent.putExtra("name", st.name).putExtra("url", st.stream);
        startForegroundService(intent);
    }

    @Override public void onPlayPause() {
        if (playing || buffering) {
            startService(new Intent(this, RadioService.class).setAction(RadioService.TOGGLE));
        } else if (selected != null) {
            play(selected);
        } else {
            onBrowse(false);
        }
    }

    @Override public void onStopPlayback() {
        startService(new Intent(this, RadioService.class).setAction(RadioService.STOP));
    }

    private void skip(int direction) {
        if (stations.isEmpty()) {
            onBrowse(false);
            return;
        }
        int index = -1;
        if (selected != null) for (int i = 0; i < stations.size(); i++)
            if (stations.get(i).id.equals(selected.id)) { index = i; break; }
        int next = index < 0 ? 0 : (index + direction + stations.size()) % stations.size();
        play(stations.get(next));
    }

    @Override public void onPrevious() { skip(-1); }
    @Override public void onNext() { skip(1); }

    @Override public void onBrowse(boolean favorites) {
        startActivity(new Intent(this, ClassicActivity.class).putExtra("favorites", favorites));
    }

    @Override public void onSettings() {
        new AlertDialog.Builder(this)
                .setTitle("Evadio Walkman")
                .setItems(new String[]{"Radiozenders zoeken", "Favorieten", "Over Evadio"},
                        (d, n) -> {
                            if (n == 0) onBrowse(false);
                            else if (n == 1) onBrowse(true);
                            else new AlertDialog.Builder(this).setTitle("Evadio 0.3")
                                    .setMessage("Walkman-skin met dynamisch cassettelabel, " +
                                            "draaiende spoelen en live internetstreaming.")
                                    .setPositiveButton("OK", null).show();
                        }).show();
    }

    @Override public void onVolume(float fraction) {
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int value = Math.round(Math.max(0f, Math.min(1f, fraction)) * max);
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
    }
}

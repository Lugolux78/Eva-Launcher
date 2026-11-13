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
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
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
        new StationPicker(favorites).show();
    }

    @Override public void onEject() {
        startService(new Intent(this, RadioService.class).setAction(RadioService.STOP));
        finishAndRemoveTask();
    }

    @Override public void onSettings() {
        new AlertDialog.Builder(this)
                .setTitle("Evadio Walkman")
                .setItems(new String[]{"Radiozenders zoeken", "Favorieten", "Over Evadio"},
                        (d, n) -> {
                            if (n == 0) onBrowse(false);
                            else if (n == 1) onBrowse(true);
                            else new AlertDialog.Builder(this).setTitle("Evadio 0.4")
                                    .setMessage("Walkman-skin met dynamisch cassettelabel, " +
                                            "draaiende spoelen en live internetstreaming.")
                                    .setPositiveButton("OK", null).show();
                        }).show();
    }

    @Override public void onVolume(float fraction) {
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int value = Math.round(Math.max(0f, Math.min(1f, fraction)) * max);
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
        walkman.setVolume(max == 0 ? 0f : value / (float) max);
    }


    /** Zenderkeuze als popup over de Walkman: land, zoeken, favorieten, live starten. */
    private final class StationPicker {
        private final int BACK = 0xff231d1a, PANEL = 0xff342b25,
                TEXT = 0xfff2e5d2, ORANGE = 0xffffa348;
        private final String[] names = {"Nederland", "België", "Luxemburg",
                "Duitsland", "Frankrijk", "Verenigd Koninkrijk",
                "Verenigde Staten", "Wereldwijd"};
        private final String[] codes = {"NL", "BE", "LU", "DE", "FR", "GB", "US", ""};
        private final boolean initialFavorites;
        private Dialog dialog;
        private Spinner countries;
        private EditText search;
        private TextView feedback;
        private LinearLayout rows;
        private Button allButton, favoritesButton;
        private List<Station> visible = new ArrayList<>();
        private boolean favoritesOnly;
        private int requestToken;
        private int selectedCountryIndex;

        StationPicker(boolean favorites) {
            initialFavorites = favorites;
            favoritesOnly = favorites;
            String saved = prefs.getString("picker_country", "NL");
            selectedCountryIndex = 0;
            for (int i = 0; i < codes.length; i++)
                if (codes[i].equals(saved)) { selectedCountryIndex = i; break; }
        }

        private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
        private GradientDrawable bg(int color, int border) {
            GradientDrawable d = new GradientDrawable();
            d.setColor(color);
            d.setCornerRadius(dp(8));
            d.setStroke(dp(1), border);
            return d;
        }
        private TextView label(String s, int sp, boolean bold) {
            TextView v = new TextView(MainActivity.this);
            v.setText(s); v.setTextSize(sp); v.setTextColor(TEXT);
            v.setGravity(Gravity.CENTER_VERTICAL);
            if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            return v;
        }
        private Button button(String s) {
            Button b = new Button(MainActivity.this);
            b.setText(s); b.setAllCaps(false); b.setTextSize(13);
            b.setTextColor(TEXT); b.setBackground(bg(PANEL, 0xff695747));
            b.setPadding(dp(8), 0, dp(8), 0);
            return b;
        }
        private LinearLayout row() {
            LinearLayout l = new LinearLayout(MainActivity.this);
            l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL);
            return l;
        }
        void show() {
            dialog = new Dialog(MainActivity.this);
            LinearLayout root = new LinearLayout(MainActivity.this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(15), dp(10), dp(15), dp(12));
            root.setBackground(bg(BACK, 0xffaa794a));

            LinearLayout header = row();
            TextView title = label("KIES EEN RADIOZENDER", 19, true);
            title.setTextColor(ORANGE);
            header.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));
            Button close = button("✕  SLUITEN");
            header.addView(close, new LinearLayout.LayoutParams(dp(100), dp(42)));
            close.setOnClickListener(v -> dialog.dismiss());
            root.addView(header);

            LinearLayout chooser = row();
            TextView land = label("LAND", 12, true);
            chooser.addView(land, new LinearLayout.LayoutParams(dp(45), dp(44)));
            countries = new Spinner(MainActivity.this);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(MainActivity.this,
                    android.R.layout.simple_spinner_dropdown_item, names);
            countries.setAdapter(adapter);
            countries.setBackground(bg(0xfff4e8da, 0xff594532));
            chooser.addView(countries, new LinearLayout.LayoutParams(0, dp(44), 1));
            allButton = button("ZENDERS");
            favoritesButton = button("★ FAVORIETEN");
            LinearLayout.LayoutParams tab = new LinearLayout.LayoutParams(dp(110), dp(44));
            tab.leftMargin = dp(8);
            chooser.addView(allButton, tab);
            LinearLayout.LayoutParams fav = new LinearLayout.LayoutParams(dp(138), dp(44));
            fav.leftMargin = dp(6);
            chooser.addView(favoritesButton, fav);
            root.addView(chooser);
            countries.setSelection(selectedCountryIndex);

            LinearLayout filter = row();
            LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(-1, dp(45));
            filterParams.topMargin = dp(7);
            search = new EditText(MainActivity.this);
            search.setSingleLine(true);
            search.setTextColor(TEXT); search.setHintTextColor(0xffb9a999);
            search.setTextSize(15); search.setHint("Zoek op zendernaam...");
            search.setPadding(dp(12), 0, dp(12), 0);
            search.setBackground(bg(PANEL, 0xff554837));
            search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            filter.addView(search, new LinearLayout.LayoutParams(0, -1, 1));
            Button searchButton = button("ZOEK");
            LinearLayout.LayoutParams searchSize = new LinearLayout.LayoutParams(dp(85), -1);
            searchSize.leftMargin = dp(6);
            filter.addView(searchButton, searchSize);
            root.addView(filter, filterParams);

            feedback = label("Zenders laden…", 12, false);
            feedback.setTextColor(0xffc4b9aa);
            LinearLayout.LayoutParams feedbackParams = new LinearLayout.LayoutParams(-1, dp(30));
            root.addView(feedback, feedbackParams);
            ScrollView scroll = new ScrollView(MainActivity.this);
            scroll.setFillViewport(false);
            rows = new LinearLayout(MainActivity.this);
            rows.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(rows);
            root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

            dialog.setContentView(root);
            dialog.show();
            Window window = dialog.getWindow();
            if (window != null) {
                android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.setLayout((int) (dm.widthPixels * 0.92),
                        (int) (dm.heightPixels * 0.90));
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }

            allButton.setOnClickListener(v -> { favoritesOnly = false; refresh(); });
            favoritesButton.setOnClickListener(v -> { favoritesOnly = true; refresh(); });
            searchButton.setOnClickListener(v -> { favoritesOnly = false; refresh(); });
            search.setOnEditorActionListener((v, action, event) -> {
                if (action == EditorInfo.IME_ACTION_SEARCH) {
                    favoritesOnly = false; refresh(); return true;
                }
                return false;
            });
            countries.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onNothingSelected(AdapterView<?> parent) {}
                @Override public void onItemSelected(AdapterView<?> parent, View view,
                                                     int position, long id) {
                    if (position != selectedCountryIndex) {
                        selectedCountryIndex = position;
                        search.setText("");
                        favoritesOnly = false;
                        prefs.edit().putString("picker_country", codes[position]).apply();
                        refresh();
                    }
                }
            });
            refresh();
        }
        private JSONObject savedFavorites() {
            try { return new JSONObject(prefs.getString("favorites", "{}")); }
            catch (Exception ignored) { return new JSONObject(); }
        }
        private boolean favorite(Station station) { return savedFavorites().has(station.id); }
        private void toggleFavorite(Station station) {
            JSONObject data = savedFavorites();
            if (data.has(station.id)) data.remove(station.id);
            else try { data.put(station.id, station.toJson()); }
            catch (Exception ignored) {}
            prefs.edit().putString("favorites", data.toString()).apply();
            refreshFavorites();
            if (favoritesOnly) refresh(); else render();
        }
        private void refresh() {
            ++requestToken;
            allButton.setText(favoritesOnly ? "ZENDERS" : "● ZENDERS");
            favoritesButton.setText(favoritesOnly ? "★ FAVORIETEN" : "☆ FAVORIETEN");
            if (favoritesOnly) {
                visible = new ArrayList<>();
                JSONObject favs = savedFavorites();
                java.util.Iterator<String> keys = favs.keys();
                while (keys.hasNext()) {
                    JSONObject record = favs.optJSONObject(keys.next());
                    if (record != null) visible.add(Station.fromJson(record));
                }
                render();
                return;
            }
            rows.removeAllViews();
            feedback.setText("Zenders laden uit " + names[countries.getSelectedItemPosition()] + "…");
            final int myToken = requestToken;
            final String country = codes[countries.getSelectedItemPosition()];
            final String query = search.getText().toString().trim();
            repository.search(country, query, new StationRepository.Callback() {
                @Override public void onResult(List<Station> result) {
                    runOnUiThread(() -> {
                        if (dialog == null || !dialog.isShowing()
                                || myToken != requestToken || favoritesOnly) return;
                        visible = new ArrayList<>(result);
                        stations.clear(); stations.addAll(result);
                        render();
                    });
                }
                @Override public void onError(String reason) {
                    runOnUiThread(() -> {
                        if (dialog == null || !dialog.isShowing()
                                || myToken != requestToken || favoritesOnly) return;
                        feedback.setText("Zenders niet bereikbaar: " + reason);
                    });
                }
            });
        }
        private void render() {
            if (dialog == null || !dialog.isShowing()) return;
            rows.removeAllViews();
            feedback.setText((favoritesOnly ? "Favorieten" : names[countries.getSelectedItemPosition()])
                    + "  •  " + visible.size() + " zenders");
            if (visible.isEmpty()) {
                TextView empty = label(favoritesOnly ? "Nog geen favorieten opgeslagen."
                        : "Geen zenders gevonden. Kies een ander land of zoekwoord.", 15, false);
                rows.addView(empty, new LinearLayout.LayoutParams(-1, dp(60)));
            }
            for (int i = 0; i < Math.min(visible.size(), 120); i++) {
                Station station = visible.get(i);
                LinearLayout item = row();
                item.setBackground(bg(i % 2 == 0 ? 0xff302720 : 0xff28211d, 0xff47382c));
                LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-1, dp(49));
                itemParams.bottomMargin = dp(3);
                rows.addView(item, itemParams);
                TextView name = label(station.name, 15, true);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                name.setPadding(dp(12), 0, dp(8), 0);
                item.addView(name, new LinearLayout.LayoutParams(0, -1, 1));
                TextView codec = label(station.codec, 11, false);
                codec.setTextColor(0xffaa9886);
                item.addView(codec, new LinearLayout.LayoutParams(dp(64), -1));
                Button star = button(favorite(station) ? "★" : "☆");
                star.setTextColor(ORANGE); star.setTextSize(22);
                item.addView(star, new LinearLayout.LayoutParams(dp(52), -1));
                star.setOnClickListener(v -> toggleFavorite(station));
                item.setOnClickListener(v -> { play(station); dialog.dismiss(); });
            }
        }
    }

}

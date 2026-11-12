package nl.eva.radio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
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

/** Tijdelijke techniek-UI. In fase 2 vervangbaar zonder wijzigingen aan RadioService. */
public final class ClassicActivity extends Activity {
    private StationRepository repository;
    private SharedPreferences preferences;
    private LinearLayout stationContainer;
    private TextView nowPlaying;
    private TextView playbackStatus;
    private TextView resultsTitle;
    private EditText searchInput;
    private Spinner countries;
    private Button playPauseButton;
    private List<Station> stations = new ArrayList<>();
    private Station selected;
    private boolean showingFavorites;
    private boolean playing;
    private boolean buffering;
    private boolean registered;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            nowPlaying.setText(intent.getStringExtra("name"));
            playbackStatus.setText(intent.getStringExtra("status"));
            playing = intent.getBooleanExtra("playing", false);
            buffering = intent.getBooleanExtra("buffering", false);
            playPauseButton.setText(playing || buffering ? "PAUZE" : "AFSPELEN");
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Skin.PRIMARY);
        getWindow().setNavigationBarColor(Skin.BACKGROUND);
        preferences = getSharedPreferences("radio", MODE_PRIVATE);
        repository = new StationRepository();
        String last = preferences.getString("lastStation", "");
        if (!last.isEmpty()) {
            try { selected = Station.fromJson(new JSONObject(last)); } catch (Exception ignored) {}
        }
        setupUi();
        if (getIntent().getBooleanExtra("favorites", false)) showFavorites();
        else loadStations();
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(RadioService.STATE_CHANGED);
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(stateReceiver, filter);
        registered = true;
        startService(new Intent(this, RadioService.class).setAction(RadioService.STATUS));
    }

    @Override protected void onStop() {
        if (registered) { unregisterReceiver(stateReceiver); registered = false; }
        super.onStop();
    }

    @Override protected void onDestroy() {
        repository.shutdown();
        super.onDestroy();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable card(int color, int border) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(16)); d.setStroke(dp(1), border);
        return d;
    }

    private TextView text(String content, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(content); t.setTextSize(size); t.setTextColor(Skin.PRIMARY);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextColor(Skin.PRIMARY);
        b.setBackground(card(Skin.SURFACE, Skin.STROKE));
        b.setPadding(dp(10), dp(6), dp(10), dp(6));
        return b;
    }

    private void setupUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16), dp(16), dp(16), dp(12));
        root.setBackgroundColor(Skin.BACKGROUND);
        setContentView(root);

        TextView title = text("EVADIO", 29, true);
        root.addView(title);
        TextView sub = text("TECH EDITION 03  •  LIVE INTERNETRADIO", 11, false);
        sub.setTextColor(Skin.MUTED);
        root.addView(sub);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(-1, dp(48));
        controlParams.topMargin = dp(16); root.addView(controls, controlParams);
        countries = new Spinner(this);
        String[] labels = {"Nederland", "België", "Luxemburg", "Duitsland", "Wereldwijd"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels);
        countries.setAdapter(adapter);
        controls.addView(countries, new LinearLayout.LayoutParams(0, -1, 1));
        Button refresh = button("↻ Ververs");
        controls.addView(refresh);
        refresh.setOnClickListener(v -> loadStations());
        countries.setSelection(0);
        countries.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (!showingFavorites) loadStations();
            }
        });

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(52));
        searchParams.topMargin = dp(8); root.addView(searchRow, searchParams);
        searchInput = new EditText(this);
        searchInput.setSingleLine(); searchInput.setTextSize(15);
        searchInput.setHint("Zoek een radiozender...");
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) { loadStations(); return true; }
            return false;
        });
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, -1, 1));
        Button search = button("Zoeken");
        searchRow.addView(search); search.setOnClickListener(v -> loadStations());

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(-1, dp(46));
        tabParams.topMargin = dp(10); root.addView(tabs, tabParams);
        Button discover = button("Ontdekken");
        Button favorites = button("★ Favorieten");
        Button custom = button("+ Stream-URL");
        tabs.addView(discover, new LinearLayout.LayoutParams(0, -1, 1));
        tabs.addView(favorites, new LinearLayout.LayoutParams(0, -1, 1));
        tabs.addView(custom, new LinearLayout.LayoutParams(0, -1, 1));
        discover.setOnClickListener(v -> loadStations());
        favorites.setOnClickListener(v -> showFavorites());
        custom.setOnClickListener(v -> showCustomDialog());

        resultsTitle = text("Zenders laden…", 13, true);
        resultsTitle.setPadding(0, dp(14), 0, dp(9)); root.addView(resultsTitle);
        ScrollView scroll = new ScrollView(this);
        stationContainer = new LinearLayout(this);
        stationContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(stationContainer);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout playerCard = new LinearLayout(this);
        playerCard.setOrientation(LinearLayout.VERTICAL);
        playerCard.setPadding(dp(16), dp(13), dp(16), dp(12));
        playerCard.setBackground(card(Skin.SURFACE, Skin.STROKE));
        LinearLayout.LayoutParams playerParams = new LinearLayout.LayoutParams(-1, -2);
        playerParams.topMargin = dp(12); root.addView(playerCard, playerParams);
        TextView overline = text("NU AFGESPEELD", 10, true);
        overline.setTextColor(Skin.MUTED); playerCard.addView(overline);
        nowPlaying = text(selected != null ? selected.name : "Kies een zender", 20, true);
        playerCard.addView(nowPlaying);
        playbackStatus = text("Gereed", 12, false);
        playbackStatus.setTextColor(Skin.ACCENT); playerCard.addView(playbackStatus);
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(-1, dp(52));
        buttonsParams.topMargin = dp(10); playerCard.addView(buttons, buttonsParams);
        playPauseButton = button("AFSPELEN");
        Button stopButton = button("STOP");
        buttons.addView(playPauseButton, new LinearLayout.LayoutParams(0, -1, 2));
        buttons.addView(stopButton, new LinearLayout.LayoutParams(0, -1, 1));
        playPauseButton.setOnClickListener(v -> {
            if (selected == null) return;
            if (playing || buffering) {
                startService(new Intent(this, RadioService.class).setAction(RadioService.TOGGLE));
            } else if (!selected.stream.isEmpty()) {
                play(selected);
            }
        });
        stopButton.setOnClickListener(v -> startService(
                new Intent(this, RadioService.class).setAction(RadioService.STOP)));
    }

    private String currentCountry() {
        switch (countries.getSelectedItemPosition()) {
            case 0: return "NL";
            case 1: return "BE";
            case 2: return "LU";
            case 3: return "DE";
            default: return "";
        }
    }

    private void loadStations() {
        if (stationContainer == null || countries == null || searchInput == null) return;
        showingFavorites = false;
        resultsTitle.setText("Actuele zenders laden…");
        stationContainer.removeAllViews();
        repository.search(currentCountry(), searchInput.getText().toString(), new StationRepository.Callback() {
            @Override public void onResult(List<Station> result) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || showingFavorites) return;
                    stations = result; renderStations();
                });
            }
            @Override public void onError(String error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || showingFavorites) return;
                    resultsTitle.setText(error + "  •  Favorieten blijven beschikbaar.");
                });
            }
        });
    }

    private JSONObject favoriteData() {
        try { return new JSONObject(preferences.getString("favorites", "{}")); }
        catch (Exception ignored) { return new JSONObject(); }
    }

    private boolean isFavorite(Station station) { return favoriteData().has(station.id); }

    private void toggleFavorite(Station station) {
        JSONObject data = favoriteData();
        if (data.has(station.id)) data.remove(station.id);
        else { try { data.put(station.id, station.toJson()); } catch (Exception ignored) {} }
        preferences.edit().putString("favorites", data.toString()).apply();
        if (showingFavorites) showFavorites(); else renderStations();
    }

    private void showFavorites() {
        showingFavorites = true;
        stations = new ArrayList<>();
        JSONObject data = favoriteData();
        java.util.Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            JSONObject item = data.optJSONObject(keys.next());
            if (item != null) stations.add(Station.fromJson(item));
        }
        renderStations();
    }

    private void renderStations() {
        resultsTitle.setText((showingFavorites ? "Mijn favorieten" : "Gevonden zenders") + "  •  " + stations.size());
        stationContainer.removeAllViews();
        if (stations.isEmpty()) {
            TextView empty = text(showingFavorites ? "Nog geen favorieten. Tik op ☆ bij een station." :
                    "Geen geschikte radiostreams gevonden. Probeer een andere zoekopdracht.", 14, false);
            empty.setPadding(dp(8), dp(20), dp(8), dp(20)); stationContainer.addView(empty);
        }
        for (Station station : stations) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(9), dp(6), dp(9));
            row.setBackground(card(Skin.SURFACE, Skin.STROKE));
            LinearLayout.LayoutParams rowParam = new LinearLayout.LayoutParams(-1, dp(70));
            rowParam.bottomMargin = dp(7); stationContainer.addView(row, rowParam);
            LinearLayout description = new LinearLayout(this);
            description.setOrientation(LinearLayout.VERTICAL);
            TextView name = text(station.name, 15, true);
            name.setMaxLines(1); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            description.addView(name);
            TextView details = text(station.country + "  •  " + station.codec + "  •  LIVE", 11, false);
            details.setTextColor(Skin.MUTED); description.addView(details);
            row.addView(description, new LinearLayout.LayoutParams(0, -2, 1));
            Button star = button(isFavorite(station) ? "★" : "☆");
            star.setTextSize(23);
            row.addView(star, new LinearLayout.LayoutParams(dp(48), dp(48)));
            star.setOnClickListener(v -> toggleFavorite(station));
            row.setOnClickListener(v -> play(station));
        }
    }

    private void play(Station station) {
        selected = station;
        preferences.edit().putString("lastStation", station.toJson().toString()).apply();
        nowPlaying.setText(station.name);
        playbackStatus.setText("Verbinden…");
        Intent intent = new Intent(this, RadioService.class).setAction(RadioService.PLAY);
        intent.putExtra("name", station.name).putExtra("url", station.stream);
        startForegroundService(intent);
        // Keer na zenderkeuze direct terug naar de Walkman; de player blijft actief.
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    private void showCustomDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        content.setOrientation(LinearLayout.VERTICAL);
        EditText name = new EditText(this); name.setHint("Naam zender"); content.addView(name);
        EditText url = new EditText(this); url.setHint("https://.../stream.mp3");
        url.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        content.addView(url);
        new AlertDialog.Builder(this).setTitle("Eigen radiostream toevoegen")
                .setView(content).setNegativeButton("Annuleren", null)
                .setPositiveButton("Toevoegen", (dialog, which) -> {
                    Station st = new Station("custom-" + url.getText().toString().hashCode(),
                            name.getText().toString().trim(), url.getText().toString().trim(), "CUSTOM", "MP3/AAC");
                    if (!st.playable() || st.name.isEmpty()) {
                        new AlertDialog.Builder(this).setMessage("Vul een naam en geldige HTTP(S)-stream-URL in.")
                                .setPositiveButton("OK", null).show();
                        return;
                    }
                    if (!isFavorite(st)) toggleFavorite(st);
                    play(st);
                }).show();
    }
}

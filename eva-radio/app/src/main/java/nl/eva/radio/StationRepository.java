package nl.eva.radio;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Geen externe SDK nodig; de zendercatalogus wordt actueel uit Radio Browser geladen. */
public final class StationRepository {
    private static final String[] MIRRORS = {
            "https://de1.api.radio-browser.info",
            "https://de2.api.radio-browser.info",
            "https://nl1.api.radio-browser.info"
    };
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface Callback {
        void onResult(List<Station> stations);
        void onError(String description);
    }

    public void search(String country, String name, Callback callback) {
        executor.execute(() -> {
            StringBuilder path = new StringBuilder("/json/stations/search?hidebroken=true&order=clickcount&reverse=true&limit=120");
            if (!country.isEmpty()) path.append("&countrycode=").append(Uri.encode(country));
            if (!name.trim().isEmpty()) path.append("&name=").append(Uri.encode(name.trim()));
            Exception failure = null;
            for (String mirror : MIRRORS) {
                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection) new URL(mirror + path).openConnection();
                    connection.setRequestProperty("User-Agent", "EVA-Radio/0.1 (Android; station directory)");
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setConnectTimeout(6000);
                    connection.setReadTimeout(10000);
                    if (connection.getResponseCode() != 200) throw new Exception("HTTP " + connection.getResponseCode());
                    StringBuilder response = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) response.append(line);
                    }
                    JSONArray data = new JSONArray(response.toString());
                    LinkedHashMap<String, Station> unique = new LinkedHashMap<>();
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject obj = data.optJSONObject(i);
                        if (obj == null) continue;
                        Station st = Station.fromApi(obj);
                        String codec = st.codec.toLowerCase(java.util.Locale.ROOT);
                        if (!st.playable() || st.name.isEmpty()) continue;
                        if (!(codec.isEmpty() || codec.contains("mp3") || codec.contains("aac") || codec.contains("hls"))) continue;
                        unique.put(st.id, st);
                    }
                    callback.onResult(new ArrayList<>(unique.values()));
                    return;
                } catch (Exception error) {
                    failure = error;
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
            callback.onError("De zenderlijst is momenteel niet bereikbaar: " +
                    (failure == null ? "onbekende fout" : failure.getMessage()));
        });
    }

    public void shutdown() { executor.shutdownNow(); }
}

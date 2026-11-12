package nl.eva.radio;

import org.json.JSONException;
import org.json.JSONObject;

public final class Station {
    public final String id;
    public final String name;
    public final String stream;
    public final String country;
    public final String codec;

    public Station(String id, String name, String stream, String country, String codec) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "Onbekend station" : name.trim();
        this.stream = stream == null ? "" : stream.trim();
        this.country = country == null ? "" : country;
        this.codec = codec == null ? "" : codec;
    }

    public static Station fromApi(JSONObject j) {
        String url = j.optString("url_resolved", "");
        if (url.isEmpty()) url = j.optString("url", "");
        return new Station(j.optString("stationuuid", url), j.optString("name", ""), url,
                j.optString("countrycode", ""), j.optString("codec", ""));
    }

    public static Station fromJson(JSONObject j) {
        return new Station(j.optString("id"), j.optString("name"), j.optString("stream"),
                j.optString("country"), j.optString("codec"));
    }

    public JSONObject toJson() {
        JSONObject j = new JSONObject();
        try {
            j.put("id", id); j.put("name", name); j.put("stream", stream);
            j.put("country", country); j.put("codec", codec);
        } catch (JSONException ignored) {}
        return j;
    }

    public boolean playable() {
        String u = stream.toLowerCase(java.util.Locale.ROOT);
        if (!(u.startsWith("https://") || u.startsWith("http://"))) return false;
        String path = u.split("\\?", 2)[0];
        return !(path.endsWith(".pls") || path.endsWith(".asx") || path.endsWith(".xspf")
                || (path.endsWith(".m3u") && !path.endsWith(".m3u8")));
    }
}

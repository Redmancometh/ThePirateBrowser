package com.thepiratebrowser.android;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TorrentSearchService {
    public static final String SOURCE_TPB = "The Pirate Bay";
    public static final String SOURCE_NYAA = "Nyaa";
    public static final String SOURCE_EZTV = "EZTV";
    public static final String SOURCE_YTS = "YTS";

    public static final List<String> SOURCES = Collections.unmodifiableList(Arrays.asList(
            SOURCE_TPB, SOURCE_NYAA, SOURCE_EZTV, SOURCE_YTS
    ));

    private static final Pattern HASH_PATTERN =
            Pattern.compile("(?i)btih:([a-z0-9]{32,40})");

    private final SharedPreferences preferences;

    public TorrentSearchService(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public SearchOutcome search(String query) {
        List<Callable<List<TorrentResult>>> searches = new ArrayList<>();
        if (enabled(SOURCE_TPB)) searches.add(() -> searchPirateBay(query));
        if (enabled(SOURCE_NYAA)) searches.add(() -> searchNyaa(query));
        if (enabled(SOURCE_EZTV)) searches.add(() -> searchEztv(query));
        if (enabled(SOURCE_YTS)) searches.add(() -> searchYts(query));

        if (searches.isEmpty()) {
            return new SearchOutcome(
                    Collections.emptyList(),
                    Collections.singletonList("No torrent sources are enabled."));
        }

        ExecutorService pool = Executors.newFixedThreadPool(searches.size());
        List<TorrentResult> combined = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        try {
            List<Future<List<TorrentResult>>> futures = pool.invokeAll(searches);
            for (Future<List<TorrentResult>> future : futures) {
                try {
                    combined.addAll(future.get());
                } catch (Exception error) {
                    failures.add(rootMessage(error));
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            failures.add("Search was interrupted.");
        } finally {
            pool.shutdownNow();
        }

        Map<String, TorrentResult> deduplicated = new LinkedHashMap<>();
        for (TorrentResult result : combined) {
            deduplicated.putIfAbsent(identity(result), result);
        }
        List<TorrentResult> results = new ArrayList<>(deduplicated.values());
        results.sort(Comparator.comparingInt((TorrentResult value) -> value.seeders).reversed());
        return new SearchOutcome(results, failures);
    }

    public boolean enabled(String source) {
        return preferences.getBoolean("source." + source, true);
    }

    public void setEnabled(String source, boolean enabled) {
        preferences.edit().putBoolean("source." + source, enabled).apply();
    }

    private List<TorrentResult> searchPirateBay(String query) throws Exception {
        JSONArray array = new JSONArray(get(
                "https://apibay.org/q.php?q=" + encode(query)
        ));
        List<TorrentResult> results = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            String hash = item.optString("info_hash");
            if (hash.trim().isEmpty() || "0".equals(item.optString("id"))) continue;
            String name = item.optString("name", "Untitled torrent");
            results.add(new TorrentResult(
                    name,
                    SOURCE_TPB,
                    magnet(hash, name),
                    parseLong(item.optString("size")),
                    parseInt(item.optString("seeders")),
                    parseInt(item.optString("leechers"))
            ));
        }
        return results;
    }

    private List<TorrentResult> searchNyaa(String query) throws Exception {
        Document document = Jsoup.connect(
                        "https://nyaa.si/?f=0&c=0_0&q=" + encode(query))
                .userAgent("PirateBrowser/1.0")
                .timeout(15_000)
                .get();
        List<TorrentResult> results = new ArrayList<>();
        for (Element row : document.select("table.torrent-list tbody tr")) {
            Element magnet = row.selectFirst("a[href^=magnet:]");
            Element title = row.selectFirst("td:nth-child(2) a:not(.comments)");
            if (magnet == null || title == null) continue;
            List<Element> cells = row.select("td");
            long size = cells.size() > 3 ? parseHumanSize(cells.get(3).text()) : 0;
            int seeders = cells.size() > 5 ? parseInt(cells.get(5).text()) : 0;
            int leechers = cells.size() > 6 ? parseInt(cells.get(6).text()) : 0;
            results.add(new TorrentResult(
                    title.attr("title").trim().isEmpty() ? title.text() : title.attr("title"),
                    SOURCE_NYAA,
                    magnet.attr("href"),
                    size,
                    seeders,
                    leechers
            ));
        }
        return results;
    }

    private List<TorrentResult> searchEztv(String query) throws Exception {
        JSONObject root = new JSONObject(get(
                "https://eztvx.to/api/get-torrents?limit=100&page=1"
        ));
        JSONArray torrents = root.optJSONArray("torrents");
        List<TorrentResult> results = new ArrayList<>();
        if (torrents == null) return results;
        String normalized = query.toLowerCase(Locale.ROOT);
        for (int i = 0; i < torrents.length(); i++) {
            JSONObject item = torrents.getJSONObject(i);
            String name = item.optString("filename");
            if (!name.toLowerCase(Locale.ROOT).contains(normalized)) continue;
            results.add(new TorrentResult(
                    name,
                    SOURCE_EZTV,
                    item.optString("magnet_url"),
                    item.optLong("size_bytes"),
                    item.optInt("seeds"),
                    item.optInt("peers")
            ));
        }
        return results;
    }

    private List<TorrentResult> searchYts(String query) throws Exception {
        JSONObject root = new JSONObject(get(
                "https://yts.mx/api/v2/list_movies.json?limit=50&query_term=" + encode(query)
        ));
        JSONObject data = root.optJSONObject("data");
        JSONArray movies = data == null ? null : data.optJSONArray("movies");
        List<TorrentResult> results = new ArrayList<>();
        if (movies == null) return results;
        for (int i = 0; i < movies.length(); i++) {
            JSONObject movie = movies.getJSONObject(i);
            JSONArray torrents = movie.optJSONArray("torrents");
            if (torrents == null) continue;
            for (int j = 0; j < torrents.length(); j++) {
                JSONObject torrent = torrents.getJSONObject(j);
                String name = movie.optString("title_long")
                        + " [" + torrent.optString("quality") + " "
                        + torrent.optString("type") + "]";
                results.add(new TorrentResult(
                        name,
                        SOURCE_YTS,
                        magnet(torrent.optString("hash"), name),
                        torrent.optLong("size_bytes"),
                        torrent.optInt("seeds"),
                        torrent.optInt("peers")
                ));
            }
        }
        return results;
    }

    private static String get(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(address).toURL().openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "application/json,text/html");
        connection.setRequestProperty("User-Agent", "PirateBrowser-Android/1.0");
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String body = read(stream);
        connection.disconnect();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("Torrent source returned HTTP " + status);
        }
        return body;
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) text.append(line).append('\n');
            return text.toString();
        }
    }

    private static String identity(TorrentResult result) {
        Matcher matcher = HASH_PATTERN.matcher(result.magnet);
        return matcher.find()
                ? matcher.group(1).toLowerCase(Locale.ROOT)
                : result.magnet;
    }

    private static String magnet(String hash, String name) {
        return "magnet:?xt=urn:btih:" + hash + "&dn=" + encode(name);
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static long parseHumanSize(String value) {
        Matcher matcher = Pattern.compile("([0-9.]+)\\s*([KMGT]?i?B)", Pattern.CASE_INSENSITIVE)
                .matcher(value);
        if (!matcher.find()) return 0;
        double number = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2).toUpperCase(Locale.ROOT);
        int power = unit.startsWith("K") ? 1
                : unit.startsWith("M") ? 2
                : unit.startsWith("G") ? 3
                : unit.startsWith("T") ? 4 : 0;
        return (long) (number * Math.pow(1024, power));
    }

    private static String rootMessage(Exception error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    public static final class SearchOutcome {
        public final List<TorrentResult> results;
        public final List<String> failures;

        public SearchOutcome(List<TorrentResult> results, List<String> failures) {
            this.results = results;
            this.failures = failures;
        }
    }
}

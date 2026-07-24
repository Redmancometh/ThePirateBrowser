package com.thepiratebrowser.web.search;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TorrentSearchService {
    private static final Pattern HASH =
            Pattern.compile("(?i)btih:([a-z0-9]{32,40})");

    private final ObjectMapper mapper;
    private final HttpClient http;

    public TorrentSearchService(ObjectMapper mapper) {
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public SearchOutcome search(String query, Set<TorrentSource> enabled) {
        if (enabled.isEmpty()) {
            return new SearchOutcome(List.of(), List.of("No torrent sources are enabled."));
        }
        Map<TorrentSource, Callable<List<TorrentResult>>> calls =
                new EnumMap<>(TorrentSource.class);
        for (TorrentSource source : enabled) {
            calls.put(source, () -> searchSource(source, query));
        }

        List<TorrentResult> combined = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Map<TorrentSource, Future<List<TorrentResult>>> futures =
                    new EnumMap<>(TorrentSource.class);
            calls.forEach((source, call) -> futures.put(source, executor.submit(call)));
            futures.forEach((source, future) -> {
                try {
                    combined.addAll(future.get());
                } catch (Exception error) {
                    failures.add(source.displayName() + ": " + rootMessage(error));
                }
            });
        }

        Map<String, TorrentResult> unique = new LinkedHashMap<>();
        for (TorrentResult result : combined) {
            String identity = identity(result);
            TorrentResult previous = unique.get(identity);
            if (previous == null || result.seeders() > previous.seeders()) {
                unique.put(identity, result);
            }
        }
        List<TorrentResult> results = new ArrayList<>(unique.values());
        results.sort(Comparator.comparingInt(TorrentResult::seeders).reversed());
        return new SearchOutcome(List.copyOf(results), List.copyOf(failures));
    }

    private List<TorrentResult> searchSource(TorrentSource source, String query) throws Exception {
        return switch (source) {
            case PIRATE_BAY -> pirateBay(query);
            case KNABEN -> knaben(query);
            case MAGNETZ -> magnetz(query);
            case TORRENTS_CSV -> torrentsCsv(query);
            case NYAA -> nyaa(query);
            case EZTV -> eztv(query);
            case YTS -> yts(query);
        };
    }

    private List<TorrentResult> pirateBay(String query) throws Exception {
        JsonNode root = getJson("https://apibay.org/q.php?q=" + encode(query));
        List<TorrentResult> results = new ArrayList<>();
        for (JsonNode item : root) {
            String hash = text(item, "info_hash");
            if (hash.isBlank() || "0".equals(text(item, "id"))) {
                continue;
            }
            String name = text(item, "name", "Untitled torrent");
            results.add(result(
                    name, TorrentSource.PIRATE_BAY, hash,
                    number(item, "size"), integer(item, "seeders"), integer(item, "leechers")));
        }
        return results;
    }

    private List<TorrentResult> knaben(String query) throws Exception {
        String request = mapper.writeValueAsString(Map.of(
                "search_type", "100%",
                "search_field", "title",
                "query", query,
                "order_by", "seeders",
                "order_direction", "desc",
                "from", 0,
                "size", 150,
                "hide_unsafe", true,
                "hide_xxx", true
        ));
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create("https://api.knaben.org/v1"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "PirateBrowser-Web/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(request))
                .build();
        JsonNode hits = json(send(httpRequest)).path("hits");
        List<TorrentResult> results = new ArrayList<>();
        for (JsonNode item : hits) {
            String hash = text(item, "hash");
            if (hash.isBlank()) continue;
            String name = text(item, "title", "Untitled torrent");
            results.add(result(
                    name, TorrentSource.KNABEN, hash,
                    number(item, "bytes"), integer(item, "seeders"), integer(item, "peers")));
        }
        return results;
    }

    private List<TorrentResult> magnetz(String query) throws Exception {
        JsonNode data = getJson(
                "https://magnetz.eu/api/magnets/search?query=" + encode(query) + "&page=1")
                .path("data");
        List<TorrentResult> results = new ArrayList<>();
        for (JsonNode item : data) {
            String hash = text(item, "info_hash");
            if (hash.isBlank()) continue;
            String name = text(item, "name", "Untitled torrent");
            results.add(result(
                    name, TorrentSource.MAGNETZ, hash,
                    number(item, "size"), integer(item, "seeders"), integer(item, "leechers")));
        }
        return results;
    }

    private List<TorrentResult> torrentsCsv(String query) throws Exception {
        JsonNode torrents = getJson(
                "https://torrents-csv.com/service/search?q=" + encode(query)
                        + "&size=50&page=1").path("torrents");
        List<TorrentResult> results = new ArrayList<>();
        for (JsonNode item : torrents) {
            String hash = text(item, "infohash");
            if (hash.isBlank()) continue;
            String name = text(item, "name", "Untitled torrent");
            results.add(result(
                    name, TorrentSource.TORRENTS_CSV, hash,
                    number(item, "size_bytes"), integer(item, "seeders"),
                    integer(item, "leechers")));
        }
        return results;
    }

    private List<TorrentResult> nyaa(String query) throws Exception {
        Document document = Jsoup.connect(
                        "https://nyaa.si/?f=0&c=0_0&q=" + encode(query))
                .userAgent("PirateBrowser-Web/1.0")
                .timeout(20_000)
                .get();
        List<TorrentResult> results = new ArrayList<>();
        for (Element row : document.select("table.torrent-list tbody tr")) {
            Element magnet = row.selectFirst("a[href^=magnet:]");
            Element title = row.selectFirst("td:nth-child(2) a:not(.comments)");
            if (magnet == null || title == null) continue;
            List<Element> cells = row.select("td");
            results.add(new TorrentResult(
                    title.attr("title").isBlank() ? title.text() : title.attr("title"),
                    TorrentSource.NYAA,
                    magnet.attr("href"),
                    cells.size() > 3 ? humanSize(cells.get(3).text()) : 0,
                    cells.size() > 5 ? parseInt(cells.get(5).text()) : 0,
                    cells.size() > 6 ? parseInt(cells.get(6).text()) : 0
            ));
        }
        return results;
    }

    private List<TorrentResult> eztv(String query) throws Exception {
        JsonNode torrents = getJson(
                "https://eztvx.to/api/get-torrents?limit=100&page=1").path("torrents");
        String normalized = query.toLowerCase(Locale.ROOT);
        List<TorrentResult> results = new ArrayList<>();
        for (JsonNode item : torrents) {
            String name = text(item, "filename");
            if (!name.toLowerCase(Locale.ROOT).contains(normalized)) continue;
            results.add(new TorrentResult(
                    name,
                    TorrentSource.EZTV,
                    text(item, "magnet_url"),
                    number(item, "size_bytes"),
                    integer(item, "seeds"),
                    integer(item, "peers")
            ));
        }
        return results;
    }

    private List<TorrentResult> yts(String query) throws Exception {
        JsonNode movies = getJson(
                "https://movies-api.accel.li/api/v2/list_movies.json?limit=50&query_term="
                        + encode(query)).path("data").path("movies");
        List<TorrentResult> results = new ArrayList<>();
        for (JsonNode movie : movies) {
            String title = text(movie, "title_long", text(movie, "title"));
            for (JsonNode torrent : movie.path("torrents")) {
                String hash = text(torrent, "hash");
                if (hash.isBlank()) continue;
                String quality = text(torrent, "quality");
                String name = quality.isBlank() ? title : title + " " + quality;
                results.add(result(
                        name, TorrentSource.YTS, hash,
                        number(torrent, "size_bytes"), integer(torrent, "seeds"),
                        integer(torrent, "peers")));
            }
        }
        return results;
    }

    private TorrentResult result(
            String name,
            TorrentSource source,
            String hash,
            long size,
            int seeders,
            int leechers
    ) {
        return new TorrentResult(name, source, magnet(hash, name), size, seeders, leechers);
    }

    private JsonNode getJson(String address) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(address))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "PirateBrowser-Web/1.0")
                .GET()
                .build();
        return json(send(request));
    }

    private String send(HttpRequest request) throws Exception {
        HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private JsonNode json(String body) throws Exception {
        return mapper.readTree(body);
    }

    static String magnet(String hash, String name) {
        return "magnet:?xt=urn:btih:" + hash + "&dn=" + encode(name);
    }

    private static String identity(TorrentResult result) {
        Matcher matcher = HASH.matcher(result.magnet());
        return matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : result.magnet();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String text(JsonNode item, String key) {
        return text(item, key, "");
    }

    private static String text(JsonNode item, String key, String fallback) {
        String value = item.path(key).asText("");
        return value.isBlank() ? fallback : value;
    }

    private static long number(JsonNode item, String key) {
        JsonNode value = item.path(key);
        return value.isNumber() ? value.asLong() : parseLong(value.asText());
    }

    private static int integer(JsonNode item, String key) {
        JsonNode value = item.path(key);
        return value.isNumber() ? value.asInt() : parseInt(value.asText());
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9-]", ""));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.replaceAll("[^0-9-]", ""));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static long humanSize(String value) {
        Matcher matcher = Pattern.compile("(?i)([0-9.]+)\\s*([kmgt]?i?b)").matcher(value);
        if (!matcher.find()) return 0;
        double number = Double.parseDouble(matcher.group(1));
        long multiplier = switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
            case "kb", "kib" -> 1L << 10;
            case "mb", "mib" -> 1L << 20;
            case "gb", "gib" -> 1L << 30;
            case "tb", "tib" -> 1L << 40;
            default -> 1;
        };
        return (long) (number * multiplier);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }
}

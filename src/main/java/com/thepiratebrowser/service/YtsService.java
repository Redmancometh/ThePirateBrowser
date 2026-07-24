package com.thepiratebrowser.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thepiratebrowser.model.TorrentResult;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class YtsService implements TorrentSource {
    private static final String API_URL =
            "https://movies-api.accel.li/api/v2/list_movies.json";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public YtsService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return "yts";
    }

    @Override
    public String name() {
        return "YTS";
    }

    @Override
    public List<TorrentResult> search(String query, int minimumSeeders) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        URI uri = URI.create(API_URL + "?limit=50&query_term="
                + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("YTS search failed: HTTP " + response.statusCode());
            }
            return parseResults(response.body(), minimumSeeders);
        } catch (IOException exception) {
            throw new IllegalStateException("YTS search failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("YTS search was interrupted", exception);
        }
    }

    List<TorrentResult> parseResults(String json, int minimumSeeders) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        if (!"ok".equalsIgnoreCase(root.path("status").asText())) {
            throw new IllegalStateException("YTS returned an unsuccessful response.");
        }
        JsonNode movies = root.path("data").path("movies");
        if (movies.isMissingNode() || movies.isNull()) {
            return List.of();
        }
        List<TorrentResult> results = new ArrayList<>();
        for (JsonNode movie : movies) {
            for (JsonNode torrent : movie.path("torrents")) {
                int seeders = torrent.path("seeds").asInt();
                String hash = torrent.path("hash").asText();
                if (hash.isBlank() || seeders < minimumSeeders) {
                    continue;
                }
                String details = String.join(" ",
                        torrent.path("quality").asText(),
                        torrent.path("type").asText()).trim();
                String title = movie.path("title_long").asText(movie.path("title").asText());
                if (!details.isBlank()) {
                    title += " [" + details + "]";
                }
                results.add(new TorrentResult(
                        movie.path("id").asText() + "-" + hash,
                        title,
                        hash.toUpperCase(Locale.ROOT),
                        torrent.path("size_bytes").asLong(),
                        seeders,
                        torrent.path("peers").asInt(),
                        "YTS",
                        "trusted",
                        "Movies",
                        Instant.ofEpochSecond(torrent.path("date_uploaded_unix")
                                .asLong(movie.path("date_uploaded_unix").asLong())),
                        false,
                        id(),
                        name(),
                    movie.path("url").asText("https://yts.proxyninja.net")
                ));
            }
        }
        results.sort(Comparator.comparingInt(TorrentResult::seeders).reversed());
        return results;
    }
}

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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class MagnetzService implements TorrentSource {
    private static final String API_URL = "https://magnetz.eu/api/magnets/search";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MagnetzService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return "magnetz";
    }

    @Override
    public String name() {
        return "Magnetz";
    }

    @Override
    public List<TorrentResult> search(String query, int minimumSeeders) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        URI uri = URI.create(API_URL + "?query="
                + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8) + "&page=1");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Magnetz search failed: HTTP " + response.statusCode());
            }
            return parseResults(response.body(), minimumSeeders);
        } catch (IOException exception) {
            throw new IllegalStateException("Magnetz search failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Magnetz search was interrupted", exception);
        }
    }

    List<TorrentResult> parseResults(String json, int minimumSeeders) throws IOException {
        JsonNode data = objectMapper.readTree(json).path("data");
        if (!data.isArray()) {
            throw new IllegalStateException("Magnetz response was missing data.");
        }
        List<TorrentResult> results = new ArrayList<>();
        for (JsonNode item : data) {
            String hash = item.path("info_hash").asText();
            int seeders = item.path("seeders").asInt();
            if (hash.isBlank() || seeders < minimumSeeders) {
                continue;
            }
            String id = item.path("sqid").asText(hash);
            results.add(new TorrentResult(
                    id,
                    item.path("name").asText("Untitled torrent"),
                    hash.toUpperCase(Locale.ROOT),
                    item.path("size").asLong(),
                    seeders,
                    item.path("leechers").asInt(),
                    "Magnetz",
                    item.path("is_verified").asBoolean() ? "verified" : "indexed",
                    item.path("release").path("type").asText("Other"),
                    instant(item.path("created_at").asText()),
                    false,
                    id(),
                    name(),
                    item.path("web_url").asText("https://magnetz.eu")
            ));
        }
        results.sort(Comparator.comparingInt(TorrentResult::seeders).reversed());
        return results;
    }

    private static Instant instant(String value) {
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (Exception ignored) {
            return Instant.EPOCH;
        }
    }
}

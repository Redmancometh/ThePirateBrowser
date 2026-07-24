package com.thepiratebrowser.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thepiratebrowser.model.TorrentResult;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class KnabenService implements TorrentSource {
    private static final URI API_URL = URI.create("https://api.knaben.org/v1");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public KnabenService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return "knaben";
    }

    @Override
    public String name() {
        return "Knaben";
    }

    @Override
    public List<TorrentResult> search(String query, int minimumSeeders) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "search_type", "100%",
                    "search_field", "title",
                    "query", query.trim(),
                    "order_by", "seeders",
                    "order_direction", "desc",
                    "from", 0,
                    "size", 150,
                    "hide_unsafe", true,
                    "hide_xxx", true
            ));
            HttpRequest request = HttpRequest.newBuilder(API_URL)
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Knaben search failed: HTTP " + response.statusCode());
            }
            return parseResults(response.body(), minimumSeeders);
        } catch (IOException exception) {
            throw new IllegalStateException("Knaben search failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Knaben search was interrupted", exception);
        }
    }

    List<TorrentResult> parseResults(String json, int minimumSeeders) throws IOException {
        JsonNode hits = objectMapper.readTree(json).path("hits");
        if (!hits.isArray()) {
            throw new IllegalStateException("Knaben response was missing hits.");
        }
        List<TorrentResult> results = new ArrayList<>();
        for (JsonNode item : hits) {
            String hash = item.path("hash").asText();
            int seeders = item.path("seeders").asInt();
            if (hash.isBlank() || seeders < minimumSeeders) {
                continue;
            }
            String id = item.path("id").asText(hash);
            String tracker = item.path("tracker").asText(
                    item.path("cachedOrigin").asText("Knaben"));
            results.add(new TorrentResult(
                    id,
                    item.path("title").asText("Untitled torrent"),
                    hash.toUpperCase(Locale.ROOT),
                    item.path("bytes").asLong(),
                    seeders,
                    item.path("peers").asInt(),
                    tracker,
                    "indexed",
                    item.path("category").asText("Other"),
                    instant(item.path("date").asText()),
                    false,
                    id(),
                    name(),
                    item.path("details").asText("https://knaben.org")
            ));
        }
        results.sort(Comparator.comparingInt(TorrentResult::seeders).reversed());
        return results;
    }

    private static Instant instant(String value) {
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
            } catch (Exception alsoIgnored) {
                return Instant.EPOCH;
            }
        }
    }
}

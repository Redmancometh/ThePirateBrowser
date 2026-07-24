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
public class TorrentsCsvService implements TorrentSource {
    private static final String API_URL = "https://torrents-csv.com/service/search";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TorrentsCsvService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return "torrents-csv";
    }

    @Override
    public String name() {
        return "Torrents.csv";
    }

    @Override
    public List<TorrentResult> search(String query, int minimumSeeders) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        URI uri = URI.create(API_URL + "?q=" + encodedQuery + "&size=50&page=1");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Torrents.csv search failed: HTTP " + response.statusCode());
            }
            return parseResults(response.body(), minimumSeeders);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Torrents.csv search failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Torrents.csv search was interrupted", exception);
        }
    }

    List<TorrentResult> parseResults(String json, int minimumSeeders) throws IOException {
        JsonNode torrents = objectMapper.readTree(json).path("torrents");
        if (!torrents.isArray()) {
            throw new IllegalStateException("Torrents.csv response was missing torrents.");
        }
        List<TorrentResult> results = new ArrayList<>();
        for (JsonNode item : torrents) {
            String hash = item.path("infohash").asText();
            int seeders = item.path("seeders").asInt();
            if (hash.isBlank() || seeders < minimumSeeders) {
                continue;
            }
            String id = item.path("id").asText(hash);
            results.add(new TorrentResult(
                    id,
                    item.path("name").asText(),
                    hash.toUpperCase(Locale.ROOT),
                    item.path("size_bytes").asLong(),
                    seeders,
                    item.path("leechers").asInt(),
                    "Torrents.csv",
                    "indexed",
                    "Other",
                    Instant.ofEpochSecond(item.path("created_unix").asLong()),
                    false,
                    id(),
                    name(),
                    "https://torrents-csv.com/#/search?q="
                            + URLEncoder.encode(item.path("name").asText(), StandardCharsets.UTF_8)
            ));
        }
        results.sort(Comparator.comparingInt(TorrentResult::seeders).reversed());
        return results;
    }
}

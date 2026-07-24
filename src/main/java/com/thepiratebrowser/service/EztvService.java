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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class EztvService implements TorrentSource {
    private static final URI LATEST =
            URI.create("https://eztvx.to/api/get-torrents?limit=100&page=1");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public EztvService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return "eztv";
    }

    @Override
    public String name() {
        return "EZTV";
    }

    @Override
    public List<TorrentResult> search(String query, int minimumSeeders) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        HttpRequest request = HttpRequest.newBuilder(LATEST)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("EZTV search failed: HTTP " + response.statusCode());
            }
            return parseResults(response.body(), query, minimumSeeders);
        } catch (IOException exception) {
            throw new IllegalStateException("EZTV search failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("EZTV search was interrupted", exception);
        }
    }

    List<TorrentResult> parseResults(String json, String query, int minimumSeeders) throws IOException {
        JsonNode torrents = objectMapper.readTree(json).path("torrents");
        if (!torrents.isArray()) {
            throw new IllegalStateException("EZTV response was missing torrents.");
        }
        List<String> terms = Arrays.stream(query.toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(term -> !term.isBlank())
                .toList();
        List<TorrentResult> results = new ArrayList<>();
        for (JsonNode item : torrents) {
            String title = item.path("title").asText(item.path("filename").asText());
            String searchable = title.toLowerCase(Locale.ROOT);
            int seeders = item.path("seeds").asInt();
            String hash = item.path("hash").asText();
            if (hash.isBlank() || seeders < minimumSeeders
                    || terms.stream().anyMatch(term -> !searchable.contains(term))) {
                continue;
            }
            String torrentId = item.path("id").asText();
            results.add(new TorrentResult(
                    torrentId,
                    title,
                    hash.toUpperCase(Locale.ROOT),
                    item.path("size_bytes").asLong(),
                    seeders,
                    item.path("peers").asInt(),
                    "EZTV",
                    "trusted",
                    "TV",
                    Instant.ofEpochSecond(item.path("date_released_unix").asLong()),
                    false,
                    id(),
                    name(),
                    "https://eztvx.to/ep/" + torrentId + "/"
            ));
        }
        results.sort(Comparator.comparingInt(TorrentResult::seeders).reversed());
        return results;
    }
}

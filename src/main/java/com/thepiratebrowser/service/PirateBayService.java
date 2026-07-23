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

@Service
public class PirateBayService {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final LocalSettingsService settingsService;

    public PirateBayService(HttpClient httpClient, ObjectMapper objectMapper, LocalSettingsService settingsService) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.settingsService = settingsService;
    }

    public List<TorrentResult> search(String query, int minimumSeeders) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String baseUrl = settingsService.get().getPirateBayApiBaseUrl().replaceAll("/+$", "");
        URI uri = URI.create(baseUrl + "/q.php?q="
                + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8) + "&cat=");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Pirate Bay search failed: HTTP " + response.statusCode());
            }
            return parseResults(response.body(), minimumSeeders);
        } catch (IOException exception) {
            throw new IllegalStateException("Pirate Bay search failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Pirate Bay search was interrupted", exception);
        }
    }

    List<TorrentResult> parseResults(String json, int minimumSeeders) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        List<TorrentResult> results = new ArrayList<>();
        for (JsonNode item : root) {
            if ("0".equals(item.path("id").asText()) || item.path("info_hash").asText().isBlank()) {
                continue;
            }
            int seeders = parseInt(item.path("seeders").asText());
            if (seeders < minimumSeeders) {
                continue;
            }
            results.add(new TorrentResult(
                    item.path("id").asText(),
                    decodeEntities(item.path("name").asText()),
                    item.path("info_hash").asText(),
                    parseLong(item.path("size").asText()),
                    seeders,
                    parseInt(item.path("leechers").asText()),
                    item.path("username").asText(),
                    item.path("status").asText(),
                    item.path("category").asText(),
                    Instant.ofEpochSecond(parseLong(item.path("added").asText())),
                    false
            ));
        }
        results.sort(Comparator.comparingInt(TorrentResult::seeders).reversed());
        return results;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String decodeEntities(String value) {
        return value.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }
}

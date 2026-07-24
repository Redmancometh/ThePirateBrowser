package com.thepiratebrowser.web.putio;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.thepiratebrowser.web.config.PirateProperties;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PutIoService {
    private static final String API = "https://api.put.io/v2";

    private final PirateProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public PutIoService(PirateProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    public boolean configured() {
        return properties.putIoConfigured();
    }

    public void addTransfer(String magnet) {
        if (magnet == null || !magnet.startsWith("magnet:?")) {
            throw new IllegalArgumentException("A valid magnet link is required.");
        }
        postForm("/transfers/add", Map.of("url", magnet));
    }

    public List<TransferView> transfers() {
        JsonNode items = getJson("/transfers/list").path("transfers");
        List<TransferView> result = new ArrayList<>();
        for (JsonNode item : items) {
            result.add(new TransferView(
                    item.path("id").asLong(),
                    item.path("name").asText("Unnamed transfer"),
                    item.path("status").asText("UNKNOWN"),
                    item.path("percent_done").asDouble(),
                    item.path("size").asLong(),
                    item.path("file_id").isNumber() ? item.path("file_id").asLong() : null
            ));
        }
        return result;
    }

    public void cancelTransfer(long id) {
        postForm("/transfers/cancel", Map.of("transfer_ids", String.valueOf(id)));
    }

    public FileListing files(long parentId) {
        JsonNode root = getJson("/files/list?parent_id=" + parentId);
        List<FileView> files = new ArrayList<>();
        for (JsonNode item : root.path("files")) {
            String contentType = item.path("content_type").asText("");
            boolean directory = "application/x-directory".equals(contentType)
                    || "FOLDER".equalsIgnoreCase(item.path("file_type").asText(""));
            files.add(new FileView(
                    item.path("id").asLong(),
                    item.path("name").asText("Unnamed file"),
                    item.path("size").asLong(),
                    contentType,
                    directory
            ));
        }
        files.sort((left, right) -> {
            if (left.directory() != right.directory()) {
                return left.directory() ? -1 : 1;
            }
            return left.name().compareToIgnoreCase(right.name());
        });
        return new FileListing(parentId, files);
    }

    public void renameFile(long id, String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 255
                || trimmed.contains("/") || trimmed.contains("\\")) {
            throw new IllegalArgumentException("Enter a valid file name.");
        }
        postForm("/files/rename", Map.of(
                "file_id", String.valueOf(id),
                "name", trimmed
        ));
    }

    public void deleteFile(long id) {
        postForm("/files/delete", Map.of("file_ids", String.valueOf(id)));
    }

    public RemoteContent content(long fileId, String range) {
        String token = token();
        HttpRequest.Builder request = HttpRequest.newBuilder(
                        URI.create(API + "/files/" + fileId + "/download?oauth_token="
                                + encode(token)))
                .timeout(Duration.ofMinutes(2))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "*/*")
                .GET();
        if (range != null && !range.isBlank()) {
            request.header("Range", range);
        }
        try {
            HttpResponse<InputStream> response =
                    http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 400) {
                response.body().close();
                throw new IllegalStateException(
                        "put.io media request returned HTTP " + response.statusCode());
            }
            return new RemoteContent(
                    response.statusCode(),
                    response.headers().firstValue("content-type")
                            .orElse("application/octet-stream"),
                    response.headers().firstValue("content-length").orElse(null),
                    response.headers().firstValue("content-range").orElse(null),
                    response.headers().firstValue("accept-ranges").orElse("bytes"),
                    response.body()
            );
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The put.io media request was interrupted.", error);
        } catch (Exception error) {
            if (error instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Could not stream the put.io file.", error);
        }
    }

    private JsonNode getJson(String path) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(API + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token())
                .header("Accept", "application/json")
                .header("User-Agent", "PirateBrowser-Web/1.0")
                .GET()
                .build();
        return json(send(request));
    }

    private JsonNode postForm(String path, Map<String, String> values) {
        String body = values.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        HttpRequest request = HttpRequest.newBuilder(URI.create(API + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token())
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "PirateBrowser-Web/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return json(send(request));
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("put.io returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("put.io request was interrupted.", error);
        } catch (Exception error) {
            if (error instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("put.io request failed.", error);
        }
    }

    private JsonNode json(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception error) {
            throw new IllegalStateException("put.io returned unreadable data.", error);
        }
    }

    private String token() {
        if (!properties.putIoConfigured()) {
            throw new PutIoNotConfiguredException();
        }
        return properties.putioToken();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record TransferView(
            long id,
            String name,
            String status,
            double percentDone,
            long size,
            Long fileId
    ) {
    }

    public record FileView(
            long id,
            String name,
            long size,
            String contentType,
            boolean directory
    ) {
    }

    public record FileListing(long parentId, List<FileView> files) {
    }

    public record RemoteContent(
            int status,
            String contentType,
            String contentLength,
            String contentRange,
            String acceptRanges,
            InputStream body
    ) {
    }

    public static final class PutIoNotConfiguredException extends RuntimeException {
        public PutIoNotConfiguredException() {
            super("The shared put.io account is not configured on this server.");
        }
    }
}

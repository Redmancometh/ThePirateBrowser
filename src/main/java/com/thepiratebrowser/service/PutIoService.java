package com.thepiratebrowser.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thepiratebrowser.model.PutIoFile;
import com.thepiratebrowser.model.PutIoFileListing;
import com.thepiratebrowser.model.PutIoTransfer;
import com.thepiratebrowser.model.TorrentResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class PutIoService {
    private static final String DEFAULT_API_BASE = "https://api.put.io/v2";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final LocalSettingsService settingsService;
    private final String apiBase;

    @Autowired
    public PutIoService(HttpClient httpClient, ObjectMapper objectMapper, LocalSettingsService settingsService) {
        this(httpClient, objectMapper, settingsService,
                System.getProperty("piratebrowser.putIoApiBase", DEFAULT_API_BASE));
    }

    PutIoService(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            LocalSettingsService settingsService,
            String apiBase
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.settingsService = settingsService;
        this.apiBase = apiBase.replaceAll("/+$", "");
    }

    public String add(TorrentResult result) {
        String body = "url=" + URLEncoder.encode(result.magnetUri(), StandardCharsets.UTF_8);
        HttpRequest request = request("/transfers/add")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        JsonNode response = send(request);
        JsonNode transfer = requireObject(response, "transfer");
        return requireText(transfer, "name");
    }

    public List<PutIoTransfer> transfers() {
        HttpRequest request = request("/transfers/list").GET().build();
        JsonNode response = send(request);
        JsonNode transferArray = response.get("transfers");
        if (transferArray == null || !transferArray.isArray()) {
            throw new IllegalStateException("put.io response was missing transfers.");
        }
        List<PutIoTransfer> transfers = new ArrayList<>();
        for (JsonNode transfer : transferArray) {
            transfers.add(new PutIoTransfer(
                    transfer.path("id").asLong(),
                    transfer.path("file_id").asLong(),
                    transfer.path("name").asText("(unnamed transfer)"),
                    transfer.path("status").asText(),
                    transfer.path("percent_done").asInt(),
                    transfer.path("error_message").asText("")
            ));
        }
        return transfers;
    }

    public PutIoFileListing files(long parentId) {
        JsonNode response = send(request("/files/list?parent_id=" + parentId
                + "&per_page=1000&mp4_status=true").GET().build());
        JsonNode fileArray = response.get("files");
        if (fileArray == null || !fileArray.isArray()) {
            throw new IllegalStateException("put.io response was missing files.");
        }
        List<PutIoFile> files = new ArrayList<>();
        for (JsonNode file : fileArray) {
            files.add(parseFile(file));
        }
        JsonNode parent = response.path("parent");
        return new PutIoFileListing(
                files,
                parent.path("id").asLong(parentId),
                parent.path("parent_id").asLong(0),
                parent.path("name").asText(parentId == 0 ? "put.io files" : "Folder"));
    }

    public String hlsStreamUrl(long fileId) {
        if (fileId <= 0) {
            throw new IllegalArgumentException("A valid put.io file is required.");
        }
        String token = settingsService.get().getPutIoToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Enter your put.io token in Settings first.");
        }
        return apiBase + "/files/" + fileId + "/hls/media.m3u8"
                + "?subtitle_key=all&oauth_token="
                + URLEncoder.encode(token.trim(), StandardCharsets.UTF_8);
    }

    public String validateAccount() {
        JsonNode info = requireObject(send(request("/account/info").GET().build()), "info");
        return requireText(info, "username");
    }

    private HttpRequest.Builder request(String path) {
        String token = settingsService.get().getPutIoToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Enter your put.io token in Settings first.");
        }
        return HttpRequest.newBuilder(URI.create(apiBase + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token.trim());
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message = errorMessage(response.body(), "HTTP " + response.statusCode());
                throw new IllegalStateException("put.io request failed: " + message);
            }
            JsonNode json;
            try {
                json = objectMapper.readTree(response.body());
            } catch (IOException exception) {
                throw new IllegalStateException("put.io returned invalid JSON.", exception);
            }
            if (!"OK".equals(json.path("status").asText())) {
                String message = errorMessage(response.body(), "Unexpected response status");
                throw new IllegalStateException("put.io request failed: " + message);
            }
            return json;
        } catch (IOException exception) {
            throw new IllegalStateException("put.io request failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("put.io request was interrupted", exception);
        }
    }

    private String errorMessage(String body, String fallback) {
        try {
            JsonNode json = objectMapper.readTree(body);
            String message = json.path("error_message").asText();
            return message.isBlank() ? fallback : message;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static JsonNode requireObject(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isObject()) {
            throw new IllegalStateException("put.io response was missing " + field + ".");
        }
        return value;
    }

    private static String requireText(JsonNode parent, String field) {
        String value = parent.path(field).asText();
        if (value.isBlank()) {
            throw new IllegalStateException("put.io response was missing " + field + ".");
        }
        return value;
    }

    private static PutIoFile parseFile(JsonNode file) {
        return new PutIoFile(
                file.path("id").asLong(),
                file.path("parent_id").asLong(),
                file.path("name").asText("(unnamed file)"),
                file.path("content_type").asText("application/octet-stream"),
                file.path("file_type").asText(),
                file.path("size").asLong(),
                file.path("is_mp4_available").asBoolean(),
                file.path("need_convert").asBoolean());
    }
}

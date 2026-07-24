package com.thepiratebrowser.service;

import com.thepiratebrowser.model.TorrentResult;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class NyaaService implements TorrentSource {
    private static final String BASE_URL = "https://nyaa.si";

    private final HttpClient httpClient;

    public NyaaService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String id() {
        return "nyaa";
    }

    @Override
    public String name() {
        return "Nyaa";
    }

    @Override
    public List<TorrentResult> search(String query, int minimumSeeders) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        URI uri = URI.create(BASE_URL + "/?page=rss&q="
                + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/rss+xml, application/xml")
                .GET()
                .build();
        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Nyaa search failed: HTTP " + response.statusCode());
            }
            return parseResults(response.body(), minimumSeeders);
        } catch (IOException exception) {
            throw new IllegalStateException("Nyaa search failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Nyaa search was interrupted", exception);
        }
    }

    List<TorrentResult> parseResults(String xml, int minimumSeeders) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));
            NodeList items = document.getElementsByTagName("item");
            List<TorrentResult> results = new ArrayList<>();
            for (int index = 0; index < items.getLength(); index++) {
                Element item = (Element) items.item(index);
                String infoHash = text(item, "infoHash");
                int seeders = integer(text(item, "seeders"));
                if (infoHash.isBlank() || seeders < minimumSeeders) {
                    continue;
                }
                String pageUrl = text(item, "guid");
                results.add(new TorrentResult(
                        pageUrl.substring(pageUrl.lastIndexOf('/') + 1),
                        text(item, "title"),
                        infoHash.toUpperCase(Locale.ROOT),
                        sizeBytes(text(item, "size")),
                        seeders,
                        integer(text(item, "leechers")),
                        "Nyaa",
                        "Yes".equalsIgnoreCase(text(item, "trusted")) ? "trusted" : "member",
                        text(item, "category"),
                        instant(text(item, "pubDate")),
                        false,
                        id(),
                        name(),
                        pageUrl
                ));
            }
            results.sort(Comparator.comparingInt(TorrentResult::seeders).reversed());
            return results;
        } catch (Exception exception) {
            throw new IllegalStateException("Nyaa returned invalid RSS.", exception);
        }
    }

    private static String text(Element element, String localName) {
        NodeList nodes = element.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            nodes = element.getElementsByTagName(localName);
        }
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    private static int integer(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static long sizeBytes(String value) {
        String[] parts = value.trim().split("\\s+");
        if (parts.length < 2) {
            return 0;
        }
        try {
            double amount = Double.parseDouble(parts[0]);
            long multiplier = switch (parts[1].toUpperCase(Locale.ROOT)) {
                case "KIB", "KB" -> 1024L;
                case "MIB", "MB" -> 1024L * 1024;
                case "GIB", "GB" -> 1024L * 1024 * 1024;
                case "TIB", "TB" -> 1024L * 1024 * 1024 * 1024;
                default -> 1L;
            };
            return (long) (amount * multiplier);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static Instant instant(String value) {
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (Exception ignored) {
            return Instant.EPOCH;
        }
    }
}

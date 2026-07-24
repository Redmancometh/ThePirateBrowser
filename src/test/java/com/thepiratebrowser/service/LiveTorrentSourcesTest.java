package com.thepiratebrowser.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "liveTorrentSources", matches = "true")
class LiveTorrentSourcesTest {
    @TempDir
    Path temporaryDirectory;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper =
            new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void broadSearchApisReturnRealResults() {
        assertFalse(new KnabenService(httpClient, mapper).search("ubuntu", 0).isEmpty());
        assertFalse(new MagnetzService(httpClient, mapper).search("ubuntu", 0).isEmpty());
    }

    @Test
    void specialtyApisReturnResultsForMatchingQueries() {
        assertFalse(new NyaaService(httpClient).search("one piece", 0).isEmpty());
        assertFalse(new EztvService(httpClient, mapper).search("1080p", 0).isEmpty());
        assertFalse(new YtsService(httpClient, mapper).search("inception", 0).isEmpty());
    }

    @Test
    void aggregateUbuntuSearchHasMultipleRealContributors() {
        String previous = System.getProperty("piratebrowser.dataDir");
        System.setProperty("piratebrowser.dataDir", temporaryDirectory.toString());
        try {
            LocalSettingsService settings = new LocalSettingsService(mapper);
            settings.get().setEnabledTorrentSources(
                    List.of("pirate-bay", "knaben", "magnetz", "torrents-csv"));
            List<TorrentSource> sources = List.of(
                    new PirateBayService(httpClient, mapper, settings),
                    new KnabenService(httpClient, mapper),
                    new MagnetzService(httpClient, mapper),
                    new TorrentsCsvService(httpClient, mapper)
            );
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var response = new TorrentSearchService(sources, settings, executor)
                        .search("ubuntu", 0);
                long contributors = response.sourceResultCounts().values().stream()
                        .filter(count -> count > 0)
                        .count();

                System.out.println("Live aggregate verification: " + response.statusText());
                assertEquals(4, response.searchedSourceCount());
                assertTrue(response.unavailableSources().isEmpty());
                assertTrue(contributors >= 3,
                        "Expected at least three providers to contribute after deduplication: "
                                + response.sourceResultCounts());
            }
        } finally {
            if (previous == null) {
                System.clearProperty("piratebrowser.dataDir");
            } else {
                System.setProperty("piratebrowser.dataDir", previous);
            }
        }
    }
}

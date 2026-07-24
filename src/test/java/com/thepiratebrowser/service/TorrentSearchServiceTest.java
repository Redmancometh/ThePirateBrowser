package com.thepiratebrowser.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.thepiratebrowser.model.TorrentResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TorrentSearchServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void searchesEnabledSourcesConcurrentlyDeduplicatesAndReportsFailures() throws Exception {
        String previous = System.getProperty("piratebrowser.dataDir");
        System.setProperty("piratebrowser.dataDir", temporaryDirectory.toString());
        try {
            LocalSettingsService settings = new LocalSettingsService(
                    new ObjectMapper().registerModule(new JavaTimeModule()));
            settings.get().setEnabledTorrentSources(List.of("pirate-bay", "nyaa", "eztv"));
            CountDownLatch entered = new CountDownLatch(2);
            TorrentSource pirateBay = source("pirate-bay", "The Pirate Bay", 5, entered);
            TorrentSource nyaa = source("nyaa", "Nyaa", 20, entered);
            TorrentSource eztv = failingSource();

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var response = new TorrentSearchService(
                        List.of(eztv, nyaa, pirateBay), settings, executor)
                        .search("example", 0);

                assertEquals(1, response.results().size());
                assertEquals("Nyaa", response.results().getFirst().source());
                assertEquals(List.of("EZTV"), response.unavailableSources());
                assertEquals(2, response.searchedSourceCount());
                assertTrue(response.statusText().contains("unavailable: EZTV"));
            }
        } finally {
            if (previous == null) {
                System.clearProperty("piratebrowser.dataDir");
            } else {
                System.setProperty("piratebrowser.dataDir", previous);
            }
        }
    }

    private static TorrentSource source(
            String id,
            String name,
            int seeders,
            CountDownLatch entered
    ) {
        return new TorrentSource() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public List<TorrentResult> search(String query, int minimumSeeders) {
                entered.countDown();
                try {
                    assertTrue(entered.await(2, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return List.of(result(id, name, seeders));
            }
        };
    }

    private static TorrentSource failingSource() {
        return new TorrentSource() {
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
                throw new IllegalStateException("offline");
            }
        };
    }

    private static TorrentResult result(String sourceId, String source, int seeders) {
        return new TorrentResult(
                "1",
                "Same torrent",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                1024,
                seeders,
                1,
                source,
                "trusted",
                "test",
                Instant.EPOCH,
                false,
                sourceId,
                source,
                "https://example.com/1");
    }
}

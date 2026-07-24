package com.thepiratebrowser.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.thepiratebrowser.model.MonitorUpdate;
import com.thepiratebrowser.model.SavedSearch;
import com.thepiratebrowser.model.TorrentResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchMonitorServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void firstCheckEstablishesQuietBaselineAndLaterCheckMarksOnlyUnseenResults() {
        withDataDirectory(() -> {
            LocalSettingsService settings = settingsService();
            SavedSearch search = new SavedSearch("Linux", "ubuntu", 0);
            settings.addSearch(search);
            MutablePirateBayService pirateBay = new MutablePirateBayService();
            pirateBay.results = List.of(result("one"));
            List<MonitorUpdate> events = new CopyOnWriteArrayList<>();
            SearchMonitorService monitor = monitor(settings, pirateBay,
                    event -> events.add((MonitorUpdate) event));

            MonitorUpdate baseline = monitor.check(search);
            pirateBay.results = List.of(result("one"), result("two"));
            MonitorUpdate update = monitor.check(search);

            assertEquals(0, baseline.newResultCount());
            assertEquals(1, update.newResultCount());
            assertEquals("two", update.results().stream()
                    .filter(TorrentResult::newMatch).findFirst().orElseThrow().id());
            assertEquals(2, search.getSeenResultIds().size());
            assertNotNull(search.getLastChecked());
            assertEquals(2, events.size());
        });
    }

    @Test
    void failedCheckDoesNotChangeSeenIdsOrLastChecked() {
        withDataDirectory(() -> {
            LocalSettingsService settings = settingsService();
            SavedSearch search = new SavedSearch("Linux", "ubuntu", 0);
            search.getSeenResultIds().add("old");
            MutablePirateBayService pirateBay = new MutablePirateBayService();
            pirateBay.failure = new IllegalStateException("offline");
            List<MonitorUpdate> events = new CopyOnWriteArrayList<>();
            SearchMonitorService monitor = monitor(settings, pirateBay,
                    event -> events.add((MonitorUpdate) event));

            MonitorUpdate update = monitor.check(search);

            assertTrue(update.error().contains("offline"));
            assertEquals(List.of("old"), search.getSeenResultIds().stream().toList());
            assertTrue(search.getLastChecked() == null);
            assertEquals(1, events.size());
        });
    }

    @Test
    void concurrentChecksForSameSearchCannotEmitDuplicateNewMatches() throws Exception {
        withDataDirectory(() -> {
            LocalSettingsService settings = settingsService();
            SavedSearch search = new SavedSearch("Linux", "ubuntu", 0);
            search.setLastChecked(Instant.now().minusSeconds(60));
            BlockingPirateBayService pirateBay = new BlockingPirateBayService(List.of(result("new")));
            List<MonitorUpdate> events = new CopyOnWriteArrayList<>();
            SearchMonitorService monitor = monitor(settings, pirateBay,
                    event -> events.add((MonitorUpdate) event));

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var first = executor.submit(() -> monitor.check(search));
                assertTrue(pirateBay.entered.await(2, TimeUnit.SECONDS));
                var second = executor.submit(() -> monitor.check(search));
                pirateBay.release.countDown();
                first.get(2, TimeUnit.SECONDS);
                second.get(2, TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }

            assertEquals(2, events.size());
            assertEquals(1, events.stream().mapToLong(MonitorUpdate::newResultCount).sum());
            assertTrue(search.getSeenResultIds().contains("new"));
        });
    }

    @Test
    void saveFailureRollsBackSeenIdsAndLastChecked() {
        withDataDirectory(() -> {
            FailingSettingsService settings = new FailingSettingsService(
                    new ObjectMapper().registerModule(new JavaTimeModule()));
            SavedSearch search = new SavedSearch("Linux", "ubuntu", 0);
            search.getSeenResultIds().add("old");
            settings.addSearch(search);
            settings.failSaves = true;
            MutablePirateBayService pirateBay = new MutablePirateBayService();
            pirateBay.results = List.of(result("new"));
            SearchMonitorService monitor = monitor(settings, pirateBay, ignored -> { });

            MonitorUpdate update = monitor.check(search);

            assertEquals("disk full", update.error());
            assertEquals(List.of("old"), search.getSeenResultIds().stream().toList());
            assertTrue(search.getLastChecked() == null);
        });
    }

    @Test
    void criteriaEditWaitsForInflightCheckAndCanResetItsCompletedBaseline() {
        withDataDirectory(() -> {
            LocalSettingsService settings = settingsService();
            SavedSearch search = new SavedSearch("Linux", "old query", 0);
            settings.addSearch(search);
            BlockingPirateBayService pirateBay = new BlockingPirateBayService(List.of(result("old")));
            SearchMonitorService monitor = monitor(settings, pirateBay, ignored -> { });

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var check = executor.submit(() -> monitor.check(search));
                assertTrue(pirateBay.entered.await(2, TimeUnit.SECONDS));
                var edit = executor.submit(() -> monitor.updateSearch(search, current -> {
                    current.setQuery("new query");
                    current.getSeenResultIds().clear();
                    current.setLastChecked(null);
                }));
                Thread.sleep(100);
                assertFalse(edit.isDone());
                pirateBay.release.countDown();
                check.get(2, TimeUnit.SECONDS);
                edit.get(2, TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }

            assertEquals("new query", search.getQuery());
            assertTrue(search.getSeenResultIds().isEmpty());
            assertTrue(search.getLastChecked() == null);
            assertEquals(1, search.getRevision());
        });
    }

    private LocalSettingsService settingsService() {
        return new LocalSettingsService(new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    private static SearchMonitorService monitor(
            LocalSettingsService settings,
            PirateBayService pirateBay,
            ApplicationEventPublisher publisher
    ) {
        TorrentSearchService torrentSearch =
                new TorrentSearchService(List.of(pirateBay), settings, Runnable::run);
        return new SearchMonitorService(settings, torrentSearch, publisher);
    }

    private static TorrentResult result(String id) {
        return new TorrentResult(id, id, id.repeat(40).substring(0, 40), 1, 1,
                0, "tester", "trusted", "100", Instant.EPOCH, false);
    }

    private void withDataDirectory(Runnable test) {
        String original = System.getProperty("piratebrowser.dataDir");
        try {
            System.setProperty("piratebrowser.dataDir", temporaryDirectory.toString());
            test.run();
        } finally {
            if (original == null) {
                System.clearProperty("piratebrowser.dataDir");
            } else {
                System.setProperty("piratebrowser.dataDir", original);
            }
        }
    }

    private static class MutablePirateBayService extends PirateBayService {
        protected List<TorrentResult> results = List.of();
        private RuntimeException failure;

        MutablePirateBayService() {
            super(null, null, null);
        }

        @Override
        public List<TorrentResult> search(String query, int minimumSeeders) {
            if (failure != null) {
                throw failure;
            }
            return results;
        }
    }

    private static final class BlockingPirateBayService extends MutablePirateBayService {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        BlockingPirateBayService(List<TorrentResult> results) {
            this.results = results;
        }

        @Override
        public List<TorrentResult> search(String query, int minimumSeeders) {
            entered.countDown();
            try {
                assertTrue(release.await(2, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return results;
        }
    }

    private static final class FailingSettingsService extends LocalSettingsService {
        private boolean failSaves;

        FailingSettingsService(ObjectMapper objectMapper) {
            super(objectMapper);
        }

        @Override
        public synchronized void save() {
            if (failSaves) {
                throw new IllegalStateException("disk full");
            }
            super.save();
        }
    }
}

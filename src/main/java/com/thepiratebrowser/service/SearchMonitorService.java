package com.thepiratebrowser.service;

import com.thepiratebrowser.model.MonitorUpdate;
import com.thepiratebrowser.model.SavedSearch;
import com.thepiratebrowser.model.TorrentResult;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class SearchMonitorService {
    private final LocalSettingsService settingsService;
    private final PirateBayService pirateBayService;
    private final ApplicationEventPublisher events;
    private final Map<String, Object> searchLocks = new ConcurrentHashMap<>();
    private final Map<String, Long> checkSequences = new ConcurrentHashMap<>();

    public SearchMonitorService(
            LocalSettingsService settingsService,
            PirateBayService pirateBayService,
            ApplicationEventPublisher events
    ) {
        this.settingsService = settingsService;
        this.pirateBayService = pirateBayService;
        this.events = events;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void checkDueSearches() {
        int intervalMinutes = settingsService.get().getMonitorIntervalMinutes();
        Instant dueBefore = Instant.now().minus(Duration.ofMinutes(intervalMinutes));
        for (SavedSearch search : settingsService.searches()) {
            if (search.isEnabled()
                    && (search.getLastChecked() == null || search.getLastChecked().isBefore(dueBefore))) {
                checkIfStillEnabled(search);
            }
        }
    }

    private void checkIfStillEnabled(SavedSearch search) {
        Object lock = searchLocks.computeIfAbsent(search.getId(), ignored -> new Object());
        synchronized (lock) {
            if (search.isEnabled()) {
                check(search);
            }
        }
    }

    public MonitorUpdate check(SavedSearch search) {
        return check(search, 0);
    }

    public MonitorUpdate check(SavedSearch search, long uiRequestGeneration) {
        Object lock = searchLocks.computeIfAbsent(search.getId(), ignored -> new Object());
        MonitorUpdate update;
        synchronized (lock) {
            try {
                List<TorrentResult> fetched =
                        pirateBayService.search(search.getQuery(), search.getMinimumSeeders());
                List<TorrentResult> deduplicated =
                        new ArrayList<>(fetched.stream()
                                .collect(Collectors.toMap(
                                        TorrentResult::id,
                                        result -> result,
                                        (first, ignored) -> first,
                                        LinkedHashMap::new))
                                .values());
                List<TorrentResult> presented;
                synchronized (settingsService) {
                    Set<String> seen = search.getSeenResultIds();
                    Set<String> previousSeen = new java.util.LinkedHashSet<>(seen);
                    Instant previousLastChecked = search.getLastChecked();
                    boolean establishingBaseline = search.getLastChecked() == null && seen.isEmpty();
                    presented = deduplicated.stream()
                            .map(result -> result.withNewMatch(
                                    !establishingBaseline && !seen.contains(result.id())))
                            .toList();

                    seen.addAll(deduplicated.stream().map(TorrentResult::id).collect(Collectors.toSet()));
                    search.setLastChecked(Instant.now());
                    try {
                        settingsService.save();
                    } catch (RuntimeException exception) {
                        search.setSeenResultIds(previousSeen);
                        search.setLastChecked(previousLastChecked);
                        throw exception;
                    }
                }

                update = monitorUpdate(search, uiRequestGeneration, presented, null);
            } catch (RuntimeException exception) {
                update = monitorUpdate(search, uiRequestGeneration, List.of(), exception.getMessage());
            }
        }
        events.publishEvent(update);
        return update;
    }

    public void updateSearch(SavedSearch search, Consumer<SavedSearch> mutation) {
        Object lock = searchLocks.computeIfAbsent(search.getId(), ignored -> new Object());
        synchronized (lock) {
            synchronized (settingsService) {
                String previousName = search.getName();
                String previousQuery = search.getQuery();
                int previousMinimumSeeders = search.getMinimumSeeders();
                boolean previousEnabled = search.isEnabled();
                long previousRevision = search.getRevision();
                Instant previousLastChecked = search.getLastChecked();
                Set<String> previousSeen = new java.util.LinkedHashSet<>(search.getSeenResultIds());
                try {
                    mutation.accept(search);
                    if (!previousQuery.equals(search.getQuery())
                            || previousMinimumSeeders != search.getMinimumSeeders()) {
                        search.setRevision(previousRevision + 1);
                    }
                    settingsService.save();
                } catch (RuntimeException exception) {
                    search.setName(previousName);
                    search.setQuery(previousQuery);
                    search.setMinimumSeeders(previousMinimumSeeders);
                    search.setEnabled(previousEnabled);
                    search.setRevision(previousRevision);
                    search.setLastChecked(previousLastChecked);
                    search.setSeenResultIds(previousSeen);
                    throw exception;
                }
            }
        }
    }

    private MonitorUpdate monitorUpdate(
            SavedSearch search,
            long uiRequestGeneration,
            List<TorrentResult> results,
            String error
    ) {
        long sequence = checkSequences.merge(search.getId(), 1L, Long::sum);
        return new MonitorUpdate(
                search.getId(),
                search.toString(),
                search.getRevision(),
                sequence,
                uiRequestGeneration,
                results,
                error);
    }
}

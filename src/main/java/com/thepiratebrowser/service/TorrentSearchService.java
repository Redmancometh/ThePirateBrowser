package com.thepiratebrowser.service;

import com.thepiratebrowser.model.TorrentResult;
import com.thepiratebrowser.model.TorrentSearchResponse;
import com.thepiratebrowser.model.TorrentSourceInfo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

@Service
public class TorrentSearchService {
    private static final List<String> SOURCE_ORDER =
            List.of("pirate-bay", "knaben", "magnetz", "torrents-csv", "nyaa", "eztv", "yts");

    private final List<TorrentSource> sources;
    private final LocalSettingsService settingsService;
    private final Executor executor;

    public TorrentSearchService(
            List<TorrentSource> sources,
            LocalSettingsService settingsService,
            Executor executor
    ) {
        this.sources = sources.stream()
                .sorted(Comparator.comparingInt(source -> sourceOrder(source.id())))
                .toList();
        this.settingsService = settingsService;
        this.executor = executor;
    }

    public List<TorrentSourceInfo> availableSources() {
        return sources.stream().map(source -> new TorrentSourceInfo(source.id(), source.name())).toList();
    }

    public TorrentSearchResponse search(String query, int minimumSeeders) {
        Set<String> enabled = Set.copyOf(settingsService.get().getEnabledTorrentSources());
        List<TorrentSource> selected = sources.stream()
                .filter(source -> enabled.contains(source.id()))
                .toList();
        if (selected.isEmpty()) {
            throw new IllegalStateException("Enable at least one torrent source in Settings.");
        }

        List<CompletableFuture<SourceResult>> futures = selected.stream()
                .map(source -> CompletableFuture.supplyAsync(
                        () -> searchSource(source, query, minimumSeeders), executor))
                .toList();
        List<SourceResult> completed = futures.stream().map(CompletableFuture::join).toList();
        List<String> unavailable = completed.stream()
                .filter(result -> result.error() != null)
                .map(result -> result.source().name())
                .toList();
        List<SourceResult> successful = completed.stream()
                .filter(result -> result.error() == null)
                .toList();
        if (successful.isEmpty()) {
            throw new IllegalStateException("All enabled torrent sources failed: "
                    + completed.stream()
                    .map(result -> result.source().name() + ": " + result.error())
                    .reduce((first, second) -> first + "; " + second)
                    .orElse("unknown error"));
        }

        Map<String, TorrentResult> unique = new LinkedHashMap<>();
        successful.stream()
                .flatMap(result -> result.results().stream())
                .forEach(result -> unique.merge(
                        deduplicationKey(result),
                        result,
                        (first, second) -> second.seeders() > first.seeders() ? second : first));
        List<TorrentResult> normalized = new ArrayList<>(unique.values());
        normalized.sort(Comparator.comparingInt(TorrentResult::seeders).reversed()
                .thenComparing(TorrentResult::name, String.CASE_INSENSITIVE_ORDER));
        Map<String, Integer> sourceResultCounts = new LinkedHashMap<>();
        successful.forEach(result -> sourceResultCounts.put(result.source().name(), 0));
        normalized.forEach(result -> sourceResultCounts.computeIfPresent(
                result.source(), (ignored, count) -> count + 1));
        return new TorrentSearchResponse(
                normalized, successful.size(), unavailable, sourceResultCounts);
    }

    private SourceResult searchSource(TorrentSource source, String query, int minimumSeeders) {
        try {
            return new SourceResult(source, source.search(query, minimumSeeders), null);
        } catch (RuntimeException exception) {
            Throwable cause = exception instanceof CompletionException && exception.getCause() != null
                    ? exception.getCause()
                    : exception;
            return new SourceResult(source, List.of(), cause.getMessage());
        }
    }

    private static String deduplicationKey(TorrentResult result) {
        return result.infoHash().isBlank()
                ? result.stableId()
                : result.infoHash().toUpperCase(Locale.ROOT);
    }

    private static int sourceOrder(String id) {
        int index = SOURCE_ORDER.indexOf(id);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private record SourceResult(TorrentSource source, List<TorrentResult> results, String error) {
    }
}

package com.thepiratebrowser.model;

import java.util.List;
import java.util.Map;

public record TorrentSearchResponse(
        List<TorrentResult> results,
        int searchedSourceCount,
        List<String> unavailableSources,
        Map<String, Integer> sourceResultCounts
) {
    public TorrentSearchResponse(
            List<TorrentResult> results,
            int searchedSourceCount,
            List<String> unavailableSources
    ) {
        this(results, searchedSourceCount, unavailableSources, Map.of());
    }

    public String statusText() {
        StringBuilder summary = new StringBuilder(results.size() + " results");
        if (!sourceResultCounts.isEmpty()) {
            summary.append(" — ");
            sourceResultCounts.forEach((source, count) -> {
                if (summary.charAt(summary.length() - 1) != ' ') {
                    summary.append(", ");
                }
                summary.append(source).append(' ').append(count);
            });
        } else {
            summary.append(" from ").append(searchedSourceCount).append(" source")
                    .append(searchedSourceCount == 1 ? "" : "s");
        }
        if (!unavailableSources.isEmpty()) {
            summary.append("; unavailable: ").append(String.join(", ", unavailableSources));
        }
        return summary.toString();
    }
}

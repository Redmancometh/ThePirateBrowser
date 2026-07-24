package com.thepiratebrowser.model;

import java.util.List;

public record TorrentSearchResponse(
        List<TorrentResult> results,
        int searchedSourceCount,
        List<String> unavailableSources
) {
    public String statusText() {
        String summary = results.size() + " results from " + searchedSourceCount + " source"
                + (searchedSourceCount == 1 ? "" : "s");
        if (unavailableSources.isEmpty()) {
            return summary;
        }
        return summary + "; unavailable: " + String.join(", ", unavailableSources);
    }
}

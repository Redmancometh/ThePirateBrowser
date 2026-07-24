package com.thepiratebrowser.web.search;

import java.util.List;

public record SearchOutcome(
        List<TorrentResult> results,
        List<String> failures
) {
}

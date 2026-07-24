package com.thepiratebrowser.web.search;

public record TorrentResult(
        String name,
        TorrentSource source,
        String magnet,
        long size,
        int seeders,
        int leechers
) {
}

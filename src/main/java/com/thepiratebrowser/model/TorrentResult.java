package com.thepiratebrowser.model;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public record TorrentResult(
        String id,
        String name,
        String infoHash,
        long size,
        int seeders,
        int leechers,
        String username,
        String status,
        String category,
        Instant added,
        boolean newMatch
) {
    public TorrentResult withNewMatch(boolean value) {
        return new TorrentResult(id, name, infoHash, size, seeders, leechers,
                username, status, category, added, value);
    }

    public String magnetUri() {
        return "magnet:?xt=urn:btih:" + infoHash
                + "&dn=" + URLEncoder.encode(name, StandardCharsets.UTF_8)
                + "&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce"
                + "&tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce";
    }
}

package com.thepiratebrowser.web.search;

public enum TorrentSource {
    PIRATE_BAY("The Pirate Bay", "General-purpose torrent index."),
    KNABEN("Knaben", "Broad metasearch with safety filtering."),
    MAGNETZ("Magnetz", "Fast general magnet search."),
    TORRENTS_CSV("Torrents.csv", "Open torrent database with broad coverage."),
    NYAA("Nyaa", "Anime-focused torrent search."),
    EZTV("EZTV", "Recent television releases."),
    YTS("YTS", "Movie releases in compact formats.");

    private final String displayName;
    private final String summary;

    TorrentSource(String displayName, String summary) {
        this.displayName = displayName;
        this.summary = summary;
    }

    public String displayName() {
        return displayName;
    }

    public String summary() {
        return summary;
    }
}

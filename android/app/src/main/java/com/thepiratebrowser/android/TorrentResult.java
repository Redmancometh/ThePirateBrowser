package com.thepiratebrowser.android;

import java.util.Locale;

public final class TorrentResult {
    public final String name;
    public final String source;
    public final String magnet;
    public final long sizeBytes;
    public final int seeders;
    public final int leechers;

    public TorrentResult(
            String name,
            String source,
            String magnet,
            long sizeBytes,
            int seeders,
            int leechers
    ) {
        this.name = name;
        this.source = source;
        this.magnet = magnet;
        this.sizeBytes = sizeBytes;
        this.seeders = seeders;
        this.leechers = leechers;
    }

    public String subtitle() {
        return source + "  •  " + seeders + " seeders  •  " + readableSize(sizeBytes);
    }

    public String metadata() {
        return seeders + " seeders  •  " + leechers + " leechers  •  "
                + readableSize(sizeBytes);
    }

    public static String readableSize(long bytes) {
        if (bytes <= 0) {
            return "Unknown size";
        }
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.US, value >= 10 ? "%.0f %s" : "%.1f %s", value, units[unit]);
    }
}

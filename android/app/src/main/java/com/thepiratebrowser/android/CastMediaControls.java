package com.thepiratebrowser.android;

import java.util.Locale;

final class CastMediaControls {
    static final int PROGRESS_MAX = 1_000;

    private CastMediaControls() {
    }

    static long seekTarget(long positionMs, long offsetMs, long durationMs) {
        long target;
        try {
            target = Math.addExact(positionMs, offsetMs);
        } catch (ArithmeticException ignored) {
            target = offsetMs > 0 ? Long.MAX_VALUE : 0;
        }
        return Math.max(0, Math.min(Math.max(0, durationMs), target));
    }

    static int progress(long positionMs, long durationMs) {
        if (durationMs <= 0) {
            return 0;
        }
        double ratio = (double) Math.max(0, Math.min(positionMs, durationMs)) / durationMs;
        return (int) Math.round(ratio * PROGRESS_MAX);
    }

    static long positionForProgress(int progress, long durationMs) {
        int bounded = Math.max(0, Math.min(PROGRESS_MAX, progress));
        return Math.round((bounded / (double) PROGRESS_MAX) * Math.max(0, durationMs));
    }

    static String formatTime(long milliseconds) {
        long totalSeconds = Math.max(0, milliseconds) / 1_000;
        long hours = totalSeconds / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }
}

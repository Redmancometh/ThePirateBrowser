package com.thepiratebrowser.android;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class CastMediaControlsTest {
    @Test
    public void seekTargetClampsToMediaBounds() {
        assertEquals(0, CastMediaControls.seekTarget(5_000, -10_000, 120_000));
        assertEquals(35_000, CastMediaControls.seekTarget(5_000, 30_000, 120_000));
        assertEquals(120_000, CastMediaControls.seekTarget(110_000, 30_000, 120_000));
    }

    @Test
    public void progressRoundTripsMediaPosition() {
        assertEquals(250, CastMediaControls.progress(30_000, 120_000));
        assertEquals(30_000, CastMediaControls.positionForProgress(250, 120_000));
        assertEquals(0, CastMediaControls.progress(50_000, 0));
    }

    @Test
    public void formatsShortAndLongMediaTimes() {
        assertEquals("0:00", CastMediaControls.formatTime(-1));
        assertEquals("2:05", CastMediaControls.formatTime(125_900));
        assertEquals("1:02:03", CastMediaControls.formatTime(3_723_000));
    }
}

package com.thepiratebrowser.android;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SavedSearchTest {
    @Test
    public void firstCheckEstablishesBaselineAndLaterChecksFindOnlyNewResults() {
        SavedSearch search = new SavedSearch("Linux", "linux", 5);
        TorrentResult first = result("First", "magnet:?xt=urn:btih:first", 10);
        TorrentResult filtered = result("Low", "magnet:?xt=urn:btih:low", 2);

        assertEquals(0, search.record(List.of(first, filtered), 100));
        assertEquals(1, search.seenResultIds.size());

        TorrentResult second = result("Second", "magnet:?xt=urn:btih:second", 8);
        assertEquals(1, search.record(List.of(first, second), 200));
        assertEquals(2, search.seenResultIds.size());
    }

    @Test
    public void savedSearchesRoundTripAllControlState() {
        SavedSearch search = new SavedSearch("Shows", "example show", 12);
        search.enabled = false;
        search.record(List.of(result("Episode", "magnet:?xt=urn:btih:episode", 20)), 1234);

        String encoded = SavedSearchStore.encode(List.of(search));
        List<SavedSearch> restored = SavedSearchStore.decode(encoded);

        assertEquals(1, restored.size());
        assertEquals(search.id, restored.get(0).id);
        assertEquals("Shows", restored.get(0).displayName());
        assertEquals(12, restored.get(0).minimumSeeders);
        assertFalse(restored.get(0).enabled);
        assertEquals(1234, restored.get(0).lastChecked);
        assertTrue(restored.get(0).seenResultIds.contains(
                "magnet:?xt=urn:btih:episode"));
    }

    private TorrentResult result(String name, String magnet, int seeders) {
        return new TorrentResult(name, "Test", magnet, 10, seeders, 0);
    }
}

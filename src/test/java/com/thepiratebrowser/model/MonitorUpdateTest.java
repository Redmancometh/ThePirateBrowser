package com.thepiratebrowser.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitorUpdateTest {
    @Test
    void rejectsOldRevisionAndOutOfOrderSequence() {
        SavedSearch search = new SavedSearch("Show", "show s01", 2);
        MonitorUpdate original = update(search, 1);

        assertTrue(original.isCurrentFor(search, 0));
        assertFalse(original.isCurrentFor(search, 1));

        search.setRevision(1);
        assertFalse(original.isCurrentFor(search, 0));

        MonitorUpdate edited = update(search, 2);
        assertTrue(edited.isCurrentFor(search, 1));
    }

    private static MonitorUpdate update(SavedSearch search, long sequence) {
        return new MonitorUpdate(
                search.getId(),
                search.toString(),
                search.getRevision(),
                sequence,
                0,
                List.of(),
                null);
    }
}

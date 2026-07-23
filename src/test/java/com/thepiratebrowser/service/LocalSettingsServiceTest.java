package com.thepiratebrowser.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.thepiratebrowser.model.SavedSearch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalSettingsServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsSettingsAsLocalJson() {
        String original = System.getProperty("piratebrowser.dataDir");
        try {
            System.setProperty("piratebrowser.dataDir", temporaryDirectory.toString());
            ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
            LocalSettingsService writer = new LocalSettingsService(mapper);
            writer.get().setPutIoToken("local-token");
            writer.get().setPutIoRefreshIntervalSeconds(45);
            writer.get().setPutIoAutoRefreshEnabled(false);
            writer.get().setSavedSearchesPanelVisible(false);
            writer.get().setPutIoPanelRatio(0.28);
            writer.addSearch(new SavedSearch("Linux", "ubuntu", 3));

            LocalSettingsService reader = new LocalSettingsService(mapper);

            assertEquals("local-token", reader.get().getPutIoToken());
            assertEquals(1, reader.searches().size());
            assertEquals("ubuntu", reader.searches().getFirst().getQuery());
            assertEquals(45, reader.get().getPutIoRefreshIntervalSeconds());
            assertEquals(false, reader.get().isPutIoAutoRefreshEnabled());
            assertEquals(false, reader.get().isSavedSearchesPanelVisible());
            assertEquals(0.28, reader.get().getPutIoPanelRatio(), 0.0001);
        } finally {
            if (original == null) {
                System.clearProperty("piratebrowser.dataDir");
            } else {
                System.setProperty("piratebrowser.dataDir", original);
            }
        }
    }

    @Test
    void clampsUiPreferencesToUsableValues() {
        com.thepiratebrowser.model.LocalSettings settings =
                new com.thepiratebrowser.model.LocalSettings();

        settings.setPutIoRefreshIntervalSeconds(1);
        settings.setSavedSearchesPanelRatio(0.01);
        settings.setPutIoPanelRatio(0.9);

        assertEquals(1, settings.getPutIoRefreshIntervalSeconds());
        assertEquals(0.1, settings.getSavedSearchesPanelRatio(), 0.0001);
        assertEquals(0.4, settings.getPutIoPanelRatio(), 0.0001);
    }

    @Test
    void failedSaveRollsBackAddAndRemove() {
        String original = System.getProperty("piratebrowser.dataDir");
        try {
            System.setProperty("piratebrowser.dataDir", temporaryDirectory.toString());
            ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
            FailingSettingsService service = new FailingSettingsService(mapper);
            SavedSearch existing = new SavedSearch("Existing", "one", 0);
            service.addSearch(existing);

            service.failSaves = true;
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    () -> service.addSearch(new SavedSearch("Failed", "two", 0)));
            assertEquals(1, service.searches().size());

            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    () -> service.removeSearch(existing.getId()));
            assertEquals(1, service.searches().size());
            assertEquals(existing.getId(), service.searches().getFirst().getId());
        } finally {
            if (original == null) {
                System.clearProperty("piratebrowser.dataDir");
            } else {
                System.setProperty("piratebrowser.dataDir", original);
            }
        }
    }

    private static final class FailingSettingsService extends LocalSettingsService {
        private boolean failSaves;

        FailingSettingsService(ObjectMapper objectMapper) {
            super(objectMapper);
        }

        @Override
        public synchronized void save() {
            if (failSaves) {
                throw new IllegalStateException("disk full");
            }
            super.save();
        }
    }
}

package com.thepiratebrowser.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thepiratebrowser.model.LocalSettings;
import com.thepiratebrowser.model.SavedSearch;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

@Service
public class LocalSettingsService {
    private final ObjectMapper objectMapper;
    private final Path settingsFile;
    private LocalSettings settings;

    public LocalSettingsService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        String configuredDirectory = System.getProperty("piratebrowser.dataDir", "data");
        this.settingsFile = Path.of(configuredDirectory).toAbsolutePath().normalize().resolve("settings.json");
        this.settings = load();
    }

    public synchronized LocalSettings get() {
        return settings;
    }

    public synchronized List<SavedSearch> searches() {
        return List.copyOf(settings.getSavedSearches());
    }

    public synchronized Optional<SavedSearch> findSearch(String id) {
        return settings.getSavedSearches().stream().filter(search -> search.getId().equals(id)).findFirst();
    }

    public synchronized void addSearch(SavedSearch search) {
        settings.getSavedSearches().add(search);
        try {
            save();
        } catch (RuntimeException exception) {
            settings.getSavedSearches().remove(search);
            throw exception;
        }
    }

    public synchronized void removeSearch(String id) {
        int index = -1;
        SavedSearch removed = null;
        for (int candidate = 0; candidate < settings.getSavedSearches().size(); candidate++) {
            SavedSearch search = settings.getSavedSearches().get(candidate);
            if (search.getId().equals(id)) {
                index = candidate;
                removed = search;
                break;
            }
        }
        if (removed == null) {
            return;
        }
        settings.getSavedSearches().remove(index);
        try {
            save();
        } catch (RuntimeException exception) {
            settings.getSavedSearches().add(index, removed);
            throw exception;
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(settingsFile.getParent());
            Path temporary = settingsFile.resolveSibling(settingsFile.getFileName() + ".tmp");
            objectMapper.writeValue(temporary.toFile(), settings);
            Files.move(temporary, settingsFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save settings to " + settingsFile, exception);
        }
    }

    public Path settingsFile() {
        return settingsFile;
    }

    private LocalSettings load() {
        if (!Files.exists(settingsFile)) {
            return new LocalSettings();
        }
        try {
            LocalSettings loaded = objectMapper.readValue(settingsFile.toFile(), LocalSettings.class);
            List<String> enabled = loaded.getEnabledTorrentSources();
            if (enabled.containsAll(List.of("pirate-bay", "nyaa", "eztv", "yts"))
                    && !enabled.contains("torrents-csv")) {
                enabled.add("torrents-csv");
            }
            if (enabled.containsAll(List.of(
                    "pirate-bay", "torrents-csv", "nyaa", "eztv", "yts"))) {
                if (!enabled.contains("knaben")) {
                    enabled.add(1, "knaben");
                }
                if (!enabled.contains("magnetz")) {
                    enabled.add(2, "magnetz");
                }
            }
            return loaded;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read settings from " + settingsFile, exception);
        }
    }
}

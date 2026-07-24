package com.thepiratebrowser.web.search;

import com.thepiratebrowser.web.account.UserAccount;
import com.thepiratebrowser.web.account.UserSettings;
import com.thepiratebrowser.web.account.UserSettingsRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserSourceService {
    private final UserSettingsRepository settings;

    public UserSourceService(UserSettingsRepository settings) {
        this.settings = settings;
    }

    public Set<TorrentSource> enabled(UserAccount account) {
        return settings.findById(account.getId())
                .map(UserSettings::getEnabledSources)
                .map(this::parse)
                .orElseGet(() -> EnumSet.allOf(TorrentSource.class));
    }

    @Transactional
    public Set<TorrentSource> update(UserAccount account, Set<TorrentSource> enabled) {
        UserSettings value = settings.findById(account.getId())
                .orElseGet(() -> new UserSettings(account.getId(), "all"));
        value.setEnabledSources(serialize(enabled));
        settings.save(value);
        return enabled(account);
    }

    private Set<TorrentSource> parse(String value) {
        if (value == null || value.equals("all")) {
            return EnumSet.allOf(TorrentSource.class);
        }
        if (value.isBlank()) {
            return EnumSet.noneOf(TorrentSource.class);
        }
        EnumSet<TorrentSource> result = EnumSet.noneOf(TorrentSource.class);
        Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(TorrentSource::valueOf)
                .forEach(result::add);
        return result;
    }

    private String serialize(Set<TorrentSource> enabled) {
        if (enabled.size() == TorrentSource.values().length) {
            return "all";
        }
        return enabled.stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(","));
    }
}

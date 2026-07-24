package com.thepiratebrowser.web.saved;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.thepiratebrowser.web.account.UserAccount;
import com.thepiratebrowser.web.search.SearchOutcome;
import com.thepiratebrowser.web.search.TorrentResult;
import com.thepiratebrowser.web.search.TorrentSearchService;
import com.thepiratebrowser.web.search.UserSourceService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SavedSearchService {
    private static final TypeReference<Set<String>> STRING_SET = new TypeReference<>() {
    };

    private final SavedSearchRepository repository;
    private final UserSourceService sources;
    private final TorrentSearchService search;
    private final ObjectMapper mapper;

    public SavedSearchService(
            SavedSearchRepository repository,
            UserSourceService sources,
            TorrentSearchService search,
            ObjectMapper mapper
    ) {
        this.repository = repository;
        this.sources = sources;
        this.search = search;
        this.mapper = mapper;
    }

    public List<SavedSearchView> list(UserAccount account) {
        return repository.findAllByUserIdOrderByCreatedAtDesc(account.getId())
                .stream()
                .map(this::view)
                .toList();
    }

    @Transactional
    public SavedSearchView create(UserAccount account, SavedSearchRequest request) {
        validate(request);
        SavedSearchEntity entity = new SavedSearchEntity(
                account.getId(),
                request.name().trim(),
                request.query().trim(),
                request.minimumSeeders(),
                writeSet(request.knownMagnets() == null ? Set.of() : request.knownMagnets())
        );
        return view(repository.save(entity));
    }

    @Transactional
    public SavedSearchView update(
            UserAccount account,
            UUID id,
            SavedSearchRequest request
    ) {
        validate(request);
        SavedSearchEntity entity = require(account, id);
        entity.update(
                request.name().trim(),
                request.query().trim(),
                request.minimumSeeders(),
                request.enabled()
        );
        return view(repository.save(entity));
    }

    @Transactional
    public void delete(UserAccount account, UUID id) {
        repository.delete(require(account, id));
    }

    @Transactional
    public SavedSearchCheck check(UserAccount account, UUID id) {
        SavedSearchEntity entity = require(account, id);
        SearchOutcome outcome = search.search(entity.getQuery(), sources.enabled(account));
        List<TorrentResult> matches = outcome.results().stream()
                .filter(result -> result.seeders() >= entity.getMinimumSeeders())
                .toList();
        Set<String> previous = readSet(entity.getKnownMagnets());
        Set<String> current = new HashSet<>();
        matches.forEach(result -> current.add(result.magnet()));
        int newCount = (int) current.stream().filter(magnet -> !previous.contains(magnet)).count();
        previous.addAll(current);
        Instant now = Instant.now();
        entity.checked(writeSet(previous), now);
        repository.save(entity);
        return new SavedSearchCheck(view(entity), newCount, matches, outcome.failures());
    }

    private SavedSearchEntity require(UserAccount account, UUID id) {
        return repository.findByIdAndUserId(id, account.getId())
                .orElseThrow(() -> new IllegalArgumentException("Saved search not found."));
    }

    private SavedSearchView view(SavedSearchEntity entity) {
        return new SavedSearchView(
                entity.getId(),
                entity.getName(),
                entity.getQuery(),
                entity.getMinimumSeeders(),
                entity.isEnabled(),
                entity.getLastCheckedAt(),
                entity.getCreatedAt(),
                readSet(entity.getKnownMagnets()).size()
        );
    }

    private void validate(SavedSearchRequest request) {
        if (request == null
                || request.name() == null || request.name().trim().isEmpty()
                || request.name().trim().length() > 100) {
            throw new IllegalArgumentException("Saved-search name must be 1–100 characters.");
        }
        if (request.query() == null
                || request.query().trim().isEmpty()
                || request.query().trim().length() > 250) {
            throw new IllegalArgumentException("Query must be 1–250 characters.");
        }
        if (request.minimumSeeders() < 0 || request.minimumSeeders() > 100_000) {
            throw new IllegalArgumentException("Minimum seeders is outside the allowed range.");
        }
    }

    private Set<String> readSet(String value) {
        try {
            return new HashSet<>(mapper.readValue(value, STRING_SET));
        } catch (Exception error) {
            return new HashSet<>();
        }
    }

    private String writeSet(Set<String> values) {
        try {
            return mapper.writeValueAsString(values);
        } catch (Exception error) {
            throw new IllegalStateException("Could not save search state.", error);
        }
    }

    public record SavedSearchRequest(
            String name,
            String query,
            int minimumSeeders,
            boolean enabled,
            Set<String> knownMagnets
    ) {
    }

    public record SavedSearchView(
            UUID id,
            String name,
            String query,
            int minimumSeeders,
            boolean enabled,
            Instant lastCheckedAt,
            Instant createdAt,
            int knownResultCount
    ) {
    }

    public record SavedSearchCheck(
            SavedSearchView savedSearch,
            int newCount,
            List<TorrentResult> results,
            List<String> failures
    ) {
    }
}

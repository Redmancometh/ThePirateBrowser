package com.thepiratebrowser.web.search;

import com.thepiratebrowser.web.account.AccountService;
import com.thepiratebrowser.web.account.UserAccount;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api")
@Validated
public class SearchController {
    private final AccountService accounts;
    private final UserSourceService sources;
    private final TorrentSearchService search;

    public SearchController(
            AccountService accounts,
            UserSourceService sources,
            TorrentSearchService search
    ) {
        this.accounts = accounts;
        this.sources = sources;
        this.search = search;
    }

    @GetMapping("/search")
    public SearchOutcome search(
            Authentication authentication,
            @RequestParam @NotBlank @Size(max = 250) String q,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int minimumSeeders
    ) {
        UserAccount account = accounts.require(authentication.getName());
        SearchOutcome outcome = search.search(q.trim(), sources.enabled(account));
        return new SearchOutcome(
                outcome.results().stream()
                        .filter(result -> result.seeders() >= minimumSeeders)
                        .toList(),
                outcome.failures()
        );
    }

    @GetMapping("/sources")
    public List<SourceView> sources(Authentication authentication) {
        UserAccount account = accounts.require(authentication.getName());
        Set<TorrentSource> enabled = sources.enabled(account);
        return Arrays.stream(TorrentSource.values())
                .map(source -> new SourceView(
                        source,
                        source.displayName(),
                        source.summary(),
                        enabled.contains(source)))
                .toList();
    }

    @PutMapping("/sources")
    public List<SourceView> updateSources(
            Authentication authentication,
            @RequestBody SourceUpdate update
    ) {
        UserAccount account = accounts.require(authentication.getName());
        sources.update(account, update.enabled() == null ? Set.of() : update.enabled());
        return sources(authentication);
    }

    public record SourceView(
            TorrentSource id,
            String name,
            String summary,
            boolean enabled
    ) {
    }

    public record SourceUpdate(Set<TorrentSource> enabled) {
    }
}

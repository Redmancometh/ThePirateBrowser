package com.thepiratebrowser.web.saved;

import com.thepiratebrowser.web.account.AccountService;
import com.thepiratebrowser.web.account.UserAccount;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/saved-searches")
public class SavedSearchController {
    private final AccountService accounts;
    private final SavedSearchService savedSearches;

    public SavedSearchController(AccountService accounts, SavedSearchService savedSearches) {
        this.accounts = accounts;
        this.savedSearches = savedSearches;
    }

    @GetMapping
    public List<SavedSearchService.SavedSearchView> list(Authentication authentication) {
        return savedSearches.list(account(authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedSearchService.SavedSearchView create(
            Authentication authentication,
            @RequestBody SavedSearchService.SavedSearchRequest request
    ) {
        return savedSearches.create(account(authentication), request);
    }

    @PutMapping("/{id}")
    public SavedSearchService.SavedSearchView update(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestBody SavedSearchService.SavedSearchRequest request
    ) {
        return savedSearches.update(account(authentication), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable UUID id) {
        savedSearches.delete(account(authentication), id);
    }

    @PostMapping("/{id}/check")
    public SavedSearchService.SavedSearchCheck check(
            Authentication authentication,
            @PathVariable UUID id
    ) {
        return savedSearches.check(account(authentication), id);
    }

    private UserAccount account(Authentication authentication) {
        return accounts.require(authentication.getName());
    }
}

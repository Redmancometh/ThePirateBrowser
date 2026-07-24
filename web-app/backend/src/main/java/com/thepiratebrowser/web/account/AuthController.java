package com.thepiratebrowser.web.account;

import com.thepiratebrowser.web.config.PirateProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;
    private final PirateProperties properties;

    public AuthController(AccountService accounts, PirateProperties properties) {
        this.accounts = accounts;
        this.properties = properties;
    }

    @GetMapping("/csrf")
    public Map<String, Object> csrf(CsrfToken token) {
        return Map.of(
                "headerName", token.getHeaderName(),
                "token", token.getToken(),
                "registrationEnabled", properties.registrationEnabled()
        );
    }

    @GetMapping("/me")
    public AccountView me(Authentication authentication) {
        UserAccount account = accounts.require(authentication.getName());
        return AccountView.from(account, properties.putIoConfigured(), properties.buildCanary());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountView register(@Valid @RequestBody RegisterRequest request) {
        UserAccount account = accounts.register(
                request.username(),
                request.password(),
                request.inviteCode()
        );
        return AccountView.from(account, properties.putIoConfigured(), properties.buildCanary());
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 40) String username,
            @NotBlank @Size(min = 12, max = 200) String password,
            @NotBlank @Size(max = 200) String inviteCode
    ) {
    }

    public record AccountView(
            String username,
            String role,
            boolean putIoConfigured,
            String canary
    ) {
        static AccountView from(
                UserAccount account,
                boolean putIoConfigured,
                String canary
        ) {
            return new AccountView(
                    account.getUsername(),
                    account.getRole().name(),
                    putIoConfigured,
                    canary.toUpperCase()
            );
        }
    }
}

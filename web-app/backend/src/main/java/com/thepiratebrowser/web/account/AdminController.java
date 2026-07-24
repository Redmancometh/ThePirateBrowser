package com.thepiratebrowser.web.account;

import com.thepiratebrowser.web.audit.AuditEvent;
import com.thepiratebrowser.web.audit.AuditEventRepository;
import com.thepiratebrowser.web.audit.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AccountService accounts;
    private final AuditEventRepository events;
    private final AuditService auditService;

    public AdminController(
            AccountService accounts,
            AuditEventRepository events,
            AuditService auditService
    ) {
        this.accounts = accounts;
        this.events = events;
        this.auditService = auditService;
    }

    @GetMapping("/users")
    public List<UserView> users() {
        return accounts.allUsers().stream().map(UserView::from).toList();
    }

    @PostMapping("/users")
    public UserView create(@Valid @RequestBody CreateUserRequest request,
                           Authentication authentication) {
        UserAccount account = accounts.createByAdmin(
                request.username(), request.password(), request.role());
        auditService.record(authentication.getName(), "ACCOUNT_CREATE", "user",
                account.getId().toString(), account.getUsername());
        return UserView.from(account);
    }

    @PatchMapping("/users/enabled")
    public UserView enabled(@Valid @RequestBody EnabledRequest request,
                            Authentication authentication) {
        UserAccount account = accounts.setEnabled(
                request.id(), request.enabled(), authentication.getName());
        auditService.record(authentication.getName(), "ACCOUNT_ENABLED", "user",
                request.id().toString(), String.valueOf(request.enabled()));
        return UserView.from(account);
    }

    @PatchMapping("/users/password")
    public UserView password(@Valid @RequestBody PasswordRequest request,
                             Authentication authentication) {
        UserAccount account = accounts.resetPassword(request.id(), request.password());
        auditService.record(authentication.getName(), "ACCOUNT_PASSWORD_RESET", "user",
                request.id().toString(), null);
        return UserView.from(account);
    }

    @GetMapping("/audit")
    public List<AuditView> audit() {
        return events.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 200))
                .stream().map(AuditView::from).toList();
    }

    public record CreateUserRequest(
            @NotBlank String username,
            @NotBlank String password,
            UserRole role
    ) {
    }

    public record EnabledRequest(UUID id, boolean enabled) {
    }

    public record PasswordRequest(UUID id, @NotBlank String password) {
    }

    public record UserView(
            UUID id, String username, UserRole role, boolean enabled, Instant createdAt
    ) {
        static UserView from(UserAccount account) {
            return new UserView(account.getId(), account.getUsername(), account.getRole(),
                    account.isEnabled(), account.getCreatedAt());
        }
    }

    public record AuditView(
            UUID id, String username, String action, String targetType,
            String targetId, String detail, Instant createdAt
    ) {
        static AuditView from(AuditEvent event) {
            return new AuditView(event.getId(), event.getUsername(), event.getAction(),
                    event.getTargetType(), event.getTargetId(), event.getDetail(),
                    event.getCreatedAt());
        }
    }
}

package com.thepiratebrowser.web.account;

import com.thepiratebrowser.web.config.PirateProperties;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AccountService {
    private static final Pattern USERNAME =
            Pattern.compile("[a-z0-9][a-z0-9._-]{2,39}");

    private final UserAccountRepository users;
    private final UserSettingsRepository settings;
    private final PasswordEncoder passwordEncoder;
    private final PirateProperties properties;

    public AccountService(
            UserAccountRepository users,
            UserSettingsRepository settings,
            PasswordEncoder passwordEncoder,
            PirateProperties properties
    ) {
        this.users = users;
        this.settings = settings;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Transactional
    public UserAccount register(String rawUsername, String password, String inviteCode) {
        if (!properties.registrationEnabled()) {
            throw new IllegalStateException("Registration is not enabled.");
        }
        if (!constantTimeEquals(properties.registrationInviteCode(), inviteCode)) {
            throw new IllegalArgumentException("The invitation code is invalid.");
        }
        String username = normalizeUsername(rawUsername);
        validatePassword(password);
        if (users.existsByUsername(username)) {
            throw new IllegalArgumentException("That username is already in use.");
        }
        UserAccount account = users.save(new UserAccount(
                username,
                passwordEncoder.encode(password),
                UserRole.USER
        ));
        settings.save(new UserSettings(account.getId(), "all"));
        return account;
    }

    public UserAccount require(String username) {
        return users.findByUsername(normalizeUsername(username))
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));
    }

    public List<UserAccount> allUsers() {
        return users.findAllByOrderByUsernameAsc();
    }

    @Transactional
    public UserAccount createByAdmin(String rawUsername, String password, UserRole role) {
        String username = normalizeUsername(rawUsername);
        validatePassword(password);
        if (users.existsByUsername(username)) {
            throw new IllegalArgumentException("That username is already in use.");
        }
        UserAccount account = users.save(new UserAccount(
                username,
                passwordEncoder.encode(password),
                role == null ? UserRole.USER : role
        ));
        settings.save(new UserSettings(account.getId(), "all"));
        return account;
    }

    @Transactional
    public UserAccount setEnabled(UUID id, boolean enabled, String actingUsername) {
        UserAccount account = users.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));
        if (!enabled && account.getUsername().equals(actingUsername)) {
            throw new IllegalArgumentException("You cannot disable your own account.");
        }
        account.setEnabled(enabled);
        return account;
    }

    @Transactional
    public UserAccount resetPassword(UUID id, String password) {
        validatePassword(password);
        UserAccount account = users.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));
        account.setPasswordHash(passwordEncoder.encode(password));
        return account;
    }

    public static String normalizeUsername(String value) {
        String username = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException(
                    "Username must be 3–40 letters, numbers, dots, dashes, or underscores.");
        }
        return username;
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 200) {
            throw new IllegalArgumentException("Password must be 12–200 characters.");
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = (actual == null ? "" : actual).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }
}

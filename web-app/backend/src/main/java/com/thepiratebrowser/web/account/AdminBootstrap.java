package com.thepiratebrowser.web.account;

import com.thepiratebrowser.web.config.PirateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserAccountRepository users;
    private final UserSettingsRepository settings;
    private final PasswordEncoder passwordEncoder;
    private final PirateProperties properties;

    public AdminBootstrap(
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

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (properties.adminUsername().isBlank() || properties.adminPassword().isBlank()) {
            log.warn("No bootstrap admin configured. Set WEB_ADMIN_USERNAME and WEB_ADMIN_PASSWORD.");
            return;
        }
        String username = AccountService.normalizeUsername(properties.adminUsername());
        AccountService.validatePassword(properties.adminPassword());
        if (users.existsByUsername(username)) {
            return;
        }
        UserAccount admin = users.save(new UserAccount(
                username,
                passwordEncoder.encode(properties.adminPassword()),
                UserRole.ADMIN
        ));
        settings.save(new UserSettings(admin.getId(), "all"));
        log.info("Created bootstrap administrator account '{}'.", username);
    }
}

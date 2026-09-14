package com.example.demo.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first operator so a fresh deployment is reachable at all.
 *
 * <p>Runs in Java rather than as a Flyway seed on purpose: the migration would
 * have to contain a bcrypt digest, and a digest checked into a public
 * repository is a published password for every deployment that ever runs the
 * migration. Hashing here means the value comes from the environment and never
 * enters the schema history.
 *
 * <p>The check is "is the table empty", not "does admin exist", so deleting or
 * renaming the bootstrap account is respected instead of being undone on the
 * next restart.
 */
@Component
public class AdminUserBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserBootstrap.class);
    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEV_ONLY_PASSWORD = "changeme-on-first-login";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;

    public AdminUserBootstrap(UserRepository userRepository,
                              PasswordEncoder passwordEncoder,
                              @Value("${siem.bootstrap.admin-password:" + DEV_ONLY_PASSWORD + "}")
                              String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        userRepository.save(new User(
                DEFAULT_USERNAME, passwordEncoder.encode(adminPassword), Role.ADMIN, true));

        if (DEV_ONLY_PASSWORD.equals(adminPassword)) {
            log.warn("Bootstrapped the '{}' account with the built-in development password. "
                    + "Set SIEM_ADMIN_PASSWORD and recreate the account before this instance "
                    + "is reachable by anyone else.", DEFAULT_USERNAME);
        } else {
            log.info("Bootstrapped the '{}' account from the configured password.", DEFAULT_USERNAME);
        }
    }
}

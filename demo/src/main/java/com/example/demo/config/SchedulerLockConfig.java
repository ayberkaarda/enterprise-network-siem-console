package com.example.demo.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Makes the scheduled tasks safe to run from more than one instance.
 *
 * <p>The lock lives in the application's own database rather than in a separate
 * coordination service: the deployment already depends on that database being
 * up, so borrowing a single row from it adds no new failure mode, while a
 * dedicated lock service would add one.
 *
 * <p>{@code defaultLockAtMostFor} is the safety net for an instance that dies
 * mid-task: the lock is released after at most that long even if the holder
 * never gets to release it itself. It is deliberately longer than a healthy
 * scan takes and shorter than the interval between scans.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT1M")
public class SchedulerLockConfig {

    /**
     * Locks are timed by the database clock, not by each instance's own clock.
     * Two instances whose clocks disagree by more than the lock duration would
     * otherwise be able to hold the same lock at the same time, which is exactly
     * the situation the lock exists to prevent.
     */
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}

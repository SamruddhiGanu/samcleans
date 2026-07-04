package com.storagehealth.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Database initialization is now handled by Flyway (see application.yml).
 * This class is kept as a no-op placeholder for reference only.
 *
 * <p>Flyway automatically runs all scripts under {@code classpath:db/migration}
 * on application startup in version order (V1, V2, V3, V4…).
 *
 * <p>To add a new migration, create: db/migration/V5__description.sql
 */
@Component
@Slf4j
public class DatabaseInitializer {
    // Flyway handles all schema migrations — nothing to do here.
}

package com.storagehealth.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Initializes the SQLite database on first startup.
 *
 * <p>Because Hibernate's {@code ddl-auto: validate} does not create tables,
 * we run the migration SQL manually on application start. This is intentionally
 * minimal — Phase 2 will integrate Flyway for proper versioned migrations.
 *
 * <p>The initializer is idempotent: all {@code CREATE TABLE IF NOT EXISTS} and
 * {@code CREATE INDEX IF NOT EXISTS} statements are safe to run multiple times.
 */
@Component
@Slf4j
public class DatabaseInitializer {

    private final DataSource dataSource;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    public DatabaseInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @EventListener(ApplicationStartedEvent.class)
    public void initializeDatabase() {
        log.info("Initializing SQLite database schema…");
        ensureDirectoryExists();
        runMigration();
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    private void ensureDirectoryExists() {
        String dbPath = datasourceUrl.replace("jdbc:sqlite:", "");
        Path dir = Paths.get(dbPath).getParent();
        if (dir != null && !Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
                log.info("Created database directory: {}", dir);
            } catch (IOException e) {
                log.error("Failed to create database directory: {}", dir, e);
            }
        }
    }

    private void runMigration() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("PRAGMA foreign_keys = ON");

            // V1 — base schema
            runSqlFile(stmt, "db/migration/V1__initial_schema.sql");

            // V2 — importance_score column (Phase 2)
            try {
                runSqlFile(stmt, "db/migration/V2__add_importance_score.sql");
            } catch (SQLException e) {
                log.debug("V2 migration skipped (already applied): {}", e.getMessage());
            }

            // V3 — image analysis fields (Phase 3)
            try {
                runSqlFile(stmt, "db/migration/V3__add_image_analysis.sql");
            } catch (SQLException e) {
                log.debug("V3 migration skipped (already applied): {}", e.getMessage());
            }

            // V4 — cleanup session fields (Phase 4)
            try {
                runSqlFile(stmt, "db/migration/V4__add_cleanup_fields.sql");
            } catch (SQLException e) {
                log.debug("V4 migration skipped (already applied): {}", e.getMessage());
            }

            log.info("Database migrations applied successfully.");

        } catch (IOException | SQLException e) {
            log.error("Failed to initialize database schema", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    private void runSqlFile(Statement stmt, String classpath) throws IOException, SQLException {
        String sql = loadSqlFile(classpath);
        for (String statement : splitStatements(sql)) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                stmt.execute(trimmed);
            }
        }
    }

    private String loadSqlFile(String classpath) throws IOException {
        ClassPathResource resource = new ClassPathResource(classpath);
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Splits a SQL file into individual statements by semicolon,
     * stripping line comments (-- …) before splitting.
     */
    private String[] splitStatements(String sql) {
        // Remove single-line comments to avoid accidental splits on comment semicolons
        String cleaned = sql.replaceAll("--[^\n]*", "");
        return cleaned.split(";");
    }
}

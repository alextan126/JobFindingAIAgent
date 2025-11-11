package com.example.persistence;

import org.flywaydb.core.Flyway;

public final class Migrations {
    private Migrations() {}

    public static void migrate(String jdbcUrl) {
        // Auto-detect database type and use appropriate migrations folder
        String migrationsPath = jdbcUrl.contains("postgresql")
            ? "classpath:db/migrations-postgres"
            : "classpath:db/migrations";

        System.out.println("Using migrations from: " + migrationsPath);

        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, null, null)
                .locations(migrationsPath)
                .validateOnMigrate(false)  // Skip validation - allow checksum mismatches
                .load();

        flyway.migrate();
    }
}
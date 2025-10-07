package com.example.persistence;

import org.flywaydb.core.Flyway;

public final class Migrations {
    private Migrations() {}

    public static void migrate(String jdbcUrl) {
        Flyway.configure()
                .dataSource(jdbcUrl, null, null)
                .locations(
                        "classpath:db/migrations",
                        "filesystem:src/main/resources/db/migrations"
                )
                .load()
                .migrate();
    }
}
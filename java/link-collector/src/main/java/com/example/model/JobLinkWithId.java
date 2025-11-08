package com.example.model;

import java.time.Instant;

/**
 * JobLink record that includes the database ID.
 * Used when querying existing links from the database.
 */
public record JobLinkWithId(
    Integer id,
    String url,
    String hostType,
    String source,
    Instant discoveredAt,
    String status
) {}

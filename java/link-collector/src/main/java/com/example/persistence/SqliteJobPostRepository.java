package com.example.persistence;

import com.example.model.JobPost;

import java.sql.*;
import java.util.List;

public final class SqliteJobPostRepository implements JobPostRepository {
    private final String jdbcUrl;
    public SqliteJobPostRepository(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }

    @Override
    public void upsert(JobPost p) {
        String sql = """
      INSERT INTO job_posts(url, platform, title, company, location, apply_url, description_text, scraped_at, http_status)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(url) DO UPDATE SET
        platform=excluded.platform,
        title=excluded.title,
        company=excluded.company,
        location=excluded.location,
        apply_url=excluded.apply_url,
        description_text=excluded.description_text,
        scraped_at=excluded.scraped_at,
        http_status=excluded.http_status
      """;
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, p.url());
            ps.setString(2, p.platform());
            ps.setString(3, p.title());
            ps.setString(4, p.company());
            ps.setString(5, p.location());
            ps.setString(6, p.applyUrl());
            ps.setString(7, p.descriptionText());
            ps.setString(8, p.scrapedAt().toString());
            if (p.httpStatus() == null) ps.setNull(9, Types.INTEGER); else ps.setInt(9, p.httpStatus());
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }
}
interface JobPostRepository { void upsert(JobPost p); }

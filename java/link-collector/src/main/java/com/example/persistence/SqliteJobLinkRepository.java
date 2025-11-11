package com.example.persistence;

import com.example.model.JobLink;
import com.example.model.JobLinkWithId;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SqliteJobLinkRepository implements JobLinkRepository {
    private final String jdbcUrl;

    public SqliteJobLinkRepository(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    @Override
    public void saveAllIgnoreDuplicates(List<JobLink> links) throws Exception {
        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            conn.setAutoCommit(false);
            // Use database-agnostic INSERT ... ON CONFLICT for PostgreSQL compatibility
            String sql = jdbcUrl.contains("postgresql")
                ? "INSERT INTO job_links(url, host_type, source, discovered_at, status) VALUES (?, ?, ?, ?, 'new') ON CONFLICT (url) DO NOTHING"
                : "INSERT OR IGNORE INTO job_links(url, host_type, source, discovered_at, status) VALUES (?, ?, ?, ?, 'new')";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (JobLink l : links) {
                    ps.setString(1, l.url());
                    ps.setString(2, l.hostType());
                    ps.setString(3, l.source());
                    // Convert Instant to Timestamp for PostgreSQL compatibility
                    ps.setTimestamp(4, java.sql.Timestamp.from(l.discoveredAt()));
                    System.out.printf(
                            "DBG insert: url=%s hostType=%s source=%s discoveredAt=%s%n",
                            l.url(), l.hostType(), l.source(), l.discoveredAt()
                    );

                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        }
    }

    public List<String> findNewUrls(int limit) {
        String sql = "SELECT url FROM job_links WHERE status='new' LIMIT ?";
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                var out = new java.util.ArrayList<String>();
                while (rs.next()) out.add(rs.getString(1));
                return out;
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void markVisited(String url) {
        String sql = "UPDATE job_links SET status='visited', last_checked_at=datetime('now'), last_error=NULL WHERE url=?";
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, url);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void markError(String url, String err) {
        String sql = "UPDATE job_links SET status='error', last_checked_at=datetime('now'), last_error=? WHERE url=?";
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, err);
            ps.setString(2, url);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
    public List<JobLinkWithId> findUnscrapedLinks(int limit) throws Exception {
        String sql = """
            SELECT id, url, host_type, source, discovered_at, status
            FROM job_links
            WHERE status = 'new'
            LIMIT ?
            """;

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, limit);

            List<JobLinkWithId> results = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // Handle discovered_at: PostgreSQL returns Timestamp, SQLite returns String
                    Instant discoveredAt;
                    Object discoveredAtObj = rs.getObject("discovered_at");
                    if (discoveredAtObj instanceof Timestamp) {
                        discoveredAt = ((Timestamp) discoveredAtObj).toInstant();
                    } else {
                        discoveredAt = Instant.parse(rs.getString("discovered_at"));
                    }

                    results.add(new JobLinkWithId(
                        rs.getInt("id"),
                        rs.getString("url"),
                        rs.getString("host_type"),
                        rs.getString("source"),
                        discoveredAt,
                        rs.getString("status")
                    ));
                }
            }
            return results;
        }
    }

    @Override
    public void markAsScraped(Integer jobLinkId) throws Exception {
        String sql = """
            UPDATE job_links
            SET status = 'scraped',
                scraped_at = ?
            WHERE id = ?
            """;

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setObject(1, Instant.now());
            ps.setInt(2, jobLinkId);
            ps.executeUpdate();
        }
    }

    @Override
    public void markAsError(Integer jobLinkId, String errorMessage) throws Exception {
        String sql = """
            UPDATE job_links
            SET status = 'error',
                last_error = ?,
                last_checked_at = ?
            WHERE id = ?
            """;

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, errorMessage);
            ps.setObject(2, Instant.now());
            ps.setInt(3, jobLinkId);
            ps.executeUpdate();
        }
    }

    public String getJobLinkUrl(int jobLinkId) throws Exception {
        String sql = "SELECT url FROM job_links WHERE id = ?";
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, jobLinkId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
                throw new IllegalArgumentException("No job link found with ID: " + jobLinkId);
            }
        }
    }

}

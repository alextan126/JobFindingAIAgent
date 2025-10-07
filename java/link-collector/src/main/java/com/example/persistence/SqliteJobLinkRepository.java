package com.example.persistence;

import com.example.model.JobLink;
import java.sql.*;
import java.time.Instant;
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
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO job_links(url, host_type, source, discovered_at, status)" +
                            "VALUES (?, ?, ?, ?, 'new')"
            )) {
                for (JobLink l : links) {
                    ps.setString(1, l.url());
                    ps.setString(2, l.url());
                    ps.setString(3, l.url());
                    ps.setString(4, l.url());
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


}

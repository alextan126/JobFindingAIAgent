package com.example.persistence;

import com.example.model.Application;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite implementation of ApplicationRepository.
 */
public final class SqliteApplicationRepository implements ApplicationRepository {
    private final String jdbcUrl;

    public SqliteApplicationRepository(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    @Override
    public void create(Application application) throws Exception {
        String sql = """
            INSERT INTO applications (
                user_id, job_info_id, status, applied_at, notes, resume_version
            ) VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, application.userId());
            ps.setInt(2, application.jobInfoId());
            ps.setString(3, application.status());
            ps.setTimestamp(4, Timestamp.from(application.appliedAt()));
            ps.setString(5, application.notes());
            ps.setString(6, application.resumeVersion());

            ps.executeUpdate();
        }
    }

    @Override
    public Optional<Application> findById(Integer id) throws Exception {
        String sql = "SELECT * FROM applications WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToApplication(rs));
                }
                return Optional.empty();
            }
        }
    }

    @Override
    public List<Application> findByUserId(Integer userId) throws Exception {
        String sql = """
            SELECT * FROM applications
            WHERE user_id = ?
            ORDER BY applied_at DESC
            """;

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);

            List<Application> results = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToApplication(rs));
                }
            }
            return results;
        }
    }

    @Override
    public List<Application> findByUserIdAndStatus(Integer userId, String status) throws Exception {
        String sql = """
            SELECT * FROM applications
            WHERE user_id = ? AND status = ?
            ORDER BY applied_at DESC
            """;

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setString(2, status);

            List<Application> results = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToApplication(rs));
                }
            }
            return results;
        }
    }

    @Override
    public void update(Application application) throws Exception {
        String sql = """
            UPDATE applications SET
                status = ?, notes = ?, resume_version = ?
            WHERE id = ?
            """;

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, application.status());
            ps.setString(2, application.notes());
            ps.setString(3, application.resumeVersion());
            ps.setInt(4, application.id());

            ps.executeUpdate();
        }
    }

    @Override
    public boolean existsByUserAndJob(Integer userId, Integer jobInfoId) throws Exception {
        String sql = "SELECT COUNT(*) FROM applications WHERE user_id = ? AND job_info_id = ?";

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setInt(2, jobInfoId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
                return false;
            }
        }
    }

    @Override
    public List<StatusCount> countByStatus(Integer userId) throws Exception {
        String sql = """
            SELECT status, COUNT(*) as count
            FROM applications
            WHERE user_id = ?
            GROUP BY status
            ORDER BY count DESC
            """;

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);

            List<StatusCount> results = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new StatusCount(
                        rs.getString("status"),
                        rs.getInt("count")
                    ));
                }
            }
            return results;
        }
    }

    private Application mapResultSetToApplication(ResultSet rs) throws SQLException {
        // Handle applied_at: PostgreSQL returns Timestamp, SQLite returns String
        Instant appliedAt;
        Object appliedAtObj = rs.getObject("applied_at");
        if (appliedAtObj instanceof Timestamp) {
            appliedAt = ((Timestamp) appliedAtObj).toInstant();
        } else {
            appliedAt = Instant.parse(rs.getString("applied_at"));
        }

        return Application.builder()
            .id(rs.getInt("id"))
            .userId(rs.getInt("user_id"))
            .jobInfoId(rs.getInt("job_info_id"))
            .status(rs.getString("status"))
            .appliedAt(appliedAt)
            .notes(rs.getString("notes"))
            .resumeVersion(rs.getString("resume_version"))
            .build();
    }
}

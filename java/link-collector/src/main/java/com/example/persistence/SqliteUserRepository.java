package com.example.persistence;

import com.example.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;

/**
 * SQLite implementation of UserRepository.
 */
public final class SqliteUserRepository implements UserRepository {
    private final String jdbcUrl;
    private final ObjectMapper objectMapper;

    public SqliteUserRepository(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void create(User user) throws Exception {
        String sql = """
            INSERT INTO users (
                email, password_hash, full_name, resume_path, resume_text,
                skills, preferences, graduation_date, experience_level,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.email());
            ps.setString(2, user.passwordHash());
            ps.setString(3, user.fullName());
            ps.setString(4, user.resumePath());
            ps.setString(5, user.resumeText());
            ps.setString(6, user.skills());
            ps.setString(7, user.preferences());
            ps.setString(8, user.graduationDate());
            ps.setString(9, user.experienceLevel());
            ps.setTimestamp(10, Timestamp.from(user.createdAt()));
            ps.setTimestamp(11, Timestamp.from(user.updatedAt()));

            ps.executeUpdate();
        }
    }

    @Override
    public Optional<User> findByEmail(String email) throws Exception {
        String sql = "SELECT * FROM users WHERE email = ?";

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
                return Optional.empty();
            }
        }
    }

    @Override
    public Optional<User> findById(Integer id) throws Exception {
        String sql = "SELECT * FROM users WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
                return Optional.empty();
            }
        }
    }

    @Override
    public void update(User user) throws Exception {
        String sql = """
            UPDATE users SET
                email = ?, password_hash = ?, full_name = ?,
                resume_path = ?, resume_text = ?, skills = ?,
                preferences = ?, graduation_date = ?,
                experience_level = ?, updated_at = ?
            WHERE id = ?
            """;

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.email());
            ps.setString(2, user.passwordHash());
            ps.setString(3, user.fullName());
            ps.setString(4, user.resumePath());
            ps.setString(5, user.resumeText());
            ps.setString(6, user.skills());
            ps.setString(7, user.preferences());
            ps.setString(8, user.graduationDate());
            ps.setString(9, user.experienceLevel());
            ps.setTimestamp(10, Timestamp.from(Instant.now()));
            ps.setInt(11, user.id());

            ps.executeUpdate();
        }
    }

    @Override
    public boolean existsByEmail(String email) throws Exception {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
                return false;
            }
        }
    }

    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        // Handle created_at: PostgreSQL returns Timestamp, SQLite can return String or Long
        Instant createdAt;
        Object createdAtObj = rs.getObject("created_at");
        if (createdAtObj instanceof Timestamp) {
            createdAt = ((Timestamp) createdAtObj).toInstant();
        } else if (createdAtObj instanceof Long) {
            // SQLite stores as epoch milliseconds
            createdAt = Instant.ofEpochMilli((Long) createdAtObj);
        } else if (createdAtObj instanceof Integer) {
            // SQLite might store as epoch seconds
            createdAt = Instant.ofEpochSecond(((Integer) createdAtObj).longValue());
        } else {
            // SQLite might return timestamp as string of epoch millis
            String createdAtStr = rs.getString("created_at");
            try {
                // Try parsing as epoch milliseconds first
                long epochMillis = Long.parseLong(createdAtStr);
                createdAt = Instant.ofEpochMilli(epochMillis);
            } catch (NumberFormatException e) {
                // Fall back to ISO-8601 parsing
                createdAt = Instant.parse(createdAtStr);
            }
        }

        // Handle updated_at: PostgreSQL returns Timestamp, SQLite can return String or Long
        Instant updatedAt;
        Object updatedAtObj = rs.getObject("updated_at");
        if (updatedAtObj instanceof Timestamp) {
            updatedAt = ((Timestamp) updatedAtObj).toInstant();
        } else if (updatedAtObj instanceof Long) {
            // SQLite stores as epoch milliseconds
            updatedAt = Instant.ofEpochMilli((Long) updatedAtObj);
        } else if (updatedAtObj instanceof Integer) {
            // SQLite might store as epoch seconds
            updatedAt = Instant.ofEpochSecond(((Integer) updatedAtObj).longValue());
        } else {
            // SQLite might return timestamp as string of epoch millis
            String updatedAtStr = rs.getString("updated_at");
            try {
                // Try parsing as epoch milliseconds first
                long epochMillis = Long.parseLong(updatedAtStr);
                updatedAt = Instant.ofEpochMilli(epochMillis);
            } catch (NumberFormatException e) {
                // Fall back to ISO-8601 parsing
                updatedAt = Instant.parse(updatedAtStr);
            }
        }

        return User.builder()
            .id(rs.getInt("id"))
            .email(rs.getString("email"))
            .passwordHash(rs.getString("password_hash"))
            .fullName(rs.getString("full_name"))
            .resumePath(rs.getString("resume_path"))
            .resumeText(rs.getString("resume_text"))
            .skills(rs.getString("skills"))
            .preferences(rs.getString("preferences"))
            .graduationDate(rs.getString("graduation_date"))
            .experienceLevel(rs.getString("experience_level"))
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .build();
    }
}

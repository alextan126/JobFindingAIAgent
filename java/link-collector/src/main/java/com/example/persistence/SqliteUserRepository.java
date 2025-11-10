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
            ps.setObject(10, user.createdAt());
            ps.setObject(11, user.updatedAt());

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
            ps.setObject(10, Instant.now());
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
            .createdAt(Instant.parse(rs.getString("created_at")))
            .updatedAt(Instant.parse(rs.getString("updated_at")))
            .build();
    }
}

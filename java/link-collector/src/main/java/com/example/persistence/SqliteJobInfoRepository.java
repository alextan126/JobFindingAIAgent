package com.example.persistence;

import com.example.model.JobInfo;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite implementation of JobInfoRepository.
 */
public final class SqliteJobInfoRepository implements JobInfoRepository {
    private final String jdbcUrl;

    public SqliteJobInfoRepository(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    @Override
    public void save(JobInfo jobInfo) throws Exception {
        String sql = """
            INSERT INTO job_info (
                job_link_id, scraped_at, scrape_success,
                title, company, location, remote_type,
                salary, description, requirements, job_type,
                posted_date, application_url
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, jobInfo.jobLinkId());
            ps.setObject(2, jobInfo.scrapedAt());
            ps.setInt(3, jobInfo.scrapeSuccess() ? 1 : 0);
            ps.setString(4, jobInfo.title());
            ps.setString(5, jobInfo.company());
            ps.setString(6, jobInfo.location());
            ps.setString(7, jobInfo.remoteType());
            ps.setString(8, jobInfo.salary());
            ps.setString(9, jobInfo.description());
            ps.setString(10, jobInfo.requirements());
            ps.setString(11, jobInfo.jobType());
            ps.setString(12, jobInfo.postedDate());
            ps.setString(13, jobInfo.applicationUrl());

            ps.executeUpdate();
        }
    }

    @Override
    public List<JobInfo> findByJobLinkIds(List<Integer> jobLinkIds) throws Exception {
        if (jobLinkIds == null || jobLinkIds.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(",", jobLinkIds.stream()
            .map(id -> "?")
            .toList());

        String sql = "SELECT * FROM job_info WHERE job_link_id IN (" + placeholders + ")";

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (int i = 0; i < jobLinkIds.size(); i++) {
                ps.setInt(i + 1, jobLinkIds.get(i));
            }

            List<JobInfo> results = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToJobInfo(rs));
                }
            }
            return results;
        }
    }

    @Override
    public boolean existsByJobLinkId(Integer jobLinkId) throws Exception {
        String sql = "SELECT COUNT(*) FROM job_info WHERE job_link_id = ?";

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, jobLinkId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
                return false;
            }
        }
    }

    private JobInfo mapResultSetToJobInfo(ResultSet rs) throws SQLException {
        return new JobInfo(
            rs.getInt("id"),
            rs.getInt("job_link_id"),
            rs.getString("title"),
            rs.getString("company"),
            rs.getString("location"),
            rs.getString("remote_type"),
            rs.getString("salary"),
            rs.getString("description"),
            rs.getString("requirements"),
            rs.getString("job_type"),
            rs.getString("posted_date"),
            rs.getString("application_url"),
            Instant.parse(rs.getString("scraped_at")),
            rs.getInt("scrape_success") == 1
        );
    }
}

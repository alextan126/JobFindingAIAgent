package com.example.model;

import java.time.Instant;

/**
 * Represents a job application by a user.
 * Maps to the applications table in the database.
 */
public record Application(
    Integer id,
    Integer userId,
    Integer jobInfoId,
    String status,  // pending, applied, interviewing, rejected, accepted
    Instant appliedAt,
    String notes,
    String resumeVersion
) {
    /**
     * Application status constants
     */
    public static class Status {
        public static final String PENDING = "pending";
        public static final String APPLIED = "applied";
        public static final String INTERVIEWING = "interviewing";
        public static final String REJECTED = "rejected";
        public static final String ACCEPTED = "accepted";
    }

    // Convenience constants at record level
    public static final String STATUS_PENDING = Status.PENDING;
    public static final String STATUS_APPLIED = Status.APPLIED;
    public static final String STATUS_INTERVIEWING = Status.INTERVIEWING;
    public static final String STATUS_REJECTED = Status.REJECTED;
    public static final String STATUS_ACCEPTED = Status.ACCEPTED;

    /**
     * Validate if a status string is valid.
     */
    public static boolean isValidStatus(String status) {
        return status != null && (
            status.equals(STATUS_PENDING) ||
            status.equals(STATUS_APPLIED) ||
            status.equals(STATUS_INTERVIEWING) ||
            status.equals(STATUS_REJECTED) ||
            status.equals(STATUS_ACCEPTED)
        );
    }

    /**
     * Builder for creating Application instances
     */
    public static class Builder {
        private Integer id;
        private Integer userId;
        private Integer jobInfoId;
        private String status = Status.PENDING;
        private Instant appliedAt;
        private String notes;
        private String resumeVersion;

        public Builder id(Integer id) {
            this.id = id;
            return this;
        }

        public Builder userId(Integer userId) {
            this.userId = userId;
            return this;
        }

        public Builder jobInfoId(Integer jobInfoId) {
            this.jobInfoId = jobInfoId;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder appliedAt(Instant appliedAt) {
            this.appliedAt = appliedAt;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public Builder resumeVersion(String resumeVersion) {
            this.resumeVersion = resumeVersion;
            return this;
        }

        public Application build() {
            if (appliedAt == null) {
                appliedAt = Instant.now();
            }
            return new Application(id, userId, jobInfoId, status, appliedAt, notes, resumeVersion);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}

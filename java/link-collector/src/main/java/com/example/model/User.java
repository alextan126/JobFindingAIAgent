package com.example.model;

import java.time.Instant;

/**
 * Represents a user profile in the system.
 * Maps to the users table in the database.
 */
public record User(
    Integer id,
    String email,
    String passwordHash,
    String fullName,
    String resumePath,
    String resumeText,
    String skills,  // JSON string: ["Java", "Python", ...]
    String preferences,  // JSON string: {"desired_salary": "80k", "locations": [...]}
    String graduationDate,
    String experienceLevel,
    Instant createdAt,
    Instant updatedAt
) {
    /**
     * Builder for creating User instances
     */
    public static class Builder {
        private Integer id;
        private String email;
        private String passwordHash;
        private String fullName;
        private String resumePath;
        private String resumeText;
        private String skills;
        private String preferences;
        private String graduationDate;
        private String experienceLevel;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(Integer id) {
            this.id = id;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder passwordHash(String passwordHash) {
            this.passwordHash = passwordHash;
            return this;
        }

        public Builder fullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public Builder resumePath(String resumePath) {
            this.resumePath = resumePath;
            return this;
        }

        public Builder resumeText(String resumeText) {
            this.resumeText = resumeText;
            return this;
        }

        public Builder skills(String skills) {
            this.skills = skills;
            return this;
        }

        public Builder preferences(String preferences) {
            this.preferences = preferences;
            return this;
        }

        public Builder graduationDate(String graduationDate) {
            this.graduationDate = graduationDate;
            return this;
        }

        public Builder experienceLevel(String experienceLevel) {
            this.experienceLevel = experienceLevel;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public User build() {
            Instant now = Instant.now();
            if (createdAt == null) {
                createdAt = now;
            }
            if (updatedAt == null) {
                updatedAt = now;
            }
            return new User(id, email, passwordHash, fullName, resumePath,
                          resumeText, skills, preferences, graduationDate,
                          experienceLevel, createdAt, updatedAt);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}

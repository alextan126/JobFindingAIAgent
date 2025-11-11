package com.example.model;

import java.time.Instant;

/**
 * Represents detailed information scraped from a job posting.
 * Maps to the job_info table in the database.
 */
public record JobInfo(
    Integer id,
    Integer jobLinkId,
    String title,
    String company,
    String location,
    String remoteType,  // 'remote', 'hybrid', 'onsite', or null
    String salary,
    String description,
    String requirements,  // JSON string
    String jobType,
    String postedDate,
    String applicationUrl,
    Instant scrapedAt,
    boolean scrapeSuccess
) {
    /**
     * Builder for creating JobInfo instances
     */
    public static class Builder {
        private Integer id;
        private Integer jobLinkId;
        private String title;
        private String company;
        private String location;
        private String remoteType;
        private String salary;
        private String description;
        private String requirements;
        private String jobType;
        private String postedDate;
        private String applicationUrl;
        private Instant scrapedAt;
        private boolean scrapeSuccess = true;

        public Builder id(Integer id) {
            this.id = id;
            return this;
        }

        public Builder jobLinkId(Integer jobLinkId) {
            this.jobLinkId = jobLinkId;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder company(String company) {
            this.company = company;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder remoteType(String remoteType) {
            this.remoteType = remoteType;
            return this;
        }

        public Builder salary(String salary) {
            this.salary = salary;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder requirements(String requirements) {
            this.requirements = requirements;
            return this;
        }

        public Builder jobType(String jobType) {
            this.jobType = jobType;
            return this;
        }

        public Builder postedDate(String postedDate) {
            this.postedDate = postedDate;
            return this;
        }

        public Builder applicationUrl(String applicationUrl) {
            this.applicationUrl = applicationUrl;
            return this;
        }

        public Builder scrapedAt(Instant scrapedAt) {
            this.scrapedAt = scrapedAt;
            return this;
        }

        public Builder scrapeSuccess(boolean scrapeSuccess) {
            this.scrapeSuccess = scrapeSuccess;
            return this;
        }

        public JobInfo build() {
            if (scrapedAt == null) {
                scrapedAt = Instant.now();
            }
            return new JobInfo(id, jobLinkId, title, company, location, remoteType,
                             salary, description, requirements, jobType, postedDate,
                             applicationUrl, scrapedAt, scrapeSuccess);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}

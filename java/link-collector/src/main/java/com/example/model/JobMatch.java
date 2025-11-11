package com.example.model;

import java.util.List;

/**
 * Represents a job matching result with score and explanation.
 */
public record JobMatch(
    JobInfo jobInfo,
    double matchScore,        // 0.0 to 100.0
    List<String> matchedSkills,
    List<String> missingSkills,
    String explanation
) implements Comparable<JobMatch> {

    @Override
    public int compareTo(JobMatch other) {
        // Sort by score descending
        return Double.compare(other.matchScore, this.matchScore);
    }

    /**
     * Get match level category.
     */
    public String getMatchLevel() {
        if (matchScore >= 80) return "Excellent";
        if (matchScore >= 60) return "Good";
        if (matchScore >= 40) return "Fair";
        return "Low";
    }
}

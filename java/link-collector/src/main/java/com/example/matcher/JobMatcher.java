package com.example.matcher;

import com.example.model.JobInfo;
import com.example.model.JobMatch;
import com.example.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Matches users to jobs based on skill overlap and requirements.
 */
public final class JobMatcher {
    private final ObjectMapper objectMapper;

    public JobMatcher() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Match a user against a list of jobs and return sorted matches.
     *
     * @param user the user with parsed resume skills
     * @param jobs the list of available jobs
     * @return sorted list of job matches (best matches first)
     */
    public List<JobMatch> matchJobs(User user, List<JobInfo> jobs) throws Exception {
        if (user.skills() == null || user.skills().trim().isEmpty()) {
            throw new IllegalArgumentException("User has no skills parsed. Please parse resume first.");
        }

        Set<String> userSkills = parseSkillsToSet(user.skills());
        System.out.println("User skills: " + userSkills);

        List<JobMatch> matches = new ArrayList<>();

        for (JobInfo job : jobs) {
            if (job.requirements() == null || job.requirements().trim().isEmpty()) {
                continue; // Skip jobs without requirements
            }

            Set<String> jobRequirements = parseSkillsToSet(job.requirements());
            JobMatch match = calculateMatch(user, job, userSkills, jobRequirements);
            matches.add(match);
        }

        // Sort by score descending
        Collections.sort(matches);
        return matches;
    }

    /**
     * Calculate match score between user skills and job requirements.
     */
    private JobMatch calculateMatch(User user, JobInfo job, Set<String> userSkills, Set<String> jobRequirements) {
        // Find matched and missing skills
        Set<String> matched = new HashSet<>();
        Set<String> missing = new HashSet<>();

        for (String req : jobRequirements) {
            boolean found = false;
            for (String skill : userSkills) {
                if (skillsMatch(skill, req)) {
                    matched.add(req);
                    found = true;
                    break;
                }
            }
            if (!found) {
                missing.add(req);
            }
        }

        // Calculate score from skill overlap
        double finalScore = jobRequirements.isEmpty() ? 0.0
            : (matched.size() * 100.0) / jobRequirements.size();

        // Generate explanation
        String explanation = generateExplanation(matched, missing);

        return new JobMatch(
            job,
            finalScore,
            new ArrayList<>(matched),
            new ArrayList<>(missing),
            explanation
        );
    }

    /**
     * Check if two skills match (case-insensitive, handles variations).
     */
    private boolean skillsMatch(String userSkill, String jobRequirement) {
        String u = userSkill.toLowerCase().trim();
        String j = jobRequirement.toLowerCase().trim();

        // Exact match
        if (u.equals(j)) return true;

        // Contains match (e.g., "JavaScript" matches "JavaScript ES6")
        if (u.contains(j) || j.contains(u)) return true;

        // Handle common variations
        Map<String, Set<String>> synonyms = Map.of(
            "js", Set.of("javascript"),
            "ts", Set.of("typescript"),
            "k8s", Set.of("kubernetes"),
            "postgres", Set.of("postgresql"),
            "react.js", Set.of("react", "reactjs"),
            "node.js", Set.of("node", "nodejs")
        );

        for (Map.Entry<String, Set<String>> entry : synonyms.entrySet()) {
            if ((u.equals(entry.getKey()) && entry.getValue().contains(j)) ||
                (j.equals(entry.getKey()) && entry.getValue().contains(u))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Generate human-readable explanation for the match.
     */
    private String generateExplanation(Set<String> matched, Set<String> missing) {
        StringBuilder sb = new StringBuilder();

        if (!matched.isEmpty()) {
            sb.append("✓ You have ").append(matched.size()).append(" matching skill(s): ");
            sb.append(String.join(", ", matched.stream().limit(5).toList()));
            if (matched.size() > 5) {
                sb.append(", and ").append(matched.size() - 5).append(" more");
            }
            sb.append("\n");
        }

        if (!missing.isEmpty()) {
            sb.append("✗ Missing ").append(missing.size()).append(" skill(s): ");
            sb.append(String.join(", ", missing.stream().limit(5).toList()));
            if (missing.size() > 5) {
                sb.append(", and ").append(missing.size() - 5).append(" more");
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }

    /**
     * Parse JSON array string to Set of skills.
     */
    private Set<String> parseSkillsToSet(String jsonArrayString) throws Exception {
        JsonNode array = objectMapper.readTree(jsonArrayString);
        Set<String> skills = new HashSet<>();

        if (array.isArray()) {
            for (JsonNode node : array) {
                String skill = node.asText().trim();
                if (!skill.isEmpty()) {
                    skills.add(skill);
                }
            }
        }

        return skills;
    }
}

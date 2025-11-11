package com.example.scrape;

import com.example.model.JobInfo;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Service for parsing job posting HTML using OpenAI.
 * Sends HTML content to OpenAI and receives structured JSON job data.
 */
public final class OpenAIJobParser {
    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
        You are a job posting data extraction specialist. Your task is to extract structured information
        from job posting HTML/text content and return it as valid JSON.

        Extract the following fields (use null for any field you cannot find):
        - title: Job title (string, or null)
        - company: Company name (string, or null)
        - location: Job location (string, or null)
        - remote_type: One of: "remote", "hybrid", "onsite", or null if not specified
        - salary: Salary range or compensation info (string, or null if not mentioned)
        - description: Full job description/summary (string, or null)
        - requirements: TECHNICAL qualifications and hard skills ONLY as a JSON array of strings.
          Focus on: programming languages, frameworks, tools, technologies, certifications, specific experience, education.
          INCLUDE: "Python", "React", "AWS", "Bachelor's in CS", "3+ years Java", "Kubernetes", "SQL", "Docker", "CI/CD"
          EXCLUDE: Soft skills like "leadership", "communication", "team player", "problem solving", "curiosity"
          Extract 5-15 specific technical requirements. If none found, use empty array []
        - job_type: One of: "full-time", "part-time", "internship", "contract", "temporary", "other", or null
        - posted_date: When the job was posted (string in ISO format if possible, or null)
        - application_url: URL to apply (string, or null)

        Return ONLY valid JSON in this exact format (no markdown, no explanation):
        {
          "title": "Software Engineer",
          "company": "Acme Corp",
          "location": "San Francisco, CA",
          "remote_type": "hybrid",
          "salary": "$120k-$150k",
          "description": "We are seeking...",
          "requirements": ["Bachelor's in Computer Science", "5+ years Python/Django", "React.js", "PostgreSQL", "AWS (EC2, S3, Lambda)", "Docker & Kubernetes", "REST API design", "Git/GitHub", "CI/CD pipelines"],
          "job_type": "full-time",
          "posted_date": "2025-11-01",
          "application_url": "https://..."
        }

        Critical rules:
        - ALWAYS return valid JSON even if most fields are null
        - If a field is not found, use null (not empty string, not "N/A")
        - For requirements: use empty array [] if none found, NOT null
        - For requirements: ONLY include technical skills (languages, frameworks, tools, years of experience, degrees)
        - For requirements: EXCLUDE all soft skills (communication, leadership, teamwork, problem-solving, etc.)
        - For requirements: Be specific and concise, extract 5-15 technical items
        - Do not include ANY text outside the JSON object
        - Ensure all JSON is properly escaped
        """;

    public OpenAIJobParser(String apiKey) {
        this.openAiService = new OpenAiService(apiKey, Duration.ofSeconds(60));
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Parse text content and extract structured job information using OpenAI.
     *
     * @param textContent The text content of the job posting page
     * @param jobLinkId The ID of the job link being processed
     * @param sourceUrl The URL of the job posting (for context)
     * @return JobInfo object with extracted data
     * @throws Exception if parsing fails
     */
    public JobInfo parseJobText(String textContent, Integer jobLinkId, String sourceUrl) throws Exception {
        // Truncate text if too long (OpenAI has token limits)
        // Increased to 50K characters (~12K tokens) for better context
        String truncatedText = truncateText(textContent, 50000);

        // Log what we're sending (first 500 chars for debugging)
        System.out.println("--- Content Preview (first 500 chars) ---");
        System.out.println(truncatedText.substring(0, Math.min(500, truncatedText.length())));
        System.out.println("--- End Preview ---");

        // Create the user message with the text content
        String userPrompt = String.format(
            "Extract job posting information from the following job posting page.\nSource URL: %s\n\nContent:\n%s",
            sourceUrl,
            truncatedText
        );

        // Build the chat completion request
        ChatCompletionRequest request = ChatCompletionRequest.builder()
            .model("gpt-4o-mini")  // Using gpt-4o-mini for cost efficiency
            .messages(List.of(
                new ChatMessage(ChatMessageRole.SYSTEM.value(), SYSTEM_PROMPT),
                new ChatMessage(ChatMessageRole.USER.value(), userPrompt)
            ))
            .temperature(0.2)  // Slightly higher for better extraction
            .maxTokens(3000)   // Increased for longer descriptions
            .build();

        // Call OpenAI API
        var response = openAiService.createChatCompletion(request);

        // Validate response
        if (response.getChoices() == null || response.getChoices().isEmpty()) {
            throw new Exception("OpenAI returned empty response");
        }

        String jsonResponse = response.getChoices().get(0).getMessage().getContent();

        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            throw new Exception("OpenAI returned empty content");
        }

        jsonResponse = jsonResponse.trim();

        // Remove markdown code blocks if present
        jsonResponse = cleanJsonResponse(jsonResponse);

        // Validate JSON is not empty
        if (jsonResponse.isEmpty() || jsonResponse.equals("{}")) {
            throw new Exception("OpenAI returned empty or invalid JSON");
        }

        // Log the JSON response for debugging
        System.out.println("--- OpenAI Response Preview (first 300 chars) ---");
        System.out.println(jsonResponse.substring(0, Math.min(300, jsonResponse.length())));
        System.out.println("--- End Response ---");

        // Parse the JSON response
        return parseJsonToJobInfo(jsonResponse, jobLinkId);
    }

    /**
     * Truncate text to fit within token limits while preserving important content.
     * Tries to break at sentence boundaries.
     */
    private String truncateText(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }

        // Try to find a good break point (end of sentence)
        int truncateAt = maxChars;

        // Look for sentence endings near the limit
        for (int i = maxChars; i > maxChars - 500 && i > 0; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                truncateAt = i + 1;
                break;
            }
        }

        String truncated = text.substring(0, truncateAt).trim();
        System.out.println("Content truncated from " + text.length() + " to " + truncated.length() + " characters");

        return truncated + "\n\n[Content truncated for length]";
    }

    /**
     * Clean JSON response by removing markdown code blocks if present.
     */
    private String cleanJsonResponse(String response) {
        // Remove ```json and ``` markers if present
        response = response.trim();
        if (response.startsWith("```json")) {
            response = response.substring("```json".length());
        } else if (response.startsWith("```")) {
            response = response.substring("```".length());
        }
        if (response.endsWith("```")) {
            response = response.substring(0, response.length() - 3);
        }
        return response.trim();
    }

    /**
     * Parse JSON string into JobInfo object.
     */
    private JobInfo parseJsonToJobInfo(String json, Integer jobLinkId) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        JobInfo.Builder builder = JobInfo.builder()
            .jobLinkId(jobLinkId)
            .scrapedAt(Instant.now())
            .scrapeSuccess(true);

        // Extract fields from JSON
        builder.title(getStringOrNull(root, "title"));
        builder.company(getStringOrNull(root, "company"));
        builder.location(getStringOrNull(root, "location"));
        builder.remoteType(getStringOrNull(root, "remote_type"));
        builder.salary(getStringOrNull(root, "salary"));
        builder.description(getStringOrNull(root, "description"));
        builder.jobType(getStringOrNull(root, "job_type"));
        builder.postedDate(getStringOrNull(root, "posted_date"));
        builder.applicationUrl(getStringOrNull(root, "application_url"));

        // Handle requirements array - convert to JSON string
        if (root.has("requirements") && !root.get("requirements").isNull()) {
            JsonNode requirementsNode = root.get("requirements");
            // Only store if it's an array with elements
            if (requirementsNode.isArray() && requirementsNode.size() > 0) {
                builder.requirements(requirementsNode.toString());
            } else {
                // Empty array or invalid - store null
                builder.requirements(null);
            }
        } else {
            builder.requirements(null);
        }

        return builder.build();
    }

    /**
     * Safely extract string value from JSON node, returning null if not present or null.
     */
    private String getStringOrNull(JsonNode node, String fieldName) {
        if (!node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }
        String value = node.get(fieldName).asText();
        return value.isEmpty() ? null : value;
    }

    /**
     * Close the OpenAI service when done.
     */
    public void close() {
        if (openAiService != null) {
            openAiService.shutdownExecutor();
        }
    }
}

package com.example.scrape;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;

import java.time.Duration;
import java.util.List;

/**
 * Parses resumes using OpenAI to extract structured information.
 * Extracts technical skills, experience, education, etc.
 */
public final class ResumeParser {
    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
        You are a resume parsing specialist. Extract structured information from resumes.

        Extract the following information:
        - full_name: Candidate's full name (string)
        - email: Email address (string, or null)
        - phone: Phone number (string, or null)
        - skills: TECHNICAL skills ONLY as JSON array
          Include: programming languages, frameworks, tools, technologies, cloud platforms
          Exclude: soft skills like "leadership", "communication", "team player"
          Example: ["Python", "React", "AWS", "Docker", "PostgreSQL", "Git"]
        - experience_years: Total years of professional experience (string like "3", "5+", or null)
        - experience_level: "entry", "mid", or "senior" based on experience
        - education: Highest degree (string like "Bachelor's in Computer Science", or null)
        - graduation_date: Expected or actual graduation date (string, or null)

        Return ONLY valid JSON in this format:
        {
          "full_name": "John Doe",
          "email": "john@example.com",
          "phone": "+1-555-0100",
          "skills": ["Python", "Django", "PostgreSQL", "AWS", "Docker", "React"],
          "experience_years": "3",
          "experience_level": "mid",
          "education": "Bachelor's in Computer Science",
          "graduation_date": "2022-05"
        }

        Rules:
        - ONLY extract technical skills for the skills array
        - Use null for any field not found
        - Ensure all JSON is valid and parseable
        - Do not include ANY text outside the JSON object
        """;

    public ResumeParser(String apiKey) {
        this.openAiService = new OpenAiService(apiKey, Duration.ofSeconds(60));
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Parse resume text and extract structured information.
     *
     * @param resumeText The text content of the resume
     * @return ParsedResume object with extracted data
     * @throws Exception if parsing fails
     */
    public ParsedResume parseResume(String resumeText) throws Exception {
        System.out.println("Parsing resume with OpenAI...");

        // Truncate if too long
        String truncated = resumeText.length() > 20000
            ? resumeText.substring(0, 20000) + "\n[truncated]"
            : resumeText;

        String userPrompt = "Extract information from this resume:\n\n" + truncated;

        ChatCompletionRequest request = ChatCompletionRequest.builder()
            .model("gpt-4o-mini")
            .messages(List.of(
                new ChatMessage(ChatMessageRole.SYSTEM.value(), SYSTEM_PROMPT),
                new ChatMessage(ChatMessageRole.USER.value(), userPrompt)
            ))
            .temperature(0.1)
            .maxTokens(1500)
            .build();

        var response = openAiService.createChatCompletion(request);

        if (response.getChoices() == null || response.getChoices().isEmpty()) {
            throw new Exception("OpenAI returned empty response");
        }

        String jsonResponse = response.getChoices().get(0).getMessage().getContent();
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            throw new Exception("OpenAI returned empty content");
        }

        // Clean markdown code blocks
        jsonResponse = cleanJsonResponse(jsonResponse.trim());

        System.out.println("--- Parsed Resume Preview ---");
        System.out.println(jsonResponse.substring(0, Math.min(300, jsonResponse.length())));
        System.out.println("--- End Preview ---");

        return parseJsonToResume(jsonResponse);
    }

    private String cleanJsonResponse(String response) {
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

    private ParsedResume parseJsonToResume(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        return new ParsedResume(
            getStringOrNull(root, "full_name"),
            getStringOrNull(root, "email"),
            getStringOrNull(root, "phone"),
            root.has("skills") && root.get("skills").isArray()
                ? root.get("skills").toString()
                : null,
            getStringOrNull(root, "experience_years"),
            getStringOrNull(root, "experience_level"),
            getStringOrNull(root, "education"),
            getStringOrNull(root, "graduation_date")
        );
    }

    private String getStringOrNull(JsonNode node, String fieldName) {
        if (!node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }
        String value = node.get(fieldName).asText();
        return value.isEmpty() ? null : value;
    }

    public void close() {
        if (openAiService != null) {
            openAiService.shutdownExecutor();
        }
    }

    /**
     * Result of resume parsing.
     */
    public record ParsedResume(
        String fullName,
        String email,
        String phone,
        String skills,          // JSON array as string
        String experienceYears,
        String experienceLevel,
        String education,
        String graduationDate
    ) {}
}

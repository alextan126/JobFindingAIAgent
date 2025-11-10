package com.example.api;

import com.example.matcher.JobMatcher;
import com.example.model.*;
import com.example.persistence.*;
import com.example.scrape.ResumeParser;
import com.example.util.PasswordUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.List;
import java.util.Map;

/**
 * REST API server for the Job Finding AI Agent.
 * Provides endpoints for frontend integration.
 */
public class ApiServer {
    private final String jdbcUrl;
    private final String openAiApiKey;
    private final ObjectMapper objectMapper;
    private final JobMatcher jobMatcher;

    public ApiServer(String jdbcUrl, String openAiApiKey) {
        this.jdbcUrl = jdbcUrl;
        this.openAiApiKey = openAiApiKey;
        this.objectMapper = new ObjectMapper();
        this.jobMatcher = new JobMatcher();
    }

    public void start(int port) {
        Javalin app = Javalin.create(config -> {
            // Enable CORS for frontend
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> {
                    it.anyHost();
                });
            });
        }).start(port);

        // Health check
        app.get("/api/health", ctx -> {
            ctx.json(Map.of("status", "ok", "message", "Job Finding AI Agent API"));
        });

        // User endpoints
        app.post("/api/users/register", this::registerUser);
        app.post("/api/users/login", this::loginUser);
        app.get("/api/users/{email}", this::getUser);
        app.post("/api/users/{email}/resume", this::parseResume);

        // Job endpoints
        app.get("/api/jobs", this::getAllJobs);
        app.get("/api/jobs/{id}", this::getJobById);
        app.get("/api/jobs/search", this::searchJobs);

        // Job matching endpoints
        app.get("/api/users/{email}/matches", this::getJobMatches);

        // Application endpoints
        app.post("/api/applications", this::createApplication);
        app.get("/api/users/{email}/applications", this::getUserApplications);
        app.patch("/api/applications/{id}/status", this::updateApplicationStatus);
        app.get("/api/users/{email}/applications/stats", this::getApplicationStats);

        System.out.println("✅ API Server started on http://localhost:" + port);
        System.out.println("📚 API Documentation available at endpoints:");
        System.out.println("   GET  /api/health");
        System.out.println("   POST /api/users/register");
        System.out.println("   POST /api/users/login");
        System.out.println("   GET  /api/users/{email}");
        System.out.println("   POST /api/users/{email}/resume");
        System.out.println("   GET  /api/jobs");
        System.out.println("   GET  /api/jobs/{id}");
        System.out.println("   GET  /api/jobs/search?q=keyword");
        System.out.println("   GET  /api/users/{email}/matches");
        System.out.println("   POST /api/applications");
        System.out.println("   GET  /api/users/{email}/applications");
        System.out.println("   PATCH /api/applications/{id}/status");
        System.out.println("   GET  /api/users/{email}/applications/stats");
    }

    // ========== User Endpoints ==========

    private void registerUser(Context ctx) {
        try {
            var body = ctx.bodyAsClass(Map.class);
            String fullName = (String) body.get("fullName");
            String email = (String) body.get("email");
            String password = (String) body.get("password");

            if (fullName == null || email == null || password == null) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "Missing required fields"));
                return;
            }

            UserRepository userRepo = new SqliteUserRepository(jdbcUrl);

            // Check if user exists
            if (userRepo.existsByEmail(email)) {
                ctx.status(HttpStatus.CONFLICT).json(Map.of("error", "User already exists"));
                return;
            }

            // Create user
            String hashedPassword = PasswordUtil.hashPassword(password);
            User user = User.builder()
                .fullName(fullName)
                .email(email)
                .passwordHash(hashedPassword)
                .build();

            userRepo.create(user);

            // Fetch created user
            var createdUser = userRepo.findByEmail(email).orElseThrow();

            ctx.status(HttpStatus.CREATED).json(Map.of(
                "id", createdUser.id(),
                "fullName", createdUser.fullName(),
                "email", createdUser.email(),
                "createdAt", createdUser.createdAt().toString()
            ));
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", e.getMessage()));
        }
    }

    private void loginUser(Context ctx) {
        try {
            var body = ctx.bodyAsClass(Map.class);
            String email = (String) body.get("email");
            String password = (String) body.get("password");

            if (email == null || password == null) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "Missing email or password"));
                return;
            }

            UserRepository userRepo = new SqliteUserRepository(jdbcUrl);
            var userOpt = userRepo.findByEmail(email);

            if (userOpt.isEmpty()) {
                ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "Invalid credentials"));
                return;
            }

            User user = userOpt.get();
            if (!PasswordUtil.verifyPassword(password, user.passwordHash())) {
                ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "Invalid credentials"));
                return;
            }

            ctx.json(Map.of(
                "id", user.id(),
                "fullName", user.fullName(),
                "email", user.email(),
                "skills", user.skills() != null ? user.skills() : "[]",
                "experienceLevel", user.experienceLevel() != null ? user.experienceLevel() : ""
            ));
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", e.getMessage()));
        }
    }

    private void getUser(Context ctx) {
        try {
            String email = ctx.pathParam("email");
            UserRepository userRepo = new SqliteUserRepository(jdbcUrl);
            var userOpt = userRepo.findByEmail(email);

            if (userOpt.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "User not found"));
                return;
            }

            User user = userOpt.get();
            ctx.json(Map.of(
                "id", user.id(),
                "fullName", user.fullName(),
                "email", user.email(),
                "skills", user.skills() != null ? user.skills() : "[]",
                "experienceLevel", user.experienceLevel() != null ? user.experienceLevel() : "",
                "graduationDate", user.graduationDate() != null ? user.graduationDate() : ""
            ));
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", e.getMessage()));
        }
    }

    private void parseResume(Context ctx) {
        try {
            String email = ctx.pathParam("email");
            var body = ctx.bodyAsClass(Map.class);
            String resumeText = (String) body.get("resumeText");

            if (resumeText == null || resumeText.trim().isEmpty()) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "Missing resumeText"));
                return;
            }

            UserRepository userRepo = new SqliteUserRepository(jdbcUrl);
            var userOpt = userRepo.findByEmail(email);

            if (userOpt.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "User not found"));
                return;
            }

            // Parse resume
            ResumeParser parser = new ResumeParser(openAiApiKey);
            var parsed = parser.parseResume(resumeText);
            parser.close();

            // Update user with parsed data
            User user = userOpt.get();
            User updatedUser = User.builder()
                .id(user.id())
                .fullName(user.fullName())
                .email(user.email())
                .passwordHash(user.passwordHash())
                .resumePath(user.resumePath())
                .resumeText(user.resumeText())
                .skills(parsed.skills())
                .preferences(user.preferences())
                .experienceLevel(parsed.experienceLevel())
                .graduationDate(parsed.graduationDate())
                .createdAt(user.createdAt())
                .build();

            userRepo.update(updatedUser);

            ctx.json(Map.of(
                "message", "Resume parsed successfully",
                "skills", parsed.skills() != null ? parsed.skills() : "[]",
                "experienceLevel", parsed.experienceLevel() != null ? parsed.experienceLevel() : "",
                "graduationDate", parsed.graduationDate() != null ? parsed.graduationDate() : ""
            ));
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", e.getMessage()));
        }
    }

    // ========== Job Endpoints ==========

    private void getAllJobs(Context ctx) {
        try {
            JobInfoRepository jobRepo = new SqliteJobInfoRepository(jdbcUrl);
            List<JobInfo> jobs = jobRepo.findAll();

            ctx.json(Map.of(
                "total", jobs.size(),
                "jobs", jobs
            ));
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", e.getMessage()));
        }
    }

    private void getJobById(Context ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            JobInfoRepository jobRepo = new SqliteJobInfoRepository(jdbcUrl);
            List<JobInfo> jobs = jobRepo.findByJobLinkIds(List.of(id));

            if (jobs.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "Job not found"));
                return;
            }

            ctx.json(jobs.get(0));
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", e.getMessage()));
        }
    }

    private void searchJobs(Context ctx) {
        try {
            String query = ctx.queryParam("q");
            JobInfoRepository jobRepo = new SqliteJobInfoRepository(jdbcUrl);
            List<JobInfo> allJobs = jobRepo.findAll();

            if (query == null || query.trim().isEmpty()) {
                ctx.json(Map.of("total", allJobs.size(), "jobs", allJobs));
                return;
            }

            // Simple text search in title, company, location, description
            String lowerQuery = query.toLowerCase();
            List<JobInfo> filtered = allJobs.stream()
                .filter(job ->
                    (job.title() != null && job.title().toLowerCase().contains(lowerQuery)) ||
                    (job.company() != null && job.company().toLowerCase().contains(lowerQuery)) ||
                    (job.location() != null && job.location().toLowerCase().contains(lowerQuery)) ||
                    (job.description() != null && job.description().toLowerCase().contains(lowerQuery))
                )
                .toList();

            ctx.json(Map.of("total", filtered.size(), "jobs", filtered));
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", e.getMessage()));
        }
    }

    // ========== Matching Endpoints ==========

    private void getJobMatches(Context ctx) {
        try {
            String email = ctx.pathParam("email");
            int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(10);

            UserRepository userRepo = new SqliteUserRepository(jdbcUrl);
            var userOpt = userRepo.findByEmail(email);

            if (userOpt.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "User not found"));
                return;
            }

            User user = userOpt.get();

            if (user.skills() == null || user.skills().trim().isEmpty()) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "User has no skills parsed"));
                return;
            }

            JobInfoRepository jobRepo = new SqliteJobInfoRepository(jdbcUrl);
            List<JobInfo> allJobs = jobRepo.findAll();

            List<JobMatch> matches = jobMatcher.matchJobs(user, allJobs);
            List<JobMatch> topMatches = matches.stream().limit(limit).toList();

            ctx.json(Map.of(
                "total", matches.size(),
                "matches", topMatches
            ));
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", e.getMessage()));
        }
    }

    // ========== Application Endpoints ==========

    private void createApplication(Context ctx) {
        try {
            var body = ctx.bodyAsClass(Map.class);
            String email = (String) body.get("email");
            Integer jobInfoId = (Integer) body.get("jobInfoId");
            String notes = (String) body.get("notes");

            if (email == null || jobInfoId == null) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "Missing required fields"));
                return;
            }

            UserRepository userRepo = new SqliteUserRepository(jdbcUrl);
            var userOpt = userRepo.findByEmail(email);

            if (userOpt.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "User not found"));
                return;
            }

            User user = userOpt.get();
            ApplicationRepository appRepo = new SqliteApplicationRepository(jdbcUrl);

            // Check for duplicate
            if (appRepo.existsByUserAndJob(user.id(), jobInfoId)) {
                ctx.status(HttpStatus.CONFLICT).json(Map.of("error", "Already applied to this job"));
                return;
            }

            Application app = Application.builder()
                .userId(user.id())
                .jobInfoId(jobInfoId)
                .status(Application.STATUS_PENDING)
                .notes(notes)
                .build();

            appRepo.create(app);

            ctx.status(HttpStatus.CREATED).json(Map.of("message", "Application created successfully"));
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", e.getMessage()));
        }
    }

    private void getUserApplications(Context ctx) {
        try {
            String email = ctx.pathParam("email");
            String statusFilter = ctx.queryParam("status");

            UserRepository userRepo = new SqliteUserRepository(jdbcUrl);
            var userOpt = userRepo.findByEmail(email);

            if (userOpt.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "User not found"));
                return;
            }

            User user = userOpt.get();
            ApplicationRepository appRepo = new SqliteApplicationRepository(jdbcUrl);

            List<Application> applications;
            if (statusFilter != null && !statusFilter.isEmpty()) {
                applications = appRepo.findByUserIdAndStatus(user.id(), statusFilter);
            } else {
                applications = appRepo.findByUserId(user.id());
            }

            ctx.json(Map.of(
                "total", applications.size(),
                "applications", applications
            ));
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", e.getMessage()));
        }
    }

    private void updateApplicationStatus(Context ctx) {
        try {
            int appId = Integer.parseInt(ctx.pathParam("id"));
            var body = ctx.bodyAsClass(Map.class);
            String status = (String) body.get("status");
            String notes = (String) body.get("notes");

            if (status == null) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "Missing status"));
                return;
            }

            if (!Application.isValidStatus(status)) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "Invalid status"));
                return;
            }

            ApplicationRepository appRepo = new SqliteApplicationRepository(jdbcUrl);
            var appOpt = appRepo.findById(appId);

            if (appOpt.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "Application not found"));
                return;
            }

            Application app = appOpt.get();
            Application updated = Application.builder()
                .id(app.id())
                .userId(app.userId())
                .jobInfoId(app.jobInfoId())
                .status(status)
                .appliedAt(app.appliedAt())
                .notes(notes != null ? notes : app.notes())
                .resumeVersion(app.resumeVersion())
                .build();

            appRepo.update(updated);

            ctx.json(Map.of("message", "Application updated successfully"));
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", e.getMessage()));
        }
    }

    private void getApplicationStats(Context ctx) {
        try {
            String email = ctx.pathParam("email");

            UserRepository userRepo = new SqliteUserRepository(jdbcUrl);
            var userOpt = userRepo.findByEmail(email);

            if (userOpt.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "User not found"));
                return;
            }

            User user = userOpt.get();
            ApplicationRepository appRepo = new SqliteApplicationRepository(jdbcUrl);
            var stats = appRepo.countByStatus(user.id());

            ctx.json(Map.of("stats", stats));
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", e.getMessage()));
        }
    }
}

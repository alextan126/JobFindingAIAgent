package com.example.api;

import com.example.model.JobInfo;
import com.example.model.User;
import com.example.persistence.JobInfoRepository;
import com.example.persistence.SqliteJobInfoRepository;
import com.example.persistence.SqliteUserRepository;
import com.example.persistence.UserRepository;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal API surface area dedicated to the AI agent.
 * Exposes read-only job data plus simple progress/results inboxes.
 */
public final class SimpleApiServer {
    private static final int DEFAULT_LIMIT = 30;
    private static final int BUFFER_MAX = 100;

    private final String jdbcUrl;
    private final JobInfoRepository jobRepo;
    private final UserRepository userRepo;
    private final int jobLimit;
    private final String resumeEmail;
    private final Path projectsPdfPath;
    private final List<Map<String, Object>> resultBuffer = Collections.synchronizedList(new ArrayList<>());
    private final List<Map<String, Object>> progressBuffer = Collections.synchronizedList(new ArrayList<>());

    public SimpleApiServer(String jdbcUrl, int jobLimit, String resumeEmail, Path projectsPdfPath) {
        this.jdbcUrl = jdbcUrl;
        this.jobRepo = new SqliteJobInfoRepository(jdbcUrl);
        this.userRepo = new SqliteUserRepository(jdbcUrl);
        this.jobLimit = jobLimit <= 0 ? DEFAULT_LIMIT : jobLimit;
        this.resumeEmail = resumeEmail;
        this.projectsPdfPath = projectsPdfPath;
    }

    public void start(int port) {
        Javalin app = Javalin.create(config ->
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()))
        ).start(port);

        System.out.printf("✅ Agent API started on http://localhost:%d%n", port);

        app.get("/api/health", ctx ->
            ctx.json(Map.of("status", "ok", "message", "Simple Agent API"))
        );

        app.get("/api/jobs", this::handleJobs);
        app.get("/api/resume", this::handleResume);

        app.post("/api/results", this::handleResults);
        app.get("/api/results", ctx -> ctx.json(Map.of("results", snapshot(resultBuffer))));

        app.post("/api/progress", this::handleProgress);
        app.get("/api/progress", ctx -> ctx.json(Map.of("events", snapshot(progressBuffer))));

        app.post("/api/reset", ctx -> {
            synchronized (resultBuffer) {
                resultBuffer.clear();
            }
            synchronized (progressBuffer) {
                progressBuffer.clear();
            }
            ctx.status(HttpStatus.NO_CONTENT);
        });
    }

    private void handleJobs(Context ctx) {
        try {
            List<JobInfo> jobs = jobRepo.findAll();
            List<Map<String, Object>> payload = jobs.stream()
                .limit(jobLimit)
                .map(this::toJobPayload)
                .toList();
            ctx.json(Map.of("jobs", payload, "total", payload.size()));
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", e.getMessage()));
        }
    }

    private void handleResume(Context ctx) {
        try {
            String emailOverride = ctx.queryParam("email");
            String emailToUse = emailOverride != null && !emailOverride.isBlank() ? emailOverride : resumeEmail;

            Optional<User> userOpt = Optional.empty();
            if (emailToUse != null && !emailToUse.isBlank()) {
                userOpt = userRepo.findByEmail(emailToUse);
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("resumeText", userOpt.map(User::resumeText).orElse(""));
            payload.put("resumePdfB64", userOpt.map(this::encodeResumePdf).orElse(""));
            payload.put("projectsPdfB64", encodeProjectsPdf());

            ctx.json(payload);
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private void handleResults(Context ctx) {
        try {
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            Map<String, Object> record = new HashMap<>(body);
            record.putIfAbsent("receivedAt", Instant.now().toString());

            synchronized (resultBuffer) {
                resultBuffer.add(record);
                trimBuffer(resultBuffer);
            }

            ctx.status(HttpStatus.ACCEPTED).json(Map.of("status", "ok"));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private void handleProgress(Context ctx) {
        try {
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            Map<String, Object> record = new HashMap<>(body);
            record.putIfAbsent("timestamp", Instant.now().toString());

            synchronized (progressBuffer) {
                progressBuffer.add(record);
                trimBuffer(progressBuffer);
            }

            ctx.status(HttpStatus.ACCEPTED).json(Map.of("status", "ok"));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> toJobPayload(JobInfo job) {
        String jobId = Optional.ofNullable(job.id())
            .map(Object::toString)
            .orElseGet(() -> Optional.ofNullable(job.jobLinkId())
                .map(Object::toString)
                .orElse(""));

        return Map.of(
            "jobId", jobId,
            "company", Optional.ofNullable(job.company()).orElse(""),
            "title", Optional.ofNullable(job.title()).orElse(""),
            "location", Optional.ofNullable(job.location()).orElse(""),
            "description", Optional.ofNullable(job.description()).orElse(""),
            "applyUrl", Optional.ofNullable(job.applicationUrl()).orElse("")
        );
    }

    private String encodeResumePdf(User user) {
        String path = user.resumePath();
        if (path == null || path.isBlank()) {
            return "";
        }
        try {
            byte[] bytes = Files.readAllBytes(Path.of(path));
            return java.util.Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            System.err.printf("⚠️  Could not read resume PDF at %s: %s%n", path, e.getMessage());
            return "";
        }
    }

    private String encodeProjectsPdf() {
        if (projectsPdfPath == null) {
            return "";
        }
        try {
            byte[] bytes = Files.readAllBytes(projectsPdfPath);
            return java.util.Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            System.err.printf("⚠️  Could not read projects PDF at %s: %s%n", projectsPdfPath, e.getMessage());
            return "";
        }
    }

    private void trimBuffer(List<Map<String, Object>> buffer) {
        while (buffer.size() > BUFFER_MAX) {
            buffer.remove(0);
        }
    }

    private List<Map<String, Object>> snapshot(List<Map<String, Object>> buffer) {
        synchronized (buffer) {
            return List.copyOf(buffer);
        }
    }

    public static void main(String[] args) {
        String jdbcUrl = Optional.ofNullable(System.getenv("JDBC_URL")).orElse("jdbc:sqlite:jobs.db");
        int port = Optional.ofNullable(System.getenv("AGENT_API_PORT"))
            .map(Integer::parseInt)
            .orElse(7071);
        int jobLimit = Optional.ofNullable(System.getenv("AGENT_JOB_LIMIT"))
            .map(Integer::parseInt)
            .orElse(DEFAULT_LIMIT);
        String resumeEmail = System.getenv("AGENT_RESUME_EMAIL");
        String projectsPathStr = System.getenv("AGENT_PROJECTS_PDF");
        Path projectsPath = projectsPathStr != null && !projectsPathStr.isBlank()
            ? Path.of(projectsPathStr)
            : null;

        SimpleApiServer server = new SimpleApiServer(jdbcUrl, jobLimit, resumeEmail, projectsPath);
        server.start(port);
    }
}


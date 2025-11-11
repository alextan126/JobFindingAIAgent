package com.example.app;

import com.example.classify.HostClassifier;
import com.example.model.JobInfo;
import com.example.model.JobLink;
import com.example.model.JobLead;
import com.example.model.User;
import com.example.model.Application;
import com.example.model.JobMatch;
import com.example.persistence.JobInfoRepository;
import com.example.persistence.JobLinkRepository;
import com.example.persistence.Migrations;
import com.example.persistence.SqliteJobInfoRepository;
import com.example.persistence.SqliteJobLinkRepository;
import com.example.persistence.UserRepository;
import com.example.persistence.SqliteUserRepository;
import com.example.persistence.ApplicationRepository;
import com.example.persistence.SqliteApplicationRepository;
import com.example.scrape.GitHubLinkCollector;
import com.example.scrape.JobInfoScraper;
import com.example.scrape.OpenAIJobParser;
import com.example.scrape.ResumeParser;
import com.example.matcher.JobMatcher;
import com.example.util.PasswordUtil;
import com.example.api.ApiServer;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Scanner;

public final class Main {
    private static final Dotenv dotenv = loadDotenv();
    private static final String DEFAULT_JDBC = getEnv("JOBS_DB_URL", "jdbc:sqlite:jobs.db");
    private static final boolean DEFAULT_HEADLESS = Boolean.parseBoolean(getEnv("HEADLESS", "true"));
    private static final String OPENAI_API_KEY = getEnv("OPENAI_API_KEY", null);

    /**
     * Load .env file if it exists, otherwise return null (will use system env vars)
     */
    private static Dotenv loadDotenv() {
        try {
            return Dotenv.configure()
                    .directory(".")
                    .ignoreIfMissing()
                    .load();
        } catch (Exception e) {
            System.out.println("No .env file found, using system environment variables");
            return null;
        }
    }

    /**
     * Get environment variable from .env or system env, with fallback
     */
    private static String getEnv(String key, String defaultValue) {
        String value = null;

        // Try .env file first
        if (dotenv != null) {
            value = dotenv.get(key);
        }

        // Fallback to system environment
        if (value == null) {
            value = System.getenv(key);
        }

        // Use default if still null
        return value != null ? value : defaultValue;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) { printHelp(); return; }
        switch (args[0]) {
            case "migrate" -> {
                System.out.println("Running Flyway migrations on: " + DEFAULT_JDBC);
                Migrations.migrate(DEFAULT_JDBC);
                System.out.println("Migrations complete.");
            }
            case "collect-github" -> {
                if (args.length < 2) {
                    System.err.println("Usage: collect-github <README_URL>");
                    System.exit(2);
                }
                collectFromGithub(args[1]);
            }
            case "scrape-jobs" -> {
                int limit = 10; // default
                if (args.length >= 2) {
                    try {
                        limit = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid limit: " + args[1]);
                        System.exit(2);
                    }
                }
                scrapeJobs(limit);
            }
            case "scrape-all" -> {
                scrapeJobs(Integer.MAX_VALUE); // scrape all available jobs
            }
            case "create-user" -> {
                createUser();
            }
            case "parse-resume" -> {
                if (args.length < 3) {
                    System.err.println("Usage: parse-resume <email> <resume_file>");
                    System.exit(2);
                }
                parseResume(args[1], args[2]);
            }
            case "apply" -> {
                if (args.length < 3) {
                    System.err.println("Usage: apply <email> <job_id>");
                    System.exit(2);
                }
                applyToJob(args[1], Integer.parseInt(args[2]));
            }
            case "update-status" -> {
                if (args.length < 3) {
                    System.err.println("Usage: update-status <application_id> <status>");
                    System.exit(2);
                }
                updateApplicationStatus(Integer.parseInt(args[1]), args[2]);
            }
            case "my-applications" -> {
                if (args.length < 2) {
                    System.err.println("Usage: my-applications <email>");
                    System.exit(2);
                }
                String status = args.length >= 3 ? args[2] : null;
                viewApplications(args[1], status);
            }
            case "match-jobs" -> {
                if (args.length < 2) {
                    System.err.println("Usage: match-jobs <email> [limit]");
                    System.exit(2);
                }
                int limit = args.length >= 3 ? Integer.parseInt(args[2]) : 10;
                matchJobs(args[1], limit);
            }
            case "api-server" -> {
                int port = 8080; // default port
                if (args.length >= 2) {
                    try {
                        port = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid port: " + args[1]);
                        System.exit(2);
                    }
                }
                startApiServer(port);
            }
            default -> {
                System.err.println("Unknown command: " + args[0]);
                printHelp();
                System.exit(2);
            }
        }
    }

    private static void collectFromGithub(String readmeUrl) throws Exception {
        System.out.println("JDBC=" + DEFAULT_JDBC);

        // Test database connection
        try (var c = java.sql.DriverManager.getConnection(DEFAULT_JDBC)) {
            System.out.println("DB Connection successful: " + c.getMetaData().getDatabaseProductName());
        }

        // make sure table exists
        Migrations.migrate(DEFAULT_JDBC);

        // start to scrape
        var collector = new GitHubLinkCollector(DEFAULT_HEADLESS);
        List<JobLead> leads = collector.collect(readmeUrl);
        System.out.println("Collected " + leads.size() + " leads from README.");

        // transform into job_link ds
        var now = Instant.now();
        var rows = leads.stream()
                .map(l -> {
                    String canon = normalizeApplyUrl(l.applyUrl());
                    String host = URI.create(canon).getHost();
                    String hostType = HostClassifier.classify(host).name();
                    return new JobLink(canon, hostType, l.sourceUrl(), now);
                })
                .toList();

        // persiste and ignore dubps
        JobLinkRepository repo = new SqliteJobLinkRepository(DEFAULT_JDBC);
        repo.saveAllIgnoreDuplicates(rows);
        System.out.println("Saved to DB: " + rows.size() + " (duplicates ignored).");
    }

    // strip common tracking params like utm_* and ref=Simplify
    private static String normalizeApplyUrl(String href) {
        try {
            var uri = URI.create(href);
            var q = uri.getQuery();
            if (q == null || q.isBlank()) return href;
            var kept = new StringBuilder();
            for (String pair : q.split("&")) {
                var kv = pair.split("=", 2);
                var key = kv[0].toLowerCase();
                if (key.startsWith("utm_")) continue;
                if (key.equals("ref") && kv.length == 2 && kv[1].equalsIgnoreCase("Simplify")) continue;
                if (kept.length() > 0) kept.append("&");
                kept.append(pair);
            }
            var newQuery = kept.length() == 0 ? null : kept.toString();
            var clean = new URI(
                    uri.getScheme(), uri.getAuthority(), uri.getPath(), newQuery, uri.getFragment()
            ).toString();
            return clean;
        } catch (Exception e) {
            return href; // fallback
        }
    }

    private static void scrapeJobs(int limit) throws Exception {
        // Validate OpenAI API key
        if (OPENAI_API_KEY == null || OPENAI_API_KEY.isBlank()) {
            System.err.println("Error: OPENAI_API_KEY environment variable not set.");
            System.err.println("Please set your OpenAI API key:");
            System.err.println("  export OPENAI_API_KEY='sk-...'");
            System.exit(1);
        }

        System.out.println("JDBC=" + DEFAULT_JDBC);
        System.out.println("Scraping up to " + limit + " job postings using OpenAI...");

        // Ensure migrations are run
        Migrations.migrate(DEFAULT_JDBC);

        // Create repositories
        JobLinkRepository linkRepo = new SqliteJobLinkRepository(DEFAULT_JDBC);
        JobInfoRepository jobInfoRepo = new SqliteJobInfoRepository(DEFAULT_JDBC);

        // Create OpenAI parser
        OpenAIJobParser openAIParser = new OpenAIJobParser(OPENAI_API_KEY);

        try {
            // Create and run scraper
            JobInfoScraper scraper = new JobInfoScraper(linkRepo, jobInfoRepo, openAIParser, DEFAULT_HEADLESS);
            int scraped = scraper.scrapeJobs(limit);

            System.out.println("\nDone! Successfully scraped " + scraped + " jobs.");
        } finally {
            // Clean up OpenAI service
            openAIParser.close();
        }
    }

    private static void createUser() throws Exception {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== Create New User ===");

        System.out.print("Full Name: ");
        String fullName = scanner.nextLine().trim();

        System.out.print("Email: ");
        String email = scanner.nextLine().trim();

        System.out.print("Password: ");
        String password = scanner.nextLine().trim();

        System.out.print("Graduation Date (YYYY-MM or leave blank): ");
        String gradDate = scanner.nextLine().trim();
        gradDate = gradDate.isEmpty() ? null : gradDate;

        System.out.print("Experience Level (entry/mid/senior or leave blank): ");
        String expLevel = scanner.nextLine().trim();
        expLevel = expLevel.isEmpty() ? null : expLevel;

        // Ensure migrations are run
        Migrations.migrate(DEFAULT_JDBC);

        // Check if user already exists
        UserRepository userRepo = new SqliteUserRepository(DEFAULT_JDBC);
        if (userRepo.existsByEmail(email)) {
            System.err.println("Error: User with email " + email + " already exists.");
            System.exit(1);
        }

        // Hash password
        String passwordHash = PasswordUtil.hashPassword(password);

        // Create user
        Instant now = Instant.now();
        User user = User.builder()
            .email(email)
            .passwordHash(passwordHash)
            .fullName(fullName)
            .graduationDate(gradDate)
            .experienceLevel(expLevel)
            .createdAt(now)
            .updatedAt(now)
            .build();

        userRepo.create(user);

        System.out.println("\n✓ User created successfully!");
        System.out.println("Email: " + email);
        System.out.println("\nNext step: Parse your resume with:");
        System.out.println("  java -jar link-collector.jar parse-resume " + email + " /path/to/resume.txt");
    }

    private static void parseResume(String email, String resumeFilePath) throws Exception {
        // Validate OpenAI API key
        if (OPENAI_API_KEY == null || OPENAI_API_KEY.isBlank()) {
            System.err.println("Error: OPENAI_API_KEY environment variable not set.");
            System.exit(1);
        }

        System.out.println("=== Parse Resume ===");
        System.out.println("Email: " + email);
        System.out.println("Resume file: " + resumeFilePath);

        // Ensure migrations are run
        Migrations.migrate(DEFAULT_JDBC);

        // Find user
        UserRepository userRepo = new SqliteUserRepository(DEFAULT_JDBC);
        var userOpt = userRepo.findByEmail(email);
        if (userOpt.isEmpty()) {
            System.err.println("Error: User with email " + email + " not found.");
            System.err.println("Create user first with: java -jar link-collector.jar create-user");
            System.exit(1);
        }

        User user = userOpt.get();

        // Read resume file
        String resumeText;
        try {
            resumeText = Files.readString(Paths.get(resumeFilePath));
        } catch (IOException e) {
            System.err.println("Error reading resume file: " + e.getMessage());
            System.exit(1);
            return;
        }

        System.out.println("Resume text loaded: " + resumeText.length() + " characters");

        // Parse resume with OpenAI
        ResumeParser parser = new ResumeParser(OPENAI_API_KEY);
        try {
            var parsed = parser.parseResume(resumeText);

            System.out.println("\n=== Parsed Resume ===");
            System.out.println("Name: " + parsed.fullName());
            System.out.println("Email: " + parsed.email());
            System.out.println("Phone: " + parsed.phone());
            System.out.println("Skills: " + parsed.skills());
            System.out.println("Experience: " + parsed.experienceYears() + " years");
            System.out.println("Level: " + parsed.experienceLevel());
            System.out.println("Education: " + parsed.education());
            System.out.println("Graduation: " + parsed.graduationDate());

            // Update user with parsed info
            User updatedUser = User.builder()
                .id(user.id())
                .email(user.email())
                .passwordHash(user.passwordHash())
                .fullName(parsed.fullName() != null ? parsed.fullName() : user.fullName())
                .resumePath(resumeFilePath)
                .resumeText(resumeText)
                .skills(parsed.skills())
                .preferences(user.preferences())
                .graduationDate(parsed.graduationDate() != null ? parsed.graduationDate() : user.graduationDate())
                .experienceLevel(parsed.experienceLevel() != null ? parsed.experienceLevel() : user.experienceLevel())
                .createdAt(user.createdAt())
                .updatedAt(Instant.now())
                .build();

            userRepo.update(updatedUser);

            System.out.println("\n✓ User profile updated with resume data!");
        } finally {
            parser.close();
        }
    }

    private static void applyToJob(String email, Integer jobId) throws Exception {
        System.out.println("=== Apply to Job ===");

        // Ensure migrations are run
        Migrations.migrate(DEFAULT_JDBC);

        // Find user
        UserRepository userRepo = new SqliteUserRepository(DEFAULT_JDBC);
        var userOpt = userRepo.findByEmail(email);
        if (userOpt.isEmpty()) {
            System.err.println("Error: User with email " + email + " not found.");
            System.exit(1);
        }
        User user = userOpt.get();

        // Find job
        JobInfoRepository jobInfoRepo = new SqliteJobInfoRepository(DEFAULT_JDBC);
        // Note: We need to add a findById method to JobInfoRepository
        // For now, we'll create the application assuming the job exists

        // Check if already applied
        ApplicationRepository appRepo = new SqliteApplicationRepository(DEFAULT_JDBC);
        if (appRepo.existsByUserAndJob(user.id(), jobId)) {
            System.err.println("Error: You have already applied to this job.");
            System.exit(1);
        }

        // Create application
        Application application = Application.builder()
            .userId(user.id())
            .jobInfoId(jobId)
            .status(Application.STATUS_PENDING)
            .appliedAt(Instant.now())
            .notes("Applied via CLI")
            .build();

        appRepo.create(application);

        System.out.println("✓ Successfully recorded application to job #" + jobId);
        System.out.println("Status: " + Application.STATUS_PENDING);
        System.out.println("\nUpdate status with:");
        System.out.println("  java -jar link-collector.jar update-status <app_id> applied");
    }

    private static void updateApplicationStatus(Integer applicationId, String newStatus) throws Exception {
        System.out.println("=== Update Application Status ===");

        // Validate status
        if (!Application.isValidStatus(newStatus)) {
            System.err.println("Error: Invalid status '" + newStatus + "'");
            System.err.println("Valid statuses: pending, applied, interviewing, rejected, accepted");
            System.exit(1);
        }

        // Ensure migrations are run
        Migrations.migrate(DEFAULT_JDBC);

        // Find application
        ApplicationRepository appRepo = new SqliteApplicationRepository(DEFAULT_JDBC);
        var appOpt = appRepo.findById(applicationId);
        if (appOpt.isEmpty()) {
            System.err.println("Error: Application #" + applicationId + " not found.");
            System.exit(1);
        }

        Application app = appOpt.get();

        // Update status
        Application updated = Application.builder()
            .id(app.id())
            .userId(app.userId())
            .jobInfoId(app.jobInfoId())
            .status(newStatus)
            .appliedAt(app.appliedAt())
            .notes(app.notes())
            .resumeVersion(app.resumeVersion())
            .build();

        appRepo.update(updated);

        System.out.println("✓ Application #" + applicationId + " status updated to: " + newStatus);
    }

    private static void viewApplications(String email, String statusFilter) throws Exception {
        System.out.println("=== My Applications ===");

        // Ensure migrations are run
        Migrations.migrate(DEFAULT_JDBC);

        // Find user
        UserRepository userRepo = new SqliteUserRepository(DEFAULT_JDBC);
        var userOpt = userRepo.findByEmail(email);
        if (userOpt.isEmpty()) {
            System.err.println("Error: User with email " + email + " not found.");
            System.exit(1);
        }
        User user = userOpt.get();

        ApplicationRepository appRepo = new SqliteApplicationRepository(DEFAULT_JDBC);

        // Get applications
        List<Application> applications;
        if (statusFilter != null) {
            System.out.println("Filter: status = " + statusFilter);
            applications = appRepo.findByUserIdAndStatus(user.id(), statusFilter);
        } else {
            applications = appRepo.findByUserId(user.id());
        }

        if (applications.isEmpty()) {
            System.out.println("No applications found.");
            return;
        }

        // Show summary
        var statusCounts = appRepo.countByStatus(user.id());
        System.out.println("\n📊 Application Summary:");
        for (var count : statusCounts) {
            System.out.println("  " + count.status() + ": " + count.count());
        }

        // Show applications
        System.out.println("\n📝 Applications (" + applications.size() + "):");
        System.out.println("-".repeat(80));
        for (Application app : applications) {
            System.out.printf("ID: %d | Job: #%d | Status: %s | Applied: %s%n",
                app.id(), app.jobInfoId(), app.status(), app.appliedAt());
            if (app.notes() != null && !app.notes().isEmpty()) {
                System.out.println("  Notes: " + app.notes());
            }
            System.out.println("-".repeat(80));
        }
    }

    private static void matchJobs(String email, int limit) throws Exception {
        System.out.println("=== Job Matching ===");

        // Ensure migrations are run
        Migrations.migrate(DEFAULT_JDBC);

        // Find user
        UserRepository userRepo = new SqliteUserRepository(DEFAULT_JDBC);
        var userOpt = userRepo.findByEmail(email);
        if (userOpt.isEmpty()) {
            System.err.println("Error: User with email " + email + " not found.");
            System.exit(1);
        }
        User user = userOpt.get();

        // Check if user has skills
        if (user.skills() == null || user.skills().trim().isEmpty()) {
            System.err.println("Error: User has no skills parsed. Please run: parse-resume " + email + " <resume_file>");
            System.exit(1);
        }

        // Load all jobs with scraped data
        JobInfoRepository jobRepo = new SqliteJobInfoRepository(DEFAULT_JDBC);
        List<JobInfo> allJobs = jobRepo.findAll();

        if (allJobs.isEmpty()) {
            System.out.println("No jobs found in database. Please run scrape-jobs first.");
            return;
        }

        System.out.println("Found " + allJobs.size() + " jobs in database.");
        System.out.println("Matching against user skills...\n");

        // Match jobs
        JobMatcher matcher = new JobMatcher();
        List<JobMatch> matches = matcher.matchJobs(user, allJobs);

        if (matches.isEmpty()) {
            System.out.println("No matches found.");
            return;
        }

        // Show top matches
        int showCount = Math.min(limit, matches.size());
        System.out.println("🎯 Top " + showCount + " Job Matches:");
        System.out.println("=".repeat(80));

        for (int i = 0; i < showCount; i++) {
            JobMatch match = matches.get(i);
            JobInfo job = match.jobInfo();

            System.out.printf("\n#%d - %s [%.1f%% Match - %s]%n",
                i + 1,
                job.title() != null ? job.title() : "Unknown Title",
                match.matchScore(),
                match.getMatchLevel()
            );

            System.out.println("Job ID: " + job.id() + " | Company: " +
                (job.company() != null ? job.company() : "Unknown"));

            if (job.location() != null) {
                System.out.println("Location: " + job.location());
            }

            if (job.salary() != null) {
                System.out.println("Salary: " + job.salary());
            }

            System.out.println("\n" + match.explanation());

            System.out.println("\n" + "─".repeat(80));
        }

        System.out.println("\n💡 Tip: Use 'apply " + email + " <job_id>' to apply to a job");
    }

    private static void startApiServer(int port) {
        System.out.println("=== Starting API Server ===");

        // Ensure migrations are run
        try {
            Migrations.migrate(DEFAULT_JDBC);
        } catch (Exception e) {
            System.err.println("Error running migrations: " + e.getMessage());
            System.exit(1);
        }

        if (OPENAI_API_KEY == null || OPENAI_API_KEY.isEmpty()) {
            System.err.println("Warning: OPENAI_API_KEY not set. Resume parsing will not work.");
        }

        ApiServer server = new ApiServer(DEFAULT_JDBC, OPENAI_API_KEY);
        server.start(port);
    }


    private static void printHelp() {
        System.out.println("""
        link-collector commands:
          migrate
          collect-github <README_URL>
          scrape-jobs [LIMIT]          (default limit: 10, scrape jobs using OpenAI)
          scrape-all                   (scrape all unscraped job links)
          scrape-job-details [limit]   (alternative scraper, default limit: 10)
          create-user                  (create a new user account)
          parse-resume <email> <resume_file>  (parse resume and extract skills)
          apply <email> <job_id>       (record a job application)
          update-status <app_id> <status>  (update application status)
          my-applications <email> [status]  (view your applications, optionally filtered)
          match-jobs <email> [limit]   (find best matching jobs based on your skills)
          api-server [PORT]            (start REST API server for frontend, default port: 8080)
        Env:
          JOBS_DB_URL      (default: jdbc:sqlite:jobs.db)
          HEADLESS         true|false (default: true)
          OPENAI_API_KEY   (required for scrape-jobs and parse-resume)
        """);
    }
}

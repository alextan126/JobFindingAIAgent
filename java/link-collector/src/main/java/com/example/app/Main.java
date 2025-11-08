package com.example.app;

import com.example.classify.HostClassifier;
import com.example.model.JobLink;
import com.example.model.JobLead;
import com.example.persistence.JobLinkRepository;
import com.example.persistence.JobInfoRepository;
import com.example.persistence.Migrations;
import com.example.persistence.SqliteJobLinkRepository;
import com.example.persistence.SqliteJobInfoRepository;
import com.example.scrape.GitHubLinkCollector;
import com.example.scrape.JobInfoScraper;
import com.example.scrape.OpenAIJobParser;
import io.github.cdimascio.dotenv.Dotenv;

import java.net.URI;
import java.time.Instant;
import java.util.List;

public final class Main {
    // Load .env file if it exists, otherwise use system environment variables
    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()  // Don't fail if .env doesn't exist
            .load();

    private static final String DEFAULT_JDBC =
            dotenv.get("JOBS_DB_URL", "jdbc:sqlite:jobs.db");
    private static final boolean DEFAULT_HEADLESS =
            Boolean.parseBoolean(dotenv.get("HEADLESS", "true"));
    private static final String OPENAI_API_KEY =
            dotenv.get("OPENAI_API_KEY");

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
            default -> {
                System.err.println("Unknown command: " + args[0]);
                printHelp();
                System.exit(2);
            }
        }
    }

    private static void collectFromGithub(String readmeUrl) throws Exception {
        System.out.println("JDBC=" + DEFAULT_JDBC);
        try (var c = java.sql.DriverManager.getConnection(DEFAULT_JDBC);
             var st = c.createStatement();
             var rs = st.executeQuery("PRAGMA database_list;")) {
            while (rs.next()) {
                System.out.printf("DB ATTACHED: name=%s file=%s%n", rs.getString("name"), rs.getString("file"));
            }
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

    private static void printHelp() {
        System.out.println("""
        link-collector commands:
          migrate
          collect-github <README_URL>
          scrape-jobs [LIMIT]     (default limit: 10)
          scrape-all              (scrape all unscraped job links)
        Env:
          JOBS_DB_URL      (default: jdbc:sqlite:jobs.db)
          HEADLESS         true|false (default: true)
          OPENAI_API_KEY   (required for scrape-jobs)
        """);
    }
}

package com.example.app;

import com.example.classify.HostClassifier;
import com.example.model.JobLink;
import com.example.model.JobLead;
import com.example.persistence.JobLinkRepository;
import com.example.persistence.Migrations;
import com.example.persistence.SqliteJobLinkRepository;
import com.example.scrape.GitHubLinkCollector;

import java.net.URI;
import java.time.Instant;
import java.util.List;

public final class Main {
    private static final String DEFAULT_JDBC =
            System.getenv().getOrDefault("JOBS_DB_URL", "jdbc:sqlite:jobs.db");
    private static final boolean DEFAULT_HEADLESS =
            Boolean.parseBoolean(System.getenv().getOrDefault("HEADLESS", "true"));

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
            default -> {
                System.err.println("Unknown command: " + args[0]);
                printHelp();
                System.exit(2);
            }
        }
    }

    private static void collectFromGithub(String readmeUrl) throws Exception {
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

    private static void printHelp() {
        System.out.println("""
        link-collector commands:
          migrate
          collect-github <README_URL>
        Env:
          JOBS_DB_URL   (default: jdbc:sqlite:jobs.db)
          HEADLESS      true|false (default: true)
        """);
    }
}

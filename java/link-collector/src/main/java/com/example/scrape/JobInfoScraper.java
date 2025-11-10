package com.example.scrape;

import com.example.model.JobInfo;
import com.example.model.JobLinkWithId;
import com.example.persistence.JobInfoRepository;
import com.example.persistence.JobLinkRepository;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.time.Instant;
import java.util.List;

/**
 * Scrapes detailed job information from job posting URLs using OpenAI for parsing.
 * Fetches unscraped links from the database, navigates to each URL,
 * extracts HTML content, sends to OpenAI for structured extraction,
 * and saves to job_info table.
 */
public final class JobInfoScraper {
    private final JobLinkRepository linkRepo;
    private final JobInfoRepository jobInfoRepo;
    private final OpenAIJobParser openAIParser;
    private final boolean headless;

    public JobInfoScraper(JobLinkRepository linkRepo, JobInfoRepository jobInfoRepo,
                          OpenAIJobParser openAIParser, boolean headless) {
        this.linkRepo = linkRepo;
        this.jobInfoRepo = jobInfoRepo;
        this.openAIParser = openAIParser;
        this.headless = headless;
    }

    /**
     * Scrape job details from up to 'limit' unscraped links.
     * @param limit maximum number of jobs to scrape
     * @return number of jobs successfully scraped
     */
    public int scrapeJobs(int limit) throws Exception {
        List<JobLinkWithId> links = linkRepo.findUnscrapedLinks(limit);
        if (links.isEmpty()) {
            System.out.println("No unscraped job links found.");
            return 0;
        }

        System.out.println("Found " + links.size() + " unscraped job links. Starting scraper...");

        int successCount = 0;

        try (Playwright pw = Playwright.create();
             Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless))) {

            for (JobLinkWithId link : links) {
                try {
                    System.out.println("\nScraping: " + link.url());
                    scrapeJob(browser, link);
                    successCount++;
                    System.out.println("✓ Successfully scraped job #" + link.id());
                } catch (Exception e) {
                    System.err.println("✗ Failed to scrape job #" + link.id() + ": " + e.getMessage());
                    // Save a failed job_info record and mark link as error
                    try {
                        saveFailedJobInfo(link, e.getMessage());
                        linkRepo.markAsError(link.id(), e.getMessage());
                    } catch (Exception saveError) {
                        System.err.println("Failed to save error info: " + saveError.getMessage());
                    }
                }
            }
        }

        System.out.println("\nScraping complete: " + successCount + "/" + links.size() + " successful.");
        return successCount;
    }

    /**
     * Scrape a single job from its link using OpenAI for parsing.
     */
    private void scrapeJob(Browser browser, JobLinkWithId link) throws Exception {
        Page page = browser.newPage();
        try {
            // Navigate to the job posting
            System.out.println("Navigating to: " + link.url());
            page.navigate(link.url(), new Page.NavigateOptions().setTimeout(30000));

            // Wait for the page to fully load (including JavaScript)
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);

            // Wait a bit more for any lazy-loaded content
            page.waitForTimeout(2000);

            // Extract clean text content from the page
            String pageText = extractPageText(page);

            System.out.println("Extracted " + pageText.length() + " characters of text content");

            // Use OpenAI to extract job info from text
            System.out.println("Sending content to OpenAI for parsing...");
            JobInfo jobInfo = openAIParser.parseJobText(pageText, link.id(), link.url());

            // Save to database
            jobInfoRepo.save(jobInfo);
            linkRepo.markAsScraped(link.id());

        } finally {
            page.close();
        }
    }

    /**
     * Extract clean text content from the page.
     * Tries to get just the main content area, falling back to body text.
     */
    private String extractPageText(Page page) {
        try {
            // Try common content selectors for job boards
            String[] contentSelectors = {
                "main",                          // Semantic main element
                "[role='main']",                 // ARIA main role
                ".job-description",              // Common class
                ".job-details",                  // Common class
                ".posting",                      // Lever/Greenhouse
                ".content",                      // Generic
                "article",                       // Semantic article
                "#content",                      // Common ID
                "body"                           // Fallback to entire body
            };

            for (String selector : contentSelectors) {
                try {
                    var element = page.locator(selector).first();
                    if (element.count() > 0) {
                        String text = element.innerText();
                        if (text != null && text.length() > 500) {
                            System.out.println("Extracted content using selector: " + selector);
                            return text;
                        }
                    }
                } catch (Exception e) {
                    // Try next selector
                    continue;
                }
            }

            // Final fallback: get all body text
            return page.locator("body").first().innerText();

        } catch (Exception e) {
            System.err.println("Failed to extract text, falling back to HTML: " + e.getMessage());
            // Last resort: return HTML
            return page.content();
        }
    }

    /**
     * Save a failed job info record when scraping fails.
     */
    private void saveFailedJobInfo(JobLinkWithId link, String errorMessage) throws Exception {
        JobInfo failedInfo = JobInfo.builder()
            .jobLinkId(link.id())
            .scrapedAt(Instant.now())
            .scrapeSuccess(false)
            .description("Scraping failed: " + errorMessage)
            .build();

        jobInfoRepo.save(failedInfo);
    }
}

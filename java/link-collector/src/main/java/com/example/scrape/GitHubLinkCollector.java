package com.example.scrape;

import com.example.model.JobLead;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class GitHubLinkCollector {
    private final boolean headless;

    public GitHubLinkCollector(boolean headless) { this.headless = headless; }

    // crawl the Simplify README table and return curated job leads.
    public List<JobLead> collect(String readmeUrl) {
        try (Playwright pw = Playwright.create();
             Browser browser = pw.chromium().launch(
                     new BrowserType.LaunchOptions().setHeadless(headless)
             );
             BrowserContext ctx = browser.newContext();
             Page page = ctx.newPage()) {

            page.navigate(readmeUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // README can render in #readme or article.markdown-body
            Locator table = page.locator("div#readme table, article.markdown-body table").first();
            if (table.count() == 0) {
                System.out.println("No table found on page: " + readmeUrl);
                return List.of();
            }

            var leads = new ArrayList<JobLead>();
            String lastCompany = null;

            for (Locator row : table.locator("tbody > tr").all()) {
                Locator cells = row.locator("td");
                int n = cells.count();
                if (n < 4) continue; // need at least Company, Role, Location, Application

                // cols
                String company = textTrim(cells.nth(0));
                String role    = textTrim(cells.nth(1));
                String location= textWithBreaks(cells.nth(2)); // keep multiple cities

                if (company != null && company.startsWith("↳")) {
                    company = lastCompany;
                } else if (company != null && !company.isBlank()) {
                    // Often the company cell contains an <a>; prefer the anchor text if present
                    String companyAnchor = anchorText(cells.nth(0));
                    if (companyAnchor != null && !companyAnchor.isBlank()) company = companyAnchor;
                    lastCompany = company;
                }

                // in app cell, grab first non-simplify link
                String applyUrl = firstNonSimplifyHref(cells.nth(3));
                if (applyUrl == null) continue; // skip rows without a real apply link

                if (role == null || role.isBlank()) continue;
                if (company == null || company.isBlank()) continue;

                leads.add(new JobLead(company, role, location, applyUrl, readmeUrl));
            }

            return leads;
        }
    }

    // ---------- small helper methds ----------

    private static String textTrim(Locator cell) {
        if (cell.count() == 0) return null;
        try {
            String t = cell.innerText();
            return t == null ? null : t.trim();
        } catch (PlaywrightException e) { return null; }
    }

    // Preserve line breaks from <br> by using innerText()
    private static String textWithBreaks(Locator cell) { return textTrim(cell); }

    // If a company cell has an anchor, return its text
    private static String anchorText(Locator cell) {
        Locator a = cell.locator("a").first();
        if (a.count() == 0) return null;
        try {
            String t = a.innerText();
            return t == null ? null : t.trim();
        } catch (PlaywrightException e) { return null; }
    }

    // Find the first <a> whose href does NOT start with simplify.
    private static String firstNonSimplifyHref(Locator cell) {
        for (Locator a : cell.locator("a[href]").all()) {
            String href = a.getAttribute("href");
            if (href == null) continue;
            String lower = href.toLowerCase();
            if (lower.contains("simplify.jobs")) continue;
            return href;
        }
        return null;
    }

    // for quick testing
    public static void main(String[] args) {
        boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("HEADLESS", "true"));
        String url = args.length > 0 ? args[0]
                : "https://github.com/SimplifyJobs/Summer2026-Internships/blob/dev/README.md#-software-engineering-internship-roles";
        var collector = new GitHubLinkCollector(headless);
        var leads = collector.collect(url);
        System.out.println("Found leads: " + leads.size());
        leads.stream().limit(15).forEach(l ->
                System.out.println("• " + l.company() + " | " + l.role() + " | " + l.location() + " | " + l.applyUrl()));
    }
}

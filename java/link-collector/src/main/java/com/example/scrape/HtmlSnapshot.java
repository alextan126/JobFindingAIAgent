package com.example.scrape;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HtmlSnapshot {
    private HtmlSnapshot() {}

    // aave the fully rendered page HTML to {@code outputFile}.
    public static void save(String url, Path outputFile, boolean headless) {
        try (Playwright pw = Playwright.create();
             Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
             BrowserContext ctx = browser.newContext();
             Page page = ctx.newPage()) {

            page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            page.waitForLoadState(LoadState.NETWORKIDLE);

            String html = page.content(); // full serialized DOM at this moment
            Files.createDirectories(outputFile.getParent());
            Files.writeString(outputFile, html, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to snapshot HTML for " + url + " -> " + outputFile, e);
        }
    }

    public static void main(String[] args) {
        String url = args.length > 0 ? args[0]
                : "https://github.com/SimplifyJobs/Summer2026-Internships/blob/dev/README.md#-software-engineering-internship-roles";
        String out = args.length > 1 ? args[1] : "out/page.html";
        boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("HEADLESS", "true"));
        save(url, Path.of(out), headless);
    }
}

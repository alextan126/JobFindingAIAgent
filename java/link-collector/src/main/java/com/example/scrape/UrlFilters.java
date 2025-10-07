package com.example.scrape;

import java.net.URI;
import java.util.regex.Pattern;

public final class UrlFilters {
    private UrlFilters() {}

    // applicant tracking systems (ATS) that we accept
    private static final String[] ATS_HOSTS = {
            "ashbyhq.com", "boards.greenhouse.io", "greenhouse.io", "lever.co",
            "myworkdayjobs.com", "workdayjobs.com", "smartrecruiters.com",
            "breezy.hr", "jobvite.com", "icims.com", "eightfold.ai",
            "workable.com", "wellfound.com", "angel.co"
    };

    // Skip redirectors / non-target domains
    private static final String[] SKIP_HOSTS = {
            "github.com", "simplify.jobs", "app.simplify.jobs", "link.simplify.jobs",
            "docs.google.com", "bit.ly", "t.co", "lnkd.in", "medium.com"
    };

    // Path shapes that look like an actual job posting
    private static final Pattern GH_POST     = Pattern.compile("^/[^/]+/jobs/\\d+(/.*)?$");
    private static final Pattern LEVER_POST  = Pattern.compile("^/[^/]+/[0-9a-f\\-]{8,}(/.*)?$");
    private static final Pattern ASHBY_POST  = Pattern.compile("^/(jobs|[^/]+/jobs)/[0-9A-Za-z\\-]+(/.*)?$");
    private static final Pattern WD_POST     = Pattern.compile("^/.+/job/.+/(JR|R|REQ|Job)-?[0-9A-Za-z\\-]+.*$");
    private static final Pattern SR_POST     = Pattern.compile("^/[^/]+/[^/]*-?\\d+-.+$");
    private static final Pattern BREEZY_POST = Pattern.compile("^/p/[0-9a-z]{6,}(-.+)?$");
    private static final Pattern JOBVITE     = Pattern.compile("^/.*/job/[^/]+(/.*)?$");
    private static final Pattern ICIMS       = Pattern.compile("^/jobs/\\d+/.+$");
    private static final Pattern EIGHTFOLD   = Pattern.compile("^/.*/careers/job/\\d+.*$");
    private static final Pattern WORKABLE    = Pattern.compile("^/[^/]+/j/[0-9A-Z]{6,}(/.*)?$");
    private static final Pattern WELLFOUND   = Pattern.compile("^/(l|job)/\\d+.*$");

    public static boolean isDirectApplicationLink(URI uri) {
        if (uri == null || uri.getScheme() == null || !uri.getScheme().startsWith("http")) return false;
        String host = lower(uri.getHost());
        if (host == null) return false;

        for (String s : SKIP_HOSTS) if (host.contains(s)) return false;

        boolean ats = false;
        for (String a : ATS_HOSTS) if (host.contains(a)) { ats = true; break; }
        if (!ats) return false;

        String path = uri.getPath() == null ? "" : uri.getPath();
        if (host.contains("greenhouse.io"))                 return GH_POST.matcher(path).find();
        if (host.contains("lever.co"))                      return LEVER_POST.matcher(path).find();
        if (host.contains("ashbyhq.com"))                   return ASHBY_POST.matcher(path).find();
        if (host.contains("workdayjobs.com") || host.contains("myworkdayjobs.com"))
            return WD_POST.matcher(path).find();
        if (host.contains("smartrecruiters.com"))           return SR_POST.matcher(path).find();
        if (host.contains("breezy.hr"))                     return BREEZY_POST.matcher(path).find();
        if (host.contains("jobvite.com"))                   return JOBVITE.matcher(path).find();
        if (host.contains("icims.com"))                     return ICIMS.matcher(path).find();
        if (host.contains("eightfold.ai"))                  return EIGHTFOLD.matcher(path).find();
        if (host.contains("workable.com"))                  return WORKABLE.matcher(path).find();
        if (host.contains("wellfound.com") || host.contains("angel.co"))
            return WELLFOUND.matcher(path).find();

        return false;
    }

    private static String lower(String s) { return s == null ? null : s.toLowerCase(); }
}

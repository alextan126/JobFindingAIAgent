package com.example.classify;
public final class HostClassifier {
    private HostClassifier() {}
    public static HostType classify(String hostRaw) {
        if (hostRaw == null) return HostType.OTHER;
        String h = hostRaw.toLowerCase();
        if (h.contains("ashbyhq.com")) return HostType.ASHBY;
        if (h.contains("boards.greenhouse.io") || h.contains("greenhouse.io")) return HostType.GREENHOUSE;
        if (h.contains("lever.co")) return HostType.LEVER;
        if (h.contains("myworkdayjobs.com") || h.contains("workdayjobs.com")) return HostType.WORKDAY;
        if (h.contains("smartrecruiters.com")) return HostType.SMARTRECRUITERS;
        if (h.contains("breezy.hr")) return HostType.BREEZY;
        if (h.contains("jobvite.com")) return HostType.JOBVITE;
        if (h.contains("icims.com")) return HostType.ICIMS;
        if (h.contains("eightfold.ai")) return HostType.EIGHTFOLD;
        if (h.contains("workable.com")) return HostType.WORKABLE;
        if (h.contains("wellfound.com") || h.contains("angel.co")) return HostType.WELLFOUND;
        return HostType.OTHER;
    }
}

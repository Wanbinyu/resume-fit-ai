package com.wanbinyu.resumefit.ai;

import java.util.*;
import java.util.function.*;

/** 每个任务独有的上下文。工具不能指定其他用户的 ID、文件路径或 shell。 */
public final class AgentRun {
    public final String role, city, company, experience;
    private final Supplier<String> ownedResume, ownedJob;
    public final BooleanSupplier cancelled;
    public final Consumer<Map<String, Object>> progress;
    public final List<Map<String, Object>> trace = new ArrayList<>();
    public final List<JobSearchProvider.Source> sources = new ArrayList<>();
    public String resume = "", jd = "", draft = "";
    public boolean loadedResume, readJob, searched, drafted;
    public boolean resumeOnly;
    public Map<String, Object> resumeReview = Map.of();
    public String searchStatus = "NOT_RUN";
    public String selectedSourceId = "", analysisBasis = "NONE";
    public ResumeAgentTools.AgentContext matching;

    public AgentRun(String role, String city, String company, String experience,
                    Supplier<String> ownedResume, Supplier<String> ownedJob,
                    BooleanSupplier cancelled, Consumer<Map<String, Object>> progress) {
        this.role = text(role); this.city = text(city); this.company = text(company); this.experience = text(experience);
        this.ownedResume = ownedResume; this.ownedJob = ownedJob;
        this.cancelled = cancelled; this.progress = progress;
    }
    public String loadResume() {
        String value = limited(ownedResume.get(), 6000);
        if (!value.equals(resume)) resetDerived();
        resume = value; loadedResume = true; return resume;
    }
    public String loadJob() { setJob(limited(ownedJob.get(), 3000), "user", "USER_JD"); return jd; }
    public void setJob(String value, String id, String kind) {
        if (!value.equals(jd)) resetDerived();
        jd = value; selectedSourceId = id; analysisBasis = value.isBlank() ? "NONE" : kind; readJob = true;
    }
    private void resetDerived() { matching = null; draft = ""; drafted = false; resumeReview = Map.of(); }
    public synchronized void event(String name, String status, String callId, String message) {
        trace.add(Map.of("name", name, "status", status, "callId", callId,
                "message", message, "at", java.time.Instant.now().toString()));
        progress.accept(Map.of("agentTrace", List.copyOf(trace), "sources", List.copyOf(sources)));
    }
    public static String text(String value) { return value == null ? "" : value.trim(); }
    public static String limited(String value, int max) {
        value = text(value);
        if (value.length() > max) throw new IllegalArgumentException("输入超出长度限制");
        return value;
    }
}

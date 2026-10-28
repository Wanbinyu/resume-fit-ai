package com.wanbinyu.resumefit.ai;

import java.util.*;
import java.util.function.*;
import java.util.concurrent.CancellationException;

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
    /** 仅在执行前由服务器注入的白名单，不接受模型指定的用户 ID、URL 或代码。 */
    public Map<String, Supplier<Object>> assistantTools = Map.of();
    public Map<String, String> assistantDescriptions = Map.of();
    public volatile Map<String, Object> assistantResults = Map.of();
    private boolean active = true;

    public AgentRun(String role, String city, String company, String experience,
                    Supplier<String> ownedResume, Supplier<String> ownedJob,
                    BooleanSupplier cancelled, Consumer<Map<String, Object>> progress) {
        this.role = text(role); this.city = text(city); this.company = text(company); this.experience = text(experience);
        this.ownedResume = ownedResume; this.ownedJob = ownedJob;
        this.cancelled = cancelled; this.progress = progress;
    }
    public String loadResume() {
        return loadResume(value -> {});
    }
    public String loadResume(Consumer<String> validate) {
        String value = limited(ownedResume.get(), 6000);
        validate.accept(value);
        commit(() -> {
            if (!value.equals(resume)) resetDerived();
            resume = value; loadedResume = true;
        });
        return value;
    }
    public String loadJob() { return loadJob(value -> {}); }
    public String loadJob(Consumer<String> validate) {
        String value = limited(ownedJob.get(), 3000);
        validate.accept(value);
        setJob(value, "user", "USER_JD");
        return value;
    }
    public void setJob(String value, String id, String kind) {
        commit(() -> {
            if (!value.equals(jd)) resetDerived();
            jd = value; selectedSourceId = id; analysisBasis = value.isBlank() ? "NONE" : kind; readJob = true;
        });
    }
    /** 只提交已计算好的状态；网络、数据库和模型调用必须在锁外执行。 */
    public void commit(Runnable change) {
        if (!isActive() || Thread.currentThread().isInterrupted())
            throw new CancellationException("任务已结束或取消");
        // Worker 的取消供应器需要查询数据库；连接等待不能阻塞 seal 获取本对象的锁。
        boolean cancellationRequested = cancelled.getAsBoolean();
        synchronized (this) {
            // 在锁外等待数据库期间可能已经超时/封存，必须重新检查，不能只用先前快照。
            if (!active || Thread.currentThread().isInterrupted() || cancellationRequested)
                throw new CancellationException("任务已结束或取消");
            change.run();
        }
    }
    public synchronized boolean isActive() { return active; }
    /** 与 commit 共用锁；返回后，迟到工具无法再提交状态。 */
    public synchronized void seal() { active = false; }
    private void resetDerived() { matching = null; draft = ""; drafted = false; resumeReview = Map.of(); }
    public void event(String name, String status, String callId, String message) {
        Map<String, Object> snapshot;
        synchronized (this) {
            if (!active) return;
            trace.add(Map.of("name", text(name), "status", text(status), "callId", text(callId),
                    "message", text(message), "at", java.time.Instant.now().toString()));
            snapshot = Map.of("agentTrace", List.copyOf(trace), "sources", List.copyOf(sources));
        }
        // 进度持久化同样可能等待数据库。锁内只采集快照，已开始的外部写入仍需消费者的终态条件保护。
        if (isActive()) progress.accept(snapshot);
    }
    public static String text(String value) { return value == null ? "" : value.trim(); }
    public static String limited(String value, int max) {
        value = text(value);
        if (value.length() > max) throw new IllegalArgumentException("输入超出长度限制");
        return value;
    }
}

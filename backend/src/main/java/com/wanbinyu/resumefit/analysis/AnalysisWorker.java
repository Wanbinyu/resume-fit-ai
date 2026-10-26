package com.wanbinyu.resumefit.analysis;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanbinyu.resumefit.ai.*;
import com.wanbinyu.resumefit.job.JobDescriptionService;
import com.wanbinyu.resumefit.resume.ResumeService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AnalysisWorker {
    private final AnalysisTaskMapper tasks;
    private final ResumeService resumes;
    private final JobDescriptionService jobs;
    private final ResumeOptimizationAgent agent;
    private final ObjectMapper json;
    private final AiUsageService usage;
    private final AnalysisReportMapper reports;
    private final org.springframework.transaction.support.TransactionTemplate transactions;
    public AnalysisWorker(AnalysisTaskMapper tasks, ResumeService resumes, JobDescriptionService jobs,
                          ResumeOptimizationAgent agent, ObjectMapper json, AiUsageService usage, AnalysisReportMapper reports,
                          org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.tasks = tasks; this.resumes = resumes; this.jobs = jobs; this.agent = agent;
        this.json = json; this.usage = usage; this.reports = reports;
        this.transactions = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }
    @Async("analysisExecutor")
    public void processAsync(Long taskId) {
        var task = tasks.selectById(taskId);
        if (task == null) return;
        int claimed = tasks.update(null, new LambdaUpdateWrapper<AnalysisTaskEntity>()
                .eq(AnalysisTaskEntity::getId, taskId).eq(AnalysisTaskEntity::getStatus, "PENDING")
                .set(AnalysisTaskEntity::getStatus, "RUNNING").set(AnalysisTaskEntity::getProgress, 5)
                .set(AnalysisTaskEntity::getMessage, "Agent 正在准备工具")
                .set(AnalysisTaskEntity::getStartedAt, LocalDateTime.now()));
        if (claimed == 0) return;
        try {
            var request = task.getRequestJson() == null ? new AnalysisDtos.CreateRequest(task.getResumeId(), task.getJobId())
                    : json.readValue(task.getRequestJson(), AnalysisDtos.CreateRequest.class);
            long started = System.nanoTime();
            String targetRole = AgentRun.text(request.targetRole());
            if(targetRole.isBlank() && task.getJobId()!=null) targetRole=jobs.requireOwned(task.getUserId(),task.getJobId()).getTitle();
            var run = new AgentRun(targetRole, request.city(), request.company(), request.experience(),
                    () -> resumes.requireOwned(task.getUserId(), task.getResumeId()).getContent(),
                    () -> task.getJobId() == null ? AgentRun.text(request.jobDescription())
                            : jobs.requireOwned(task.getUserId(), task.getJobId()).getContent(),
                    () -> { var current = tasks.selectById(taskId); return current == null || "CANCELLED".equals(current.getStatus()); },
                    progress -> {
                        try {
                            tasks.update(null, running(taskId).set(AnalysisTaskEntity::getResultJson, json.writeValueAsString(progress))
                                    .set(AnalysisTaskEntity::getMessage, "Agent 正在执行，请查看工具轨迹")
                                    .set(AnalysisTaskEntity::getProgress, 30).set(AnalysisTaskEntity::getUpdatedAt, LocalDateTime.now()));
                        } catch (Exception exception) { throw new IllegalStateException("进度无法保存"); }
                    });
            run.resumeOnly = Boolean.TRUE.equals(request.resumeOnly());
            Map<String, Object> result = agent.analyze(run);
            String termination = String.valueOf(result.get("termination"));
            String status = switch (termination) {
                case "COMPLETED" -> "SUCCEEDED";
                case "NEEDS_INPUT" -> "NEEDS_INPUT";
                case "CANCELLED" -> "CANCELLED";
                default -> "FAILED";
            };
            String resultJson = json.writeValueAsString(result);
            transactions.executeWithoutResult(transaction -> {
            int updated = tasks.update(null, running(taskId).set(AnalysisTaskEntity::getResultJson, resultJson)
                    .set(AnalysisTaskEntity::getStatus, status).set(AnalysisTaskEntity::getProgress, status.equals("SUCCEEDED") ? 100 : 0)
                    .set(AnalysisTaskEntity::getMessage, status.equals("SUCCEEDED") ? "分析完成，请检查草稿" : "Agent 已停止：" + termination)
                    .set(AnalysisTaskEntity::getErrorMessage, status.equals("FAILED") ? result.get("summary") : null)
                    .set(AnalysisTaskEntity::getCompletedAt, LocalDateTime.now()).set(AnalysisTaskEntity::getUpdatedAt, LocalDateTime.now()));
            // 条件更新防止模型结束时覆盖用户取消状态。取消/失败不生成成功报告或保存草稿版本。
            if (updated == 0) return;
            usage.recordAgent(taskId, agent.providerName(), (Map<?, ?>) result.get("usage"), (System.nanoTime() - started) / 1_000_000);
            if (!status.equals("SUCCEEDED")) return;
            var report = new AnalysisReportEntity();
            report.setTaskId(taskId); report.setUserId(task.getUserId()); report.setResumeId(task.getResumeId());
            report.setJobId(task.getJobId()); report.setScore(((Number) result.get("score")).intValue());
            report.setReportJson(resultJson); reports.insert(report);
            });
        } catch (Exception exception) {
            tasks.update(null, running(taskId).set(AnalysisTaskEntity::getStatus, "FAILED")
                    .set(AnalysisTaskEntity::getMessage, "分析失败")
                    .set(AnalysisTaskEntity::getErrorMessage, "任务执行失败，请检查输入和服务配置后重试")
                    .set(AnalysisTaskEntity::getCompletedAt, LocalDateTime.now()));
        }
    }
    private LambdaUpdateWrapper<AnalysisTaskEntity> running(Long id) {
        return new LambdaUpdateWrapper<AnalysisTaskEntity>().eq(AnalysisTaskEntity::getId, id)
                .eq(AnalysisTaskEntity::getStatus, "RUNNING");
    }
}

package com.wanbinyu.resumefit.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanbinyu.resumefit.ai.AiUsageService;
import com.wanbinyu.resumefit.ai.ResumeOptimizationAgent;
import com.wanbinyu.resumefit.job.JobDescriptionMapper;
import com.wanbinyu.resumefit.job.JobDescriptionEntity;
import com.wanbinyu.resumefit.resume.ResumeMapper;
import com.wanbinyu.resumefit.resume.ResumeEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AnalysisWorker {
    private final AnalysisTaskMapper taskMapper;
    private final ResumeMapper resumeMapper;
    private final JobDescriptionMapper jobMapper;
    private final ResumeOptimizationAgent agent;
    private final ObjectMapper objectMapper;
    private final AiUsageService usageService;
    private final AnalysisReportMapper reportMapper;

    public AnalysisWorker(AnalysisTaskMapper taskMapper, ResumeMapper resumeMapper,
                          JobDescriptionMapper jobMapper, ResumeOptimizationAgent agent,
                          ObjectMapper objectMapper, AiUsageService usageService,
                          AnalysisReportMapper reportMapper) {
        this.taskMapper = taskMapper;
        this.resumeMapper = resumeMapper;
        this.jobMapper = jobMapper;
        this.agent = agent;
        this.objectMapper = objectMapper;
        this.usageService = usageService;
        this.reportMapper = reportMapper;
    }

    @Async("analysisExecutor")
    public void processAsync(Long taskId) {
        AnalysisTaskEntity task = taskMapper.selectById(taskId);
        if (task == null || "CANCELLED".equals(task.getStatus())) return;
        task.setStatus("RUNNING");
        task.setProgress(10);
        task.setMessage("正在读取简历和岗位信息");
        task.setStartedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        try {
            ResumeEntity resume = resumeMapper.selectById(task.getResumeId());
            JobDescriptionEntity job = jobMapper.selectById(task.getJobId());
            if (resume == null || job == null) throw new IllegalStateException("分析输入不存在");
            task.setProgress(30);
            task.setMessage("Agent 正在分析岗位匹配度");
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);

            long aiStartedAt = System.nanoTime();
            var result = agent.analyze(resume.getContent(), job.getContent());
            long aiDurationMs = (System.nanoTime() - aiStartedAt) / 1_000_000;
            task = taskMapper.selectById(taskId);
            if (task == null || "CANCELLED".equals(task.getStatus())) return;
            String resultJson = objectMapper.writeValueAsString(result);
            usageService.record(taskId, agent.providerName(),
                    resume.getContent().length() + job.getContent().length(), resultJson.length(), aiDurationMs);
            AnalysisReportEntity report = new AnalysisReportEntity();
            report.setTaskId(taskId);
            report.setUserId(task.getUserId());
            report.setResumeId(task.getResumeId());
            report.setJobId(task.getJobId());
            report.setScore(result.get("score") instanceof Number number ? number.intValue() : 0);
            report.setReportJson(resultJson);
            reportMapper.insert(report);
            task.setResultJson(resultJson);
            task.setStatus("SUCCEEDED");
            task.setProgress(100);
            task.setMessage("分析完成");
            task.setCompletedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        } catch (Exception exception) {
            task = taskMapper.selectById(taskId);
            if (task == null || "CANCELLED".equals(task.getStatus())) return;
            task.setStatus("FAILED");
            task.setProgress(0);
            task.setMessage("分析失败");
            task.setErrorMessage(publicMessage(exception));
            task.setCompletedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        }
    }

    private String publicMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "AI 分析失败，请稍后重试" : message.substring(0, Math.min(message.length(), 500));
    }
}

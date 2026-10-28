package com.wanbinyu.resumefit.analysis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanbinyu.resumefit.common.BusinessException;
import com.wanbinyu.resumefit.job.JobDescriptionService;
import com.wanbinyu.resumefit.resume.ResumeService;
import com.wanbinyu.resumefit.resume.ResumeVersionService;
import com.wanbinyu.resumefit.career.CareerAssistantContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AnalysisTaskService {
    private final AnalysisTaskMapper taskMapper;
    private final ResumeService resumeService;
    private final JobDescriptionService jobService;
    private final AnalysisWorker worker;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;
    private final ResumeVersionService versions;

    public AnalysisTaskService(AnalysisTaskMapper taskMapper, ResumeService resumeService,
                               JobDescriptionService jobService, AnalysisWorker worker,
                               ObjectMapper objectMapper,
                               @Qualifier("analysisScheduler") ScheduledExecutorService scheduler, ResumeVersionService versions) {
        this.taskMapper = taskMapper;
        this.resumeService = resumeService;
        this.jobService = jobService;
        this.worker = worker;
        this.objectMapper = objectMapper;
        this.scheduler = scheduler;
        this.versions = versions;
    }

    @Transactional
    public AnalysisDtos.TaskView create(Long userId, AnalysisDtos.CreateRequest request) {
        return createBound(userId, request, null);
    }

    /** Only server code can supply the account-bound assistant context. */
    @Transactional
    public AnalysisDtos.TaskView createAssistant(Long userId, AnalysisDtos.CreateRequest request, CareerAssistantContext assistant) {
        return createBound(userId, request, assistant);
    }

    private AnalysisDtos.TaskView createBound(Long userId, AnalysisDtos.CreateRequest request, CareerAssistantContext assistant) {
        var resume = resumeService.requireOwned(userId, request.resumeId());
        String content = request.versionId() == null ? resume.getContent()
                : versions.get(userId, request.resumeId(), request.versionId()).content();
        var selectedJob = request.jobId() == null ? null : jobService.requireOwned(userId, request.jobId());
        // Freeze the inferred target title as well as its JD, so later edits cannot alter the task's meaning.
        if (selectedJob != null && com.wanbinyu.resumefit.ai.AgentRun.text(request.targetRole()).isBlank()) {
            request = new AnalysisDtos.CreateRequest(request.resumeId(), request.jobId(), selectedJob.getTitle(),
                    request.city(), request.company(), request.experience(), request.jobDescription(), request.resumeOnly(), request.versionId());
        }
        if (assistant == null && !Boolean.TRUE.equals(request.resumeOnly()) && request.jobId() == null && (request.targetRole() == null || request.targetRole().isBlank())
                && (request.jobDescription() == null || request.jobDescription().isBlank()))
            throw new BusinessException("请补充目标岗位，或选择/粘贴目标 JD；城市、公司和年限可选");
        AnalysisTaskEntity task = new AnalysisTaskEntity();
        task.setUserId(userId);
        task.setResumeId(request.resumeId());
        task.setJobId(request.jobId());
        String jd = request.jobId() == null ? com.wanbinyu.resumefit.ai.AgentRun.text(request.jobDescription())
                : selectedJob.getContent();
        try {
            task.setRequestJson(objectMapper.writeValueAsString(request));
            task.setInputSnapshotJson(objectMapper.writeValueAsString(new AnalysisInputSnapshot(request.versionId(), content, jd, assistant)));
        }
        catch (Exception exception) { throw new BusinessException("任务输入无法保存"); }
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setMessage("任务已创建，等待执行");
        task.setRetryCount(0);
        taskMapper.insert(task);
        // 提交后再派发，避免异步线程读到尚未提交的任务。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { worker.processAsync(task.getId()); }
                catch (org.springframework.core.task.TaskRejectedException exception) {
                    taskMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AnalysisTaskEntity>()
                            .eq(AnalysisTaskEntity::getId, task.getId()).eq(AnalysisTaskEntity::getStatus, "PENDING")
                            .set(AnalysisTaskEntity::getStatus, "FAILED").set(AnalysisTaskEntity::getErrorMessage, "任务队列已满，请稍后重试")
                            .set(AnalysisTaskEntity::getMessage, "任务队列已满").set(AnalysisTaskEntity::getCompletedAt, LocalDateTime.now()));
                }
            }
        });
        return view(task);
    }

    public AnalysisDtos.TaskView get(Long userId, Long taskId) {
        return view(requireOwned(userId, taskId));
    }

    public List<AnalysisDtos.TaskView> list(Long userId) {
        return taskMapper.selectList(new LambdaQueryWrapper<AnalysisTaskEntity>()
                        .eq(AnalysisTaskEntity::getUserId, userId).orderByDesc(AnalysisTaskEntity::getCreatedAt).last("LIMIT 100"))
                .stream().map(this::view).toList();
    }

    @Transactional
    public AnalysisDtos.TaskView cancel(Long userId, Long taskId) {
        AnalysisTaskEntity task = requireOwned(userId, taskId);
        if (isTerminal(task.getStatus())) return view(task);
        taskMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AnalysisTaskEntity>()
                .eq(AnalysisTaskEntity::getId, taskId).in(AnalysisTaskEntity::getStatus, "PENDING", "RUNNING")
                .set(AnalysisTaskEntity::getStatus, "CANCELLED").set(AnalysisTaskEntity::getProgress, 0)
                .set(AnalysisTaskEntity::getMessage, "任务已取消").set(AnalysisTaskEntity::getErrorMessage, "用户取消了任务")
                .set(AnalysisTaskEntity::getCompletedAt, LocalDateTime.now()).set(AnalysisTaskEntity::getUpdatedAt, LocalDateTime.now()));
        return view(requireOwned(userId, taskId));
    }

    public SseEmitter stream(Long userId, Long taskId) {
        requireOwned(userId, taskId);
        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(10));
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                AnalysisDtos.TaskView view = get(userId, taskId);
                emitter.send(view);
                if (isTerminal(view.status())) {
                    emitter.complete();
                    ScheduledFuture<?> scheduled = futureRef.get();
                    if (scheduled != null) scheduled.cancel(false);
                }
            } catch (Exception exception) {
                emitter.completeWithError(exception);
                ScheduledFuture<?> scheduled = futureRef.get();
                if (scheduled != null) scheduled.cancel(false);
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
        futureRef.set(future);
        emitter.onCompletion(() -> future.cancel(false));
        emitter.onTimeout(() -> future.cancel(false));
        return emitter;
    }

    AnalysisTaskEntity requireOwned(Long userId, Long taskId) {
        AnalysisTaskEntity task = taskMapper.selectOne(new LambdaQueryWrapper<AnalysisTaskEntity>()
                .eq(AnalysisTaskEntity::getId, taskId).eq(AnalysisTaskEntity::getUserId, userId));
        if (task == null) throw new BusinessException("分析任务不存在");
        return task;
    }

    AnalysisTaskEntity require(Long taskId) {
        AnalysisTaskEntity task = taskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("分析任务不存在");
        return task;
    }

    AnalysisDtos.TaskView view(AnalysisTaskEntity task) {
        Map<String, Object> result = null;
        if (task.getResultJson() != null && !task.getResultJson().isBlank()) {
            try {
                result = objectMapper.readValue(task.getResultJson(), new TypeReference<>() {});
            } catch (Exception ignored) {
                result = Map.of("raw", task.getResultJson());
            }
        }
        return new AnalysisDtos.TaskView(task.getId(), task.getStatus(), task.getProgress(), task.getMessage(),
                task.getErrorMessage(), result, task.getCreatedAt(), task.getStartedAt(), task.getCompletedAt());
    }

    private boolean isTerminal(String status) {
        return "SUCCEEDED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status) || "NEEDS_INPUT".equals(status);
    }
}

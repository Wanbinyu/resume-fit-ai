package com.wanbinyu.resumefit.analysis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanbinyu.resumefit.common.BusinessException;
import com.wanbinyu.resumefit.job.JobDescriptionEntity;
import com.wanbinyu.resumefit.job.JobDescriptionService;
import com.wanbinyu.resumefit.resume.ResumeEntity;
import com.wanbinyu.resumefit.resume.ResumeService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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

    public AnalysisTaskService(AnalysisTaskMapper taskMapper, ResumeService resumeService,
                               JobDescriptionService jobService, AnalysisWorker worker,
                               ObjectMapper objectMapper,
                               @Qualifier("analysisScheduler") ScheduledExecutorService scheduler) {
        this.taskMapper = taskMapper;
        this.resumeService = resumeService;
        this.jobService = jobService;
        this.worker = worker;
        this.objectMapper = objectMapper;
        this.scheduler = scheduler;
    }

    @Transactional
    public AnalysisDtos.TaskView create(Long userId, AnalysisDtos.CreateRequest request) {
        resumeService.requireOwned(userId, request.resumeId());
        jobService.requireOwned(userId, request.jobId());
        AnalysisTaskEntity task = new AnalysisTaskEntity();
        task.setUserId(userId);
        task.setResumeId(request.resumeId());
        task.setJobId(request.jobId());
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setMessage("任务已创建，等待执行");
        task.setRetryCount(0);
        taskMapper.insert(task);
        worker.processAsync(task.getId());
        return view(task);
    }

    public AnalysisDtos.TaskView get(Long userId, Long taskId) {
        return view(requireOwned(userId, taskId));
    }

    public List<AnalysisDtos.TaskView> list(Long userId) {
        return taskMapper.selectList(new LambdaQueryWrapper<AnalysisTaskEntity>()
                        .eq(AnalysisTaskEntity::getUserId, userId).orderByDesc(AnalysisTaskEntity::getCreatedAt))
                .stream().map(this::view).toList();
    }

    @Transactional
    public AnalysisDtos.TaskView cancel(Long userId, Long taskId) {
        AnalysisTaskEntity task = requireOwned(userId, taskId);
        if (isTerminal(task.getStatus())) return view(task);
        task.setStatus("CANCELLED");
        task.setProgress(0);
        task.setMessage("任务已取消");
        task.setErrorMessage("用户取消了任务");
        task.setCompletedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return view(task);
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
        return "SUCCEEDED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status);
    }
}

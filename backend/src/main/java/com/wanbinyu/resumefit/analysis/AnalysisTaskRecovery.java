package com.wanbinyu.resumefit.analysis;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wanbinyu.resumefit.ai.AiProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.concurrent.*;

/** Expire abandoned work rather than silently replaying potentially billable model calls after a crash. */
@Component
public class AnalysisTaskRecovery {
    private final AnalysisTaskMapper tasks;
    private final AiProperties limits;
    private final ScheduledExecutorService scheduler;
    public AnalysisTaskRecovery(AnalysisTaskMapper tasks,AiProperties limits,@Qualifier("analysisScheduler") ScheduledExecutorService scheduler) {
        this.tasks=tasks; this.limits=limits; this.scheduler=scheduler;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void start() { scheduler.scheduleWithFixedDelay(this::sweepSafely,5,30,TimeUnit.SECONDS); }
    private void sweepSafely() {
        try { expire(LocalDateTime.now()); }
        catch (Exception exception) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Task recovery sweep failed; will retry"); }
    }
    public void expire(LocalDateTime now) {
        tasks.update(null,new LambdaUpdateWrapper<AnalysisTaskEntity>()
                .eq(AnalysisTaskEntity::getStatus,"RUNNING").lt(AnalysisTaskEntity::getStartedAt,now.minusSeconds(limits.timeoutSeconds()+30L))
                .set(AnalysisTaskEntity::getStatus,"FAILED").set(AnalysisTaskEntity::getMessage,"任务中断或超时，请重新发起")
                .set(AnalysisTaskEntity::getErrorMessage,"任务未在期限内完成，已停止；不会自动重放模型请求")
                .set(AnalysisTaskEntity::getCompletedAt,now).set(AnalysisTaskEntity::getUpdatedAt,now));
        tasks.update(null,new LambdaUpdateWrapper<AnalysisTaskEntity>()
                .eq(AnalysisTaskEntity::getStatus,"PENDING").lt(AnalysisTaskEntity::getCreatedAt,now.minusMinutes(5))
                .set(AnalysisTaskEntity::getStatus,"FAILED").set(AnalysisTaskEntity::getMessage,"任务排队超时，请重新发起")
                .set(AnalysisTaskEntity::getErrorMessage,"任务长时间未开始，可能服务已重启；请重新发起")
                .set(AnalysisTaskEntity::getCompletedAt,now).set(AnalysisTaskEntity::getUpdatedAt,now));
    }
}

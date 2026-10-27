package com.wanbinyu.resumefit.analysis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/** 分析任务线程池与状态通知调度器。任务池限制并发及排队数量，调度器负责周期读取任务状态。 */
@Configuration
public class TaskExecutorConfig {

    @Bean(name = "analysisExecutor")
    ThreadPoolTaskExecutor analysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("resume-ai-");
        executor.initialize();
        return executor;
    }

    @Bean(destroyMethod = "shutdown", name = "analysisScheduler")
    ScheduledExecutorService analysisScheduler() {
        return new ScheduledThreadPoolExecutor(2);
    }
}

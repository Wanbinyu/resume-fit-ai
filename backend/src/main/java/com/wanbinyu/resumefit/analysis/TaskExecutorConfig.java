package com.wanbinyu.resumefit.analysis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

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

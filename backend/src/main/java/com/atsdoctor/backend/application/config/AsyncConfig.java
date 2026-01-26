package com.atsdoctor.backend.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * In-process async executor for the resume pipeline (SPRINT-01). Single-user
 * local app — an executor is sufficient; no queue infrastructure (DEC from
 * 04-sprints SPRINT-01 planning).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "resumeTaskExecutor")
    public ThreadPoolTaskExecutor resumeTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("resume-pipeline-");
        executor.initialize();
        return executor;
    }
}
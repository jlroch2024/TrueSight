package com.truesight.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Bounded worker pools for the external AI work that can safely run in parallel. */
@Configuration
public class WorkExecutors {

    /** Keeps the existing asynchronous jobs off request threads; their CPU/AI work uses the dedicated pools below. */
    @Bean("taskExecutor")
    ThreadPoolTaskExecutor taskExecutor() {
        return pool(4, "background-");
    }

    @Bean("analysisExecutor")
    ThreadPoolTaskExecutor analysisExecutor(
            @Value("${truesight.analysis.parallel:4}") int parallel) {
        return pool(parallel, "analysis-");
    }

    @Bean("newsBatchExecutor")
    ThreadPoolTaskExecutor newsBatchExecutor(
            @Value("${truesight.news.parallel-batches:4}") int parallel) {
        return pool(parallel, "news-batch-");
    }

    private ThreadPoolTaskExecutor pool(int parallel, String threadNamePrefix) {
        int size = Math.max(1, parallel);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(size);
        executor.setMaxPoolSize(size);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        return executor;
    }
}

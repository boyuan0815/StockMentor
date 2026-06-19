package net.boyuan.stockmentor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class BackgroundTaskConfig {
    @Bean(name = "stockMentorBackgroundTaskExecutor")
    public TaskExecutor stockMentorBackgroundTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("stockmentor-bg-");
        executor.initialize();
        return executor;
    }
}

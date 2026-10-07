package com.individual.messenger.config;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Slow external projections must not starve media leases or presence expiry. */
@Configuration
public class BackgroundTasksConfiguration {
    @Bean(name="taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler=new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);scheduler.setThreadNamePrefix("messenger-background-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);scheduler.setAwaitTerminationSeconds(10);
        scheduler.setErrorHandler(error -> org.slf4j.LoggerFactory.getLogger(getClass())
                .warn("Background task failed: {}",error.getClass().getSimpleName()));
        return scheduler;
    }
}

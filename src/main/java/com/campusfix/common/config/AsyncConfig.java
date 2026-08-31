package com.campusfix.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * The thread pool that notifications are delivered on.
 *
 * <p>Turning on {@code @Async} without defining an executor is the trap here:
 * Spring falls back to {@code SimpleAsyncTaskExecutor}, which creates a
 * <strong>new thread for every call and never reuses one</strong>. Under any
 * real load that is an unbounded thread count and eventually an
 * {@code OutOfMemoryError}. A bounded pool is the whole point.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Override
    @Bean(name = "notificationExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Small on purpose. Sending a notification is mostly waiting on a mail
        // server, and the free-tier instance this runs on has 512 MB — every
        // thread costs stack space whether it is doing anything or not.
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notify-");

        // If the queue fills, the calling thread runs the task itself rather
        // than the notification being silently dropped. It slows that one
        // request down, which is the right way round: losing the work is worse.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // On shutdown, finish what is already queued instead of killing threads
        // mid-send. Bounded so a stuck mail server cannot block a deploy.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);

        executor.initialize();
        return executor;
    }

    /**
     * An exception thrown inside an {@code @Async void} method goes nowhere by
     * default — no stack trace, no log line, nothing. Without this, a
     * notification failing would be completely invisible.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
                log.error("Async {} failed with arguments {}", method.getName(), Arrays.toString(params), throwable);
    }
}

package com.ecommerce.order.config;

import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.task.TaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executors;

/**
 * Configuration for asynchronous task execution using Virtual Threads (Project Loom).
 *
 * <p>Virtual Threads Benefits:</p>
 * <ul>
 *   <li>Lightweight: Can create millions of threads without overwhelming the system</li>
 *   <li>Improved throughput for I/O-bound operations (DB, Kafka, Payment API)</li>
 *   <li>Simplified concurrency model compared to traditional thread pools</li>
 *   <li>Better resource utilization with automatic thread management</li>
 * </ul>
 *
 * <p>Use Cases in This Service:</p>
 * <ul>
 *   <li>Kafka message processing (order events)</li>
 *   <li>Payment processing (@Async methods)</li>
 *   <li>Database queries</li>
 *   <li>Event publishing</li>
 * </ul>
 *
 * @since Java 21 (Virtual Threads)
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Configures the application task executor to use virtual threads.
     *
     * <p>This replaces the default thread pool with a virtual thread executor,
     * enabling automatic scaling based on workload without manual tuning.</p>
     *
     * @return AsyncTaskExecutor backed by virtual threads
     */
    @Bean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    public AsyncTaskExecutor asyncTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}

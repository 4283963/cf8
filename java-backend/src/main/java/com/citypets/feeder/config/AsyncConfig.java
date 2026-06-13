package com.citypets.feeder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String AI_SIGNAL_QUEUE_BEAN = "aiSignalProcessingExecutor";
    public static final String HARDWARE_CMD_QUEUE_BEAN = "hardwareCommandExecutor";

    @Bean(name = AI_SIGNAL_QUEUE_BEAN)
    public ThreadPoolExecutor aiSignalProcessingExecutor(FeederProperties props) {
        int core = Runtime.getRuntime().availableProcessors() * 2;
        int max = Math.max(core, 32);
        return new InstrumentedThreadPool(
                core,
                max,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(props.getAsyncSignalQueueCapacity()),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("ai-signal-consumer-" + t.getId());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy(),
                "AiSignalPool"
        );
    }

    @Bean(name = HARDWARE_CMD_QUEUE_BEAN)
    public ThreadPoolExecutor hardwareCommandExecutor() {
        return new InstrumentedThreadPool(
                2,
                8,
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2048),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("hw-cmd-worker-" + t.getId());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.DiscardOldestPolicy(),
                "HwCmdPool"
        );
    }

    public static class InstrumentedThreadPool extends ThreadPoolExecutor {
        private final String poolName;
        private final AtomicLong rejectedCount = new AtomicLong(0);

        public InstrumentedThreadPool(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                                      TimeUnit unit, ArrayBlockingQueue<Runnable> workQueue,
                                      java.util.concurrent.ThreadFactory threadFactory,
                                      RejectedExecutionHandler handler, String poolName) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
            this.poolName = poolName;
        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);
            if (t != null) {
                rejectedCount.incrementAndGet();
            }
        }

        public String getPoolName() { return poolName; }
        public long getRejectedCount() { return rejectedCount.get(); }

        public PoolMetrics getMetrics() {
            return new PoolMetrics(
                    poolName,
                    getActiveCount(),
                    getPoolSize(),
                    getMaximumPoolSize(),
                    getCorePoolSize(),
                    getQueue().size(),
                    ((ArrayBlockingQueue<?>) getQueue()).remainingCapacity(),
                    getCompletedTaskCount(),
                    getTaskCount(),
                    rejectedCount.get()
            );
        }
    }

    public record PoolMetrics(
            String poolName,
            int activeCount,
            int poolSize,
            int maxPoolSize,
            int corePoolSize,
            int queueSize,
            int queueRemainingCapacity,
            long completedTasks,
            long totalTasks,
            long rejectedCount
    ) {}
}

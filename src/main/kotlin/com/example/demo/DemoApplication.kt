package com.example.demo

import org.apache.logging.log4j.ThreadContext
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.core.task.TaskDecorator
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

@SpringBootApplication
@EnableScheduling
class DemoApplication {
    val factory: ThreadFactory = Thread.ofVirtual().name("vt-factory-", 0).factory()

    @Bean(value = ["virtualThreadTaskScheduler"])
    fun virtualThreadTaskScheduler(): TaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = 10
        scheduler.setThreadFactory(factory)
        scheduler.initialize()
        return scheduler
    }

    @Bean(name = ["virtualThreadTaskExecutor"])
    fun taskExecutor(): TaskExecutor {
        val virtualThreadExecutor: Executor = Executors.newThreadPerTaskExecutor(factory)
        val taskExecutor = ConcurrentTaskExecutor(virtualThreadExecutor)
        taskExecutor.setTaskDecorator(object : TaskDecorator {
            override fun decorate(runnable: Runnable): Runnable {
                val mdcContext: Map<String, String> = ThreadContext.getImmutableContext()
                return Runnable {
                    try {
                        ThreadContext.putAll(mdcContext);
                        runnable.run();
                    } finally {
                        ThreadContext.clearAll();
                    }
                }
            }
        })
        return taskExecutor
    }
}

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}

package com.example.demo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@SpringBootApplication
@EnableScheduling
class DemoApplication {
    @Bean(value = ["virtualThreadTaskScheduler"])
    fun virtualThreadTaskScheduler(): TaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = 10 // ignored for virtual threads but required
        scheduler.setThreadFactory(Thread.ofVirtual().factory())
        scheduler.setThreadNamePrefix("vt-scheduler-")
        scheduler.initialize()
        return scheduler
    }
}

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}

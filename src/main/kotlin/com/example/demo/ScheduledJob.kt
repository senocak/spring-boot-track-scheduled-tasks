package com.example.demo

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class ScheduledJob {
    val log: Logger = LoggerFactory.getLogger(this.javaClass)

    @Scheduled(cron = "0/5 * * * * ?")
    fun every5second(): Unit = log.info("every5second")

    @Scheduled(fixedRate = 15_000)
    fun regularJob(): Unit = log.info("regularJob")

    @Scheduled(fixedRate = 30_000)
    fun longRunningJob() {
        Thread.sleep(10_000)
        log.info("longRunningJob")
    }

    @Scheduled(fixedRate = 3, timeUnit = TimeUnit.SECONDS)
    fun sometimesThrowingException() {
        val random: Int = (0..10).random()
        require(value = random <= 2) {
            "sometimesThrowingException-$random"
        }
        log.info("sometimesThrowingException-$random")
    }

    @Scheduled(fixedRate = 60_000)
    fun alwaysException() {
        throw IllegalArgumentException("alwaysException")
    }

    @Scheduled(initialDelay = 1_000_000_000, fixedRate = 1_000_000_000_000_000)
    fun neverRunningJob(): Unit = log.info("neverRunningJob")

}
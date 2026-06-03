package com.example.demo.tracking

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.scheduling.config.CronTask
import org.springframework.scheduling.config.ScheduledTask
import org.springframework.scheduling.support.ScheduledMethodRunnable
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

enum class JobStatus {
    RUNNING, STOPPED
}

@JsonInclude(value = JsonInclude.Include.NON_NULL)
data class TrackedScheduledJob(
    var status: JobStatus,
    val className: String,
    val methodName: String,
    val settings: Settings,
    val stats: Stats = Stats(),
    val runs: MutableList<ScheduledJobRun> = ArrayList(),

    @JsonIgnore val runnable: ScheduledMethodRunnable,
    @JsonIgnore var future: ScheduledFuture<*>? = null,
    @JsonIgnore var scheduledTask: ScheduledTask
) {
    private val trackingLimit = 10

    fun addRun(run: ScheduledJobRun) {
        stats.numberOfInvocations++
        if (runs.size == trackingLimit)
            runs.removeAt(index = trackingLimit - 1)
        runs.add(index = 0, element = run)
    }

    fun updateNextRun(cron: String, nextRun: String) {
        settings.cron = cron
        settings.nextRun = nextRun
    }

    fun toExceptionInfo(t: Throwable?): ExceptionInfo? {
        if (t == null)
            return null
        return ExceptionInfo(
            type = t::class.java.name,
            message = t.message,
            rootCause = t.cause?.let { cause: Throwable ->
                var root: Throwable = cause
                while (root.cause != null) {
                    root = root.cause!!
                }
                "${root::class.java.name}: ${root.message}"
            },
            stackTrace = t.stackTrace.map { it.toString() }.toString(),
            timestamp = Instant.now()
        )
    }

    fun endRun(uuid: UUID, exception: Throwable?) {
        val endedAt: Instant? = Instant.now()
        val run: ScheduledJobRun = runs.find { it.uuid == uuid } ?: return
        val index: Int = runs.indexOf(element = run)
        runs.remove(element = run)
        runs.add(index = index, element = run.copy(endedAt = endedAt, exception = toExceptionInfo(t = exception)))

        if (exception != null)
            stats.numberOfExceptions++

        val durationInMillis: Long = Duration.between(run.startedAt, endedAt).toMillis()
        stats.totalTimeInMs += durationInMillis
        if (stats.shortestRunDurationInMs == null || durationInMillis < stats.shortestRunDurationInMs!!)
            stats.shortestRunDurationInMs = durationInMillis
        if (stats.longestRunDurationInMs == null || durationInMillis > stats.longestRunDurationInMs!!)
            stats.longestRunDurationInMs = durationInMillis
    }
}

data class Settings(
    var cron: String?,
    var zone: String?,
    var nextRun: String?,
    val fixedRate: Long?,
    val initialDelay: Long?,
    val fixedDelay: Long?,
    val timeUnit: TimeUnit
)

data class Stats(
    var numberOfExceptions: Long = 0,
    var numberOfInvocations: Long = 0,
    var longestRunDurationInMs: Long? = null,
    var shortestRunDurationInMs: Long? = null,
    var totalTimeInMs: Long = 0
) {
    val averageDurationInMs: Long? = if (numberOfInvocations == 0L) null else totalTimeInMs / numberOfInvocations
}

data class UpdateCronRequest(
    val cron: String
)

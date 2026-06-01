package com.example.demo.tracking

import org.aspectj.lang.reflect.MethodSignature
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.Trigger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.config.ScheduledTask
import org.springframework.scheduling.config.ScheduledTaskHolder
import org.springframework.scheduling.support.CronExpression
import org.springframework.scheduling.support.CronTrigger
import org.springframework.scheduling.support.PeriodicTrigger
import org.springframework.scheduling.support.ScheduledMethodRunnable
import org.springframework.stereotype.Component
import java.lang.reflect.Field
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ScheduledFuture
import kotlin.collections.HashSet

@Component
class ScheduledJobTracker(
    private val scheduledTaskHolder: ScheduledTaskHolder,
    @Qualifier(value = "virtualThreadTaskScheduler") private val taskScheduler: TaskScheduler
) {
    var trackedJobs: MutableSet<TrackedScheduledJob> = HashSet()
    @EventListener(value = [ApplicationReadyEvent::class])
    fun init() {
        scheduledTaskHolder.scheduledTasks.forEach { it: ScheduledTask ->
            val runnable: ScheduledMethodRunnable? = when (val runnableCandidate: Runnable = it.task.runnable) {
                is ScheduledMethodRunnable -> runnableCandidate
                else -> unwrapToScheduledMethodRunnable(runnableCandidate = runnableCandidate)
            }
            val annotation: Scheduled = runnable!!.method.getAnnotation(Scheduled::class.java)
            trackedJobs.add(element =
                TrackedScheduledJob(
                    status = JobStatus.RUNNING,
                    className = runnable.method.declaringClass.name,
                    methodName = runnable.method.name,
                    settings = Settings(
                        cron = nullIfEmptyString(str = annotation.cron),
                        nextRun = if (annotation.cron.isNotEmpty())
                            CronExpression.parse(annotation.cron).next(LocalDateTime.now()).toString()
                        else null,
                        fixedRate = nullIfNegativeNumber(number = annotation.fixedRate),
                        initialDelay = nullIfNegativeNumber(number = annotation.initialDelay),
                        fixedDelay = nullIfNegativeNumber(number = annotation.fixedDelay),
                        timeUnit = annotation.timeUnit
                    ),
                    runnable = runnable,
                    future = null,
                    scheduledTask = it
                )
            )
        }
    }

    fun stop(className: String, methodName: String): TrackedScheduledJob {
        val task: TrackedScheduledJob = trackedJobs
            .firstOrNull { it.className == className && it.methodName == methodName }
            ?: throw IllegalArgumentException("Task not found: $className.$methodName")
        task.scheduledTask.cancel()
        task.status = JobStatus.STOPPED
        return task
    }

    fun resume(className: String, methodName: String): TrackedScheduledJob {
        val key = "$className.$methodName"
        val task: TrackedScheduledJob = trackedJobs
            .firstOrNull { it.className == className && it.methodName == methodName }
            ?: throw IllegalArgumentException("Task not found: $key")
        val cronExpr: String? = task.settings.cron
        val trigger: Trigger = when {
            cronExpr != null -> CronTrigger(cronExpr)
            task.settings.fixedRate != null -> PeriodicTrigger(Duration.of(task.settings.fixedRate, task.settings.timeUnit.toChronoUnit()))
            task.settings.fixedDelay != null -> PeriodicTrigger(Duration.of(task.settings.fixedDelay, task.settings.timeUnit.toChronoUnit()))
            else -> throw IllegalArgumentException("No cron, fixedRate or fixedDelay defined for job: $key")
        }
        task.future?.cancel(false)
        task.future = taskScheduler.schedule(task.runnable, trigger)
        task.status = JobStatus.RUNNING
        return task
    }

    fun updateCron(className: String, methodName: String, cron: String): TrackedScheduledJob {
        val key = "$className.$methodName"
        val task: TrackedScheduledJob = trackedJobs
            .firstOrNull { it.className == className && it.methodName == methodName }
            ?: throw IllegalArgumentException("Task not found: $key")
        stop(className = className, methodName = methodName)
        val future: ScheduledFuture<*> = taskScheduler.schedule(task.runnable, CronTrigger(cron))
            ?: throw IllegalStateException("Could not schedule task: $key")
        task.future = future
        task.settings.cron?.let { it: String ->
            val nextRun: String = CronExpression.parse(it).next(LocalDateTime.now()).toString()
            task.updateNextRun(cron = it, nextRun = nextRun)
        }
        task.status = JobStatus.RUNNING
        return task
    }

    fun triggerJob(className: String, methodName: String): TrackedScheduledJob {
        val task: TrackedScheduledJob = findJobByClassAndMethod(className = className, methodName = methodName)
            ?: throw RuntimeException("Job is null or $className.$methodName is already running or not activated")
        val scheduledTasks: Set<ScheduledTask> = scheduledTaskHolder.scheduledTasks
        val matchingJob: ScheduledMethodRunnable? = scheduledTasks
            .mapNotNull { unwrapToScheduledMethodRunnable(runnableCandidate = it.task.runnable) }
            .firstOrNull { it.method.declaringClass.name == className && it.method.name == methodName }
        if (matchingJob == null) {
            throw RuntimeException("No matching job found for class: $className and method: $methodName")
        }
        matchingJob.run()
        return task
    }

    fun jobStart(signature: MethodSignature): UUID {
        val uuid: UUID = UUID.randomUUID()
        val task: TrackedScheduledJob = get(signature = signature)
        task.settings.cron?.let { it: String ->
            val nextRun: String = CronExpression.parse(it).next(LocalDateTime.now()).toString()
            task.updateNextRun(cron = it, nextRun = nextRun)
        }
        task.addRun(run = ScheduledJobRun(uuid = uuid, startedAt = Instant.now()))
        task.status = JobStatus.RUNNING
        return uuid
    }

    private fun get(signature: MethodSignature): TrackedScheduledJob {
        val className: String? = signature.declaringTypeName
        val methodName: String = signature.method.name
        return trackedJobs.find { it.className == className && it.methodName == methodName }
            ?: throw RuntimeException("No tracked job found for method: $className.$methodName")
    }

    fun jobEnd(uuid: UUID, signature: MethodSignature, exception: Throwable?) {
        get(signature = signature).endRun(uuid = uuid, exception = exception)
    }

    fun findJobsByClass(className: String): List<TrackedScheduledJob> =
        trackedJobs.filter { it.className == className }

    fun findJobByClassAndMethod(className: String, methodName: String): TrackedScheduledJob? =
        trackedJobs.firstOrNull { it.className == className && it.methodName == methodName }

    fun findRunByUuid(className: String, methodName: String, uuid: String): ScheduledJobRun? {
        val task: TrackedScheduledJob = findJobByClassAndMethod(className = className, methodName = methodName)
            ?: throw RuntimeException("No tracked job found for class: $className.$methodName")
        return task.runs.firstOrNull { it.uuid.toString() == uuid }
    }

    private fun nullIfEmptyString(str: String): String? = str.ifEmpty { null }

    private fun nullIfNegativeNumber(number: Long): Long? = if (number < 0) null else number

    private fun unwrapToScheduledMethodRunnable(runnableCandidate: Runnable?): ScheduledMethodRunnable? {
        var unwrapped: Any? = runnableCandidate
        // field names commonly used by wrapper classes
        val tryFields: Array<String> = arrayOf("runnable", "delegate", "task", "target")
        for (fieldName: String in tryFields) {
            if (unwrapped == null)
                break
            if (unwrapped is ScheduledMethodRunnable)
                return unwrapped
            try {
                val field: Field = unwrapped::class.java.getDeclaredField(fieldName)
                field.isAccessible = true
                unwrapped = field.get(unwrapped)
                if (unwrapped is ScheduledMethodRunnable)
                    break
            } catch (ignored: Exception) {
                println(ignored)
            }
        }
        return unwrapped as? ScheduledMethodRunnable
    }
}


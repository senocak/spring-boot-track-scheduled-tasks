package com.example.demo.tracking

import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@RestController
@RequestMapping(value = ["\${tracking.scheduledJobs.path:/scheduled-jobs}"])
@CrossOrigin(origins = ["*"])
class ScheduledJobController(
    private val scheduledJobTracker: ScheduledJobTracker
) {
    @GetMapping
    fun getScheduledJobs(): MutableSet<TrackedScheduledJob> = scheduledJobTracker.trackedJobs

    @GetMapping(value = ["{className}"])
    fun getScheduledJobsPerClass(@PathVariable className: String): List<TrackedScheduledJob> =
        scheduledJobTracker.findJobsByClass(className = className)

    @GetMapping(value = ["{className}/{methodName}"])
    fun getScheduledJob(@PathVariable className: String, @PathVariable methodName: String): TrackedScheduledJob =
        scheduledJobTracker.findJobByClassAndMethod(className = className, methodName = methodName)
            ?: throw RuntimeException("Scheduled job not found for class $className and method $methodName")

    @GetMapping(value = ["{className}/{methodName}/{uuid}"])
    fun getScheduledJobRun(@PathVariable className: String, @PathVariable methodName: String, @PathVariable uuid: String): ScheduledJobRun =
        scheduledJobTracker.findRunByUuid(className = className, methodName = methodName, uuid = uuid)
            ?: throw RuntimeException("Scheduled job run not found for class $className, method $methodName and uuid $uuid")

    @PostMapping(value = ["{className}/{methodName}"])
    fun triggerJob(@PathVariable className: String, @PathVariable methodName: String): String =
        scheduledJobTracker.triggerJob(className = className, methodName = methodName)

    @PostMapping(value = ["/{className}/{methodName}/stop"])
    fun stop(@PathVariable className: String, @PathVariable methodName: String): TrackedScheduledJob =
        scheduledJobTracker.stop(className = className, methodName = methodName)

    @PostMapping(value = ["/{className}/{methodName}/resume"])
    fun resume(@PathVariable className: String, @PathVariable methodName: String): TrackedScheduledJob =
        scheduledJobTracker.resume(className = className, methodName = methodName)

    @PostMapping(value = ["/{className}/{methodName}/cron"])
    fun updateCron(@PathVariable className: String, @PathVariable methodName: String,
                   @RequestBody request: UpdateCronRequest): TrackedScheduledJob =
        scheduledJobTracker.updateCron(className = className, methodName = methodName, cron = request.cron)
}
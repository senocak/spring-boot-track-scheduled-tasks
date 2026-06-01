package com.example.demo.tracking

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.context.annotation.Configuration
import java.util.UUID

@Aspect
@Configuration
class ScheduledJobTrackingAspect(
    private val scheduledJobTracker: ScheduledJobTracker
) {
    @Around(value = "@annotation(org.springframework.scheduling.annotation.Scheduled)")
    fun around(joinPoint: ProceedingJoinPoint) {
        val signature: MethodSignature = joinPoint.signature as MethodSignature
        val uuid: UUID = scheduledJobTracker.jobStart(signature = signature)
        var exception: Throwable? = null
        try {
            joinPoint.proceed()
        } catch (t: Throwable) {
            exception = t
        }
        scheduledJobTracker.jobEnd(uuid = uuid, signature = signature, exception = exception)
        if (exception != null)
            throw exception
    }
}

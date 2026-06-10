# Spring Boot Scheduled Tasks Tracker

An observability, tracking and updating scheduled jobs runtime in Spring Boot applications. This project leverages Spring AOP to transparently monitor the execution lifecycle, timings, and exceptions to any method annotated with `@Scheduled`.

## 📖 Overview
In complex Spring Boot systems, keeping track of asynchronous, recurring background tasks can be a blind spot. This project demonstrates a non-invasive way to capture the execution metrics of scheduled tasks without cluttering your business logic.

By utilizing Aspect-Oriented Programming (AOP), the application automatically intercepts `@Scheduled` methods, assigns unique execution UUIDs, and records precise start/end timings and durations. This data is then aggregated and exposed via a REST API.

## The Problem
In a typical Spring Boot application, you might have multiple scheduled tasks running at different intervals. However, there is no built-in mechanism to track the execution history, durations, or exceptions of these tasks. This can lead to challenges in monitoring, debugging, and optimizing the performance of scheduled jobs.

Dynamic scheduling is a classic challenge in Spring Boot. The declarative approach—using the `@Scheduled(cron = "...")` annotation—is strictly static. Once the application context loads, those cron expressions are locked in. If you need to change the schedule at runtime, you typically have to implement a custom scheduling mechanism or restart the application with new configurations. This project aims to provide a way to track and update scheduled jobs at runtime without needing to restart the application. To achieve dynamic control (start, stop, resume, and update) at runtime, you need to shift to programmatic scheduling using Spring's TaskScheduler and ScheduledFuture.

Here is the business logic and architectural approach to building a dynamic job manager.

## 🏗️ Architecture & How It Works
* **`ScheduledJobTrackingAspect`**: An AOP Aspect that wraps around `Scheduled` methods. It captures the exact start time, invokes the job, catches exceptions, and calculates the total execution duration.
* **`ScheduledJobTracker`**: A centralized registry that maintains the state of all tracked jobs. Since a single scheduled task might run concurrently or overlap, each execution run is mapped using a unique UUID.
* **`ScheduledJobController`**: A REST controller that exposes the aggregated tracking data, allowing external monitoring tools or dashboards to poll the job health and performance metrics and update the cron in runtime.

### The Core Mechanics
To control a task, you need to hold a reference to its execution state.

- **TaskScheduler**: The Spring interface that executes the tasks.
- **CronTrigger**: Evaluates the cron expression to determine the next execution time.
- **ScheduledFuture<?>**: The object returned when a task is scheduled. This is your "remote control" for the task. Calling .cancel() on this object stops the job.

#### Handling State and Persistence
The ConcurrentHashMap only lives in the application's memory. If your app crashes or restarts, you lose the state of all running jobs. For an enterprise-grade solution, you must pair this in-memory registry with a persistent data store.

- Database/Cache: Store the jobId, beanName, methodName, cronExpression, and status (RUNNING, STOPPED) in a relational database or a fast key-value store.
- On Application Startup: Implement an ApplicationRunner or @PostConstruct method that queries the database for all jobs with a RUNNING status and loops through them, passing them into the startJob() method to rebuild the in-memory map.

## Sample scheduled job

The demo has a very simple scheduled job, that prints *foo* every 5 seconds.

```kotlin
package com.example.demo

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ScheduledJob {

    @Scheduled(fixedRate = 5000)
    fun println() = println("foo")

}
```

### Get all scheduled jobs
```http request
GET http://localhost:8080/scheduled-jobs
```
```http request
GET http://localhost:8080/scheduled-jobs/com.example.demo.ScheduledJob
```
```json
[
  {
    "status": "RUNNING",
    "className": "com.example.demo.ScheduledJob",
    "methodName": "sometimesThrowingException",
    "settings": {
      "cron": null,
      "zone": null,
      "nextRun": null,
      "fixedRate": 3,
      "initialDelay": null,
      "fixedDelay": null,
      "timeUnit": "SECONDS"
    },
    "stats": {
      "numberOfExceptions": 7,
      "numberOfInvocations": 9,
      "longestRunDurationInMs": 1,
      "shortestRunDurationInMs": 0,
      "totalTimeInMs": 1,
      "averageDurationInMs": null
    },
    "runs": [
      {
        "uuid": "a71adeb1-ebbd-44d7-a7f0-9957a9436340",
        "startedAt": "2026-06-08T16:54:37.616527Z",
        "endedAt": "2026-06-08T16:54:37.616589Z",
        "exception": {
          "type": "java.lang.IllegalArgumentException",
          "message": "sometimesThrowingException-9",
          "rootCause": null,
          "stackTrace": "[com.example.demo.ScheduledJob.sometimesThrowingException(ScheduledJob.kt:28)]",
          "timestamp": "2026-06-08T16:54:37.616641Z"
        },
        "duration": 0
      },
      {
        "uuid": "71f312f1-7fc9-4991-abf3-4a310ba1461f",
        "startedAt": "2026-06-08T16:54:25.616380Z",
        "endedAt": "2026-06-08T16:54:25.617069Z",
        "duration": 0
      }
    ]
  }
]
```

### Get all scheduled jobs
```http request
GET http://localhost:8080/scheduled-jobs/com.example.demo.ScheduledJob/sometimesThrowingException
```
```json
{
  "status": "RUNNING",
  "className": "com.example.demo.ScheduledJob",
  "methodName": "sometimesThrowingException",
  "settings": {
    "cron": null,
    "zone": null,
    "nextRun": null,
    "fixedRate": 3,
    "initialDelay": null,
    "fixedDelay": null,
    "timeUnit": "SECONDS"
  },
  "stats": {
    "numberOfExceptions": 80,
    "numberOfInvocations": 110,
    "longestRunDurationInMs": 8,
    "shortestRunDurationInMs": 0,
    "totalTimeInMs": 29,
    "averageDurationInMs": null
  },
  "runs": [
    {
      "uuid": "7ce52bf0-0297-43f2-8a58-7af5ed6d87bd",
      "startedAt": "2026-06-08T16:59:16.612041Z",
      "endedAt": "2026-06-08T16:59:16.612167Z",
      "exception": {
        "type": "java.lang.IllegalArgumentException",
        "message": "sometimesThrowingException-8",
        "rootCause": null,
        "stackTrace": "[com.example.demo.ScheduledJob.sometimesThrowingException(ScheduledJob.kt:28), java...",
        "timestamp": "2026-06-08T16:59:16.612266Z"
      },
      "duration": 0
    },
    {
      "uuid": "0b383017-eef3-4492-9253-effecf1d2ea0",
      "startedAt": "2026-06-08T16:59:13.619247Z",
      "endedAt": "2026-06-08T16:59:13.620492Z",
      "duration": 1
    }
  ]
}
```

### Trigger a scheduled job manually
```http request
POST http://localhost:8080/scheduled-jobs/com.example.demo.ScheduledJob/sometimesThrowingException/run
```
The response will be the response from the method itself, or an exception if it threw one.

### Stop the scheduled jobs
```http request
POST http://localhost:8080/scheduled-jobs/com.example.demo.ScheduledJob/sometimesThrowingException/stop
```
The response will be a 200 OK if the job was successfully stopped with "status" field is "STOPPED".

### Start the scheduled jobs
```http request
POST http://localhost:8080/scheduled-jobs/com.example.demo.ScheduledJob/sometimesThrowingException/resume
```
The response will be a 200 OK if the job was successfully resumed with "status" field is "RUNNING".

### Update the cron expression of a scheduled job
```http request
POST http://localhost:8080/scheduled-jobs/com.example.demo.ScheduledJob/every5second/cron
Content-Type: application/json

{
  "cron": "0/1 * * * * ?"
}
```
If the jobs is not cron based, the response will be a 500 with an error message "Task with cron not found". If all good, the response will be a 200 OK with the updated job details and job will be rescheduled with the new cron expression.

### 🗺️ Roadmap & Future Enhancements

This project currently serves as a functional proof-of-concept. The long-term vision is to extract this core tracking functionality into a plug-and-play **custom Spring Boot Starter library** for enterprise environments.

#### Planned architectural enhancements include:

* **Standalone Auto-Configuration:** Package the Aspect and Tracker into a publishable library activated by a simple `@EnableScheduledJobTracking` annotation.
* **Grafana Loki Integration:** Seamlessly push scheduled job execution states, exception traces, and configuration drifts directly to Grafana Loki for highly efficient centralized logging and alerting.
* **Redis Persistence & Search:** Replace the current in-memory tracker with a Redis-backed persistent store. Leveraging RediSearch could allow for rapid querying of high-volume historical execution data across distributed application instances.
* **Micrometer Support:** Export job execution timings and failure metrics directly to `/actuator/prometheus` for seamless integration with existing observability dashboards.

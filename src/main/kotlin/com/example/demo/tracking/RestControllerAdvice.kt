package com.example.demo.tracking

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.HandlerMethod

@RestControllerAdvice
class RestControllerAdvice {

    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(value = [
        IllegalStateException::class,
        IllegalArgumentException::class,
        RuntimeException::class,
    ])
    fun illegalStateException(ex: Exception, handlerMethod: HandlerMethod?): String =
        ex.message ?: "Exception: $handlerMethod $ex"
}
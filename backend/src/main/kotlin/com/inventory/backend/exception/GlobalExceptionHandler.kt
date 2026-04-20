package com.inventory.backend.exception

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.time.OffsetDateTime

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(
        ex: NotFoundException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> = buildResponse(
        status = HttpStatus.NOT_FOUND,
        message = ex.message ?: HttpStatus.NOT_FOUND.reasonPhrase,
        path = request.requestURI,
        details = null,
    )

    @ExceptionHandler(BadRequestException::class)
    fun handleBadRequest(
        ex: BadRequestException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> = buildResponse(
        status = HttpStatus.BAD_REQUEST,
        message = ex.message ?: HttpStatus.BAD_REQUEST.reasonPhrase,
        path = request.requestURI,
        details = null,
    )

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(
        ex: ConflictException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> = buildResponse(
        status = HttpStatus.CONFLICT,
        message = ex.message ?: HttpStatus.CONFLICT.reasonPhrase,
        path = request.requestURI,
        details = null,
    )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        val details = ex.bindingResult
            .allErrors
            .map { error ->
                if (error is FieldError) {
                    "${error.field}: ${error.defaultMessage}"
                } else {
                    error.defaultMessage ?: "Ошибка валидации"
                }
            }

        return buildResponse(
            status = HttpStatus.BAD_REQUEST,
            message = "Ошибка валидации запроса",
            path = request.requestURI,
            details = details,
        )
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(
        ex: MethodArgumentTypeMismatchException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> = buildResponse(
        status = HttpStatus.BAD_REQUEST,
        message = "Некорректное значение параметра: ${ex.name}",
        path = request.requestURI,
        details = listOfNotNull(ex.message),
    )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> = buildResponse(
        status = HttpStatus.BAD_REQUEST,
        message = "Некорректный формат JSON в теле запроса",
        path = request.requestURI,
        details = listOfNotNull(ex::class.simpleName, ex.mostSpecificCause?.message),
    )

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        ex: ConstraintViolationException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> = buildResponse(
        status = HttpStatus.BAD_REQUEST,
        message = "Нарушение ограничений запроса",
        path = request.requestURI,
        details = ex.constraintViolations.map { "${it.propertyPath}: ${it.message}" },
    )

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(
        ex: DataIntegrityViolationException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        log.warn("Data integrity violation on {}: {}", request.requestURI, ex.mostSpecificCause?.message ?: ex.message)
        return buildResponse(
            status = HttpStatus.CONFLICT,
            message = "Операция нарушает ограничения данных",
            path = request.requestURI,
            details = listOfNotNull(ex.mostSpecificCause?.message ?: ex.message),
        )
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(
        ex: ResponseStatusException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> = buildResponse(
        status = HttpStatus.valueOf(ex.statusCode.value()),
        message = ex.reason ?: ex.statusCode.toString(),
        path = request.requestURI,
        details = listOfNotNull(ex::class.simpleName, ex.cause?.message),
    )

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(
        ex: NoResourceFoundException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> = buildResponse(
        status = HttpStatus.NOT_FOUND,
        message = "Ресурс не найден",
        path = request.requestURI,
        details = listOfNotNull(ex::class.simpleName, ex.message),
    )

    @ExceptionHandler(AccessDeniedException::class, AuthorizationDeniedException::class)
    fun handleAccessDenied(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> = buildResponse(
        status = HttpStatus.FORBIDDEN,
        message = "Доступ запрещен",
        path = request.requestURI,
        details = listOfNotNull(ex::class.simpleName, ex.message),
    )

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        log.error("Unexpected error on {}", request.requestURI, ex)
        return buildResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            message = "Внутренняя ошибка сервера",
            path = request.requestURI,
            details = listOfNotNull(ex::class.simpleName, ex.message),
        )
    }

    private fun buildResponse(
        status: HttpStatus,
        message: String,
        path: String,
        details: List<String>?,
    ): ResponseEntity<ApiErrorResponse> {
        val body = ApiErrorResponse(
            timestamp = OffsetDateTime.now(),
            status = status.value(),
            error = status.reasonPhrase,
            message = message,
            path = path,
            details = details,
        )

        return ResponseEntity.status(status).body(body)
    }
}

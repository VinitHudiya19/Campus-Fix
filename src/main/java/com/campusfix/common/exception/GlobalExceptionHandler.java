package com.campusfix.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every error leaving the API passes through here and comes out as an
 * {@link ApiError}. The frontend can therefore write one error handler instead
 * of guessing at a different shape per endpoint.
 *
 * <p>It extends {@code ResponseEntityExceptionHandler} so that Spring's own
 * failures — wrong HTTP method, unreadable JSON, unknown URL — are converted
 * too. Without that, those would come back in Spring's default format and the
 * API would have two different error shapes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, WebRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateResourceException ex, WebRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException ex, WebRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    /**
     * Last resort. The real cause is logged for the developer, while the client
     * gets a neutral message so stack traces and SQL never leak through the API.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception on {}", pathOf(request), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.", request);
    }

    /**
     * Bean Validation failures. Each rejected field is reported separately so the
     * form can show the message next to the field the user actually got wrong.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(ApiError.validation(pathOf(request), fieldErrors));
    }

    /**
     * Called by every handler inherited from the parent class, so overriding it
     * once rewrites all of Spring's built-in responses into our own format.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode status,
                                                             WebRequest request) {
        ApiError error = ApiError.of(status.value(), messageFor(status), pathOf(request));
        return ResponseEntity.status(status).headers(headers).body(error);
    }

    private String messageFor(HttpStatusCode status) {
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return "The requested resource does not exist";
        }
        if (status.value() == HttpStatus.METHOD_NOT_ALLOWED.value()) {
            return "That HTTP method is not supported on this endpoint";
        }
        if (status.value() == HttpStatus.BAD_REQUEST.value()) {
            return "The request could not be read";
        }
        return HttpStatus.valueOf(status.value()).getReasonPhrase();
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, WebRequest request) {
        return ResponseEntity.status(status).body(ApiError.of(status.value(), message, pathOf(request)));
    }

    private String pathOf(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }
        return request.getDescription(false);
    }
}

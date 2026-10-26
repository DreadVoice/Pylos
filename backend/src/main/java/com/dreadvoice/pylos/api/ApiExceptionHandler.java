package com.dreadvoice.pylos.api;

import com.dreadvoice.pylos.action.ActionNotFoundException;
import com.dreadvoice.pylos.action.ActionStateException;
import com.dreadvoice.pylos.action.ActionValidationException;
import com.dreadvoice.pylos.agent.LlmNotConfiguredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ActionNotFoundException.class)
    public ProblemDetail handleNotFound(ActionNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ActionStateException.class)
    public ProblemDetail handleConflict(ActionStateException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(LlmNotConfiguredException.class)
    public ProblemDetail handleLlmNotConfigured(LlmNotConfiguredException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(ActionValidationException.class)
    public ProblemDetail handleValidationFailure(ActionValidationException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
        problemDetail.setProperty("violations", exception.getViolations());
        return problemDetail;
    }
}

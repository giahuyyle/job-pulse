package com.huy.jobpulse.shared;

import com.huy.jobpulse.jobs.application.DuplicateJobException;
import com.huy.jobpulse.ingestion.application.DuplicateIngestionTargetException;
import com.huy.jobpulse.ingestion.application.IngestionAlreadyRunningException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DuplicateJobException.class)
    public ProblemDetail handleDuplicate(
            DuplicateJobException exception
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatus(HttpStatus.CONFLICT);

        problem.setTitle("Duplicate job");
        problem.setDetail(exception.getMessage());

        return problem;
    }

    @ExceptionHandler(DuplicateIngestionTargetException.class)
    public ProblemDetail handleDuplicateTarget(
            DuplicateIngestionTargetException exception
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatus(HttpStatus.CONFLICT);

        problem.setTitle("Duplicate ingestion target");
        problem.setDetail(exception.getMessage());

        return problem;
    }

    @ExceptionHandler(IngestionAlreadyRunningException.class)
    public ProblemDetail handleAlreadyRunning(
            IngestionAlreadyRunningException exception
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatus(HttpStatus.CONFLICT);

        problem.setTitle("Ingestion already running");
        problem.setDetail(exception.getMessage());

        return problem;
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(
            EntityNotFoundException exception
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatus(HttpStatus.NOT_FOUND);

        problem.setTitle("Resource not found");
        problem.setDetail(exception.getMessage());

        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidArgument(
            IllegalArgumentException exception
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);

        problem.setTitle("Invalid request");
        problem.setDetail(exception.getMessage());

        return problem;
    }
}

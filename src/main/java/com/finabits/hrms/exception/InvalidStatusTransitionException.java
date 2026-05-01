package com.finabits.hrms.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an employee tries an invalid task status transition.
 * e.g. PENDING → SUBMITTED (skipping IN_PROGRESS)
 * Maps to HTTP 422 Unprocessable Entity.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(String from, String to) {
        super("Invalid status transition: " + from + " → " + to +
                ". Please follow the correct flow.");
    }
}
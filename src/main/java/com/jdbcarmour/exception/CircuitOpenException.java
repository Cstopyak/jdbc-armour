package com.jdbcarmour.exception;

public class CircuitOpenException extends RuntimeException {

    public CircuitOpenException(String message) {
        super(message);
    }

    public CircuitOpenException(String message, Throwable cause) {
        super(message, cause);
    }
}

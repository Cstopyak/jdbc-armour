package com.jdbcarmour.exception;

public class ConnectionExhaustedException extends RuntimeException {

    public ConnectionExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}

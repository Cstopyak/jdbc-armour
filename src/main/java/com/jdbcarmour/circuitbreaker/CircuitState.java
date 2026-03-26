package com.jdbcarmour.circuitbreaker;

public enum CircuitState {
    CLOSED,
    OPEN,
    HALF_OPEN
}
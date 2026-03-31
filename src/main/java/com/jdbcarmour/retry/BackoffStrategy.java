package com.jdbcarmour.retry;

public enum BackoffStrategy {
    FIXED,
    EXPONENTIAL,
    LINEAR,
    CUSTOM;

}
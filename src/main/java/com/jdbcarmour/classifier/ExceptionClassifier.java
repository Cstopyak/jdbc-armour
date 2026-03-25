package com.jdbcarmour.classifier;

public interface ExceptionClassifier {
    FailureType classify(java.sql.SQLException e);
}
package com.jdbcarmour.core;

import com.jdbcarmour.retry.RetryEngine;
import com.jdbcarmour.retry.RetryPolicy;
import com.jdbcarmour.circuitbreaker.CircuitBreaker;
import com.jdbcarmour.classifier.ExceptionClassifier;
import com.jdbcarmour.classifier.FailureType;
import com.jdbcarmour.exception.CircuitOpenException;
import com.jdbcarmour.exception.ConnectionExhaustedException;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResilientDataSourceTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private ExceptionClassifier classifier;

    @Mock
    private CircuitBreaker circuitBreaker;

    @Mock
    private Connection connection;

    private RetryEngine retryEngine;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(circuitBreaker.allowRequest()).thenReturn(true);
        retryEngine = new RetryEngine(new RetryPolicy.Builder().maxAttempts(3).build());
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void successOnFirstAttempt() throws SQLException {
        // Arrange: Use a real RetryPolicy with default values
        when(dataSource.getConnection()).thenReturn(connection);

        // Act: Create the ResilientDataSource with the real RetryPolicy
        ResilientDataSource rds = new ResilientDataSource(dataSource, classifier, circuitBreaker, retryEngine);
        Connection result = rds.acquireConnection();

        // Assert: Only one attempt, success recorded, no failures
        assertSame(connection, result);
        verify(dataSource, times(1)).getConnection();
        verify(circuitBreaker, times(1)).recordSuccess();
        verify(circuitBreaker, never()).recordFailure();
    }

    @Test
    void successAfterTransientFailures() throws SQLException {
        SQLException sqlEx = new SQLException("timeout", "08001");
        when(classifier.classify(sqlEx)).thenReturn(FailureType.TRANSIENT);
        when(dataSource.getConnection())
            .thenThrow(sqlEx)
            .thenThrow(sqlEx)
            .thenReturn(connection);

        ResilientDataSource rds = new ResilientDataSource(dataSource, classifier, circuitBreaker, retryEngine);
        Connection result = rds.acquireConnection();

        assertSame(connection, result);
        verify(dataSource, times(3)).getConnection();
        verify(circuitBreaker, times(2)).recordFailure();
        verify(circuitBreaker, times(1)).recordSuccess();
    }

    @Test
    void exhaustsAllAttemptsWithTransientExceptions() throws SQLException {
        SQLException sqlEx = new SQLException("timeout", "08001");
        when(classifier.classify(sqlEx)).thenReturn(FailureType.TRANSIENT);
        when(dataSource.getConnection()).thenThrow(sqlEx);

        ResilientDataSource rds = new ResilientDataSource(dataSource, classifier, circuitBreaker, retryEngine);
        ConnectionExhaustedException ex = assertThrows(
            ConnectionExhaustedException.class,
            rds::acquireConnection
        );

        assertSame(sqlEx, ex.getCause());
        verify(dataSource, times(3)).getConnection();
        verify(circuitBreaker, times(3)).recordFailure();
        verify(circuitBreaker, never()).recordSuccess();
    }

    @Test
    void circuitOpenRejectsRequest() throws SQLException {
        when(circuitBreaker.allowRequest()).thenReturn(false);

        ResilientDataSource rds = new ResilientDataSource(dataSource, classifier, circuitBreaker, retryEngine);
        assertThrows(
            CircuitOpenException.class,
            rds::acquireConnection
        );

        verify(dataSource, never()).getConnection();
        verify(circuitBreaker, never()).recordSuccess();
        verify(circuitBreaker, never()).recordFailure();
    }

    @Test
    void fatalExceptionRethrowsImmediately() throws SQLException {
        SQLException sqlEx = new SQLException("bad auth", "28000");
        when(classifier.classify(sqlEx)).thenReturn(FailureType.FATAL);
        when(dataSource.getConnection()).thenThrow(sqlEx);

        ResilientDataSource rds = new ResilientDataSource(dataSource, classifier, circuitBreaker, retryEngine);
        SQLException thrown = assertThrows(
            SQLException.class,
            rds::acquireConnection
        );

        assertSame(sqlEx, thrown);
        verify(dataSource, times(1)).getConnection();
        verify(circuitBreaker, never()).recordFailure();
        verify(circuitBreaker, never()).recordSuccess();
    }

    @Test
    void constraintViolationRethrowsImmediately() throws SQLException {
        SQLException sqlEx = new SQLException("duplicate key", "23000");
        when(classifier.classify(sqlEx)).thenReturn(FailureType.CONSTRAINT_VIOLATION);
        when(dataSource.getConnection()).thenThrow(sqlEx);

        ResilientDataSource rds = new ResilientDataSource(dataSource, classifier, circuitBreaker, retryEngine);
        SQLException thrown = assertThrows(
            SQLException.class,
            rds::acquireConnection
        );

        assertSame(sqlEx, thrown);
        verify(dataSource, times(1)).getConnection();
        verify(circuitBreaker, never()).recordFailure();
        verify(circuitBreaker, never()).recordSuccess();
    }

    @Test
    void unknownClassificationRecordsFailureAndRethrows() throws SQLException {
        SQLException sqlEx = new SQLException("unknown error");
        when(classifier.classify(sqlEx)).thenReturn(FailureType.UNKNOWN);
        when(dataSource.getConnection()).thenThrow(sqlEx);

        ResilientDataSource rds = new ResilientDataSource(dataSource, classifier, circuitBreaker, retryEngine);
        SQLException thrown = assertThrows(
            SQLException.class,
            rds::acquireConnection
        );

        assertSame(sqlEx, thrown);
        verify(dataSource, times(1)).getConnection();
        verify(circuitBreaker, times(1)).recordFailure();
        verify(circuitBreaker, never()).recordSuccess();
    }

    @Test
    void singleAttemptExhausted() throws SQLException {
        SQLException sqlEx = new SQLException("timeout", "08001");
        when(classifier.classify(sqlEx)).thenReturn(FailureType.TRANSIENT);
        when(dataSource.getConnection()).thenThrow(sqlEx);
        retryEngine = new RetryEngine(new RetryPolicy.Builder().maxAttempts(1).build());

        ResilientDataSource rds = new ResilientDataSource(dataSource, classifier, circuitBreaker, retryEngine);
        assertThrows(
            ConnectionExhaustedException.class,
            rds::acquireConnection
        );

        verify(dataSource).getConnection();
        verify(circuitBreaker).recordFailure();
        verify(circuitBreaker, never()).recordSuccess();
    }
}
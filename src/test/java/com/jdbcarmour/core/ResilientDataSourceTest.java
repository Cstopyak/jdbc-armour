package com.jdbcarmour.core;

import com.jdbcarmour.classifier.ExceptionClassifier;
import com.jdbcarmour.classifier.FailureType;
import com.jdbcarmour.exception.ConnectionExhaustedException;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResilientDataSourceTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private ExceptionClassifier classifier;

    @Mock
    private Connection connection;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void successOnFirstAttempt() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);

        ResilientDataSource rds = new ResilientDataSource(dataSource, classifier, 3, 0);
        Connection result = rds.acquireConnection();

        assertSame(connection, result);
        verify(dataSource, times(1)).getConnection();
    }

    @Test
    void successAfterTransientFailures() throws SQLException {
        SQLException sqlEx = new SQLException("timeout", "08001");
        when(classifier.classify(sqlEx)).thenReturn(FailureType.TRANSIENT);
        when(dataSource.getConnection())
            .thenThrow(sqlEx)
            .thenThrow(sqlEx)
            .thenReturn(connection);

        ResilientDataSource rds = new ResilientDataSource(dataSource, classifier, 3, 0);
        Connection result = rds.acquireConnection();

        assertSame(connection, result);
        verify(dataSource, times(3)).getConnection();
    }

    @Test
    void exhaustsAllAttemptsWithTransientExceptions() throws SQLException {
        SQLException sqlEx = new SQLException("timeout", "08001");
        when(classifier.classify(sqlEx)).thenReturn(FailureType.TRANSIENT);
        when(dataSource.getConnection()).thenThrow(sqlEx);

        ResilientDataSource rds = new ResilientDataSource(dataSource, classifier, 3, 0);
        ConnectionExhaustedException ex = assertThrows(
            ConnectionExhaustedException.class,
            rds::acquireConnection
        );

        assertSame(sqlEx, ex.getCause());
        verify(dataSource, times(3)).getConnection();
    }

    @Test
    void fatalExceptionRethrowsImmediately() throws SQLException {
        SQLException sqlEx = new SQLException("bad auth", "28000");
        when(classifier.classify(sqlEx)).thenReturn(FailureType.FATAL);
        when(dataSource.getConnection()).thenThrow(sqlEx);

        ResilientDataSource rds = new ResilientDataSource(dataSource, classifier, 3, 0);
        SQLException thrown = assertThrows(
            SQLException.class,
            rds::acquireConnection
        );

        assertSame(sqlEx, thrown);
        verify(dataSource, times(1)).getConnection();
    }

    @Test
    void constraintViolationRethrowsImmediately() throws SQLException {
        SQLException sqlEx = new SQLException("duplicate key", "23000");
        when(classifier.classify(sqlEx)).thenReturn(FailureType.CONSTRAINT_VIOLATION);
        when(dataSource.getConnection()).thenThrow(sqlEx);

        ResilientDataSource rds = new ResilientDataSource(dataSource, classifier, 3, 0);
        SQLException thrown = assertThrows(
            SQLException.class,
            rds::acquireConnection
        );

        assertSame(sqlEx, thrown);
        verify(dataSource, times(1)).getConnection();
    }

    @Test
    void unknownClassificationRethrowsImmediately() throws SQLException {
        SQLException sqlEx = new SQLException("unknown error");
        when(classifier.classify(sqlEx)).thenReturn(FailureType.UNKNOWN);
        when(dataSource.getConnection()).thenThrow(sqlEx);

        ResilientDataSource rds = new ResilientDataSource(dataSource, classifier, 3, 0);
        SQLException thrown = assertThrows(
            SQLException.class,
            rds::acquireConnection
        );

        assertSame(sqlEx, thrown);
        verify(dataSource, times(1)).getConnection();
    }

    @Test
    void singleAttemptExhausted() throws SQLException {
        SQLException sqlEx = new SQLException("timeout", "08001");
        when(classifier.classify(sqlEx)).thenReturn(FailureType.TRANSIENT);
        when(dataSource.getConnection()).thenThrow(sqlEx);

        ResilientDataSource rds = new ResilientDataSource(dataSource, classifier, 1, 0);
        assertThrows(
            ConnectionExhaustedException.class,
            rds::acquireConnection
        );

        verify(dataSource, times(1)).getConnection();
    }
}
package com.jdbcarmour.classifier;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
@Slf4j
public class DefaultExceptionClassifier implements ExceptionClassifier {


    private static final Map<String, FailureType> SQL_STATE_MAP;

    static {
        Map<String, FailureType> map = new HashMap<>();
        map.put("08001", FailureType.TRANSIENT);  // client unable to establish connection
        map.put("08006", FailureType.TRANSIENT);  // connection failure
        map.put("28000", FailureType.FATAL);      // invalid authorization / bad password
        SQL_STATE_MAP = Collections.unmodifiableMap(map);
    }

    @Override
    public FailureType classify(SQLException e) {
        String sqlState = e.getSQLState();
        FailureType type = sqlState != null
            ? SQL_STATE_MAP.getOrDefault(sqlState, FailureType.UNKNOWN)
            : FailureType.UNKNOWN;
        log.debug("Classified SQLException sqlState={} as {}", sqlState, type);
        return type;
    }
}
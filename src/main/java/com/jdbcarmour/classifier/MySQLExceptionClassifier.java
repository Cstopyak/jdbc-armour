package com.jdbcarmour.classifier;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MySQLExceptionClassifier implements ExceptionClassifier {

    private static final Map<Integer, FailureType> ERROR_CODE_MAP;

    static {
        Map<Integer, FailureType> map = new HashMap<>();

        // ── CONSTRAINT_VIOLATION ──────────────────────────────────────────────
        map.put(1048, FailureType.CONSTRAINT_VIOLATION); // MySQL: Column cannot be null
        map.put(1062, FailureType.CONSTRAINT_VIOLATION); // MySQL: Duplicate entry for key
        map.put(1138, FailureType.CONSTRAINT_VIOLATION); // MySQL: Out of range value for column
        map.put(1215, FailureType.CONSTRAINT_VIOLATION); // MySQL: Cannot add foreign key constraint
        map.put(1216, FailureType.CONSTRAINT_VIOLATION); // MySQL: Cannot add or update a child row: a foreign key constraint fails
        map.put(1217, FailureType.CONSTRAINT_VIOLATION); // MySQL: Cannot delete or update a parent row: a foreign key constraint fails
        map.put(1364, FailureType.CONSTRAINT_VIOLATION); // MySQL: Field doesn't have a default value
        map.put(1451, FailureType.CONSTRAINT_VIOLATION); // MySQL: Cannot delete or update a parent row: a foreign key constraint fails
        map.put(1452, FailureType.CONSTRAINT_VIOLATION); // MySQL: Cannot add or update a child row: a foreign key constraint fails
        map.put(3819, FailureType.CONSTRAINT_VIOLATION); // MySQL: Check constraint '...' is violated
        map.put(3820, FailureType.CONSTRAINT_VIOLATION); // MySQL: Duplicate entry for key '...' is not allowed in a partitioned table
        map.put(4025, FailureType.CONSTRAINT_VIOLATION); // MySQL: Generic constraint violation error

        // ── TRANSIENT ─────────────────────────────────────────────────────────
        map.put(1040, FailureType.TRANSIENT); // MySQL: Too many connections
        map.put(1041, FailureType.TRANSIENT); // MySQL: Out of memory
        map.put(1153, FailureType.TRANSIENT); // MySQL: Got a packet bigger than 'max_allowed_packet'
        map.put(1154, FailureType.TRANSIENT); // MySQL: Network read error from client
        map.put(1155, FailureType.TRANSIENT); // MySQL: Network write error to client
        map.put(1158, FailureType.TRANSIENT); // MySQL: Network read error from pipe
        map.put(1159, FailureType.TRANSIENT); // MySQL: Network error on write
        map.put(1160, FailureType.TRANSIENT); // MySQL: Network packets out of order
        map.put(1161, FailureType.TRANSIENT); // MySQL: network compress error
        map.put(1162, FailureType.TRANSIENT); // MySQL: Network read error
        map.put(1163, FailureType.TRANSIENT); // MySQL: Network read interupt
        map.put(1164, FailureType.TRANSIENT); // MySQL: Network error on write
        map.put(1205, FailureType.TRANSIENT); // MySQL: Lock wait timeout exceeded
        map.put(1213, FailureType.TRANSIENT); // MySQL: Deadlock found when trying to get lock

        // ── FATAL ─────────────────────────────────────────────────────────────
        map.put(1042, FailureType.FATAL); // MySQL: Can't get hostname for your address
        map.put(1043, FailureType.FATAL); // MySQL: Bad handshake
        map.put(1044, FailureType.FATAL); // MySQL: Access denied for user to database
        map.put(1045, FailureType.FATAL); // MySQL: Access denied for user (using password: YES/NO)
        map.put(1046, FailureType.FATAL); // MySQL: No database selected
        map.put(1047, FailureType.FATAL); // MySQL: Unknown command
        map.put(1049, FailureType.FATAL); // MySQL: Unknown database
        map.put(1050, FailureType.FATAL); // MySQL: Table already exists
        map.put(1051, FailureType.FATAL); // MySQL: Unknown table
        map.put(1064, FailureType.FATAL); // MySQL: Syntax error in SQL statement
        map.put(1095, FailureType.FATAL); // MySQL: Table is locket or active transaction
        map.put(1105, FailureType.FATAL); // MySQL: Unknown error
        map.put(1129, FailureType.FATAL); // MySQL: Host is blocked because of too many connection errors
        map.put(1130, FailureType.FATAL); // MySQL: Host is not allowed to connect to this MySQL server
        map.put(1131, FailureType.FATAL); // MySQL: You are using MySQL as an anonymous user and anonymous users are not allowed
        map.put(1132, FailureType.FATAL); // MySQL: You must have privileges to perform this operation
        map.put(1133, FailureType.FATAL); // MySQL: Your password does not match
        map.put(1135, FailureType.FATAL); // MySQL: Unknown command
        map.put(1146, FailureType.FATAL); // MySQL: Table doesn't exist
        map.put(1152, FailureType.FATAL); // MySQL: Aborted connection
        map.put(1156, FailureType.FATAL); // MySQL: Network read error from server
        map.put(1157, FailureType.FATAL); // MySQL: Network write error to server
        map.put(1184, FailureType.FATAL); // MySQL: Abort Connection (new)
        map.put(1203, FailureType.FATAL); // MySQL: User has exceeded the 'max_user_connections' resource
        map.put(1226, FailureType.FATAL); // MySQL: User has exceeded the 'max_questions' resource
        map.put(1275, FailureType.FATAL); // MySQL: Server is running in secure auth mode, and you attemptedto connect with an insecure password
        map.put(1277, FailureType.FATAL); // MySQL: Server shutdown in progress
        map.put(1290, FailureType.FATAL); // MySQL: the MySQL server is running with the read-only option
        map.put(1927, FailureType.FATAL); // MySQL: Connection was killed
        map.put(2002, FailureType.FATAL); // MySQL: Can't connect to local MySQL server
        map.put(2003, FailureType.FATAL); // MySQL: Can't connect to MySQL server on host
        map.put(2006, FailureType.FATAL); // MySQL: MySQL server has gone away
        map.put(2011, FailureType.FATAL); // MySQL: Out of memory
        map.put(2013, FailureType.FATAL); // MySQL: Lost connection to MySQL server during query
        map.put(2026, FailureType.FATAL); // MySQL: Can't create TCP/IP socket
        map.put(2055, FailureType.FATAL); // MySQL: Lost connection to Mysql server at 'reading authorization packet'
        map.put(3087, FailureType.FATAL); // MySQL: Query execution was interrupted
        map.put(4031, FailureType.FATAL); // MySQL: Resource group was busy (may be fatal in resource-constrained environments)


        ERROR_CODE_MAP = Collections.unmodifiableMap(map);
    }

    @Override
    public FailureType classify(SQLException e) {
        return ERROR_CODE_MAP.getOrDefault(e.getErrorCode(), FailureType.UNKNOWN);
    }
}
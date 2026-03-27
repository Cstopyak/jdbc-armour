package com.jdbcarmour.classifier;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OracleExceptionClassifier implements ExceptionClassifier {

    private static final Map<Integer, FailureType> ERROR_CODE_MAP;

    static {
        Map<Integer, FailureType> map = new HashMap<>();

        // ── CONSTRAINT_VIOLATION ──────────────────────────────────────────────
        map.put(1,    FailureType.CONSTRAINT_VIOLATION); // ORA-00001: unique constraint violated
        map.put(1400, FailureType.CONSTRAINT_VIOLATION); // ORA-01400: cannot insert NULL
        map.put(1401, FailureType.CONSTRAINT_VIOLATION); // ORA-01401: inserted value too large for column
        map.put(2290, FailureType.CONSTRAINT_VIOLATION); // ORA-02290: check constraint violated
        map.put(2291, FailureType.CONSTRAINT_VIOLATION); // ORA-02291: integrity constraint - parent key not found
        map.put(2292, FailureType.CONSTRAINT_VIOLATION); // ORA-02292: integrity constraint - child record found
        map.put(2293, FailureType.CONSTRAINT_VIOLATION); // ORA-02293: cannot validate - check constraint violated
        map.put(2296, FailureType.CONSTRAINT_VIOLATION); // ORA-02296: cannot enable - null values found
        map.put(2298, FailureType.CONSTRAINT_VIOLATION); // ORA-02298: cannot validate - parent keys not found

        // ── TRANSIENT ─────────────────────────────────────────────────────────
        map.put(28,    FailureType.TRANSIENT); // ORA-00028: your session has been killed
        map.put(51,    FailureType.TRANSIENT); // ORA-00051: timeout waiting for resource
        map.put(60,    FailureType.TRANSIENT); // ORA-00060: deadlock detected
        map.put(1012,  FailureType.TRANSIENT); // ORA-01012: not logged on
        map.put(1033,  FailureType.TRANSIENT); // ORA-01033: ORACLE initialization or shutdown in progress
        map.put(1034,  FailureType.TRANSIENT); // ORA-01034: ORACLE not available
        map.put(1089,  FailureType.TRANSIENT); // ORA-01089: immediate shutdown in progress
        map.put(3113,  FailureType.TRANSIENT); // ORA-03113: end-of-file on communication channel
        map.put(3114,  FailureType.TRANSIENT); // ORA-03114: not connected to ORACLE
        map.put(3135,  FailureType.TRANSIENT); // ORA-03135: connection lost contact
        map.put(12170, FailureType.TRANSIENT); // ORA-12170: TNS connect timeout
        map.put(12203, FailureType.TRANSIENT); // ORA-12203: TNS unable to connect to destination
        map.put(12500, FailureType.TRANSIENT); // ORA-12500: TNS listener failed to start dedicated server
        map.put(12514, FailureType.TRANSIENT); // ORA-12514: TNS listener does not know of service
        map.put(12516, FailureType.TRANSIENT); // ORA-12516: TNS listener could not find available handler
        map.put(12518, FailureType.TRANSIENT); // ORA-12518: TNS listener could not hand off client connection
        map.put(12535, FailureType.TRANSIENT); // ORA-12535: TNS operation timed out
        map.put(12541, FailureType.TRANSIENT); // ORA-12541: TNS no listener
        map.put(12543, FailureType.TRANSIENT); // ORA-12543: TNS destination host unreachable

        // ── FATAL ─────────────────────────────────────────────────────────────
        map.put(900,  FailureType.FATAL); // ORA-00900: invalid SQL statement
        map.put(904,  FailureType.FATAL); // ORA-00904: invalid identifier
        map.put(907,  FailureType.FATAL); // ORA-00907: missing right parenthesis
        map.put(911,  FailureType.FATAL); // ORA-00911: invalid character
        map.put(920,  FailureType.FATAL); // ORA-00920: invalid relational operator
        map.put(923,  FailureType.FATAL); // ORA-00923: FROM keyword not found
        map.put(933,  FailureType.FATAL); // ORA-00933: SQL command not properly ended
        map.put(942,  FailureType.FATAL); // ORA-00942: table or view does not exist
        map.put(947,  FailureType.FATAL); // ORA-00947: not enough values
        map.put(1017, FailureType.FATAL); // ORA-01017: invalid username/password; login denied
        map.put(1403, FailureType.FATAL); // ORA-01403: no data found
        map.put(1410, FailureType.FATAL); // ORA-01410: invalid ROWID
        map.put(1422, FailureType.FATAL); // ORA-01422: exact fetch returns more than requested rows
        map.put(1461, FailureType.FATAL); // ORA-01461: can bind LONG only for insert into LONG column
        map.put(1476, FailureType.FATAL); // ORA-01476: divisor is equal to zero
        map.put(1722, FailureType.FATAL); // ORA-01722: invalid number
        map.put(1858, FailureType.FATAL); // ORA-01858: non-numeric character where numeric expected
        map.put(1861, FailureType.FATAL); // ORA-01861: literal does not match format string
        map.put(4043, FailureType.FATAL); // ORA-04043: object does not exist
        map.put(6500, FailureType.FATAL); // ORA-06500: PL/SQL storage error
        map.put(6502, FailureType.FATAL); // ORA-06502: PL/SQL numeric or value error
        map.put(6508, FailureType.FATAL); // ORA-06508: PL/SQL could not find program unit

        // ── UCP (Universal Connection Pool) ───────────────────────────────────
        // Codes match the numeric suffix of UCP-45xxx as returned by getErrorCode()
        map.put(45001, FailureType.TRANSIENT); // UCP-45001: cannot acquire connection from pool
        map.put(45010, FailureType.TRANSIENT); // UCP-45010: connections cannot be acquired
        map.put(45011, FailureType.TRANSIENT); // UCP-45011: connection pool is not running
        map.put(45014, FailureType.TRANSIENT); // UCP-45014: max pool size reached
        map.put(45015, FailureType.TRANSIENT); // UCP-45015: connection validation failed
        map.put(45016, FailureType.TRANSIENT); // UCP-45016: failed to create connection
        map.put(45028, FailureType.TRANSIENT); // UCP-45028: borrowed connection is no longer valid
        map.put(45047, FailureType.TRANSIENT); // UCP-45047: reconnect attempts exceeded
        map.put(45054, FailureType.TRANSIENT); // UCP-45054: connection pool unavailable
        map.put(45068, FailureType.TRANSIENT); // UCP-45068: timeout waiting for connection from pool
        map.put(45600, FailureType.TRANSIENT); // UCP-45600: pool disabled due to RAC node down
        map.put(45040, FailureType.FATAL);     // UCP-45040: unable to start connection pool
        map.put(45041, FailureType.FATAL);     // UCP-45041: unable to stop connection pool
        map.put(45044, FailureType.FATAL);     // UCP-45044: cannot create statement
        map.put(45055, FailureType.FATAL);     // UCP-45055: connection pool has been destroyed

        ERROR_CODE_MAP = Collections.unmodifiableMap(map);
    }

    @Override
    public FailureType classify(SQLException e) {
        return ERROR_CODE_MAP.getOrDefault(e.getErrorCode(), FailureType.UNKNOWN);
    }
}

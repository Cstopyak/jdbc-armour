package com.jdbcarmour.classifier;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PostgresExceptionClassifier implements ExceptionClassifier{
    private static final Map<String, FailureType> ERROR_CODE_MAP;

    static {
        Map<String, FailureType> map = new HashMap<>();

        // ── CONSTRAINT_VIOLATION ──────────────────────────────────────────────
        map.put("23500", FailureType.CONSTRAINT_VIOLATION); // PostgreSQL: integrity constraint violation
        map.put("23501", FailureType.CONSTRAINT_VIOLATION); // PostgreSQL: restrict violation
        map.put("23502", FailureType.CONSTRAINT_VIOLATION); // PostgreSQL: not null violation
        map.put("23503", FailureType.CONSTRAINT_VIOLATION); // PostgreSQL: foreign key violation
        map.put("23505", FailureType.CONSTRAINT_VIOLATION); // PostgreSQL: unique violation
        map.put("23514", FailureType.CONSTRAINT_VIOLATION); // PostgreSQL: check violation
        map.put("23P01", FailureType.CONSTRAINT_VIOLATION); // PostgreSQL: exclusion violation
        map.put("23504", FailureType.CONSTRAINT_VIOLATION); // PostgreSQL: invalid foreign key

        // ── TRANSIENT ─────────────────────────────────────────────────────────
        map.put("08000", FailureType.TRANSIENT); // PostgreSQL: connection exception
        map.put("08001", FailureType.TRANSIENT); // PostgreSQL: SQL client unable to establish SQL connection
        map.put("08003", FailureType.TRANSIENT); // PostgreSQL: connection does not exist
        map.put("08004", FailureType.TRANSIENT); // PostgreSQL: SQL server rejected establishment of SQL connection
        map.put("08006", FailureType.TRANSIENT); // PostgreSQL: connection failure
        map.put("08007", FailureType.TRANSIENT); // PostgreSQL: transaction resolution unknown
        map.put("40001", FailureType.TRANSIENT); // PostgreSQL: serialization failure
        map.put("40003", FailureType.TRANSIENT); // PostgreSQL: statement completion unknown
        map.put("53000", FailureType.TRANSIENT); // PostgreSQL: insufficient resources
        map.put("53100", FailureType.TRANSIENT); // PostgreSQL: disk full
        map.put("53200", FailureType.TRANSIENT); // PostgreSQL: out of memory
        map.put("53300", FailureType.TRANSIENT); // PostgreSQL: too many connections
        map.put("53400", FailureType.TRANSIENT); // PostgreSQL: configuration limit exceeded
        map.put("53400", FailureType.TRANSIENT); // PostgreSQL: configuration limit exceeded
        map.put("54000", FailureType.TRANSIENT); // PostgreSQL: program limit exceeded
        map.put("54001", FailureType.TRANSIENT); // PostgreSQL: statement too complex
        map.put("54011", FailureType.TRANSIENT); // PostgreSQL: too many columns
        map.put("54023", FailureType.TRANSIENT); // PostgreSQL: too many arguments
        map.put("55000", FailureType.TRANSIENT); // PostgreSQL: object not in prerequisite state
        map.put("55006", FailureType.TRANSIENT); // PostgreSQL: object in use
        map.put("57000", FailureType.TRANSIENT); // PostgreSQL: operator intervention
        map.put("57014", FailureType.TRANSIENT); // PostgreSQL: query canceled
        map.put("58000", FailureType.TRANSIENT); // PostgreSQL: system error
        map.put("58030", FailureType.TRANSIENT); // PostgreSQL: io error
        map.put("08P01", FailureType.TRANSIENT); // PostgreSQL: protocol violation
        map.put("55P02", FailureType.TRANSIENT); // PostgreSQL: cannot cahnge runtime paramter
        map.put("55P03", FailureType.TRANSIENT); // PostgreSQL: lock not available
        map.put("57P01", FailureType.TRANSIENT); // PostgreSQL: admin shutdown
        map.put("57P02", FailureType.TRANSIENT); // PostgreSQL: crash shutdown
        map.put("57P03", FailureType.TRANSIENT); // PostgreSQL: cannot connect now
        map.put("57P04", FailureType.TRANSIENT); // PostgreSQL: database dropped

        // ── FATAL ─────────────────────────────────────────────────────────────

        map.put("0A000", FailureType.FATAL); // feature not supported
        map.put("0B000", FailureType.FATAL); // invalid transaction initiation
        map.put("0F000", FailureType.FATAL); // locator exception
        map.put("0F001", FailureType.FATAL); // invalid locator specification
        map.put("0L000", FailureType.FATAL); // invalid grantor
        map.put("0LP01", FailureType.FATAL); // invalid grant operation
        map.put("0P000", FailureType.FATAL); // invalid role specification
        map.put("25000", FailureType.FATAL); // invalid transaction state
        map.put("25001", FailureType.FATAL); // active sql transaction
        map.put("25002", FailureType.FATAL); // branch transaction already active
        map.put("25008", FailureType.FATAL); // held cursor requires same isolation level
        map.put("25P01", FailureType.FATAL); // no active sql transaction
        map.put("25P02", FailureType.FATAL); // in failed sql transaction
        map.put("25P03", FailureType.FATAL); // idle in transaction session timeout
        map.put("28000", FailureType.FATAL); // invalid authorization specification
        map.put("28P01", FailureType.FATAL); // invalid password
        map.put("2D000", FailureType.FATAL); // invalid transaction termination
        map.put("2F000", FailureType.FATAL); // sql routine exception
        map.put("2F002", FailureType.FATAL); // modifying sql data not permitted
        map.put("2F003", FailureType.FATAL); // prohibited sql statement attempted
        map.put("2F004", FailureType.FATAL); // reading sql data not permitted
        map.put("2F005", FailureType.FATAL); // function executed no return statement
        map.put("34000", FailureType.FATAL); // invalid cursor name
        map.put("38000", FailureType.FATAL); // external routine exception
        map.put("38001", FailureType.FATAL); // containing sql not permitted
        map.put("38002", FailureType.FATAL); // modifying sql data not permitted
        map.put("38003", FailureType.FATAL); // prohibited sql statement attempted
        map.put("38004", FailureType.FATAL); // reading sql data not permitted
        map.put("39000", FailureType.FATAL); // external routine invocation exception
        map.put("39001", FailureType.FATAL); // invalid sqlstate returned
        map.put("39004", FailureType.FATAL); // null value not allowed
        map.put("39P01", FailureType.FATAL); // trigger protocol violated
        map.put("39P02", FailureType.FATAL); // srf protocol violated
        map.put("39P03", FailureType.FATAL); // event trigger protocol violated
        map.put("3B000", FailureType.FATAL); // savepoint exception
        map.put("3B001", FailureType.FATAL); // invalid savepoint specification
        map.put("3D000", FailureType.FATAL); // invalid catalog name
        map.put("3F000", FailureType.FATAL); // invalid schema name
        map.put("42000", FailureType.FATAL); // syntax error or access rule violation
        map.put("42501", FailureType.FATAL); // insufficient privilege
        map.put("42601", FailureType.FATAL); // syntax error
        map.put("42701", FailureType.FATAL); // duplicate column
        map.put("42702", FailureType.FATAL); // ambiguous column
        map.put("42703", FailureType.FATAL); // undefined column
        map.put("42704", FailureType.FATAL); // undefined object
        map.put("42710", FailureType.FATAL); // duplicate object
        map.put("42712", FailureType.FATAL); // duplicate alias
        map.put("42723", FailureType.FATAL); // duplicate function
        map.put("42725", FailureType.FATAL); // ambiguous function
        map.put("42803", FailureType.FATAL); // grouping error
        map.put("42804", FailureType.FATAL); // datatype mismatch
        map.put("42809", FailureType.FATAL); // wrong object type
        map.put("42830", FailureType.FATAL); // invalid foreign key
        map.put("42846", FailureType.FATAL); // cannot coerce
        map.put("42883", FailureType.FATAL); // undefined function
        map.put("428C9", FailureType.FATAL); // generated always
        map.put("42939", FailureType.FATAL); // reserved name
        map.put("42P01", FailureType.FATAL); // undefined table
        map.put("42P02", FailureType.FATAL); // undefined parameter
        map.put("42P03", FailureType.FATAL); // duplicate cursor
        map.put("42P04", FailureType.FATAL); // duplicate database
        map.put("42P05", FailureType.FATAL); // duplicate prepared statement
        map.put("42P06", FailureType.FATAL); // duplicate schema
        map.put("42P07", FailureType.FATAL); // duplicate table
        map.put("42P08", FailureType.FATAL); // ambiguous parameter
        map.put("42P09", FailureType.FATAL); // ambiguous alias
        map.put("42P10", FailureType.FATAL); // invalid column reference
        map.put("42P11", FailureType.FATAL); // invalid cursor definition
        map.put("42P12", FailureType.FATAL); // invalid database definition
        map.put("42P13", FailureType.FATAL); // invalid function definition
        map.put("42P14", FailureType.FATAL); // invalid prepared statement definition
        map.put("42P15", FailureType.FATAL); // invalid schema definition
        map.put("42P16", FailureType.FATAL); // invalid table definition
        map.put("42P17", FailureType.FATAL); // invalid object definition
        map.put("44000", FailureType.FATAL); // with check option violation
        map.put("F0000", FailureType.FATAL); // config file error
        map.put("F0001", FailureType.FATAL); // lock file exists
        map.put("HV000", FailureType.FATAL); // foreign data wrapper error
        map.put("HV005", FailureType.FATAL); // fdw column name not found
        map.put("HV002", FailureType.FATAL); // fdw dynamic parameter value needed
        map.put("HV010", FailureType.FATAL); // fdw function sequence error
        map.put("HV021", FailureType.FATAL); // fdw inconsistent descriptor information
        map.put("HV024", FailureType.FATAL); // fdw invalid attribute value
        map.put("HV007", FailureType.FATAL); // fdw invalid column name
        map.put("HV008", FailureType.FATAL); // fdw invalid column number
        map.put("HV004", FailureType.FATAL); // fdw invalid data type
        map.put("HV006", FailureType.FATAL); // fdw invalid data type descriptors
        map.put("HV091", FailureType.FATAL); // fdw invalid descriptor field identifier
        map.put("HV00B", FailureType.FATAL); // fdw invalid handle
        map.put("HV00C", FailureType.FATAL); // fdw invalid option index
        map.put("HV00D", FailureType.FATAL); // fdw invalid option name
        map.put("HV090", FailureType.FATAL); // fdw invalid string length or buffer length
        map.put("HV00A", FailureType.FATAL); // fdw invalid string format
        map.put("HV009", FailureType.FATAL); // fdw invalid use of null pointer
        map.put("HV014", FailureType.FATAL); // fdw too many handles
        map.put("HV001", FailureType.FATAL); // fdw out of memory
        map.put("HV00P", FailureType.FATAL); // fdw no schemas
        map.put("HV00J", FailureType.FATAL); // fdw option name not found
        map.put("HV00K", FailureType.FATAL); // fdw reply handle
        map.put("HV00Q", FailureType.FATAL); // fdw schema not found
        map.put("HV00R", FailureType.FATAL); // fdw table not found
        map.put("HV00L", FailureType.FATAL); // fdw unable to create execution
        map.put("HV00M", FailureType.FATAL); // fdw unable to create reply
        map.put("HV00N", FailureType.FATAL); // fdw unable to establish connection
        map.put("P0000", FailureType.FATAL); // plpgsql error
        map.put("P0001", FailureType.FATAL); // raise exception
        map.put("P0002", FailureType.FATAL); // no data found
        map.put("P0003", FailureType.FATAL); // too many rows
        map.put("P0004", FailureType.FATAL); // assert failure
        map.put("XX000", FailureType.FATAL); // internal error
        map.put("XX001", FailureType.FATAL); // data corrupted
        map.put("XX002", FailureType.FATAL); // index corrupted

        ERROR_CODE_MAP = Collections.unmodifiableMap(map);

    }

    @Override
    public FailureType classify(SQLException e) {
        return ERROR_CODE_MAP.getOrDefault(e.getErrorCode(), FailureType.UNKNOWN);
    }
}
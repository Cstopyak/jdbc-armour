# JDBC Armour — Developer Wiki

## Table of Contents

1. [What Is JDBC Armour?](#1-what-is-jdbc-armour)
2. [Why Does It Exist?](#2-why-does-it-exist)
3. [Core Concepts](#3-core-concepts)
4. [Getting Started](#4-getting-started)
5. [Retry Policy](#5-retry-policy)
6. [Circuit Breaker](#6-circuit-breaker)
7. [Exception Classifiers](#7-exception-classifiers)
8. [Putting It All Together](#8-putting-it-all-together)
9. [How It Compares to Plain JDBC and Connection Pools](#9-how-it-compares-to-plain-jdbc-and-connection-pools)
10. [Exception Reference](#10-exception-reference)
11. [Architecture Overview](#11-architecture-overview)

---

## 1. What Is JDBC Armour?

JDBC Armour is a lightweight Java library that creates resilient database connections. It wraps a vendor-native `DataSource` and adds three protective layers around every connection attempt:

| Layer | Class | Purpose |
|---|---|---|
| Exception Classification | `ExceptionClassifier` | Determines whether a failure is worth retrying |
| Retry Engine | `RetryEngine` + `RetryPolicy` | Retries transient failures with configurable backoff |
| Circuit Breaker | `CircuitBreaker` | Stops trying when the database is clearly unavailable |

These three layers work together through a single entry point — `ResilientDataSource` — which gives you a production-ready database connection in a few lines of setup code, with no additional libraries required.

---

## 2. Why Does It Exist?

### The Problem With Raw JDBC

The simplest way to get a database connection in Java is:

```java
Connection conn = DriverManager.getConnection(url, user, password);
```

This works fine during development. In production it fails silently when:

- The database is briefly overloaded and refuses new connections.
- A network hiccup causes a connection timeout.
- The database restarts during a rolling deployment.
- A deadlock causes a query to fail moments before it would have succeeded.

When any of these happen, `getConnection()` throws a `SQLException` and your application does nothing useful with it. Most applications log an error and return a 500. Some retry immediately with no delay — which makes the database situation worse by adding more load at exactly the wrong moment.

Standard datasource pools (HikariCP, c3p0, DBCP) solve the connection reuse problem well, but they have **no opinion** on what you should do when the database itself is struggling. They surface the `SQLException` and leave the resilience logic entirely to you.

### What JDBC Armour Adds

JDBC Armour answers three questions that plain JDBC and connection pools leave unanswered:

**1. Is this failure worth retrying?**
Not all `SQLException`s are equal. A duplicate key violation (MySQL error 1062) should never be retried — it will always fail. A deadlock (MySQL error 1213) or a brief connection limit spike (MySQL error 1040) is temporary; retrying after a short delay will likely succeed. JDBC Armour classifies every exception using vendor-specific error codes so the retry logic only activates when there is a genuine chance of success.

**2. How long should I wait before retrying?**
Retrying immediately puts the same load on a struggling database. JDBC Armour supports fixed, exponential, and linear backoff with optional random jitter. Exponential backoff with jitter is the default because it spreads retry attempts across time and prevents multiple threads from all retrying at the same instant.

**3. When should I stop trying entirely?**
If the database has been failing continuously, individual retries waste time and keep threads blocked. The circuit breaker tracks the failure rate and, once a threshold is crossed, immediately rejects further connection attempts until a recovery window has passed. This gives the database breathing room to recover.

---

## 3. Core Concepts

### FailureType

Every `SQLException` is classified into one of four types:

```
TRANSIENT            — Temporary condition; retrying may succeed
FATAL                — Permanent error; retrying will not help
CONSTRAINT_VIOLATION — Data integrity violation; retrying will not help
UNKNOWN              — Unrecognized error code; treated as non-retryable by default
```

### Circuit States

The circuit breaker maintains a state machine:

```
    CLOSED  ──(failures >= threshold)──>  OPEN
      ^                                     |
      |                               (recovery window passes)
      |                                     v
      └──(successes >= threshold)──  HALF_OPEN
                                           |
                                    (fails again)
                                           |
                                           v
                                          OPEN
```

- **CLOSED** — Normal operation. All requests go through.
- **OPEN** — Database is considered down. All requests are rejected immediately with `CircuitOpenException`.
- **HALF_OPEN** — Recovery is being probed. One request is allowed through. If it succeeds the circuit closes. If it fails the circuit returns to OPEN and resets the recovery timer.

### Backoff Strategies

| Strategy | Behavior | Use Case |
|---|---|---|
| `EXPONENTIAL` | Delay doubles each attempt | Default; handles most production scenarios |
| `FIXED` | Same delay every attempt | Predictable retry interval |
| `LINEAR` | Delay grows by one increment each attempt | Gradual back-off |
| `CUSTOM` | Reserved for custom implementations | Specialized requirements |

---

## 4. Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.x
- The JDBC driver for your database on the classpath (MySQL Connector/J, PostgreSQL JDBC Driver, Oracle JDBC, etc.)

### Step 1 — Create Your Underlying DataSource

JDBC Armour wraps the vendor-native `DataSource` that ships with your database driver. You do not need any additional pool library.

**MySQL**
```java
import com.mysql.cj.jdbc.MysqlDataSource;

MysqlDataSource ds = new MysqlDataSource();
ds.setURL("jdbc:mysql://localhost:3306/mydb");
ds.setUser("appuser");
ds.setPassword("secret");
```

**PostgreSQL**
```java
import org.postgresql.ds.PGSimpleDataSource;

PGSimpleDataSource ds = new PGSimpleDataSource();
ds.setServerNames(new String[]{"localhost"});
ds.setPortNumbers(new int[]{5432});
ds.setDatabaseName("mydb");
ds.setUser("appuser");
ds.setPassword("secret");
```

**Oracle**
```java
import oracle.jdbc.pool.OracleDataSource;

OracleDataSource ds = new OracleDataSource();
ds.setURL("jdbc:oracle:thin:@localhost:1521:XE");
ds.setUser("appuser");
ds.setPassword("secret");
```

### Step 2 — Choose an Exception Classifier

JDBC Armour provides vendor-specific classifiers for MySQL, PostgreSQL, and Oracle. **Always prefer the vendor-specific classifier** for your database — each one knows that database's actual error codes, which gives far more accurate classification than the generic default.

```java
// For MySQL — uses MySQL vendor error codes (recommended for MySQL)
ExceptionClassifier classifier = new MySQLExceptionClassifier();

// For PostgreSQL — uses PostgreSQL SQLSTATE codes
ExceptionClassifier classifier = new PostgresExceptionClassifier();

// For Oracle — uses Oracle ORA- error codes
ExceptionClassifier classifier = new OracleExceptionClassifier();

// Fallback — SQL-standard SQLSTATE codes only
// Use this only when no vendor-specific classifier exists for your database
ExceptionClassifier classifier = new DefaultExceptionClassifier();
```

The `DefaultExceptionClassifier` recognizes only a handful of standard SQLSTATE codes and will classify most vendor-specific errors as `UNKNOWN`. For any supported database, the vendor classifier is always the better choice.

### Step 3 — Configure the Retry Policy

The `RetryPolicy` uses a builder. Every option has a sensible default so you only need to override what you want to change.

```java
RetryPolicy retryPolicy = RetryPolicy.builder()
    .maxAttempts(3)                            // try up to 3 times total
    .backoffStrategy(BackoffStrategy.EXPONENTIAL)
    .initialDelay(Duration.ofMillis(100))      // wait 100ms before first retry
    .maxDelay(Duration.ofSeconds(5))           // never wait longer than 5s
    .jitter(true)                              // add random spread to delays
    .retryOn(Set.of(FailureType.TRANSIENT))    // only retry transient errors
    .build();

RetryEngine retryEngine = new RetryEngine(retryPolicy);
```

With exponential backoff and the defaults above:
- 1st retry: ~100ms
- 2nd retry: ~200ms
- 3rd retry: ~400ms (plus up to 100ms of random jitter on each)

### Step 4 — Configure the Circuit Breaker

```java
CircuitBreaker circuitBreaker = CircuitBreaker.builder()
    .failureThreshold(5)                      // open after 5 consecutive failures
    .successThreshold(2)                      // close after 2 successes in HALF_OPEN
    .recoveryWindow(Duration.ofSeconds(30))   // wait 30s before probing recovery
    .build();
```

### Step 5 — Create the ResilientDataSource

Combine all four components:

```java
ResilientDataSource resilientDs = new ResilientDataSource(
    ds,              // your vendor-native datasource
    classifier,      // how to classify exceptions
    circuitBreaker,  // how to handle sustained outages
    retryEngine      // how to retry transient failures
);
```

### Step 6 — Acquire Connections

Replace any call to `dataSource.getConnection()` or `DriverManager.getConnection()` with:

```java
Connection conn = resilientDs.acquireConnection();
```

That single line now has retry logic, backoff, jitter, circuit breaking, and intelligent exception classification built in.

### Full Example (MySQL)

```java
import com.jdbcarmour.core.ResilientDataSource;
import com.jdbcarmour.circuitbreaker.CircuitBreaker;
import com.jdbcarmour.classifier.MySQLExceptionClassifier;
import com.jdbcarmour.classifier.FailureType;
import com.jdbcarmour.retry.*;
import com.mysql.cj.jdbc.MysqlDataSource;

import java.sql.Connection;
import java.time.Duration;
import java.util.Set;

public class MyApplication {

    public static void main(String[] args) throws Exception {

        // 1. Vendor datasource — no pool library needed
        MysqlDataSource ds = new MysqlDataSource();
        ds.setURL("jdbc:mysql://localhost:3306/mydb");
        ds.setUser("appuser");
        ds.setPassword("secret");

        // 2. MySQL-specific classifier — knows MySQL's own error codes
        MySQLExceptionClassifier classifier = new MySQLExceptionClassifier();

        // 3. Retry policy
        RetryPolicy retryPolicy = RetryPolicy.builder()
            .maxAttempts(3)
            .backoffStrategy(BackoffStrategy.EXPONENTIAL)
            .initialDelay(Duration.ofMillis(100))
            .maxDelay(Duration.ofSeconds(5))
            .jitter(true)
            .retryOn(Set.of(FailureType.TRANSIENT))
            .build();

        // 4. Circuit breaker
        CircuitBreaker circuitBreaker = CircuitBreaker.builder()
            .failureThreshold(5)
            .successThreshold(2)
            .recoveryWindow(Duration.ofSeconds(30))
            .build();

        // 5. Resilient datasource
        ResilientDataSource resilientDs = new ResilientDataSource(
            ds,
            classifier,
            circuitBreaker,
            new RetryEngine(retryPolicy)
        );

        // 6. Use it
        try (Connection conn = resilientDs.acquireConnection()) {
            // execute queries normally
        }
    }
}
```

---

## 5. Retry Policy

### Class: `com.jdbcarmour.retry.RetryPolicy`

The retry policy controls when and how retries happen.

### Builder Options

| Method | Type | Default | Description |
|---|---|---|---|
| `maxAttempts(int)` | `int` | `3` | Total number of attempts (including the first try) |
| `initialDelay(Duration)` | `Duration` | `100ms` | Delay before the first retry |
| `maxDelay(Duration)` | `Duration` | `5s` | Upper cap on any single delay |
| `jitter(boolean)` | `boolean` | `true` | Adds up to 100ms of random spread to each delay |
| `backoffStrategy(BackoffStrategy)` | `BackoffStrategy` | `EXPONENTIAL` | How the delay grows across attempts |
| `retryOn(Set<FailureType>)` | `Set<FailureType>` | `{TRANSIENT}` | Which failure types trigger a retry |

### Delay Calculation

For a given attempt number `n` (starting at 1):

- **FIXED**: `delay = initialDelay`
- **EXPONENTIAL**: `delay = initialDelay * 2^(n-1)`
- The result is capped at `maxDelay`
- If jitter is enabled, a random value between 0 and 100ms is added on top

### Controlling Which Failures Are Retried

You can extend the retry set if your use case requires it:

```java
// Also retry errors that could not be classified
RetryPolicy lenientPolicy = RetryPolicy.builder()
    .retryOn(Set.of(FailureType.TRANSIENT, FailureType.UNKNOWN))
    .build();
```

You should **never** add `FATAL` or `CONSTRAINT_VIOLATION` to this set. Fatal errors indicate a configuration or server problem that will not self-resolve. Constraint violations indicate a data problem in the query itself — retrying the same query will always produce the same violation.

---

## 6. Circuit Breaker

### Class: `com.jdbcarmour.circuitbreaker.CircuitBreaker`

The circuit breaker prevents your application from repeatedly hitting a database that is clearly not going to respond. When a threshold of consecutive failures is reached, the circuit opens and all further requests are rejected immediately without attempting a connection.

### Builder Options

| Method | Type | Required | Description |
|---|---|---|---|
| `failureThreshold(int)` | `int` | Yes | Consecutive failures needed to trip the circuit OPEN |
| `successThreshold(int)` | `int` | Yes | Consecutive successes in HALF_OPEN to close the circuit |
| `recoveryWindow(Duration)` | `Duration` | Yes | Time to wait in OPEN before probing recovery |

### Tuning Guidance

**`failureThreshold`** — Lower values trip faster (more protective, higher false positive risk). Higher values tolerate brief blips.

```java
// Aggressive: trip after 3 failures
.failureThreshold(3)

// Conservative: tolerate up to 10 failures before tripping
.failureThreshold(10)
```

**`recoveryWindow`** — How long to wait before probing the database after the circuit opens. Should be longer than a typical restart cycle in your environment.

```java
// Short: suits high-availability setups with fast failover
.recoveryWindow(Duration.ofSeconds(10))

// Longer: suits databases that take more time to restart
.recoveryWindow(Duration.ofMinutes(2))
```

**`successThreshold`** — How many successful probes before fully restoring traffic. A value of 1 closes immediately. A value of 2–3 provides more confidence before reopening the floodgates.

### Checking Circuit State

```java
CircuitState state = circuitBreaker.getState(); // CLOSED, OPEN, or HALF_OPEN
```

---

## 7. Exception Classifiers

### Interface: `com.jdbcarmour.classifier.ExceptionClassifier`

```java
public interface ExceptionClassifier {
    FailureType classify(SQLException e);
}
```

Every built-in classifier maps database-specific error codes or SQL state codes to one of the four `FailureType` values. The vendor-specific classifiers are far more comprehensive than the default because they map dozens of actual database error codes rather than a small set of SQL-standard states.

### Classifier Comparison

| Classifier | Basis | Coverage |
|---|---|---|
| `MySQLExceptionClassifier` | MySQL vendor error codes | ~40+ mapped codes across all three types |
| `PostgresExceptionClassifier` | PostgreSQL SQLSTATE codes | ~50+ mapped states across all three types |
| `OracleExceptionClassifier` | Oracle ORA- error codes | ~40+ mapped codes across all three types |
| `DefaultExceptionClassifier` | SQL-standard SQLSTATE codes | 3 codes total (minimal coverage) |

### MySQLExceptionClassifier

| FailureType | Example Error Codes | Example Scenarios |
|---|---|---|
| `TRANSIENT` | 1040, 1041, 1205, 1213 | Too many connections, lock wait timeout, deadlock |
| `FATAL` | 1042, 1049, 1064, 1146 | Host unknown, unknown database, syntax error, table not found |
| `CONSTRAINT_VIOLATION` | 1048, 1062, 1216, 1452 | NOT NULL violation, duplicate entry, foreign key failure |

### PostgresExceptionClassifier

| FailureType | Example SQLSTATE Codes | Example Scenarios |
|---|---|---|
| `TRANSIENT` | 08000–08007, 40001, 53x00 | Connection failure, serialization failure, insufficient resources |
| `FATAL` | 28000, 42000–42Pxx, 3D000 | Invalid auth, syntax errors, invalid catalog name |
| `CONSTRAINT_VIOLATION` | 23500–23505, 23514, 23P01 | Unique violation, foreign key violation, check constraint |

### OracleExceptionClassifier

| FailureType | Example ORA- Codes | Example Scenarios |
|---|---|---|
| `TRANSIENT` | 28, 51, 60, 3113, 3114 | Session killed, timeout, deadlock, connection lost |
| `FATAL` | 900, 904, 942, 1017, 6502 | SQL command error, invalid column, table not found, wrong password |
| `CONSTRAINT_VIOLATION` | 1, 1400, 2290–2293, 2298 | Unique constraint, NOT NULL, check constraint, foreign key |

### DefaultExceptionClassifier

Use this only when no vendor-specific classifier exists for your database. It recognizes only three SQL-standard SQLSTATE codes and will classify everything else as `UNKNOWN`.

| FailureType | SQLSTATE | Scenario |
|---|---|---|
| `TRANSIENT` | 08001, 08006 | Connection failed, connection failure during transaction |
| `FATAL` | 28000 | Invalid authorization specification |

### Writing a Custom Classifier

```java
public class MariaDBExceptionClassifier implements ExceptionClassifier {

    @Override
    public FailureType classify(SQLException e) {
        int code = e.getErrorCode();

        if (code == 1040 || code == 1213) {
            return FailureType.TRANSIENT;
        }
        if (code == 1062) {
            return FailureType.CONSTRAINT_VIOLATION;
        }
        if (code == 1049 || code == 1064) {
            return FailureType.FATAL;
        }
        return FailureType.UNKNOWN;
    }
}
```

---

## 8. Putting It All Together

### How `ResilientDataSource.acquireConnection()` Works

When you call `acquireConnection()`, the following sequence runs:

```
acquireConnection() called
         │
         ├── Is circuit OPEN?
         │       Yes → throw CircuitOpenException immediately
         │
         ├── Attempt connection from underlying DataSource
         │
         ├── Connection succeeds?
         │       Yes → circuitBreaker.recordSuccess()
         │             return Connection
         │
         └── SQLException thrown
                 │
                 ├── Classify the exception
                 │
                 ├── TRANSIENT?
                 │       → circuitBreaker.recordFailure()
                 │         retryEngine retries with backoff
                 │         (up to maxAttempts)
                 │
                 ├── FATAL?
                 │       → circuitBreaker.recordFailure()
                 │         throw immediately, no retry
                 │
                 ├── CONSTRAINT_VIOLATION?
                 │       → throw immediately, no retry
                 │         (does NOT count as a circuit failure)
                 │
                 └── UNKNOWN?
                         → circuitBreaker.recordFailure()
                           throw immediately, no retry
```

Key design decisions:
- Constraint violations do **not** count as circuit failures. A bad INSERT does not indicate a database outage.
- Fatal errors **do** count as circuit failures because they often indicate the server is unreachable or misconfigured at the infrastructure level.
- Only transient failures are retried because those are the only ones where waiting and trying again makes sense.

### Using With a Repository Pattern

```java
public class UserRepository {

    private final ResilientDataSource dataSource;

    public UserRepository(ResilientDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<User> findById(long id) throws SQLException {
        try (Connection conn = dataSource.acquireConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, name, email FROM users WHERE id = ?")) {

            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new User(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("email")
                    ));
                }
                return Optional.empty();
            }
        }
    }
}
```

---

## 9. How It Compares to Plain JDBC and Connection Pools

### Plain `DriverManager.getConnection()`

```java
// No resilience — any failure surfaces immediately as a SQLException
Connection conn = DriverManager.getConnection(url, user, password);
```

| Concern | Plain JDBC | JDBC Armour |
|---|---|---|
| Retry on transient failure | No | Yes, configurable |
| Intelligent error classification | No | Yes, vendor-specific |
| Circuit breaking | No | Yes |
| Backoff between retries | No | Yes, with jitter |
| Prevents thundering herd | No | Yes |
| Fast-fail on non-retryable errors | No | Yes |

### Standard Connection Pool Alone

Libraries like HikariCP are excellent at managing a pool of live connections, reusing them efficiently, and enforcing timeouts. They are **not** designed to handle the scenario where the database itself becomes temporarily unavailable or overloaded.

| Concern | Pool Alone | JDBC Armour |
|---|---|---|
| Retry transient failures | No — surfaces exception immediately | Yes |
| Classify error types | No | Yes, vendor-specific |
| Protect application during outage | No | Yes, via circuit breaker |
| Configurable backoff | No | Yes |
| Stop retrying constraint violations | No | Yes — fails fast |
| Additional library required | Yes | No — wraps vendor DataSource directly |

JDBC Armour does not compete with connection pools. If you already use a pool, you can pass its `DataSource` into `ResilientDataSource` just as you would a vendor-native one. The resilience layer operates above whatever datasource you give it.

### When JDBC Armour Makes the Biggest Difference

**High-traffic services** — Without a circuit breaker, threads pile up waiting for a database that is down. JDBC Armour fails fast and frees those threads immediately when the circuit is open.

**Cloud databases with connection limits** — Aurora, Cloud SQL, and similar services have hard connection limits. A brief traffic spike can trigger `too many connections` errors (MySQL 1040) that resolve on their own in seconds. JDBC Armour retries these transparently with no code changes needed in your application.

**Microservices with rolling deployments** — Database restarts or failovers during deployments produce transient connection errors. JDBC Armour retries automatically, preventing request failures during routine operations.

**Applications with concurrent writes** — If one operation hits a deadlock, JDBC Armour retries just that operation rather than surfacing the error to the caller.

---

## 10. Exception Reference

| Exception Class | Package | When Thrown |
|---|---|---|
| `CircuitOpenException` | `com.jdbcarmour.exception` | Circuit breaker is OPEN — request rejected without attempting a connection |
| `ConnectionExhaustedException` | `com.jdbcarmour.exception` | All retry attempts were exhausted without a successful connection |
| `ArmorConfigurationException` | `com.jdbcarmour.exception` | The library was configured incorrectly |

### Handling JDBC Armour Exceptions

```java
try {
    Connection conn = resilientDs.acquireConnection();
    // use connection
} catch (CircuitOpenException e) {
    // Database is known to be down — return a degraded response
    // or fail fast to avoid blocking the calling thread
    log.warn("Database circuit is open, returning cached result");
    return getCachedResult();
} catch (ConnectionExhaustedException e) {
    // All retries failed — surface as a service error
    log.error("Could not connect to database after all retries", e);
    throw new ServiceUnavailableException("Database unavailable");
} catch (SQLException e) {
    // Fatal or constraint violation — handle normally
    throw e;
}
```

---

## 11. Architecture Overview

```
Your Application Code
         │
         │  acquireConnection()
         ▼
  ResilientDataSource
  ┌───────────────────────────────────────────────────────┐
  │                                                       │
  │   CircuitBreaker ──── checks state before each call   │
  │                                                       │
  │   RetryEngine ──────── loops with backoff on TRANSIENT│
  │                                                       │
  │   ExceptionClassifier ─ maps SQLException → FailureType│
  │                                                       │
  └───────────────────────────────────────────────────────┘
         │
         │  getConnection()
         ▼
  Vendor DataSource
  (MysqlDataSource / PGSimpleDataSource / OracleDataSource / any DataSource)
         │
         ▼
  Database (MySQL / PostgreSQL / Oracle)
```

JDBC Armour sits directly above the vendor datasource. No additional pool library is required.

### Package Map

```
com.jdbcarmour
├── core
│   └── ResilientDataSource          ← entry point
├── retry
│   ├── RetryPolicy                  ← configure retry behavior
│   ├── RetryEngine                  ← executes retry loop
│   └── BackoffStrategy              ← FIXED, EXPONENTIAL, LINEAR, CUSTOM
├── circuitbreaker
│   ├── CircuitBreaker               ← state machine
│   └── CircuitState                 ← CLOSED, OPEN, HALF_OPEN
├── classifier
│   ├── ExceptionClassifier          ← interface (implement for custom databases)
│   ├── FailureType                  ← TRANSIENT, FATAL, CONSTRAINT_VIOLATION, UNKNOWN
│   ├── MySQLExceptionClassifier     ← use with MysqlDataSource
│   ├── PostgresExceptionClassifier  ← use with PGSimpleDataSource
│   ├── OracleExceptionClassifier    ← use with OracleDataSource
│   └── DefaultExceptionClassifier   ← fallback for unsupported databases
└── exception
    ├── CircuitOpenException
    ├── ConnectionExhaustedException
    └── ArmorConfigurationException
```
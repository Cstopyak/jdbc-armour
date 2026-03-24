# jdbc-armour Style Guide

These rules are enforced automatically during `mvn clean install` via Checkstyle (`checkstyle.xml`).
Violations will fail the build.

---

## Naming Conventions

| Element | Convention | Example |
|---|---|---|
| Classes | `UpperCamelCase` | `ResilientDataSource` |
| Interfaces | `UpperCamelCase` | `ExceptionClassifier` |
| Enums | `UpperCamelCase` | `CircuitState` |
| Enum constants | `UPPER_SNAKE_CASE` | `HALF_OPEN` |
| Methods | `lowerCamelCase` | `getConnection()` |
| Parameters | `lowerCamelCase` | `maxAttempts` |
| Local variables | `lowerCamelCase` | `retryCount` |
| Instance fields | `lowerCamelCase` | `failureThreshold` |
| Static non-final fields | `lowerCamelCase` | `defaultTimeout` |
| Static final constants | `UPPER_SNAKE_CASE` | `MAX_RETRY_COUNT` |
| Packages | `lowercase.dotted` | `com.jdbcarmour.retry` |
| Type parameters | Single uppercase letter | `T`, `E`, `K` |

---

## Imports

- No wildcard imports (`import java.util.*` is banned)
- No duplicate imports
- No unused imports

---

## Code Structure

- All control structures (`if`, `for`, `while`, etc.) must use braces, even for single-line bodies
- Opening brace on the same line as the statement
- One top-level class per file — filename must match the class name
- Empty `catch`/`finally` blocks must contain a comment explaining why

---

## Modifiers

Order: `public protected private abstract static final transient volatile synchronized native strictfp`

Redundant modifiers are banned (e.g. `public` on interface methods).

---

## Logging

`System.out` and `System.err` are banned. Use SLF4J:

```java
private static final Logger log = LoggerFactory.getLogger(MyClass.class);
```

---

## Formatting

- No tab characters — use spaces
- No trailing whitespace

---

## Misc

- `equals()` overrides must also override `hashCode()`
- Simplify boolean expressions — avoid `if (x == true)` or `return x == true ? true : false`
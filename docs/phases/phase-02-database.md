# Phase 2 — Database Connection

## What we built

The application now talks to MySQL. No tables of our own yet — this phase is
only about making the connection work and making the settings safe.

## Why a whole phase for a connection string

Because almost every "Spring Boot won't start" problem a beginner hits is a
database problem: wrong password, wrong port, missing database, timezone errors.
Getting this right on its own means that when Phase 3 breaks, you know it is your
code and not your connection.

## What we added to `pom.xml`

| Dependency | What it does |
|---|---|
| `spring-boot-starter-data-jpa` | Hibernate + Spring Data. Lets us describe tables as Java classes. |
| `mysql-connector-j` | The MySQL driver. Scope is `runtime` because our code never imports it — only the JVM needs it while running. |
| `spring-boot-starter-validation` | `@NotBlank`, `@Size` and friends, used from Phase 3 onwards. |
| `h2` (test scope) | An in-memory database used only by tests. Explained below. |

## The configuration, line by line

```properties
spring.datasource.url=jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:campusfix}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:root}
```

**`${DB_PASSWORD:root}` means**: use the environment variable `DB_PASSWORD`; if
it is not set, fall back to `root`.

Why do it this way? A password written directly in the file gets committed to Git
and ends up public. With this pattern the file contains no real secret — a server
sets `DB_PASSWORD` in its environment and the same code works there untouched.
The fallback only exists so a fresh clone runs on a normal local MySQL install.

To use a different password locally, do not edit the file:

```bash
export DB_PASSWORD=yourpassword     # Git Bash / Linux / Mac
set DB_PASSWORD=yourpassword        # Windows CMD
```

---

```properties
spring.jpa.hibernate.ddl-auto=update
```

Hibernate reads the entity classes and creates or alters the matching tables when
the application starts. It **adds** columns and tables; it never drops them, so
existing rows survive.

This is a development convenience. It is not how you manage a production
database, because "update" cannot rename a column, cannot fill in a new NOT NULL
column, and gives you no history of what changed. The proper answer is a
migration tool such as Flyway, which is recorded as a planned improvement rather
than something we add before it is needed.

---

```properties
spring.jpa.open-in-view=false
```

By default, Spring keeps the database session open until the HTTP response has
been written. That sounds helpful — lazy relationships still load in the
controller — but it means a request can quietly fire extra queries after the
service method has finished, and it holds a database connection for longer than
necessary.

Turning it off means a lazy-loading mistake fails immediately, loudly, in the
service layer, where you can fix it properly. Spring even logs a warning
suggesting you set this. We set it.

---

```properties
spring.jpa.properties.hibernate.jdbc.time_zone=UTC
```

MySQL's `DATETIME` type stores no timezone at all. Without this setting Hibernate
writes timestamps using whatever timezone the JVM happens to be in, so the same
database opened from a machine in another region reports different times.

Forcing UTC everywhere makes stored times unambiguous. SLA deadlines in Phase 9
are pure arithmetic on these timestamps, so this has to be correct now.

---

```properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

Prints the SQL Hibernate generates, formatted. Keep it on during development —
seeing the real queries is how you notice a screen quietly running fifty of them.

## Why tests use H2 instead of MySQL

If tests needed MySQL, then:

- nobody could run the test suite without installing and starting MySQL
- tests would leave rows behind and start failing on the second run
- a CI server would need a database just to compile

So `src/test/resources/application-test.properties` points the same code at H2,
an in-memory database that is created empty at the start of each run and vanishes
at the end. `MODE=MySQL` makes H2 accept MySQL-flavoured SQL.

This file is named `application-**test**.properties`, and the naming is
deliberate. A file called `application.properties` inside `src/test/resources`
does not merge with the main one — it **replaces** it, and every unrelated
setting would silently disappear. (This exact mistake broke the build once during
this phase: `spring.application.name` vanished and the health endpoint started
returning the literal text `${spring.application.name}`.)

The profile is switched on for the whole test run from `pom.xml`:

```xml
<plugin>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <systemPropertyVariables>
            <spring.profiles.active>test</spring.profiles.active>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

So no test class needs to remember `@ActiveProfiles("test")`.

**The trade-off:** H2 is not MySQL. A query using MySQL-only syntax could pass in
tests and fail in production. We accept that for now because the queries are
ordinary JPQL. If that changes, the answer is Testcontainers — a real MySQL in
Docker — which belongs with the Docker phase, not here.

## Setup you have to do once

```sql
CREATE DATABASE campusfix
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

`utf8mb4` is the character set that actually supports every Unicode character,
including emoji, which students will absolutely paste into a complaint
description.

## How to check it worked

Start the application:

```bash
./mvnw spring-boot:run
```

The log should contain `HikariPool-1 - Start completed.` and no stack trace.
Hikari is the connection pool Spring Boot uses by default: instead of opening a
new database connection per request, it keeps a small set open and hands them out.

Then confirm from MySQL:

```sql
USE campusfix;
SHOW TABLES;
```

## Common errors and what they mean

| Message | Cause |
|---|---|
| `Access denied for user 'root'@'localhost'` | Wrong password. Set `DB_PASSWORD`. |
| `Unknown database 'campusfix'` | The `CREATE DATABASE` above was never run. |
| `Communications link failure` | MySQL service is not running. |
| `Failed to configure a DataSource` | JPA is on the classpath but no `spring.datasource.url` is set. |

# Phase 1 — Project Setup

## What we built

An empty but working Spring Boot application, plus one test endpoint
`GET /api/hello` that proves the application is alive.

No database, no login, no screens yet. Only the skeleton.

## Why this is a separate phase

If the build environment is broken *and* the code is unfinished, every error has
two possible causes and you waste hours guessing which one it is. So we make the
build boring and reliable first, then add features on top of something we trust.

## What the machine already had

| Tool | Version found |
|---|---|
| JDK | 22.0.1 |
| Maven | not installed on PATH, only IntelliJ's bundled copy |
| MySQL | 8.0.41, service running |

## Decisions and the reason for each

### Spring Boot 3.5.6

Current stable release. It needs Java 17 or newer, which we have.

### We compile for Java 21 even though the JDK is 22

`pom.xml` contains:

```xml
<java.version>21</java.version>
```

Maven turns this into `--release 21`, which means "produce class files that a
Java 21 runtime can load". The newer JDK is only the tool doing the compiling.

Why bother? The project documentation says Java 21, servers commonly run Java 21,
and this guarantees we never accidentally use a Java 22-only feature that would
crash there.

**In IntelliJ:** set Project SDK to 22 and language level to 21. That is the
"IntelliJ vs Maven mismatch" the early progress log was worried about.

### We added the Maven Wrapper

`mvn` is not on this machine's PATH. Instead of asking every developer to install
Maven, the project now contains:

```
mvnw            (Linux/Mac script)
mvnw.cmd        (Windows script)
.mvn/wrapper/   (which Maven version to download)
```

Run `./mvnw test` and the script downloads the exact Maven version the project
expects, then uses it. Anyone who clones the repository gets an identical build.
This is standard practice in real teams and costs nothing.

### We did not use Lombok

Lombok generates getters, constructors and `toString` from annotations. It saves
typing but hides code.

Two reasons we skipped it:

1. In an interview you will be asked what `@Data` actually generates. Code you
   can read is code you can defend.
2. Java `record` already removes most of the boilerplate for DTOs, which is where
   the pain actually was.

### We only added the dependencies this phase needs

`pom.xml` starts with three things: web, devtools, test.

Not JPA, not MySQL, not security. This matters: if you add
`spring-boot-starter-data-jpa` before configuring a database, **Spring Boot
refuses to start**, because it sees JPA on the classpath and looks for a
datasource that does not exist. New dependencies get added in the phase that
actually uses them.

### `spring.mvc.problemdetails.enabled=true`

Without this, a request to an unknown URL returns Spring's default HTML error
page. A frontend calling `fetch()` then tries to parse HTML as JSON and gets a
confusing crash. With it on, unknown paths return JSON like every other response.

## How the code works

```
src/main/java/com/campusfix/
├── CampusFixApplication.java          starts everything
└── common/health/
    ├── HealthController.java          handles GET /api/hello
    └── HealthResponse.java            the JSON that comes back
```

**`CampusFixApplication`** carries `@SpringBootApplication`. That single
annotation tells Spring: scan this package and everything under it for classes to
manage, and auto-configure whatever it finds on the classpath. It found
spring-boot-starter-web, so it started an embedded Tomcat server on port 8080.

**`HealthController`**

```java
@RestController
@RequestMapping("/api")
public class HealthController {

    private final String applicationName;

    public HealthController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping("/hello")
    public HealthResponse hello() {
        return new HealthResponse(applicationName, "UP", Instant.now());
    }
}
```

- `@RestController` = this class handles HTTP and returns data, not HTML pages.
  Whatever the method returns is converted to JSON automatically.
- `@RequestMapping("/api")` + `@GetMapping("/hello")` = the full path `/api/hello`.
- The value comes in through the **constructor**, not through a field with
  `@Autowired`. The field can be `final`, so the object can never exist in a
  half-built state, and you can create one in a test with plain `new`. Use
  constructor injection everywhere in this project.

**`HealthResponse`** is a `record`:

```java
public record HealthResponse(String application, String status, Instant timestamp) { }
```

One line gives you a constructor, accessors, `equals`, `hashCode` and `toString`.
Jackson converts it to JSON with no extra configuration.

**Why `Instant` and not `LocalDateTime`?** `Instant` is an exact moment in UTC.
`LocalDateTime` has no timezone attached, so "2 PM" on a server in India and a
server in London are different moments but look identical in the database. SLA
deadlines in Phase 9 depend on getting this right, so the rule is set now: the
whole project stores and sends `Instant`.

## Tests

| Test | What it proves |
|---|---|
| `CampusFixApplicationTests.contextLoads` | Every bean can be created and wired. Catches broken configuration before you ever open a browser. |
| `HealthControllerTest` | `/api/hello` really returns 200 and the expected JSON fields. |

`HealthControllerTest` uses `@WebMvcTest`, which loads only the web layer instead
of the whole application, so it finishes in well under a second.

## How to run it

```bash
./mvnw spring-boot:run
```

Then open http://localhost:8080/api/hello

Expected:

```json
{
  "application": "CampusFix",
  "status": "UP",
  "timestamp": "2026-08-22T17:05:52.115755100Z"
}
```

Run the tests:

```bash
./mvnw test
```

## Note on temporary code

`HealthController` is scaffolding, not a feature. It gets deleted once real
endpoints exist and Spring Boot Actuator provides a proper health check. It is
recorded here so nobody later wonders why an endpoint called "hello" is in a
college maintenance system.

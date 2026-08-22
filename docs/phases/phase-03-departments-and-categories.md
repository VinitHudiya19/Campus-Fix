# Phase 3 — Departments and Categories

## What we built

The first real feature: the admin-managed lists that everything else depends on.

- **Department** — a team that fixes things. IT Support, Electrical, Facilities.
- **Category** — the kind of problem a student reports. Wi-Fi, Projector, Fan.

Each category belongs to exactly one department. That single link is what makes
the whole product work: **the student picks a problem type, and the system works
out who is responsible.** Students should never have to know that a broken
projector is AV Support's job.

Also built in this phase: the shared error format and the exception handling that
every future feature reuses.

## Why this comes before service requests

A service request needs a category. A category needs a department. Building them
in the other order would mean writing throwaway code. Starting here also lets us
get the layering right on something small, before the complicated features.

## The business rules

These are decisions, not obvious facts. Each one is enforced in the service layer
and covered by a test.

| Rule | Reason |
|---|---|
| Department names are unique, ignoring case | "IT Support" and "it support" are the same team. Allowing both splits reporting and confuses assignment. |
| Category names are unique **within a department**, not globally | "Wiring" can reasonably exist under both Electrical and Hostel Maintenance. A global rule would block a legitimate setup. |
| A category must point at an **active** department | Otherwise a request could be filed into a team that no longer exists, and nobody would ever be assigned. |
| Nothing is ever deleted — only deactivated | A service request from last year points at its category. Deleting the row would either break that request or erase its history. Deactivating hides it from new dropdowns while old records stay readable. |
| A department cannot be deactivated while it still has active categories | Deactivating it silently would leave categories pointing at a dead team. The user is asked to deal with the categories first. |
| A category cannot be activated while its department is inactive | Same rule from the other direction. |
| Names are trimmed before saving | `"IT Support "` and `"IT Support"` would otherwise both be stored and both look identical on screen. |

## How the code is organised

Every feature in this project follows the same four layers. Data flows down and
back up:

```
HTTP request
    │
    ▼
Controller     reads the HTTP request, hands over a DTO, returns a DTO
    │          knows nothing about business rules
    ▼
Service        enforces the business rules, owns the transaction
    │          this is where the actual thinking lives
    ▼
Repository     talks to the database, nothing else
    │
    ▼
Entity         a row in a table, expressed as a Java object
```

Files:

```
src/main/java/com/campusfix/
├── common/
│   ├── model/Auditable.java              createdAt / updatedAt for every table
│   └── exception/
│       ├── ApiError.java                 the one error shape the API returns
│       ├── GlobalExceptionHandler.java   turns exceptions into that shape
│       ├── ResourceNotFoundException.java
│       ├── DuplicateResourceException.java
│       └── BusinessRuleException.java
├── department/
│   ├── Department.java                   entity
│   ├── DepartmentRepository.java         database access
│   ├── DepartmentService.java            business rules
│   ├── DepartmentController.java         HTTP
│   └── dto/DepartmentRequest.java, DepartmentResponse.java
└── category/
    └── (the same five pieces)
```

Packages are grouped **by feature**, not by layer. Everything about departments
sits in one folder. The alternative — a `controllers` folder, a `services`
folder — means one small change touches four distant directories.

## Why DTOs instead of returning the entity

`Department` is a database object. `DepartmentResponse` is what the API promises.

Keeping them separate means:

- adding an internal column does not accidentally change the public API
- a password or internal note can never leak just because someone added a field
- JSON serialisation never touches a lazy relationship and fires surprise queries

`DepartmentRequest` is separate from `DepartmentResponse` for a related reason:
the client sends a name and description, but must not be able to send an `id` or
`active` flag and have the server trust it.

Both are `record` types, so they are immutable and short.

## Reading the key code

### The entity has no setters

```java
public class Department extends Auditable {
    private String name;
    private boolean active = true;

    protected Department() { }                        // JPA needs this

    public Department(String name, String description) { ... }

    public void rename(String name) { this.name = name; }
    public void deactivate() { this.active = false; }
}
```

Instead of `setActive(false)` there is `deactivate()`. Instead of `setName()`
there is `rename()`. The method name says what is happening in the product, and
there is no way to construct a `Department` without a name.

The empty `protected` constructor exists only because Hibernate has to create the
object before filling it in. It is `protected` so nothing else uses it by
accident.

### `Auditable` gives every table timestamps

```java
@MappedSuperclass
public abstract class Auditable {
    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp  private Instant updatedAt;
}
```

`@MappedSuperclass` means "these columns belong to every entity that extends me",
without `Auditable` becoming a table itself. Hibernate fills both fields in, so
no service ever has to remember to set them.

### The N+1 problem, and the query that avoids it

A category table on screen shows the department name. Written naively, Spring
would run one query for the list of categories, then one more query per row to
fetch each department. Twenty categories become twenty-one queries. This is
called the **N+1 problem** and it is the most common performance bug in JPA
applications.

The relationship is deliberately lazy:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
private Department department;
```

and the query that needs the department asks for it explicitly:

```java
@Query("""
        select c from Category c
        join fetch c.department d
        where (:departmentId is null or d.id = :departmentId)
          and (:activeOnly = false or c.active = true)
        order by d.name asc, c.name asc
        """)
List<Category> search(@Param("departmentId") Long departmentId,
                      @Param("activeOnly") boolean activeOnly);
```

`join fetch` loads categories and their departments in **one** query.

The two `:param is null or ...` conditions let one query serve all four filter
combinations, instead of writing four repository methods.

### The service owns the rules and the transaction

```java
@Transactional
public DepartmentResponse update(Long id, DepartmentRequest request) {
    Department department = getOrThrow(id);
    String name = request.name().trim();

    // A department may keep its own name; only a clash with a different row is a conflict.
    departmentRepository.findByNameIgnoreCase(name)
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(existing -> { throw new DuplicateResourceException(...); });

    department.rename(name);
    return DepartmentResponse.from(department);
}
```

Two things worth noticing:

**There is no `save()` call.** Inside a `@Transactional` method, an entity loaded
from the database is *managed*: Hibernate compares it against its original state
when the transaction commits and writes an UPDATE automatically. This is called
dirty checking. `save()` is only needed for brand-new objects.

**Reads are marked `@Transactional(readOnly = true)`.** This tells Hibernate it
does not need to track changes for those objects, which saves memory and work,
and it documents that the method cannot modify anything.

### The controller is deliberately boring

```java
@PostMapping
public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody DepartmentRequest request) {
    DepartmentResponse created = departmentService.create(request);
    return ResponseEntity.created(URI.create("/api/departments/" + created.id())).body(created);
}
```

Four lines, no rules, no `if`. `@Valid` triggers the annotations on the DTO; if
anything fails, the method never runs. `ResponseEntity.created(...)` returns
**201 Created** with a `Location` header pointing at the new resource, which is
what REST expects for a successful POST.

## Errors: one shape for the whole API

Every failure — a validation error, a missing row, a broken rule, an unexpected
crash — comes back looking the same:

```json
{
  "timestamp": "2026-08-22T17:26:53.331481800Z",
  "status": 400,
  "message": "Validation failed",
  "path": "/api/departments",
  "fieldErrors": { "name": "Department name is required" }
}
```

`fieldErrors` only appears on validation failures. The frontend can write **one**
error handler instead of a different one per endpoint.

### Which status code, and why

| Situation | Status | Reasoning |
|---|---|---|
| Field failed validation | 400 Bad Request | The request itself is malformed. |
| Department id does not exist | 404 Not Found | The thing being addressed is not there. |
| Name already taken | 409 Conflict | The request is valid, but it clashes with existing data. |
| Department still has categories | 422 Unprocessable Entity | Understood perfectly, but a domain rule forbids it. |
| Anything unexpected | 500 Internal Server Error | Real cause goes to the log, not to the user. |

409 versus 422 is a genuine judgement call. The line drawn here: 409 means "this
already exists", 422 means "the state of the system does not allow this".

### Why the handler extends `ResponseEntityExceptionHandler`

This was found the hard way. With a plain `@RestControllerAdvice`, validation
errors were still being answered by Spring's own built-in advice, so the API
returned two different error shapes depending on which failure occurred:

```json
{"type":"about:blank","title":"Bad Request","status":400,"detail":"Invalid request content."}
```

Extending `ResponseEntityExceptionHandler` and overriding `handleExceptionInternal`
routes Spring's own failures — unknown URL, wrong HTTP method, unreadable JSON —
through our format as well. Now every error looks the same.

The catch-all is worth reading:

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiError> handleUnexpected(Exception ex, WebRequest request) {
    log.error("Unhandled exception on {}", pathOf(request), ex);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.", request);
}
```

The developer gets the full stack trace in the log. The user gets a plain
sentence. A raw exception message can expose table names, SQL, or file paths, and
that is a genuine security problem, not just untidiness.

## The API

### Departments

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/departments?activeOnly=false` | List |
| GET | `/api/departments/{id}` | One department |
| POST | `/api/departments` | Create → 201 |
| PUT | `/api/departments/{id}` | Update |
| DELETE | `/api/departments/{id}` | Deactivate → 204 |
| POST | `/api/departments/{id}/activate` | Reactivate → 204 |

`DELETE` deactivates rather than deletes. The verb is kept because that is what a
client means by it, and the behaviour is documented here and in the API contract.

### Categories

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/categories?departmentId=1&activeOnly=true` | List, optionally filtered |
| GET | `/api/categories/{id}` | One category |
| POST | `/api/categories` | Create → 201 |
| PUT | `/api/categories/{id}` | Update, including moving it to another department |
| DELETE | `/api/categories/{id}` | Deactivate → 204 |
| POST | `/api/categories/{id}/activate` | Reactivate → 204 |

The student-facing dropdown will call `GET /api/categories?activeOnly=true`.

## Tables created

```sql
departments(id, name UNIQUE, description, active, created_at, updated_at)

categories(id, name, description, department_id FK, active, created_at, updated_at,
           UNIQUE KEY uk_category_name_department (name, department_id))
```

The unique constraint lives in the database as well as in the service. The
service check produces a friendly message; the database constraint is what
actually guarantees correctness if two requests arrive at the same instant.
Application checks alone always have that race.

## Tests

14 tests, all on the service layer with mocked repositories, so they run in
milliseconds and each one fails for exactly one reason.

`DepartmentServiceTest`
- name and description are trimmed on create
- duplicate name rejected regardless of case
- a department may keep its own name when updated
- a name owned by another department is rejected
- deactivation blocked while active categories exist
- deactivation succeeds when nothing depends on it
- missing id reports not found

`CategoryServiceTest`
- create links the category to its department
- unknown department rejected
- inactive department rejected
- duplicate name in the same department rejected
- the same name **is** allowed under a different department
- deactivate keeps the row (never calls `delete`)
- activate blocked while the department is inactive

One detail: entity ids are normally assigned by the database, and there is no
database in a unit test, so the id is set directly with
`ReflectionTestUtils.setField(...)` to represent a row that already exists.

## How to try it yourself

Start the app:

```bash
./mvnw spring-boot:run
```

Create a department:

```bash
curl -X POST http://localhost:8080/api/departments -H "Content-Type: application/json" -d "{\"name\":\"IT Support\",\"description\":\"Network and lab computers\"}"
```

Create a category under it:

```bash
curl -X POST http://localhost:8080/api/categories -H "Content-Type: application/json" -d "{\"name\":\"Wi-Fi\",\"departmentId\":1}"
```

Now try to break it — each of these should fail with a clear message:

```bash
curl -X POST http://localhost:8080/api/departments -H "Content-Type: application/json" -d "{\"name\":\"it support\"}"
```
→ 409, duplicate name

```bash
curl -X DELETE http://localhost:8080/api/departments/1
```
→ 422, the department still has an active category

```bash
curl -X POST http://localhost:8080/api/departments -H "Content-Type: application/json" -d "{\"name\":\"\"}"
```
→ 400 with `fieldErrors.name`

## What is deliberately not here yet

- **No authentication.** Anyone can call these endpoints. Security is Phase 5, and
  adding it now would mean writing every controller test twice.
- **No pagination.** A college has perhaps thirty categories; returning them all
  is correct today. Service requests will run into thousands and will be
  paginated from the start, in Phase 11.
- **No UI.** Phase 10.
- **No seed data.** Added with the demo dataset later, so the tables stay honest
  in the meantime.

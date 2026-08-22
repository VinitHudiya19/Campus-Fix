# Phase 6 — Service Requests

## What we built

The thing the whole product exists for: a student reports a problem, and the
right department can see it.

- **Locations** — where on campus a problem is, stored in parts
- **Service requests** — the report itself, with a readable reference number
- **Visibility rules** — who is allowed to see which requests
- list and detail endpoints, paginated and filterable

This phase creates and reads requests. Nobody is assigned to one yet (Phase 7)
and the status never changes from `OPEN` (Phase 8).

## Locations came first

A request has to record *where*. The database design already had `location_id`
as a foreign key, so this could not be skipped or faked with a text box.

The temptation is one free-text field: "Block A, 2nd floor, room 201". That
arrives twenty different ways — `Block-A`, `block a`, `BLOCK A 201` — and the
moment a department head asks "how many requests came from Block A this month?",
there is no way to answer.

So a location is stored in parts:

```sql
locations(id, campus, building, floor, room, active,
          UNIQUE KEY uk_location_place (campus, building, floor, room))
```

`floor` and `room` are nullable, because some places genuinely have neither — a
main gate, a sports ground. One rule keeps that honest:

```java
/** Blank becomes null so "no floor" is stored one way and never two. */
private String trimOrNull(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
}
```

Without it, the same room could be added twice — once with `floor = null`, once
with `floor = ""` — and the unique constraint would not notice.

`displayName()` puts the pieces back together for dropdowns:
`Main Campus - Library Block - Floor 2 - Reading Hall`.

## The request

```java
new ServiceRequest(title, description, student, category, location, priority, dueAt);
// status is set to OPEN inside the constructor, never passed in
```

There is no way to construct a request in the wrong state. `status` is not a
constructor parameter, so no caller can create one that starts as `CLOSED`.

### What the client is not allowed to send

`CreateRequestRequest` has five fields: title, description, categoryId,
locationId, priority. It does **not** have:

| Missing field | Why |
|---|---|
| `studentId` | Taken from the signed-in user. Otherwise anyone could file a request in someone else's name. |
| `status` | Always `OPEN`. The workflow decides the rest. |
| `requestNumber` | The server generates it. |
| `dueAt` | Derived from the priority — a student must not set their own deadline. |

This is the whole argument for request DTOs rather than accepting the entity: the
fields that are not there cannot be tampered with.

## The business rules

| Rule | Reason |
|---|---|
| Only students report problems | Matches the product spec. Staff act *on* requests; a technician who spots a fault raises it through their own department. |
| A student may pick LOW, MEDIUM or HIGH — never CRITICAL | Everyone believes their own problem is critical. If students could set it, the field would be meaningless within a week. Staff can escalate. |
| The category must exist and be active | A retired category has no department behind it, so nothing would ever be assigned. |
| The location, if given, must exist and be active | Same reasoning. It stays optional because a campus-wide outage has no single place. |
| `dueAt` is calculated once, at creation | Explained below. |
| A student sees only their own requests | Requests contain personal complaints. |
| Staff see their department's requests | A plumber has no business reading IT tickets. |
| A request outside your scope returns **404**, not 403 | Explained below. |

## Reading the key code

### The request number

```java
private String buildRequestNumber(Long id, Instant createdAt) {
    int year = createdAt.atZone(ZoneOffset.UTC).getYear();
    return "CF-%d-%06d".formatted(year, id);
}
```

Produces `CF-2026-000042`. A student quoting "CF-2026-000042" over the phone is
workable; quoting "id 42" is not, and exposing raw ids also tells anyone who
looks how many requests exist.

The number is built **from the database id**, which is the interesting decision:

| Approach | Problem |
|---|---|
| A counter table (`next_number`) | Two requests created at the same instant read the same value. Avoiding that needs row locking, which serialises every insert. |
| Random string | Has to be checked for collisions, and is not readable or ordered. |
| **From the id** (chosen) | The id is already unique, so a collision is impossible. |

The cost is honest: the row must be inserted before the id exists, so creation is
one INSERT followed by one UPDATE.

```java
ServiceRequest saved = requestRepository.saveAndFlush(serviceRequest);
saved.assignRequestNumber(buildRequestNumber(saved.getId(), now));
```

`saveAndFlush` forces the INSERT immediately instead of waiting for the
transaction to commit, so the id is available. The UPDATE happens through dirty
checking. Both are in one transaction, so a request can never exist without a
number.

The entity refuses to be renumbered:

```java
void assignRequestNumber(String requestNumber) {
    if (this.requestNumber != null) {
        throw new IllegalStateException("Request number is already set on request " + id);
    }
    this.requestNumber = requestNumber;
}
```

The method is package-private — only the service beside it can call it, not a
controller.

### Why the deadline is stored, not calculated

```java
@Column(name = "due_at", nullable = false)
private Instant dueAt;
```

`dueAt` could be worked out on every read as `createdAt + priority hours`. It is
stored instead, and that is deliberate.

If the college later changes the MEDIUM target from 48 hours to 24, a calculated
deadline would **retrospectively rewrite history**: requests that met their SLA
last month would suddenly appear to have breached it. Storing the deadline
captures the promise that was made at the time.

It is also the column the "what is overdue?" query filters and sorts on, which is
why it carries an index.

### The clock is injected

```java
public ServiceRequestService(..., Clock clock) { ... }

Instant now = clock.instant();
```

Calling `Instant.now()` inside the service would make time untestable — a test
could only check that the due date is *roughly* 48 hours away. With an injected
`Clock`, the test fixes the time and asserts the exact instant:

```java
Clock.fixed(Instant.parse("2026-08-23T10:00:00Z"), ZoneOffset.UTC)
...
assertThat(response.dueAt()).isEqualTo(Instant.parse("2026-08-25T10:00:00Z"));
```

Phase 9 will lean on this heavily for SLA warnings and escalation.

### Visibility is one small class

```java
public static RequestScope forUser(AuthenticatedUser user) {
    return switch (user.role()) {
        case STUDENT -> new RequestScope(user.id(), null);
        case TECHNICIAN, DEPARTMENT_HEAD -> new RequestScope(null, user.departmentId());
        case ADMIN -> EVERYTHING;
    };
}
```

A role becomes two filter values. Everything else in the system reads those two
values, so the rule exists once.

The `switch` has no `default`. That is on purpose: add a fifth role and the code
**stops compiling** until someone decides what that role may see. A `default`
clause would silently give it some existing scope, which is exactly the kind of
security bug nobody notices.

The same scope answers both questions — the list and a single request:

```java
public boolean permits(ServiceRequest request) { ... }
```

Keeping both in one class means the list view and the detail view can never
disagree about who may see what, which is a classic source of leaks.

### The filter runs in the database, not in Java

```java
where (:studentId is null or s.id = :studentId)
  and (:departmentId is null or d.id = :departmentId)
```

`studentId` and `departmentId` are **not** user-supplied filters — they are the
security boundary, pushed down into the query. The alternative, fetching every
request and filtering in Java, means one forgotten `if` leaks the whole table,
and it does not paginate correctly either.

This is why `GET /api/requests` accepts **no** `studentId` parameter. It was
tested: asking for `/api/requests?studentId=4` as a different student returns
zero results, because the parameter is not bound to anything.

### 404 instead of 403

```java
ServiceRequest request = requestRepository.findByIdWithDetail(id)
        .filter(scope::permits)
        .orElseThrow(() -> new ResourceNotFoundException("Request", id));
```

Reading someone else's request gives **404 Not Found**, not 403 Forbidden.

403 would confirm the id exists. Anyone could then walk `/api/requests/1`,
`/2`, `/3` and learn how many requests the college has, when activity peaks, and
which ids are missing. From outside your scope, the request simply is not there.

This is the opposite of the choice made for `/api/users`, where 403 is correct —
there the *endpoint* is forbidden to the role, and no individual record is being
confirmed or denied.

### Pagination from the first day

```java
@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
Pageable pageable
```

Departments and categories return a plain list because a college has perhaps
thirty of them. Requests run into thousands, so this list is paginated from the
start rather than being retrofitted after it becomes slow.

The response uses a small record of this project's own rather than Spring's
`Page`:

```java
public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages)
```

Serialising Spring's `Page` directly exposes a large, unstable set of internal
fields — Spring itself warns about it — and a framework upgrade could change the
API without a line of this project's code changing.

### Two response shapes, on purpose

`RequestSummaryResponse` for lists, `RequestDetailResponse` for one request. The
summary leaves out the description, which can be 2000 characters. Twenty rows a
page would otherwise carry 40 KB of text nobody reads until they click.

### Indexes, chosen not sprinkled

```java
@Index(name = "idx_request_status", columnList = "status"),
@Index(name = "idx_request_student", columnList = "student_id"),
@Index(name = "idx_request_category", columnList = "category_id"),
@Index(name = "idx_request_due_at", columnList = "due_at")
```

Exactly the four columns the list screens filter by. Not `title`, not
`description`, not `priority` — every index has to be rewritten on every insert
and update, so an unused one is a permanent cost for nothing.

## The API

### Locations

| Method | Path | Who |
|---|---|---|
| GET | `/api/locations?campus=&activeOnly=true` | Any signed-in user |
| GET | `/api/locations/campuses` | Any signed-in user |
| GET | `/api/locations/{id}` | Any signed-in user |
| POST / PUT / DELETE / activate | `/api/locations...` | `ADMIN` |

### Requests

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/requests?status=&categoryId=&priority=&page=0&size=20` | List, scoped to the caller |
| GET | `/api/requests/{id}` | One request, 404 if outside your scope |
| POST | `/api/requests` | File a request → 201 (students only) |
| GET | `/api/requests/priorities` | Priority dropdown, with SLA hours |
| GET | `/api/requests/statuses` | Status dropdown |

Creating a request:

```json
{
  "title": "Wi-Fi down in library",
  "description": "No internet on the second floor since this morning",
  "categoryId": 1,
  "locationId": 1,
  "priority": "MEDIUM"
}
```

comes back as:

```json
{
  "id": 1,
  "requestNumber": "CF-2026-000001",
  "title": "Wi-Fi down in library",
  "categoryName": "Wi-Fi",
  "departmentName": "IT Support",
  "locationName": "Main Campus - Library Block - Floor 2 - Reading Hall",
  "priority": "MEDIUM", "priorityLabel": "Medium",
  "status": "OPEN", "statusLabel": "Open",
  "studentName": "Priya Nair",
  "dueAt": "2026-08-24T18:59:00.727Z",
  "createdAt": "2026-08-22T18:59:00.729Z"
}
```

The student never mentioned IT Support. The category decided it — the point made
back in Phase 3, now doing real work.

## Tables created

```sql
locations(id, campus, building, floor, room, active, created_at, updated_at,
          UNIQUE KEY uk_location_place (campus, building, floor, room))

service_requests(id, request_number UNIQUE, title, description,
                 student_id FK, category_id FK, location_id FK NULL,
                 priority, status, due_at, created_at, updated_at,
                 INDEX idx_request_status, idx_request_student,
                       idx_request_category, idx_request_due_at)
```

## Tests

`ServiceRequestServiceTest` — 5 tests, one file.

- a new request opens with the right number, status and due date (fixed clock, exact assertion)
- a student cannot set CRITICAL
- staff cannot report through this endpoint
- a retired category is refused
- another student's request reports not-found

Visibility was also checked against the running application, because a rule that
is only unit tested with mocks has not really been proved:

| Check | Result |
|---|---|
| Second student's list | empty |
| Second student reads request 1 | 404 |
| Technician in IT Support reads it | 200 |
| Admin's list | 1 request |
| `?studentId=4` as another student | 0 — the parameter is not bound |

## How to try it yourself

```bash
./mvnw spring-boot:run
```

Sign in as the admin, add a location, then create a student and a category if you
have not already. As the student:

```bash
curl -X POST http://localhost:8080/api/requests -H "Authorization: Bearer STUDENT_TOKEN" -H "Content-Type: application/json" -d "{\"title\":\"Wi-Fi down in library\",\"description\":\"No internet on the second floor since this morning\",\"categoryId\":1,\"locationId\":1,\"priority\":\"MEDIUM\"}"
```

Then try to break the rules:

```bash
curl -X POST http://localhost:8080/api/requests -H "Authorization: Bearer STUDENT_TOKEN" -H "Content-Type: application/json" -d "{\"title\":\"Everything is broken\",\"description\":\"This is very urgent indeed\",\"categoryId\":1,\"priority\":\"CRITICAL\"}"
```
→ 422, students cannot set CRITICAL

Log in as a *different* student and fetch the first student's request:

```bash
curl -i http://localhost:8080/api/requests/1 -H "Authorization: Bearer OTHER_STUDENT_TOKEN"
```
→ 404, as if it did not exist

## What is deliberately not here yet

- **Nothing can be assigned.** Phase 7. A technician currently sees their whole
  department's queue; that view narrows to "assigned to me" once assignments
  exist.
- **The status never changes.** Every request sits at `OPEN`. The legal moves
  between statuses are Phase 8, which is why `RequestStatus` declares all seven
  values but encodes no transitions.
- **`dueAt` is set but nothing watches it.** SLA warnings, breach detection and
  escalation are Phase 9. The hours live on the `Priority` enum for now and move
  into a configurable table then — which is why the due date is stored on each
  request rather than calculated, so nothing has to be backfilled.
- **No comments, attachments or timeline.** Later phases.
- **No editing or cancelling a request.** A student cannot currently withdraw
  one. That belongs with the status workflow in Phase 8.

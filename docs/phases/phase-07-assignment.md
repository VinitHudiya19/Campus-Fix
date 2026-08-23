# Phase 7 — Assignment

## What we built

Requests existed but nobody was responsible for any of them. This phase decides
who does the work.

- a department head hands a request to a technician in their own department
- reassigning keeps the previous record instead of overwriting it
- the status moves `OPEN` → `ASSIGNED` automatically
- a technician's list narrows to the work actually given to them
- a "who had this, and when" history on every request

## Why a table and not a column

The obvious version is one column — `service_requests.technician_id` — and
reassignment just overwrites it. That loses the answer to questions a department
head genuinely asks:

- who had this last week?
- how long did Amit hold it before it moved?
- who keeps handing work back?

So an assignment is a **period of responsibility**, stored as a row:

```sql
assignments(id, request_id FK, technician_id FK, assigned_by FK,
            note, assigned_at, unassigned_at NULL)
```

Reassigning ends the running row and opens a new one. Nothing is ever
overwritten or deleted.

The design document also listed an `active` flag on this table. It was dropped:
"active" is exactly `unassigned_at is null`, and storing the same fact twice only
creates a way for the two to disagree.

`assigned_by` is kept for accountability. When a technician says "I never should
have got this", the record shows who gave it to them and what note they left.

## The one duplication, and why it is worth it

`service_requests` also carries `assigned_technician_id`:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "assigned_technician_id")
private User assignedTechnician;
```

This is the newest open row of the `assignments` table, copied. That is a real
duplication and normally a bad idea, so it needs a reason.

Every list screen and every detail screen asks "who has this?". Deriving it from
the history table would put a correlated subquery into the hottest query in the
application — the one that runs on every page load, for every user, paginated.

The risk of two sources of truth drifting is contained by making sure only one
class writes either of them:

```java
assignmentRepository.findActiveForRequest(requestId).ifPresent(active -> active.end(now));
assignmentRepository.save(new Assignment(request, technician, assigner, now, note));
request.assignTo(technician, now);
```

All three lines are in one `@Transactional` method in `AssignmentService`.
Nothing else in the codebase touches `assignedTechnician`, so they cannot fall
out of step — either all three happen or none do.

This is the trade-off worth being able to explain: a denormalised column is fine
when exactly one place writes it, and dangerous when several do.

## The business rules

| Rule | Reason |
|---|---|
| Only an admin, or the head **of the owning department**, may assign | The head of Electrical has no business routing IT tickets. |
| The technician must have the `TECHNICIAN` role | A department head or a student is not somebody work can be given to. |
| The technician must be in the request's own department | Work is routed by category → department. Assigning across that line breaks the routing the whole product depends on. |
| The technician's account must be active | A deactivated account cannot log in, so the work would sit unread. |
| Assigning the same technician twice is refused | It would close and reopen an identical row, adding a meaningless line to the history. |
| A `CLOSED` or `REJECTED` request cannot be assigned | It is finished. Nobody needs to work on it. |
| Someone not entitled to assign gets **404**, not 403 | Same reasoning as reading a request: do not confirm an id exists to someone with no business knowing about it. |

## Reading the key code

### Status changes live on the entity

```java
void assignTo(User technician, Instant at) {
    this.assignedTechnician = technician;
    this.assignedAt = at;
    if (status == RequestStatus.OPEN) {
        status = RequestStatus.ASSIGNED;
    }
}
```

The `if` matters. A request already `IN_PROGRESS` keeps that status when it
changes hands — handing the work to somebody else does not undo the work already
done. Only an untouched `OPEN` request becomes `ASSIGNED`.

Unassigning is the mirror image:

```java
void clearAssignment() {
    this.assignedTechnician = null;
    this.assignedAt = null;
    if (status == RequestStatus.ASSIGNED) {
        status = RequestStatus.OPEN;
    }
}
```

A request that was `IN_PROGRESS` does **not** go back to `OPEN` — work was
genuinely started, and pretending otherwise would hide it from the SLA numbers.

Both methods are package-private, so only the service beside them can call them.
A controller cannot reach in and change a status directly. The full transition
table — which moves are legal from where — is Phase 8; these two methods only
cover the moves that assignment itself causes.

### Ending before starting

```java
assignmentRepository.findActiveForRequest(requestId).ifPresent(active -> active.end(now));
assignmentRepository.save(new Assignment(request, technician, assigner, now, note));
```

The old row is closed first, so the table never holds two open rows for the same
request. Both use the same `now` from the injected clock, so the history has no
gap and no overlap — the smoke test confirmed the previous row's `unassignedAt`
is the exact instant of the new row's `assignedAt`.

The entity refuses to be closed twice:

```java
void end(Instant at) {
    if (unassignedAt != null) {
        throw new IllegalStateException("Assignment " + id + " has already ended");
    }
    this.unassignedAt = at;
}
```

### A technician now sees only their own work

```java
case TECHNICIAN -> new RequestScope(null, null, user.id());
case DEPARTMENT_HEAD -> new RequestScope(null, user.departmentId(), null);
```

Phase 6 gave technicians the whole department queue, because assignments did not
exist yet and an empty screen would have been worse. That was flagged at the time
as temporary, and this is the promised narrowing.

A head still sees the entire department, including unassigned requests — that
queue is the thing they are supposed to be working through.

### An endpoint that exists for a permissions reason

```java
@GetMapping("/assignable-technicians")
```

`/api/users` is admin-only, but a department head still has to fill the dropdown
on the assignment screen. Rather than loosening the rule on `/api/users`, this
returns exactly the people they are allowed to pick:

```json
[{ "id": 2, "fullName": "Amit Sharma", "openRequests": 0 }]
```

No emails, no roles, no way to enumerate staff outside their own department. It
also carries each technician's current open workload, so the head is choosing
with the one fact that actually matters.

`openRequests` deliberately ignores `CLOSED` and `REJECTED` work:

```java
and a.request.status not in (RequestStatus.CLOSED, RequestStatus.REJECTED)
```

A technician who has closed two hundred requests this year is not busy.

### Assignment sits under the request in the URL

```java
@RequestMapping("/api/requests/{id}")
```

`POST /api/requests/1/assign`, not `POST /api/assignments`. An assignment has no
meaning on its own — `/api/assignments/7` would be an id nobody could act on. The
URL says what the operation is about.

`POST /assign` returns the **whole request**, not just the assignment, because
assigning also changes the status. The client would otherwise have to make a
second call to find out what happened.

## The API

| Method | Path | Who | Purpose |
|---|---|---|---|
| POST | `/api/requests/{id}/assign` | Admin, or the owning department's head | Assign or reassign |
| DELETE | `/api/requests/{id}/assignment` | Same | Back to the unassigned queue |
| GET | `/api/requests/{id}/assignments` | Anyone who can read the request | Full history |
| GET | `/api/requests/{id}/assignable-technicians` | Admin, or the owning department's head | Dropdown with workload |

`GET /api/requests` gained one filter:

| Query param | Type | Default | Meaning |
|---|---|---|---|
| `unassignedOnly` | boolean | `false` | Only requests nobody is working on |

That is the department head's work queue.

Assigning:

```json
{ "technicianId": 2, "note": "On that floor today" }
```

201 is not used — this is not creating a resource the client will address later,
it is changing an existing one, so it returns 200 with the updated request.

History:

```json
[
  { "id": 2, "technicianName": "Sana Iqbal", "assignedByName": "Neha Rao",
    "note": "Amit is on leave", "assignedAt": "2026-08-23T07:54:58.207Z",
    "unassignedAt": null, "active": true },
  { "id": 1, "technicianName": "Amit Sharma", "assignedByName": "Neha Rao",
    "note": "On that floor today", "assignedAt": "2026-08-23T07:54:21.855Z",
    "unassignedAt": "2026-08-23T07:54:58.207Z", "active": false }
]
```

Newest first. The previous row closes at exactly the instant the new one opens.

## Table created

```sql
assignments(id, request_id FK, technician_id FK, assigned_by FK, note,
            assigned_at, unassigned_at NULL,
            INDEX idx_assignment_request, idx_assignment_technician)

-- added to service_requests
assigned_technician_id FK NULL, assigned_at NULL,
INDEX idx_request_technician
```

## Tests

`AssignmentServiceTest` — 5 tests.

- assigning an open request makes it `ASSIGNED` and records who did it
- reassigning **ends** the old row rather than overwriting it, and the history survives
- a technician from another department is refused
- a head of another department is told the request does not exist
- a closed request cannot be assigned

Checked against the running application as well, because rules about *who can see
what* are not really proved by mocks:

| Check | Result |
|---|---|
| Electrical technician on an IT request | 422, "does not work in IT Support" |
| Amit's list after being assigned | 1 request |
| Vikram (other department) list | 0 |
| Vikram reads the request | 404 |
| A technician tries to assign | 404 |
| Assigning the same person twice | 422 |
| History after reassignment | 2 rows, first closed at the second's start |
| Unassign | status back to `OPEN` |
| Unassign twice | 422 |

## How to try it yourself

```bash
./mvnw spring-boot:run
```

As admin, create a department head for the department that owns your category:

```bash
curl -X POST http://localhost:8080/api/users -H "Authorization: Bearer ADMIN_TOKEN" -H "Content-Type: application/json" -d "{\"fullName\":\"Neha Rao\",\"email\":\"neha@college.edu\",\"password\":\"head1234\",\"role\":\"DEPARTMENT_HEAD\",\"departmentId\":1}"
```

Log in as that head and look at the queue:

```bash
curl "http://localhost:8080/api/requests?unassignedOnly=true" -H "Authorization: Bearer HEAD_TOKEN"
```

See who is available, then assign:

```bash
curl http://localhost:8080/api/requests/1/assignable-technicians -H "Authorization: Bearer HEAD_TOKEN"
```

```bash
curl -X POST http://localhost:8080/api/requests/1/assign -H "Authorization: Bearer HEAD_TOKEN" -H "Content-Type: application/json" -d "{\"technicianId\":2,\"note\":\"On that floor today\"}"
```

Now reassign to somebody else and read the history — both periods are there:

```bash
curl http://localhost:8080/api/requests/1/assignments -H "Authorization: Bearer HEAD_TOKEN"
```

## What is deliberately not here yet

- **The technician cannot do anything with the work yet.** Starting, resolving
  and rejecting are Phase 8. Right now a request can only sit at `OPEN` or
  `ASSIGNED`.
- **No transition rules.** `assignTo` and `clearAssignment` move the status, but
  nothing yet prevents an invalid jump such as `OPEN` straight to `CLOSED`. That
  table of legal moves is Phase 8's entire job.
- **No notification.** A technician is not told they have been assigned; they
  have to look. Notifications come later.
- **No automatic assignment.** Round-robin or load balancing would be guessing at
  a policy the college has not stated. `openRequests` gives the head the
  information to decide; a human makes the call.
- **No self-assignment.** A technician cannot pick up an unassigned request
  themselves. The spec puts that decision with the department head.

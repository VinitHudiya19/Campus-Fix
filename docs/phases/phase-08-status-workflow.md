# Phase 8 — Status Workflow

## What we built

Until now a request was created `OPEN` and stayed there. Assignment could nudge
it to `ASSIGNED`. Nothing else ever moved.

This phase makes the status mean something:

- one table of legal moves — anything not in it is impossible
- a technician starts work, then says what they did to fix it
- the **student** decides whether it is really fixed, or reopens it
- staff can reject an invalid request, with a reason
- every change is written to a timeline nobody can edit afterwards

## The lifecycle

```
                    ┌──────────► REJECTED  (staff, with a reason)
                    │
OPEN ──assign──► ASSIGNED ──start──► IN_PROGRESS ──resolve──► RESOLVED
 ▲                  │                    ▲                       │
 └────unassign──────┘                    │                  ┌────┴────┐
                                         │                  │         │
                                    start│           confirm│         │reopen
                                         │                  ▼         ▼
                                         └──────────── CLOSED     REOPENED
                                                      (final)         │
                                                                      │
                                                                      ▼
                                                             (start → IN_PROGRESS)
```

`CLOSED` and `REJECTED` are the two ends. Everything else can still move.

## The rule that makes this a workflow

Every legal move is one row of an enum:

```java
public enum StatusAction {
    START   ("Start work",          IN_PROGRESS, from(ASSIGNED, REOPENED),              WORKER,   false),
    RESOLVE ("Mark as resolved",    RESOLVED,    from(IN_PROGRESS),                     WORKER,   true),
    REJECT  ("Reject",              REJECTED,    from(OPEN, ASSIGNED, IN_PROGRESS, REOPENED), MANAGER,  true),
    CONFIRM ("Confirm it is fixed", CLOSED,      from(RESOLVED),                        REPORTER, false),
    REOPEN  ("Still not fixed",     REOPENED,    from(RESOLVED),                        REPORTER, true);
}
```

Five columns, and they answer every question the workflow can be asked:

| Column | Question it answers |
|---|---|
| label | What does the button say? |
| target | Where does it end up? |
| allowedFrom | Where can it be used from? |
| actor | Who is entitled to press it? |
| noteRequired | Do they have to explain themselves? |

**A request cannot go from `OPEN` to `CLOSED` because no action does that.** Not
because a check forbids it — because it does not exist. That is the difference
between a rule and a workflow.

## Who may do what, and why it is not a role check

```java
return switch (action.getActor()) {
    case WORKER   -> manages || isAssignedTechnician(request, user);
    case MANAGER  -> manages;
    case REPORTER -> user.id().equals(request.getStudent().getId());
};
```

`Actor` is about the person's relationship to **this** request, not their job
title:

- a technician may resolve the request *they hold*, not any request
- a department head may reject inside *their* department, not another one
- only the student who reported it may confirm or reopen it

That last one is the important product decision. **An admin cannot close a
request on a student's behalf.** Nobody else can truthfully say whether the fan
in someone's room is working. Letting staff close their own work would turn the
resolution rate into a number staff award themselves.

`WORKER` does include the department head, deliberately: when a technician leaves
mid-term, somebody has to be able to finish their requests.

## Reading the key code

### Reopening undoes the resolution

```java
case REOPENED -> {
    this.resolvedAt = null;
    this.resolutionNote = null;
}
```

This one is easy to get wrong. If `resolvedAt` survived a reopen, the SLA figures
would keep claiming the problem was fixed on Tuesday — when the student is
telling you it was never fixed at all. The record has to match what actually
happened.

### The note requirement lives with the workflow, not on the DTO

`StatusChangeRequest` has no `@NotBlank`:

```java
public record StatusChangeRequest(
        @Size(max = 1000, ...) String note) { }
```

because whether a note is required depends on the action, and Bean Validation
cannot see which endpoint it is. Putting `@NotBlank` there would force a note on
`start`, where there is nothing to explain.

So the rule sits on `StatusAction` with the rest of the workflow, and the service
produces a message that says what is actually wanted:

```java
case RESOLVE -> "Please describe what you did to fix it";
case REJECT  -> "Please give a reason for rejecting this request";
case REOPEN  -> "Please explain what is still not working";
```

Not "note is required". A technician told "please describe what you did" knows
what to type.

### An endpoint per action, not `PUT /status`

```
POST /api/requests/1/start
POST /api/requests/1/resolve
POST /api/requests/1/reject
POST /api/requests/1/confirm
POST /api/requests/1/reopen
```

The alternative — `PUT /api/requests/1/status` with `{"status": "CLOSED"}` —
looks tidier and is worse. It invites the client to ask for *any* status and
relies on the server to refuse, so the URL promises more than the system allows.
These five endpoints are exactly the five legal moves.

### The server tells the UI which buttons to draw

```
GET /api/requests/1/available-actions
```

```json
[
  { "action": "CONFIRM", "label": "Confirm it is fixed", "noteRequired": false },
  { "action": "REOPEN",  "label": "Still not fixed",     "noteRequired": true }
]
```

The frontend *could* work this out from the status and the role. Then the same
rule would exist in Java and in JavaScript, and one day they would disagree —
usually as a button that appears and then fails with a 422.

Asking the server means the screen can never offer something the server will
refuse. It filters by both the status **and** the caller, so a technician looking
at the same resolved request gets an empty list.

### 404, again, for the wrong person

```java
if (!mayAct(action, request, signedIn)) {
    throw new ResourceNotFoundException("Request", requestId);
}
```

Checked before the status check, so the error message cannot leak what state
someone else's request is in.

## The timeline

Every change writes one row:

```sql
activity_logs(id, request_id FK, actor_id FK NULL, type, old_value, new_value,
              message, created_at)
```

**Append only.** Nothing in the codebase updates or deletes a row here. An audit
trail that can be edited afterwards is worth nothing.

`actor_id` is nullable because not every event has a person behind it — the SLA
check in Phase 9 acts on its own, and the response shows `"System"` rather than a
blank name.

Writing is centralised in `ActivityLogService`, called from every service that
changes a request. If each service wrote its own rows, the timeline would quietly
grow gaps wherever one code path forgot.

The entries join the caller's transaction rather than opening their own. If the
change rolls back, its log entry rolls back with it — the history never claims
something happened that did not.

A real timeline from the running app:

```
Priya Nair reported this problem
Neha Rao assigned this to Amit Sharma
Amit Sharma started work on this
Amit Sharma marked this as resolved — Replaced the access point
Priya Nair reopened this — Still no signal in the corner
Amit Sharma started work on this
Amit Sharma marked this as resolved — Replaced the whole unit this time
Priya Nair confirmed the problem is fixed
```

Reading is a separate service — `RequestTimelineService` — because the writer is
called from inside other people's transactions and knows nothing about who is
asking, while the reader has to check whether the caller may look.

## The API

| Method | Path | Who | Result |
|---|---|---|---|
| POST | `/api/requests/{id}/start` | Assigned technician, or department head/admin | → `IN_PROGRESS` |
| POST | `/api/requests/{id}/resolve` | Same | → `RESOLVED`, note required |
| POST | `/api/requests/{id}/reject` | Department head or admin | → `REJECTED`, reason required |
| POST | `/api/requests/{id}/confirm` | The reporting student only | → `CLOSED` |
| POST | `/api/requests/{id}/reopen` | The reporting student only | → `REOPENED`, reason required |
| GET | `/api/requests/{id}/available-actions` | Anyone who can read it | Which of the five apply |
| GET | `/api/requests/{id}/timeline` | Anyone who can read it | Full history |

All five return the whole updated request.

422 if the move is illegal from the current status, or a required note is
missing. 404 if the caller has no right to make that move.

## Columns added

```sql
-- service_requests
resolved_at NULL, closed_at NULL, resolution_note NULL, rejection_reason NULL
```

Added by the phase that can actually set them, rather than sitting empty since
Phase 6.

## Tests

`WorkflowServiceTest` — 7 tests.

- resolving records the note and the time
- `OPEN` cannot jump straight to `RESOLVED`
- resolving without saying what was done is refused
- a technician cannot close a request on the student's behalf
- reopening clears `resolvedAt` and the note
- only the owning department's head may reject
- `available-actions` differs for the student and for staff on the same request

Also run end to end against the live application, through a full reopen cycle:
assign → start → resolve → reopen → start → resolve → confirm, checking the
status, the timeline and the cleared `resolvedAt` at each step.

## What is deliberately not here yet

- **No comments.** A student cannot ask "any update?" — only the fixed set of
  status notes exists. Comments are their own feature.
- **No attachments.** A photo of the broken fan would help; it needs file storage.
- **No cancelling.** A student cannot withdraw a request they filed by mistake.
  Staff reject it instead, which records who decided and why — arguably better,
  but it is a gap.
- **No notification.** Nobody is told their request was resolved; they have to
  look.

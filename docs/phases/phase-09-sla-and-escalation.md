# Phase 9 — SLA and Escalation

## What we built

A deadline nobody watches is a wish. This phase makes the system notice.

- SLA targets an admin can change, instead of constants in the code
- every request judged against its deadline: on track, due soon, breached
- a background check that runs on its own and pushes late requests up
- two-step escalation: department head, then administration
- both steps written to the request's timeline

## What an SLA is here

A service level agreement is a promise about time: *a high-priority problem will
be dealt with within 24 hours*. The defaults come from the product spec:

| Priority | Target | Warning at 75% |
|---|---:|---:|
| Low | 72 hours | 54 hours |
| Medium | 48 hours | 36 hours |
| High | 24 hours | 18 hours |
| Critical | 4 hours | 3 hours |

These are defaults, not industry standards. A hostel with two electricians cannot
promise what a campus with twenty can, which is exactly why they moved out of the
code and into a table an admin can edit.

## Two decisions that shape everything else

### The deadline is stored. The state is not.

`due_at` is written once, when the request is created. `slaState` is never
stored — it is calculated every time anyone looks.

This sounds inconsistent until you ask what each one is:

**The deadline is a promise made at a moment in time.** If the college shortens
the MEDIUM target from 48 hours to 24 tomorrow, a request filed today keeps the
48 hours it was actually given. Recalculating would retrospectively move a
promise already made to a student, and turn last month's met targets into
breaches.

**The state is a function of the clock.** A request that is `ON_TRACK` now
becomes `DUE_SOON` in an hour and `BREACHED` tonight, with nothing having
happened. Storing it would mean a column that is wrong most of the time, plus a
job whose only purpose is to keep rewriting it.

```java
@Column(name = "due_at", nullable = false)
private Instant dueAt;          // stored, never recalculated

// slaState: nowhere in the entity
```

### A finished request stops moving

```java
if (finished != null) {
    return finished.isAfter(deadline) ? SlaState.MISSED : SlaState.MET;
}
```

Once a request is resolved or closed, its verdict is fixed forever: `MET` or
`MISSED`. Checked a week later, a month later, it says the same thing. That is
what makes the numbers usable for reporting — the past does not drift.

`MISSED` exists on purpose. It would be easy to let a late-but-finished request
quietly show as `MET`; the honest record of a missed target is the whole point of
measuring.

## The five states

| State | Meaning |
|---|---|
| `ON_TRACK` | Plenty of time left |
| `DUE_SOON` | Past the warning threshold, not yet late |
| `BREACHED` | Deadline gone, still not fixed |
| `MET` | Finished inside the deadline — final |
| `MISSED` | Finished, but late — final |

## Reading the key code

### One snapshot per page

```java
SlaSnapshot sla = slaService.snapshot();
return PagedResponse.of(page, request -> RequestSummaryResponse.from(request, sla.stateOf(request)));
```

Rendering twenty rows would otherwise re-read the config table twenty times, and
call `Instant.now()` twenty times — so two rows on the same screen could be
measured against slightly different moments. One row could read `ON_TRACK` and
the next `DUE_SOON` when they are in fact identical.

`SlaSnapshot` is the settings plus one instant, captured once.

### The warning point comes from the request's own window

```java
int percentage = warningPercentages.getOrDefault(request.getPriority(), 75);
Duration window = Duration.between(request.getCreatedAt(), request.getDueAt());
return request.getCreatedAt().plus(window.multipliedBy(percentage).dividedBy(100));
```

Note what it does **not** do: it never reads `durationHours`. The window is
measured between the two dates already stored on the request. So if the target
changes tomorrow, an old request's warning point still sits three quarters
through the window it was really given.

`getOrDefault(..., 75)` is a safety net — a missing config row cannot make the
calculation fail.

### The check runs itself

```java
@Scheduled(fixedDelayString = "${campusfix.sla.check-interval-ms:900000}",
           initialDelayString = "${campusfix.sla.initial-delay-ms:60000}")
public void checkOverdueRequests() { ... }
```

`fixedDelay`, not `fixedRate`. Fixed delay starts the next run a set time after
the previous one **finished**; fixed rate starts it a set time after the previous
one *began*, so on a large database a slow pass would have runs piling up on top
of each other.

The one-minute initial delay keeps the check from firing while the application is
still warming up during a deployment.

Fifteen minutes is frequent enough that a breach is noticed within the same
working hour, and rare enough to cost nothing.

### Resolved requests are not chased

```sql
where r.dueAt < :now
  and r.status not in (RESOLVED, CLOSED, REJECTED)
```

`RESOLVED` is excluded along with the two final states. The technician has done
the work and it is the student's turn to confirm — escalating would be chasing
the department for something they have already finished. It is still recorded as
`MISSED` if it was late; it just does not generate an escalation.

### Escalating twice is impossible

```java
if (escalationRepository.existsByRequestIdAndLevel(request.getId(), level)) {
    return false;
}
```

Without this the check would escalate the same request every fifteen minutes
forever. The same rule is also enforced by the database:

```sql
UNIQUE KEY uk_escalation_request_level (request_id, level)
```

Belt and braces, for a real reason: the scheduler runs per application instance,
so two instances would both run the check. The application check keeps the log
quiet; the database constraint is what actually guarantees correctness if two
passes overlap.

### Two steps, and the grace period

```java
escalate(request, DEPARTMENT_HEAD, now, "Past its high deadline and still unresolved");

boolean graceExpired = now.isAfter(request.getDueAt().plus(Duration.ofHours(graceHours)));
if (graceExpired) {
    escalate(request, ADMIN, now, "Still unresolved 24 hours after the deadline");
}
```

Past the deadline it becomes the department head's problem. Still not fixed a
further 24 hours later and it goes to the administration. The grace period is
configurable, because "how long before this becomes the principal's problem?" is
a policy question, not a technical one.

### The admin can run it now

```
POST /api/sla/check-now
```

Exists so the escalation rules can be demonstrated and tested without waiting a
quarter of an hour. Safe to call repeatedly — the second call reports
`{"escalated": 0}` because everything already escalated is skipped.

## The API

| Method | Path | Who |
|---|---|---|
| GET | `/api/sla` | Any signed-in user |
| PUT | `/api/sla/{priority}` | `ADMIN` |
| POST | `/api/sla/check-now` | `ADMIN` |
| GET | `/api/requests/{id}/escalations` | Anyone who can read the request |

Reading the targets is open to everyone on purpose: a student is entitled to know
what turnaround the college promises for a high-priority problem.

```json
[
  { "id": 3, "priority": "HIGH", "priorityLabel": "High",
    "durationHours": 24, "warningPercentage": 75 }
]
```

Updating:

```json
{ "durationHours": 12, "warningPercentage": 80 }
```

`warningPercentage` is capped at 99, with the message explaining why: a warning
at 100% is the breach itself.

**Changing a target only affects requests created afterwards.** Existing requests
keep the deadline they were given.

Every request response now carries its state:

```json
{ "slaState": "BREACHED", "slaStateLabel": "Breached", "dueAt": "2026-08-22T02:40:00Z" }
```

## Tables created

```sql
sla_configs(id, priority UNIQUE, duration_hours, warning_percentage,
            created_at, updated_at)

escalations(id, request_id FK, level, reason, created_at,
            UNIQUE KEY uk_escalation_request_level (request_id, level))
```

One `sla_configs` row per priority, seeded on first startup and never overwritten
afterwards, so an edited target survives a restart.

The design document had an `active` flag on `sla_configs`. It was dropped: a
deactivated SLA row raises a question nobody has an answer to — what is the
target then?

## Tests

`SlaSnapshotTest` — 4 tests, pure date arithmetic, no mocks and no Spring.

- plenty of time left is `ON_TRACK`
- the warning point is exact: `ON_TRACK` one second before, `DUE_SOON` at it
- the deadline itself already counts as `BREACHED`
- a finished request keeps its verdict when checked a week later

Escalation was proved against the running application instead, because the parts
worth checking — the scheduler, the unique constraint, the grace period — are
about real infrastructure rather than logic a mock can stand in for:

| Check | Result |
|---|---|
| A 30-hour-overdue request | `slaState: BREACHED` |
| First `check-now` | `{"escalated": 2}` — both levels, grace already passed |
| Second `check-now` | `{"escalated": 0}` |
| Escalations on the request | Department head, then administration |
| Its timeline | Two `System` entries, no person attached |
| A student calling `check-now` | 403 |

## How to try it yourself

```bash
./mvnw spring-boot:run
```

See the targets:

```bash
curl http://localhost:8080/api/sla -H "Authorization: Bearer TOKEN"
```

Shorten one, as admin:

```bash
curl -X PUT http://localhost:8080/api/sla/HIGH -H "Authorization: Bearer ADMIN_TOKEN" -H "Content-Type: application/json" -d "{\"durationHours\":12,\"warningPercentage\":80}"
```

To watch an escalation without waiting a day, push a request's deadline into the
past directly:

```bash
mysql -u root -p -e "UPDATE campusfix.service_requests SET due_at = DATE_SUB(UTC_TIMESTAMP(), INTERVAL 30 HOUR) WHERE id = 2"
```

Then run the check by hand:

```bash
curl -X POST http://localhost:8080/api/sla/check-now -H "Authorization: Bearer ADMIN_TOKEN"
```

and read what it did:

```bash
curl http://localhost:8080/api/requests/2/timeline -H "Authorization: Bearer ADMIN_TOKEN"
```

## Known limitations

- **Escalation records, it does not notify.** A row and a timeline entry are
  written; no email is sent. The department head has to look. Notifications need
  mail infrastructure this project does not have, and inventing a fake bell icon
  would be worse than the honest gap.
- **The scheduler runs on every instance.** Fine for escalation, because the
  unique constraint makes a duplicate harmless. A heavier job would need a lock
  or a real scheduler such as Quartz or ShedLock.
- **No working-hours calendar.** A 4-hour critical SLA filed at 11pm on a Friday
  expires at 3am on Saturday. Real SLAs usually pause outside working hours. That
  needs a calendar of college hours and holidays, and it changes every SLA
  calculation — a feature of its own, not a footnote to this one.
- **No SLA dashboard.** The state is on every request, but "how is IT Support
  doing this month?" needs the reporting phase.

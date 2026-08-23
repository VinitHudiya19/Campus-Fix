# CampusFix API Contract

Base URL: `/api`

Sections marked **Implemented** are live and tested. Everything else is a plan
and will be filled in with real request/response bodies during its phase.

All timestamps are UTC, serialised as ISO-8601 (`2026-08-22T17:26:53.331Z`).

Every endpoint requires `Authorization: Bearer <token>` unless it is marked
public. See [Authentication](#authentication--implemented) for how to get one and
which role each endpoint needs.

---

## Error format

Every failure returns the same shape, whatever caused it.

```json
{
  "timestamp": "2026-08-22T17:26:53.331481800Z",
  "status": 400,
  "message": "Validation failed",
  "path": "/api/departments",
  "fieldErrors": {
    "name": "Department name is required"
  }
}
```

`fieldErrors` is present only when validation failed. It is omitted otherwise.

### Status codes

| Code | Meaning in this API |
|---|---|
| 200 | Success |
| 201 | Created — includes a `Location` header |
| 204 | Success, nothing to return (deactivate, activate) |
| 400 | Request was malformed or failed field validation |
| 401 | Not signed in, or the token is invalid or expired |
| 403 | Signed in, but this role is not allowed to do it |
| 404 | The addressed resource does not exist |
| 409 | Conflicts with existing data, e.g. a duplicate name |
| 422 | Well formed and understood, but a business rule forbids it |
| 500 | Unexpected failure — details are in the server log only |

---

## Health — Implemented

### `GET /api/hello`

Temporary. Confirms the application started. Removed once Actuator is added.

```json
{ "application": "CampusFix", "status": "UP", "timestamp": "2026-08-22T17:05:52.115Z" }
```

---

## Departments — Implemented

### `GET /api/departments`

| Query param | Type | Default | Meaning |
|---|---|---|---|
| `activeOnly` | boolean | `false` | Return only active departments |

```json
[
  { "id": 1, "name": "IT Support", "description": "Network and lab computers", "active": true }
]
```

### `GET /api/departments/{id}`

200 with the department, or 404.

### `POST /api/departments`

```json
{ "name": "IT Support", "description": "Network and lab computers" }
```

| Field | Rules |
|---|---|
| `name` | required, max 100 characters, unique ignoring case |
| `description` | optional, max 255 characters |

201 Created with `Location: /api/departments/{id}`.
409 if the name is already taken.

### `PUT /api/departments/{id}`

Same body and rules as POST. A department may keep its own name; a name belonging
to a different department returns 409.

### `DELETE /api/departments/{id}`

**Deactivates** — it does not delete the row, because service requests reference
departments and that history has to survive.

204 on success.
422 if the department still has active categories.

### `POST /api/departments/{id}/activate`

204 on success.

---

## Categories — Implemented

### `GET /api/categories`

| Query param | Type | Default | Meaning |
|---|---|---|---|
| `departmentId` | long | none | Only categories of that department |
| `activeOnly` | boolean | `false` | Only active categories |

```json
[
  {
    "id": 1,
    "name": "Wi-Fi",
    "description": "Internet not working",
    "departmentId": 1,
    "departmentName": "IT Support",
    "active": true
  }
]
```

The department name is included so a table can be rendered without one extra
request per row.

### `GET /api/categories/{id}`

200 with the category, or 404.

### `POST /api/categories`

```json
{ "name": "Wi-Fi", "description": "Internet not working", "departmentId": 1 }
```

| Field | Rules |
|---|---|
| `name` | required, max 100, unique **within the department** |
| `description` | optional, max 255 |
| `departmentId` | required, must exist and be active |

201 Created with `Location: /api/categories/{id}`.
404 if the department does not exist.
409 if that department already has a category with this name.
422 if the department is inactive.

### `PUT /api/categories/{id}`

Same body. Changing `departmentId` moves the category to another department.

### `DELETE /api/categories/{id}`

Deactivates. 204.

### `POST /api/categories/{id}/activate`

204.
422 if the parent department is inactive — activate the department first.

---

## Users — Implemented

**All of these require the `ADMIN` role.** Any other signed-in user gets 403.

No response ever contains a password or a password hash.

### `GET /api/users`

| Query param | Type | Default | Meaning |
|---|---|---|---|
| `role` | enum | none | `STUDENT`, `TECHNICIAN`, `DEPARTMENT_HEAD`, `ADMIN` |
| `departmentId` | long | none | Only staff of that department |
| `activeOnly` | boolean | `false` | Only active users |

```json
[
  {
    "id": 2,
    "fullName": "Amit Sharma",
    "email": "amit@college.edu",
    "role": "TECHNICIAN",
    "roleLabel": "Technician",
    "departmentId": 1,
    "departmentName": "IT Support",
    "active": true
  }
]
```

`departmentId` and `departmentName` are `null` for students and admins.

### `GET /api/users/roles`

Fills role dropdowns without hardcoding the list in JavaScript.

```json
[
  { "value": "STUDENT", "label": "Student", "departmentRequired": false },
  { "value": "TECHNICIAN", "label": "Technician", "departmentRequired": true },
  { "value": "DEPARTMENT_HEAD", "label": "Department Head", "departmentRequired": true },
  { "value": "ADMIN", "label": "Administrator", "departmentRequired": false }
]
```

`departmentRequired` lets the form show or hide the department field as soon as a
role is chosen, using the same rule the server enforces.

### `GET /api/users/{id}`

200 with the user, or 404.

### `POST /api/users`

```json
{
  "fullName": "Amit Sharma",
  "email": "amit@college.edu",
  "password": "tech1234",
  "role": "TECHNICIAN",
  "departmentId": 1
}
```

| Field | Rules |
|---|---|
| `fullName` | required, max 120 |
| `email` | required, valid email, max 160, unique — stored lowercase |
| `password` | required, 8–72 characters, stored as a BCrypt hash |
| `role` | required, one of the four values |
| `departmentId` | required for `TECHNICIAN` and `DEPARTMENT_HEAD`, must be omitted for `STUDENT` and `ADMIN` |

201 Created with `Location: /api/users/{id}`.
404 if the department does not exist.
409 if the email is already registered, regardless of capitalisation.
422 if the role/department combination breaks the rule above, or the department is inactive.

### `PUT /api/users/{id}`

```json
{ "fullName": "Amit Sharma", "role": "DEPARTMENT_HEAD", "departmentId": 1 }
```

No `password` and no `email`. A profile edit cannot overwrite a password hash,
and the email is the login identity.

### `PUT /api/users/{id}/password`

```json
{ "newPassword": "newpass123" }
```

Admin reset. 204. A self-service change that checks the old password arrives in
Phase 5, once the API knows who is calling.

### `DELETE /api/users/{id}`

Deactivates. 204. The row is kept because requests, comments and assignments all
point at it.

422 if an admin tries to deactivate their **own** account. Together with the same
guard on role changes in `PUT /api/users/{id}`, this guarantees at least one
working administrator always remains.

### `POST /api/users/{id}/activate`

204.
422 if the user is staff and their department is inactive.

---

## Authentication — Implemented

Every endpoint except those marked public needs a header:

```
Authorization: Bearer <token>
```

### `POST /api/auth/login` — public

```json
{ "email": "admin@campusfix.local", "password": "admin12345" }
```

```json
{
  "token": "eyJhbGciOiJIUzM4NCJ9...",
  "expiresInSeconds": 28800,
  "user": {
    "id": 3,
    "fullName": "CampusFix Administrator",
    "email": "admin@campusfix.local",
    "role": "ADMIN",
    "roleLabel": "Administrator",
    "departmentId": null,
    "departmentName": null
  }
}
```

Email is matched case-insensitively.
401 for a wrong email **or** a wrong password — the same message for both, so the
response cannot be used to discover which addresses are registered.
403 if the account is deactivated.

### `GET /api/auth/me`

The signed-in user, read fresh from the database rather than from the token, so a
change made since login shows immediately.

### `PUT /api/auth/password`

```json
{ "currentPassword": "admin12345", "newPassword": "newpass123" }
```

204. 401 if the current password is wrong.
Different from `PUT /api/users/{id}/password`, which is an admin resetting
somebody else's password and does not require the old one.

### There is no `/api/auth/register`

Originally planned, then dropped. Accounts are created by an admin through
`POST /api/users`. A college issues accounts to its own students and staff — open
self-registration would let anyone create one.

### Who can reach what

| Endpoint | Who |
|---|---|
| `POST /api/auth/login`, `GET /api/hello` | Anyone |
| `GET /api/departments/**`, `GET /api/categories/**` | Any signed-in user |
| Other methods on departments/categories | `ADMIN` |
| `/api/users/**` | `ADMIN` |
| `/api/auth/me`, `/api/auth/password` | Any signed-in user |

| Situation | Status | Message |
|---|---|---|
| Missing or invalid token | 401 | `You need to sign in to do that` |
| Valid token, insufficient role | 403 | `Your role does not allow that action` |

401 means "I do not know who you are". 403 means "I know who you are, and no".

## Locations — Implemented

Reading is open to any signed-in user, because the report form needs the list.
Everything else requires `ADMIN`.

### `GET /api/locations`

| Query param | Type | Default | Meaning |
|---|---|---|---|
| `campus` | string | none | Only that campus, matched case-insensitively |
| `activeOnly` | boolean | `false` | Only locations still in use |

```json
[
  {
    "id": 1,
    "campus": "Main Campus",
    "building": "Library Block",
    "floor": "Floor 2",
    "room": "Reading Hall",
    "displayName": "Main Campus - Library Block - Floor 2 - Reading Hall",
    "active": true
  }
]
```

`floor` and `room` may be null — a main gate has neither.

### `GET /api/locations/campuses`

A plain list of campus names, for the first dropdown on the report form.

### `POST /api/locations`

```json
{ "campus": "Main Campus", "building": "Library Block", "floor": "Floor 2", "room": "Reading Hall" }
```

| Field | Rules |
|---|---|
| `campus` | required, max 80 |
| `building` | required, max 80 |
| `floor` | optional, max 40 |
| `room` | optional, max 40 |

201 Created.
409 if the same place already exists — compared case-insensitively, with blank
treated as absent, so `floor: ""` and `floor: null` are the same place.

### `PUT`, `DELETE`, `POST /{id}/activate`

As elsewhere: update, deactivate (204), reactivate (204).

---

## Service requests — Implemented

Everything here needs a signed-in user. **What you see depends on your role**,
and it is enforced in the query, not by a URL rule:

| Role | Sees |
|---|---|
| `STUDENT` | Only the requests they reported |
| `TECHNICIAN` | Only the requests assigned to them |
| `DEPARTMENT_HEAD` | Their whole department, including the unassigned queue |
| `ADMIN` | Everything |

### `GET /api/requests`

| Query param | Type | Default | Meaning |
|---|---|---|---|
| `status` | enum | none | `OPEN`, `ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `REOPENED`, `REJECTED` |
| `categoryId` | long | none | Only that category |
| `priority` | enum | none | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| `unassignedOnly` | boolean | `false` | Only requests nobody is working on — the department head's queue |
| `page` | int | `0` | Page number |
| `size` | int | `20` | Page size |
| `sort` | string | `createdAt,desc` | Any field of the request |

There is deliberately **no** `studentId` or `departmentId` parameter. Scope comes
from the token, so it cannot be widened by editing the URL.

```json
{
  "content": [
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
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

The list row has no `description`. It is up to 2000 characters and nobody reads
it until they open the request.

### `GET /api/requests/{id}`

The full record, including `description`, `studentEmail` and the category and
department ids.

**404 if the request is outside your scope** — not 403. A 403 would confirm the
id exists and let anyone count the college's requests by walking the id range.

### `POST /api/requests`

Students only. 422 for any other role.

```json
{
  "title": "Wi-Fi down in library",
  "description": "No internet on the second floor since this morning",
  "categoryId": 1,
  "locationId": 1,
  "priority": "MEDIUM"
}
```

| Field | Rules |
|---|---|
| `title` | required, 5–150 characters |
| `description` | required, 10–2000 characters |
| `categoryId` | required, must exist and be active |
| `locationId` | optional, must exist and be active if given |
| `priority` | required — `LOW`, `MEDIUM` or `HIGH`; `CRITICAL` is staff-only |

The body has no `studentId`, `status`, `requestNumber` or `dueAt`. The reporter
comes from the token, the status is always `OPEN`, and the server generates the
number and the deadline.

201 Created with `Location: /api/requests/{id}`.
404 if the category or location does not exist.
422 if the category or location is inactive, if a student picks `CRITICAL`, or if
a non-student tries to report.

### `GET /api/requests/priorities`

```json
[
  { "value": "LOW", "label": "Low", "slaHours": 72, "studentSelectable": true },
  { "value": "MEDIUM", "label": "Medium", "slaHours": 48, "studentSelectable": true },
  { "value": "HIGH", "label": "High", "slaHours": 24, "studentSelectable": true },
  { "value": "CRITICAL", "label": "Critical", "slaHours": 4, "studentSelectable": false }
]
```

`studentSelectable` lets the form hide `CRITICAL` from students using the same
rule the server enforces.

### `GET /api/requests/statuses`

`value` and `label` for each of the seven statuses.

---

## Assignment — Implemented

Assignment lives under a request, because it has no meaning on its own.

Assigning and unassigning require **an admin, or the head of the department that
owns the request's category**. Anyone else gets 404 — not 403, so an id is never
confirmed to someone with no business knowing about it.

### `POST /api/requests/{id}/assign`

```json
{ "technicianId": 2, "note": "On that floor today" }
```

| Field | Rules |
|---|---|
| `technicianId` | required, must be an active `TECHNICIAN` in the request's own department |
| `note` | optional, max 255 — why this person |

Returns **the whole request**, because assigning also changes its status:
`OPEN` becomes `ASSIGNED`. A request already `IN_PROGRESS` keeps that status —
changing hands does not undo work already done.

Reassigning is the same call with a different technician. The previous
assignment is closed at the exact instant the new one opens.

404 if the request does not exist, or the caller may not assign it.
422 if the technician is not a technician, is deactivated, works in another
department, already has this request, or the request is `CLOSED`/`REJECTED`.

### `DELETE /api/requests/{id}/assignment`

Returns the request to the unassigned queue. `ASSIGNED` goes back to `OPEN`; a
request that was already `IN_PROGRESS` keeps that status, because the work really
did start.

200 with the updated request. 422 if nobody was assigned.

### `GET /api/requests/{id}/assignments`

The full history, newest first. Readable by anyone who can read the request.

```json
[
  { "id": 2, "technicianId": 8, "technicianName": "Sana Iqbal",
    "assignedByName": "Neha Rao", "note": "Amit is on leave",
    "assignedAt": "2026-08-23T07:54:58.207Z", "unassignedAt": null, "active": true },
  { "id": 1, "technicianId": 2, "technicianName": "Amit Sharma",
    "assignedByName": "Neha Rao", "note": "On that floor today",
    "assignedAt": "2026-08-23T07:54:21.855Z",
    "unassignedAt": "2026-08-23T07:54:58.207Z", "active": false }
]
```

### `GET /api/requests/{id}/assignable-technicians`

```json
[{ "id": 2, "fullName": "Amit Sharma", "openRequests": 0 }]
```

Exists because `/api/users` is admin-only but a department head still has to fill
the assignment dropdown. Returns only technicians in the owning department, with
no emails — there is no way to enumerate staff elsewhere.

`openRequests` counts current assignments that are not `CLOSED` or `REJECTED`, so
a technician who closed two hundred requests this year does not look busy.

### Planned — Phase 8

    PUT  /api/requests/{id}/status             Phase 8
    POST /api/requests/{id}/reopen             Phase 8
    POST /api/requests/{id}/confirm-resolution Phase 8

## API documentation

Swagger/OpenAPI is added once the request endpoints exist, so it documents
something worth reading.

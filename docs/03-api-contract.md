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

## Service requests — Planned (Phase 6 onwards)

    POST /api/requests
    GET  /api/requests/{id}
    GET  /api/requests
    PUT  /api/requests/{id}/status
    POST /api/requests/{id}/assign
    POST /api/requests/{id}/reopen
    POST /api/requests/{id}/confirm-resolution

Listing will support `page`, `size`, `sort`, `status`, `priority`, `category`,
`department` and `search`. Not every filter arrives at once — the base endpoint
comes first.

## API documentation

Swagger/OpenAPI is added once the request endpoints exist, so it documents
something worth reading.

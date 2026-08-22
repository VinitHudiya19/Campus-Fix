# CampusFix API Contract

Base URL: `/api`

Sections marked **Implemented** are live and tested. Everything else is a plan
and will be filled in with real request/response bodies during its phase.

All timestamps are UTC, serialised as ISO-8601 (`2026-08-22T17:26:53.331Z`).

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

## Users — Planned (Phase 4)

    GET    /api/users
    POST   /api/users
    GET    /api/users/{id}
    PUT    /api/users/{id}
    DELETE /api/users/{id}

## Authentication — Planned (Phase 5)

    POST /api/auth/login
    POST /api/auth/register
    GET  /api/auth/me

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

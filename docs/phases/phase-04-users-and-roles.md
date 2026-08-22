# Phase 4 — Users and Roles

## What we built

The people. Until now the system had teams and problem types but nobody to
report a problem or fix one.

Four kinds of user:

| Role | What they do | Belongs to a department? |
|---|---|---|
| **STUDENT** | Reports problems, follows their own requests | No |
| **TECHNICIAN** | Works on requests assigned to them | Yes |
| **DEPARTMENT_HEAD** | Assigns work inside their department, watches its queue | Yes |
| **ADMIN** | Manages departments, categories and users campus-wide | No |

This phase creates and manages user accounts. It does **not** include logging in.
Passwords are hashed correctly from the very first row, but nothing checks them
yet — that is Phase 5.

## Why accounts come before login

Logging in means comparing a typed password against a stored one. There has to be
a stored one first. Building the accounts separately also keeps the two hard
parts apart: this phase is about *who exists and what is a valid user*, the next
is about *proving you are that user*.

## Design decision: an enum instead of a `roles` table

`docs/02-database-design.md` originally planned a `roles` table with a `role_id`
foreign key on `users`. That was changed here, deliberately.

A lookup table is worth it when rows can be **added at runtime**. Roles cannot.
The code itself decides what each role may do — Phase 5 will contain rules like
"only an admin may create users". If an administrator inserted a fifth row called
`AUDITOR`, nothing in the application would know what an auditor is allowed to
do. The table would look configurable while being nothing of the sort.

So the role is stored as a string column on the user row:

```java
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 30)
private Role role;
```

What this buys:

- no join on every single user lookup
- the database is readable — `SELECT role FROM users` shows `TECHNICIAN`, not `2`
- an invalid role cannot exist, because Java will not compile one

`EnumType.STRING` rather than the default `ORDINAL` matters more than it looks.
`ORDINAL` stores the enum's position — 0, 1, 2, 3. Reorder the enum later, or
insert a new role in the middle, and **every existing row silently changes
meaning**: technicians become admins. Storing the name makes that impossible.
This is a standard interview question, and the reason is exactly this.

## Where the role rules live

Whether a role needs a department is a property of the role, so it is written on
the enum itself:

```java
public enum Role {
    STUDENT("Student", false),
    TECHNICIAN("Technician", true),
    DEPARTMENT_HEAD("Department Head", true),
    ADMIN("Administrator", false);

    private final boolean departmentRequired;
}
```

The service then reads one flag instead of running an if-else chain over four
role names:

```java
if (!role.isDepartmentRequired()) { ... }
```

Add a fifth role later and there is exactly one place to update. An if-else chain
would be scattered across every method that cares.

## The business rules

| Rule | Reason |
|---|---|
| Email is unique | It is the login identity. Two accounts on one email means nobody can log in predictably. |
| Email is stored lowercase and trimmed | `Ravi@College.edu` and `ravi@college.edu` are one person. Without this, a student could create a second account by changing capitalisation. |
| Passwords are hashed with BCrypt, never stored as typed | A stolen database must not hand over everyone's password. |
| Password must be at least 8 characters | A minimum floor. Real strength rules would frustrate users more than they help here. |
| Technicians and department heads **must** have a department | They receive work through their department. Without one they can never be assigned anything. |
| Students and admins **must not** have a department | A student reports problems anywhere on campus; an admin manages the whole campus. Attaching either to one department would be meaningless data. |
| Staff cannot be added to an inactive department | Same reasoning as categories in Phase 3 — do not attach new things to a team that has been closed. |
| Users are deactivated, never deleted | Their requests, comments and assignments all point at them. Deleting the row would leave the history full of broken references. |
| A user cannot be reactivated while their department is inactive | Reactivating them would recreate the exact state the department rule forbids. |

### One rule added to Phase 3

`DepartmentService.deactivate()` now also refuses when the department still has
active staff:

```java
if (userRepository.existsByDepartmentIdAndActiveTrue(id)) {
    throw new BusinessRuleException(
            "Move or deactivate this department's staff before deactivating it");
}
```

An active technician whose department is closed sits in a dead end — nothing can
be assigned to them, but the system still shows them as available.

## Password hashing, explained

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

Two things make BCrypt the right choice over a plain hash like SHA-256:

**It salts automatically.** A salt is random data mixed into the password before
hashing. Two students who both pick `campus123` get completely different stored
values. Without salting, an attacker hashes a list of common passwords once and
matches it against every row at the same time.

**It is deliberately slow.** SHA-256 is built to be fast, which is exactly wrong
for passwords — fast means billions of guesses per second. BCrypt takes a
measurable fraction of a second on purpose. A single login never notices; an
attacker trying millions of guesses very much does.

The hash is one-way. Nothing in this codebase can turn it back into a password,
which is why a forgotten password gets **reset**, never emailed back.

### Why only `spring-security-crypto`, not the full starter

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
</dependency>
```

Adding `spring-boot-starter-security` would switch on Spring Security's whole
filter chain, which locks every endpoint behind a login page — before any login
endpoint exists. Every existing endpoint would break immediately.

`spring-security-crypto` is just the hashing classes with no auto-configuration.
Phase 5 upgrades to the full starter when there is actually something to log in
to.

## Three DTOs instead of one

| DTO | Fields | Why separate |
|---|---|---|
| `CreateUserRequest` | fullName, email, password, role, departmentId | Password is required exactly once — at creation. |
| `UpdateUserRequest` | fullName, role, departmentId | **No password, no email.** |
| `ChangePasswordRequest` | newPassword | A password change is its own deliberate action. |

`UpdateUserRequest` leaving out the password is the important one. If editing a
name and changing a password shared a DTO, a form that forgot to send the
password field would overwrite the stored hash with an empty value. Separating
them makes that mistake impossible rather than merely unlikely.

Email is left out for a different reason: it is the login identity. Changing it
changes who the account is, which deserves its own flow with verification later.

`UserResponse` has **no password field of any kind**, so a hash cannot leak
through an endpoint even by accident. This is the clearest example of why the
project returns DTOs and not entities.

## Reading the key code

### The service applies one rule for both create and update

```java
private Department resolveDepartment(Role role, Long departmentId) {
    if (!role.isDepartmentRequired()) {
        if (departmentId != null) {
            throw new BusinessRuleException("A " + role.getDisplayName().toLowerCase()
                    + " is not attached to a department");
        }
        return null;
    }

    if (departmentId == null) {
        throw new BusinessRuleException("A " + role.getDisplayName().toLowerCase()
                + " must belong to a department");
    }

    Department department = departmentRepository.findById(departmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Department", departmentId));
    if (!department.isActive()) {
        throw new BusinessRuleException("Department '" + department.getName()
                + "' is inactive and cannot take new staff");
    }
    return department;
}
```

One private method, called from both `create` and `update`. The rule cannot drift
between the two paths, which is what happens when validation is copy-pasted.

Note that it rejects a department for a student rather than quietly ignoring it.
Silently dropping data the client sent hides bugs in the frontend.

### `left join fetch`, not `join fetch`

```java
@Query("""
        select u from User u
        left join fetch u.department d
        where (:role is null or u.role = :role)
          and (:departmentId is null or d.id = :departmentId)
          and (:activeOnly = false or u.active = true)
        order by u.fullName asc
        """)
```

Phase 3 used `join fetch` for categories because a category **always** has a
department. A user might not — students and admins have none. A plain `join`
would drop every student from the results. `left join` keeps them, with a null
department.

Same N+1 avoidance as before: one query loads users and their departments
together.

### The roles endpoint

```java
@GetMapping("/roles")
public List<RoleOption> roles() { ... }
```

Returns:

```json
[{"value":"STUDENT","label":"Student","departmentRequired":false},
 {"value":"TECHNICIAN","label":"Technician","departmentRequired":true}, ...]
```

The admin screen fills its role dropdown from this instead of hardcoding four
options in JavaScript. It also gets `departmentRequired`, so the form can show or
hide the department field the moment a role is picked — the same rule the server
enforces, without duplicating it in the frontend.

Spring matches the literal path `/api/users/roles` ahead of `/api/users/{id}`, so
the two do not collide.

## The API

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/users?role=TECHNICIAN&departmentId=1&activeOnly=true` | List, all filters optional |
| GET | `/api/users/{id}` | One user |
| GET | `/api/users/roles` | Role list for dropdowns |
| POST | `/api/users` | Create → 201 |
| PUT | `/api/users/{id}` | Update name, role, department |
| PUT | `/api/users/{id}/password` | Reset password → 204 |
| DELETE | `/api/users/{id}` | Deactivate → 204 |
| POST | `/api/users/{id}/activate` | Reactivate → 204 |

## Table created

```sql
users(id, full_name, email UNIQUE, password_hash, role, department_id FK NULL,
      active, created_at, updated_at)
```

`department_id` is nullable because students and admins genuinely have none. The
database cannot express "required for two roles, forbidden for the other two", so
that rule lives in the service. The database still guarantees what it can: the
email is unique and the foreign key is real.

## Tests

`UserServiceTest` — 5 tests, service layer only, repositories mocked.

- email is lowercased and trimmed, and the stored value is a hash, not the typed password
- a duplicate email is rejected regardless of capitalisation
- a technician without a department is rejected
- a student **with** a department is rejected
- staff cannot be added to an inactive department

The password encoder is real rather than mocked in these tests. That is
deliberate: mocking it would prove only that a method was called, while the real
one proves `passwordEncoder.matches("student123", storedHash)` is true and the
stored value is not the plain password.

Nothing tests the controller, the DTOs or the repository methods. Those either
only delegate or are generated by Spring Data — testing them checks the framework,
not this project.

## How to try it yourself

```bash
./mvnw spring-boot:run
```

Create a student:

```bash
curl -X POST http://localhost:8080/api/users -H "Content-Type: application/json" -d "{\"fullName\":\"Ravi Kumar\",\"email\":\"Ravi@College.edu\",\"password\":\"student123\",\"role\":\"STUDENT\"}"
```

The response comes back with `"email":"ravi@college.edu"` — lowercased — and no
password field at all.

Now try to break it:

```bash
curl -X POST http://localhost:8080/api/users -H "Content-Type: application/json" -d "{\"fullName\":\"Amit Sharma\",\"email\":\"amit@college.edu\",\"password\":\"tech1234\",\"role\":\"TECHNICIAN\"}"
```
→ 422, a technician must belong to a department

```bash
curl -X POST http://localhost:8080/api/users -H "Content-Type: application/json" -d "{\"fullName\":\"Other\",\"email\":\"RAVI@college.edu\",\"password\":\"student123\",\"role\":\"STUDENT\"}"
```
→ 409, the email is already registered even though the capitalisation differs

```bash
curl -X POST http://localhost:8080/api/users -H "Content-Type: application/json" -d "{\"fullName\":\"X\",\"email\":\"x@college.edu\",\"password\":\"123\",\"role\":\"STUDENT\"}"
```
→ 400 with `fieldErrors.password`

Check the hash is real:

```bash
mysql -u root -p -e "SELECT email, password_hash FROM campusfix.users"
```

The stored value starts with `$2a$10$` — the BCrypt marker — and looks nothing
like what was typed.

## What is deliberately not here yet

- **No login and no JWT.** Phase 5. Right now anyone can call `POST /api/users`
  and create an admin, which is why the endpoints are not exposed to a browser yet.
- **No "change my own password" flow.** The current endpoint is an admin reset. A
  self-service change needs the old password checked first, which needs to know
  who is logged in — Phase 5 again.
- **No email verification or password reset by email.** Needs mail infrastructure;
  out of scope for the core product.
- **No pagination.** A college has a few thousand users, but the admin screen
  filters by role and department. Pagination arrives in Phase 11 with the list
  screens that actually need it.

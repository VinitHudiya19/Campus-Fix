# Phase 5 — Login and Security

## What we built

Until now every endpoint was open. Anyone who could reach the server could
create an admin account. This phase closes that.

- `POST /api/auth/login` — email and password in, a signed token out
- a filter that reads that token on every request and works out who is calling
- one place that says which role may reach which endpoint
- `GET /api/auth/me`, so a page refresh does not lose who you are
- `PUT /api/auth/password`, a self-service password change
- a first administrator created automatically on an empty database

## The problem this solves

HTTP has no memory. Each request arrives on its own with no idea that the
previous one came from the same person. Something has to travel with every
request to prove identity.

The two usual answers:

| Approach | How it works | Cost |
|---|---|---|
| **Server session** | Server stores who you are, browser holds a session id in a cookie | Server has to remember every logged-in user; two servers need shared session storage |
| **JWT (chosen)** | Server hands out a signed token, browser sends it back on every request | Server remembers nothing; a token cannot be withdrawn before it expires |

JWT was chosen because the server keeps no state at all. Any instance can serve
any request, which is what makes the app straightforward to deploy later. The
cost is real and covered under "the honest trade-off" below.

## What a JWT actually is

Three base64 pieces joined by dots:

```
eyJhbGciOiJIUzM4NCJ9 . eyJzdWIiOiI0Iiwicm9sZSI6IlNUVURFTlQiLCJleHAiOjE3ODc0NTMwMjh9 . VWd9NeVGFOoLoCLZ...
      header                              payload (claims)                                    signature
```

The middle piece decodes to plain readable JSON:

```json
{"sub":"4","email":"priya@college.edu","name":"Priya Nair","role":"STUDENT","iat":1787424254,"exp":1787453054}
```

**This is the part people get wrong.** A JWT is *encoded*, not *encrypted*.
Anyone holding the token can read every claim in it. Nothing secret may go
inside — no password, no private note.

What the signature guarantees is that the claims have not been **changed**. The
server signs `header.payload` with a secret key only it knows. Change
`"role":"STUDENT"` to `"role":"ADMIN"` and the signature no longer matches what
was signed, so the token is thrown out. An attacker cannot produce a matching
signature without the key.

This was tested rather than assumed — see "proving it actually works" below.

## How a request flows

```
POST /api/auth/login  { email, password }
        │
        ▼
   AuthService: find user by email
                BCrypt check of the password
                is the account active?
        │
        ▼
   token issued, valid 8 hours
        │
        ▼
Browser stores it and sends it on every later request:
   Authorization: Bearer eyJhbGci...
        │
        ▼
JwtAuthenticationFilter   verify signature → build AuthenticatedUser → put in the security context
        │
        ▼
SecurityConfig            does this role reach this endpoint?
        │
        ▼
Controller                runs, or 401 / 403 comes back
```

The important detail: **step four touches no database.** Everything needed to
authorise the request — user id, role, department — is inside the token, already
proven genuine by the signature. Authorising a request costs one hash check
rather than a query.

## The security policy, in one place

All of it lives in `SecurityConfig`:

| Endpoint | Who |
|---|---|
| `POST /api/auth/login`, `GET /api/hello` | Anyone |
| Static pages, CSS, JS | Anyone |
| `GET /api/departments/**`, `GET /api/categories/**` | Any signed-in user |
| Any other method on departments/categories | `ADMIN` |
| `/api/users/**` | `ADMIN` |
| Everything else | Any signed-in user |

Students can *read* categories because they have to pick one to report a
problem. They cannot change them.

This is deliberately one list rather than `@PreAuthorize` sprinkled through the
controllers. With annotations, answering "who can reach this endpoint?" means
opening every controller. Here it is one screen.

## Reading the key code

### The filter never rejects anything

```java
String token = bearerToken(request);
if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
    jwtService.readClaims(token).ifPresent(claims -> authenticate(claims, request));
}
filterChain.doFilter(request, response);
```

If the token is missing or invalid, the filter shrugs and passes the request on
unauthenticated. It does **not** return 401.

That is intentional. If the filter rejected bad tokens itself, it would also
reject requests to the login page and every public URL. Deciding what needs
authentication belongs to one place — `SecurityConfig` — and this filter only
answers "who is this, if anyone?"

### An invalid token is not an exception worth throwing

```java
public Optional<Claims> readClaims(String token) {
    try {
        return Optional.of(Jwts.parser().verifyWith(signingKey).build()
                .parseSignedClaims(token).getPayload());
    } catch (JwtException | IllegalArgumentException ex) {
        return Optional.empty();
    }
}
```

Expired and forged tokens are ordinary events on a public API, not bugs. Letting
them fly as exceptions would fill the log with stack traces from bots. An empty
`Optional` says "no valid identity here" and the caller decides what that means.

### `ROLE_` prefix

```java
new SimpleGrantedAuthority("ROLE_" + role.name())
```

`hasRole("ADMIN")` silently looks for an authority literally named `ROLE_ADMIN`.
Spring adds the prefix to the *check* but not to the *authority*, so it has to be
stored with it. Forget it, and every rule fails with a 403 that looks like a
configuration bug. (`hasAuthority("ADMIN")` is the version with no prefix magic.)

### Why CSRF is switched off

```java
.csrf(csrf -> csrf.disable())
```

Not laziness. CSRF attacks work because a browser **automatically** attaches
cookies to any request to a site, including one triggered from an attacker's
page. This API uses no cookies — the token is attached by our own JavaScript,
and an attacker's page cannot read it or make the browser send it. There is
nothing for CSRF protection to protect, and leaving it on would break every POST
with a missing-token error.

If this app ever stores the token in a cookie, CSRF protection has to come back.

### Login gives the same answer for both failures

```java
User user = userRepository.findByEmailWithDepartment(email)
        .orElseThrow(() -> new AuthenticationFailedException("Email or password is incorrect"));

if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
    throw new AuthenticationFailedException("Email or password is incorrect");
}
```

Unknown email and wrong password produce the identical message. Saying "no such
user" would let anyone test a list of addresses and learn which are registered —
useful for a targeted phishing mail. The real reason is written to the debug log,
where only a developer sees it.

Deactivated accounts are the deliberate exception: they get a clear "this account
has been deactivated" instead. It does admit the email exists, but the
alternative sends a suspended staff member off to reset a password that was never
the problem.

### A password change needs the old password

```java
if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
    throw new AuthenticationFailedException("Your current password is incorrect");
}
```

Without this, anyone who found an unlocked laptop could lock the real owner out
of their own account permanently. The admin reset endpoint from Phase 4 is
different on purpose — an admin resetting someone else's password does not know
the old one.

### Why the token is not trusted for `/api/auth/me`

```java
public CurrentUserResponse currentUser() {
    return CurrentUserResponse.from(loadSignedInUser());   // reads the database
}
```

The token is a photograph taken at login. If an admin moves a technician to a
different department an hour later, the token still names the old one. For
*authorising* a request that snapshot is good enough and fast. For *displaying*
who you are, it would show stale information for up to eight hours, so `/me`
reads the database.

### The first administrator

Only an admin can create users. On an empty database there is no admin, so
nobody could ever make one. `AdminSeeder` breaks the loop:

```java
if (userRepository.existsByRole(Role.ADMIN)) {
    return;                       // already sorted, do nothing
}
```

It runs on every startup and does nothing once any admin exists, so it is safe to
leave in permanently. It never resets an existing account. The credentials come
from `ADMIN_EMAIL` and `ADMIN_PASSWORD`, with development defaults, and it logs a
warning telling you to change the password.

### Two rules that stop an admin locking everyone out

```java
if (isSelf(id)) throw new BusinessRuleException("You cannot deactivate your own account");
if (isSelf(id) && request.role() != user.getRole())
    throw new BusinessRuleException("You cannot change your own role");
```

Together these guarantee at least one working administrator always exists. An
admin cannot deactivate or demote *themselves*, so the only way to remove an
admin is for a **different** admin to do it — and that other admin is still there
afterwards. No "count the admins" query is needed; the guarantee follows from the
two rules.

## Configuration

```properties
campusfix.jwt.secret=${JWT_SECRET:campusfix-local-development-signing-key-change-me}
campusfix.jwt.expiry-minutes=${JWT_EXPIRY_MINUTES:480}
campusfix.admin.email=${ADMIN_EMAIL:admin@campusfix.local}
campusfix.admin.password=${ADMIN_PASSWORD:admin12345}
```

**The secret is the whole security of the system.** Anyone holding it can mint a
token for any user, including an admin. On a real server it must come from the
environment. The committed default exists only so a fresh clone runs, and it is
named to make that obvious.

`JwtProperties` refuses to start the application if the secret is under 32
characters:

```java
if (secret == null || secret.length() < 32) {
    throw new IllegalStateException("campusfix.jwt.secret must be at least 32 characters ...");
}
```

Failing at startup is much better than failing at the first login. A short key
would either throw at runtime or, worse, weaken the signature.

The key length also picks the algorithm. jjwt uses the strongest HMAC the key
supports, so the 52-character development secret produces `HS384` rather than
`HS256` — visible in the token header.

**Eight hours** for expiry: long enough to cover a working day without a second
login, short enough that a stolen token stops working the same day.

### One line worth explaining

```properties
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
```

With Spring Security on the classpath and no `UserDetailsService` bean, Spring
Boot helpfully invents an in-memory user and prints a random password at every
startup. This app authenticates against the `users` table, so that default is
pure noise in the log and misleading to anyone reading it. Excluding it removes
the line.

## The honest trade-off

**A JWT cannot be withdrawn.** Deactivate a user at 10:00 and their existing
token keeps working until it expires. Nothing on the server is tracking it.

This is not a bug in the implementation; it is the cost of statelessness, and any
JWT system has it. The usual answers:

| Fix | Cost |
|---|---|
| Short expiry + refresh tokens | Two token types, refresh storage, rotation logic |
| A denylist of revoked tokens | The server keeps state again — the thing JWT avoided |
| Check `active` on every request | One query per request — same cost as sessions |

For a campus tool where deactivation means "this person left" rather than "this
account is compromised right now", an eight-hour window is acceptable. This is
recorded as a known limitation rather than quietly ignored.

Login itself is protected: a wrong password fails immediately, and a deactivated
user cannot obtain a *new* token. Only tokens issued before deactivation survive.

## Proving it actually works

Security that is assumed to work usually does not, so the three classic attacks
were run against the live app.

**1. Privilege escalation.** Take a student's token, decode the payload, change
`"role":"STUDENT"` to `"role":"ADMIN"`, re-encode, keep the original signature:

```
→ 401
```

The signature covers the payload, so editing the payload breaks it.

**2. The `alg:none` attack.** A famous flaw in early JWT libraries: replace the
header with `{"alg":"none"}` and drop the signature, and a naive parser accepts
the token as unsigned:

```
→ 401
```

`verifyWith(signingKey)` demands a real signature, so the algorithm named in the
header cannot talk the parser out of checking it.

**3. A token signed with a different key.**

```
→ 401
```

One result worth recording honestly: appending a stray character to the end of a
valid signature is still accepted, because base64 decoding discards an incomplete
trailing character group, leaving the same signature bytes. This is a
canonicalisation quirk in the decoder, not a way in — it only works with a token
you already hold, and it grants nothing you did not already have. Every attempt
to *change* a token was rejected.

## The API

| Method | Path | Who | Purpose |
|---|---|---|---|
| POST | `/api/auth/login` | Anyone | Email + password → token |
| GET | `/api/auth/me` | Signed in | Who am I, read fresh from the database |
| PUT | `/api/auth/password` | Signed in | Change own password, old one required |

Login response:

```json
{
  "token": "eyJhbGciOiJIUzM4NCJ9...",
  "expiresInSeconds": 28800,
  "user": {
    "id": 3, "fullName": "CampusFix Administrator", "email": "admin@campusfix.local",
    "role": "ADMIN", "roleLabel": "Administrator", "departmentId": null, "departmentName": null
  }
}
```

The user is included so the browser can draw the correct menu straight away
instead of making a second call to `/api/auth/me`.

Errors keep the same shape as everything else:

| Situation | Status | Message |
|---|---|---|
| No token, or an invalid one | 401 | `You need to sign in to do that` |
| Wrong email or password | 401 | `Email or password is incorrect` |
| Valid token, wrong role | 403 | `Your role does not allow that action` |
| Deactivated account | 403 | `This account has been deactivated...` |

401 versus 403: **401 means "I do not know who you are", 403 means "I know
exactly who you are, and no".**

## Tests

`AuthServiceTest` — 3 tests, with a **real** encoder and a **real** token
service, not mocks.

- a correct password returns a token whose claims carry the right id and role
- an unknown email and a wrong password fail with the identical message
- a deactivated account is refused even with the correct password

Mocking the encoder here would only prove a method was called. Using the real one
proves a wrong password is genuinely rejected — which is the entire point of the
class.

The filter and `SecurityConfig` are not unit tested. They are configuration, and
the checks that matter are the attack attempts above, run against the running
application.

## How to try it yourself

```bash
./mvnw spring-boot:run
```

The log prints the seeded admin on first run. Sign in:

```bash
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"email\":\"admin@campusfix.local\",\"password\":\"admin12345\"}"
```

Copy the `token` value, then use it:

```bash
curl http://localhost:8080/api/auth/me -H "Authorization: Bearer PASTE_TOKEN_HERE"
```

Without a token:

```bash
curl http://localhost:8080/api/departments
```
→ 401

Paste the token into [jwt.io](https://jwt.io) to see the claims in plain text —
that is the point about encoded-not-encrypted, made visible.

Now create a student with the admin token, log in as them, and try:

```bash
curl -X POST http://localhost:8080/api/departments -H "Authorization: Bearer STUDENT_TOKEN" -H "Content-Type: application/json" -d "{\"name\":\"Test\"}"
```
→ 403, the role does not allow it

## What is deliberately not here yet

- **No refresh tokens.** One token, eight hours, log in again. Refresh tokens
  solve a problem this app does not have yet.
- **No token revocation.** Covered under the trade-off above.
- **No login rate limiting.** A real deployment should slow down repeated failed
  logins from one address. Noted as a limitation; it needs infrastructure this
  project does not have.
- **No "forgot password" email.** Needs a mail server. An admin reset covers it.
- **No login page.** Phase 10 builds the frontend. Right now the API is driven
  with curl.

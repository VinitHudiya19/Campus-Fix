# Phase 10 — Frontend

## What we built

Nine phases of API, driven by curl. This phase gives it a face.

Plain HTML, CSS and JavaScript with Bootstrap. **No React, no build step, no
`npm install`.** The files are served by Spring from `src/main/resources/static`,
so `./mvnw spring-boot:run` is still the whole toolchain.

| Screen | Who |
|---|---|
| Sign in | Everyone |
| Dashboard | Everyone — the tiles differ by role |
| Requests list | Everyone — the rows differ by role |
| Report a problem | Students |
| Request detail: description, timeline, actions, assignment | Everyone who can see it |
| Departments, Categories, Locations, Users, SLA targets | Admin |
| Change password | Everyone |

## Why no framework

React would need Node, a bundler, a `package.json`, and a build step in front of
Maven — for what is, in the end, a set of tables and forms talking to a REST API.
The gain would be state management this application does not have. The cost would
be a second toolchain a reader has to install before they can run anything.

Bootstrap arrives from a CDN, so there is nothing to install at all.

## The four files everything else is built from

```
static/
├── css/app.css      # small additions to Bootstrap, nothing more
└── js/
    ├── api.js       # every call to the backend
    ├── ui.js        # rendering helpers, escaping, states
    ├── app.js       # session and navigation
    └── crud.js      # the admin table-and-form, written once
```

Each page then has one small file of its own in `js/pages/`.

### `api.js` — one door to the backend

Every request goes through it: one place that attaches the token, one place that
turns a failure into a readable message, one place that notices a dead session.

```js
if (response.status === 401 && !path.endsWith('/auth/login')) {
    clearSession();
    window.location.href = 'login.html?expired=1';
}
```

An expired token bounces to the login page with an explanation, instead of every
panel on the screen showing its own error.

Network failure is separated from rejection, because they mean different things
to the user:

```js
catch (networkFailure) {
    throw new ApiError(0, 'Could not reach the server. Check your connection and try again.');
}
```

`fetch` only rejects when the request never completed, so this really is "no
connection" rather than "the server said no".

### `ui.js` — and the one function that matters

```js
function text(value) {
    return String(value)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}
```

Everything a person typed passes through `text()` before it reaches the page.

This is not decoration. The screens are built with template strings and
`innerHTML`, so a student who titles their request
`<img src=x onerror=alert(1)>` would otherwise have that run in the department
head's browser. Escaped, it is displayed as characters. **Every single
interpolation of user data in this project uses `UI.text()`** — one missed call
is one hole.

### `crud.js` — the admin screens, written once

Departments, categories, locations and users are the same screen four times: list
the rows, open a dialog to add or edit one, deactivate, restore. Written out four
times they would drift — one would lose its empty state, another would forget to
show field errors.

Each page is now a small configuration:

```js
Crud.mount({
    host: document.getElementById('table'),
    singular: 'Department',
    emptyTitle: 'No departments yet',
    emptyHint: 'Add the teams that handle problems — IT Support, Electrical, Facilities.',
    columns: [ { label: 'Name', render: row => UI.text(row.name) }, ... ],
    fields:  [ { name: 'name', label: 'Name', required: true }, ... ],
    load:       () => Api.get('/api/departments'),
    create:     values => Api.post('/api/departments', values),
    update:     (id, values) => Api.put('/api/departments/' + id, values),
    deactivate: id => Api.del('/api/departments/' + id),
    activate:   id => Api.post('/api/departments/' + id + '/activate')
});
```

Forty lines per admin screen instead of four hundred, and all four behave
identically because they *are* the same code.

## The rule this whole phase follows

**The server decides; the page asks.**

Every time the frontend was tempted to work something out for itself, it asks
instead. Three places where that matters:

### Which buttons appear on a request

```js
const actions = await Api.get(`/api/requests/${id}/available-actions`);
```

The page could work this out — it knows the status and the role. Then the rule
would exist in Java *and* in JavaScript, and one day they would disagree, showing
a button that fails with a 422 when pressed.

Instead the server returns exactly what this person may do right now. Proved in
the browser: on the same resolved request, the student sees **Confirm it is
fixed** and **Still not fixed**; the department head sees nothing at all.

### Whether a user needs a department

```js
const chosen = roles.find(r => r.value === form.elements.role.value);
wrap.classList.toggle('d-none', !chosen.departmentRequired);
```

`departmentRequired` comes from `/api/users/roles`, which reads it off the same
Java enum the service validates against. Verified in the browser: choosing
Technician or Department Head reveals the field, Student or Administrator hides
it.

### Which priorities a student may pick

```js
priorities.filter(p => p.studentSelectable).forEach(...)
```

`CRITICAL` never reaches the dropdown, because the server refuses it from a
student. Offering a choice that always fails is a broken promise.

The navigation menu is the one exception, and it is honest about it:

```js
// This mirrors what the server allows rather than deciding it: hiding a link is
// a convenience, not a security measure — anyone can type the URL, and the API
// refuses them regardless.
```

## Loading, empty and error — the three states

A table that is blank while loading and blank again when there is nothing to show
tells the user nothing. Every list on every screen handles all three:

```js
UI.loading(host, 'Loading requests…');
try {
    const result = await Api.get(...);
    if (result.content.length === 0) {
        UI.empty(host, 'Nothing matches', anyFilterSet() ? 'Try clearing the filters.' : hint);
        return;
    }
    render(result);
} catch (error) {
    UI.failed(host, error.message, load);   // with a "Try again" button
}
```

Two details worth copying:

**The empty message depends on why it is empty.** "Nothing matches — try clearing
the filters" is useful; "no requests" when three exist behind a filter is a lie.

**The error state carries a retry button.** A page that fails and then does
nothing forces a full reload and loses the user's place.

Empty states are written for the person reading them:

| Role | What they see on an empty list |
|---|---|
| Student | "You have not reported anything yet — use *Report a problem* when something needs fixing." |
| Technician | "Nothing is assigned to you — your department head assigns work here." |
| Admin | "No requests have been reported yet." |

Failures also fail **partially**. On the request detail page the description
loads first; the timeline, the action buttons and the assignment panel each load
after and each fail on their own. A broken timeline does not take the page with
it.

## Where the token lives, honestly

```js
const TOKEN_KEY = 'campusfix.token';
sessionStorage.setItem(TOKEN_KEY, ...);
```

`sessionStorage`, not `localStorage`: the token disappears when the tab closes,
which limits the damage on a shared library computer where nobody signs out.

The honest limitation, and it is written in the code as well as here:
**JavaScript can read it either way.** A cross-site scripting hole would expose
it. The alternative — an `HttpOnly` cookie the browser attaches automatically —
is safer against XSS but brings CSRF straight back, which is exactly what
[Phase 5](phase-05-login-and-security.md) disabled protection for. That would
mean CSRF tokens and a different session model.

For a campus tool with an eight-hour token, `sessionStorage` plus escaping
everything is the reasonable trade. It is a trade, not a free win.

Signing out is simply forgetting the token:

```js
// Nothing to tell the server: a JWT is not stored anywhere on it, so signing
// out is simply forgetting the token on this machine.
```

## The look

Rules followed deliberately:

- **light theme**, one page background, white cards
- **borders, not shadows** — on a light page a hairline reads as structure; six
  drop shadows read as clutter
- **muted badge colours** — a table of twenty requests has twenty status pills;
  saturated ones make it unreadable
- **no gradients, no glassmorphism, no animation** beyond Bootstrap's own
- tables and forms that look like tables and forms

Two small things that make the screens easier to use:

**Relative time next to absolute time.** "Aug 25, 2026, 02:42 PM" *and* "in 2
days". A deadline is much easier to judge as a distance.

**The routing hint on the report form.** Choosing a category shows *"This goes to
IT Support"*. The student never picks a department — that is the whole design
from Phase 3 — but telling them where it went is reassuring, and makes an
obviously wrong category easy to spot before sending.

## One security change on the server

```java
.requestMatchers("/", "/*.html", "/css/**", "/js/**", "/favicon.ico").permitAll()
```

The HTML files are public. That sounds wrong until you look at what is in them:
markup and no data. The token lives in a header, and a browser loading an HTML
file cannot send one, so if the pages were protected nobody could ever reach the
login screen.

The data behind them is protected exactly as before. Every page's JavaScript
redirects to the login screen when there is no session.

## Verified in a browser, not just assumed

The whole student-to-head flow was driven through the actual UI:

| Step | Result |
|---|---|
| Sign in as a student | Dashboard with their four tiles |
| Report a problem | `CF-2026-000003` created, redirected to it |
| Student's actions on the new request | "Nothing to do right now" — correct, it is `OPEN` |
| Sign in as the department head | Sees all three IT Support requests, plus the *Unassigned only* filter |
| Head's actions on the same request | **Reject** only, plus the assignment panel |
| Assign to Amit Sharma with a note | Status → `Assigned`, timeline updated, actions became **Start work** / **Reject** |
| Technician dropdown | "Amit Sharma (0 open)", "Sana Iqbal (0 open)" — the department's staff, with workload |
| User form, role switching | Department field shown for Technician and Department Head, hidden for Student and Administrator |
| SLA page | All four targets, with "75% (after 18 hours)" worked out from the percentage |

One thing found this way: the automated click helper did not always trigger a
real form submission, while `form.requestSubmit()` did. That was the test harness
rather than the application — the same forms submit normally by hand and the
network log confirmed the `POST … → 201`.

## Files added

```
src/main/resources/static/
├── login.html  index.html  requests.html  request-new.html  request-detail.html
├── departments.html  categories.html  locations.html  users.html  sla.html
├── password.html
├── css/app.css
└── js/
    ├── api.js  ui.js  app.js  crud.js
    └── pages/  (one small file per screen)
```

## What is deliberately not here yet

- **No notifications.** Nobody is told their request was resolved; they have to
  look. A bell icon that never rings would be worse than its absence.
- **No search box.** Filters cover status, category and priority. Full-text
  search over titles needs a backend endpoint that does not exist yet.
- **No charts or reporting screen.** The SLA state is on every request, but "how
  is IT Support doing this month?" needs the reporting phase.
- **No file uploads.** A photo of the broken fan would help; it needs file
  storage.
- **Bootstrap comes from a CDN**, so the pages need internet access. Vendoring it
  into `static/` is a one-file change if that ever matters.
- **Not audited for accessibility.** Labels, roles and focus order are handled,
  but nobody has run it through a screen reader, so that is not a claim being
  made.

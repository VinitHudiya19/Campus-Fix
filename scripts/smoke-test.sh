#!/usr/bin/env bash
#
# End-to-end check of every feature, against a running CampusFix with the demo
# profile loaded. Exercises the real HTTP API as each role, so it catches the
# things unit tests cannot: security rules, serialisation, and whether the
# pieces actually fit together.
#
#   ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
#   ./scripts/smoke-test.sh
#
# Against a deployed instance, pass its address and admin credentials:
#
#   BASE_URL=https://campusfix.onrender.com \
#   ADMIN_EMAIL=you@college.edu ADMIN_PASSWORD=... ./scripts/smoke-test.sh
#
# Exits non-zero if anything fails, so it can be used in CI later.
#
# It writes real data — a department, a category, a location, a user and a
# request, all suffixed with a run id so repeated runs never collide. Run it
# against a scratch database, or reseed the demo data afterwards, rather than
# against anything you are about to demonstrate.

BASE="${BASE_URL:-http://localhost:8080}"
PASS=0
FAIL=0
FAILURES=""

# Everything this script creates carries a unique suffix. Without it a second
# run collides with the first on a unique name, and one 409 cascades into
# dozens of misleading failures further down.
RUN=$(date +%s)

check() {
    local name="$1" expected="$2" actual="$3"
    if [ "$expected" = "$actual" ]; then
        PASS=$((PASS + 1))
    else
        FAIL=$((FAIL + 1))
        FAILURES="${FAILURES}\n  ${name}\n     expected: ${expected}\n     actual:   ${actual}"
        echo "  FAIL  ${name}  (expected ${expected}, got ${actual})"
    fi
}

contains() {
    local name="$1" needle="$2" haystack="$3"
    case "$haystack" in
        *"$needle"*) PASS=$((PASS + 1)) ;;
        *)
            FAIL=$((FAIL + 1))
            FAILURES="${FAILURES}\n  ${name}\n     expected to contain: ${needle}"
            echo "  FAIL  ${name}  (missing '${needle}')"
            ;;
    esac
}

section() { echo; echo "$1"; }

status() {  # method, path, token, [body]
    if [ -n "$4" ]; then
        curl -s -o /dev/null -w "%{http_code}" -X "$1" "$BASE$2" \
             -H "Authorization: Bearer $3" -H "Content-Type: application/json" -d "$4"
    else
        curl -s -o /dev/null -w "%{http_code}" -X "$1" "$BASE$2" -H "Authorization: Bearer $3"
    fi
}

body() {    # method, path, token, [body]
    if [ -n "$4" ]; then
        curl -s -X "$1" "$BASE$2" -H "Authorization: Bearer $3" \
             -H "Content-Type: application/json" -d "$4"
    else
        curl -s -X "$1" "$BASE$2" -H "Authorization: Bearer $3"
    fi
}

login() {
    curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
         -d "{\"email\":\"$1\",\"password\":\"$2\"}" |
        sed -n 's/.*"token":"\([^"]*\)".*/\1/p'
}

field() { sed -n "s/.*\"$2\":\([0-9]*\).*/\1/p" <<< "$1" | head -1; }

# Pulls a value out of a JSON array properly. sed cannot do this: a greedy
# regex picks the *last* match, which silently returns the wrong element.
pick_id_where() {   # json, field, value
    python -c "
import sys, json
data = json.load(sys.stdin)
key, want = sys.argv[1], sys.argv[2]
print(next((row['id'] for row in data if str(row.get(key)) == want), ''))
" "$2" "$3" <<< "$1"
}

pick_id_where_not() {   # json, field, value
    python -c "
import sys, json
data = json.load(sys.stdin)
key, avoid = sys.argv[1], sys.argv[2]
print(next((row['id'] for row in data if str(row.get(key)) != avoid), ''))
" "$2" "$3" <<< "$1"
}

# ---------------------------------------------------------------------------
section "AUTHENTICATION"

# The administrator is the one account the demo seeder does not own, so its
# credentials differ per deployment. Override them when running against a real
# server, where ADMIN_PASSWORD is not the local default.
ADMIN=$(login "${ADMIN_EMAIL:-admin@campusfix.local}" "${ADMIN_PASSWORD:-admin12345}")
HEAD=$(login neha.rao@college.edu demo1234)
TECH=$(login amit.sharma@college.edu demo1234)
STUDENT=$(login priya.nair@college.edu demo1234)
OTHER_STUDENT=$(login karan.mehta@college.edu demo1234)
ELEC_HEAD=$(login vikram.das@college.edu demo1234)

check "admin can sign in"                    "true"  "$([ -n "$ADMIN" ] && echo true || echo false)"
check "department head can sign in"          "true"  "$([ -n "$HEAD" ] && echo true || echo false)"
check "technician can sign in"               "true"  "$([ -n "$TECH" ] && echo true || echo false)"
check "student can sign in"                  "true"  "$([ -n "$STUDENT" ] && echo true || echo false)"

check "wrong password is refused"            "401"   "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' -d '{"email":"admin@campusfix.local","password":"wrong"}')"
check "unknown email is refused"             "401"   "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' -d '{"email":"nobody@college.edu","password":"demo1234"}')"
check "email case is ignored"                "200"   "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' -d '{"email":"PRIYA.NAIR@college.edu","password":"demo1234"}')"
check "blank credentials fail validation"    "400"   "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' -d '{"email":"","password":""}')"

ME=$(body GET /api/auth/me "$STUDENT")
contains "/me returns the signed-in user"    "priya.nair@college.edu" "$ME"
check "/me needs a token"                    "401"   "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/auth/me")"
check "a tampered token is refused"          "401"   "$(status GET /api/auth/me "${STUDENT}x9")"
check "wrong current password is refused"    "401"   "$(status PUT /api/auth/password "$STUDENT" '{"currentPassword":"nope","newPassword":"changed123"}')"

# ---------------------------------------------------------------------------
section "ROLE-BASED ACCESS"

check "student cannot list users"            "403"   "$(status GET /api/users "$STUDENT")"
check "technician cannot list users"         "403"   "$(status GET /api/users "$TECH")"
check "head cannot list users"               "403"   "$(status GET /api/users "$HEAD")"
check "admin can list users"                 "200"   "$(status GET /api/users "$ADMIN")"

check "student cannot create a department"   "403"   "$(status POST /api/departments "$STUDENT" '{"name":"Hacked"}')"
check "student can read categories"          "200"   "$(status GET /api/categories "$STUDENT")"
check "student can read locations"           "200"   "$(status GET /api/locations "$STUDENT")"
check "student cannot open reports"          "403"   "$(status GET /api/reports "$STUDENT")"
check "technician cannot open reports"       "403"   "$(status GET /api/reports "$TECH")"
check "head can open reports"                "200"   "$(status GET /api/reports "$HEAD")"
check "unauthenticated request is refused"   "401"   "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/requests")"

# ---------------------------------------------------------------------------
section "REFERENCE DATA (admin)"

DEPT_NAME="Grounds Keeping $RUN"
NEW_DEPT=$(body POST /api/departments "$ADMIN" "{\"name\":\"$DEPT_NAME\",\"description\":\"Lawns and paths\"}")
DEPT_ID=$(field "$NEW_DEPT" id)
check "create a department"                  "true"  "$([ -n "$DEPT_ID" ] && echo true || echo false)"
check "duplicate department name conflicts"  "409"   "$(status POST /api/departments "$ADMIN" "{\"name\":\"$(tr '[:upper:]' '[:lower:]' <<< "$DEPT_NAME")\"}")"
check "blank department name is invalid"     "400"   "$(status POST /api/departments "$ADMIN" '{"name":"  "}')"
check "update a department"                  "200"   "$(status PUT "/api/departments/$DEPT_ID" "$ADMIN" "{\"name\":\"$DEPT_NAME\",\"description\":\"Lawns, paths and hedges\"}")"

NEW_CAT=$(body POST /api/categories "$ADMIN" "{\"name\":\"Hedges $RUN\",\"departmentId\":$DEPT_ID}")
CAT_ID=$(field "$NEW_CAT" id)
check "create a category"                    "true"  "$([ -n "$CAT_ID" ] && echo true || echo false)"
contains "category carries its department"   "$DEPT_NAME" "$NEW_CAT"
check "duplicate name in same department"    "409"   "$(status POST /api/categories "$ADMIN" "{\"name\":\"hedges $RUN\",\"departmentId\":$DEPT_ID}")"
check "category needs a real department"     "404"   "$(status POST /api/categories "$ADMIN" '{"name":"Ghost","departmentId":99999}')"
check "department with categories is held"   "422"   "$(status DELETE "/api/departments/$DEPT_ID" "$ADMIN")"
check "deactivate the category"              "204"   "$(status DELETE "/api/categories/$CAT_ID" "$ADMIN")"
check "now the department can go"            "204"   "$(status DELETE "/api/departments/$DEPT_ID" "$ADMIN")"
check "reactivate the department"            "204"   "$(status POST "/api/departments/$DEPT_ID/activate" "$ADMIN")"
check "reactivate the category"              "204"   "$(status POST "/api/categories/$CAT_ID/activate" "$ADMIN")"

NEW_LOC=$(body POST /api/locations "$ADMIN" "{\"campus\":\"Main Campus\",\"building\":\"Test Block $RUN\",\"floor\":\"Floor 9\",\"room\":\"Room 900\"}")
LOC_ID=$(field "$NEW_LOC" id)
check "create a location"                    "true"  "$([ -n "$LOC_ID" ] && echo true || echo false)"
contains "location builds a display name"    "Main Campus - Test Block $RUN" "$NEW_LOC"
check "same place twice conflicts"           "409"   "$(status POST /api/locations "$ADMIN" "{\"campus\":\"main campus\",\"building\":\"test block $RUN\",\"floor\":\"floor 9\",\"room\":\"room 900\"}")"
check "campus list is available"             "200"   "$(status GET /api/locations/campuses "$STUDENT")"

# ---------------------------------------------------------------------------
section "USERS (admin)"

TEST_EMAIL="Test.Person.$RUN@college.edu"
NEW_USER=$(body POST /api/users "$ADMIN" "{\"fullName\":\"Test Person\",\"email\":\"$TEST_EMAIL\",\"password\":\"testpass123\",\"role\":\"STUDENT\"}")
USER_ID=$(field "$NEW_USER" id)
check "create a student"                     "true"  "$([ -n "$USER_ID" ] && echo true || echo false)"
contains "email is lowercased"               "test.person.$RUN@college.edu" "$NEW_USER"
check "no password in the response"          "false" "$(grep -q password <<< "$NEW_USER" && echo true || echo false)"
check "duplicate email conflicts"            "409"   "$(status POST /api/users "$ADMIN" "{\"fullName\":\"Copy\",\"email\":\"$(tr '[:lower:]' '[:upper:]' <<< "$TEST_EMAIL")\",\"password\":\"testpass123\",\"role\":\"STUDENT\"}")"
check "short password is invalid"            "400"   "$(status POST /api/users "$ADMIN" "{\"fullName\":\"X\",\"email\":\"x.$RUN@college.edu\",\"password\":\"123\",\"role\":\"STUDENT\"}")"
check "technician needs a department"        "422"   "$(status POST /api/users "$ADMIN" "{\"fullName\":\"Y\",\"email\":\"y.$RUN@college.edu\",\"password\":\"testpass123\",\"role\":\"TECHNICIAN\"}")"
check "student must not have one"            "422"   "$(status POST /api/users "$ADMIN" "{\"fullName\":\"Z\",\"email\":\"z.$RUN@college.edu\",\"password\":\"testpass123\",\"role\":\"STUDENT\",\"departmentId\":$DEPT_ID}")"
check "admin cannot deactivate themselves"   "422"   "$(status DELETE "/api/users/$(field "$(body GET /api/auth/me "$ADMIN")" id)" "$ADMIN")"
check "reset someone's password"             "204"   "$(status PUT "/api/users/$USER_ID/password" "$ADMIN" '{"newPassword":"resetpass123"}')"
check "the reset password works"             "true"  "$([ -n "$(login "test.person.$RUN@college.edu" resetpass123)" ] && echo true || echo false)"
check "deactivate the test user"             "204"   "$(status DELETE "/api/users/$USER_ID" "$ADMIN")"
check "deactivated user cannot sign in"      "403"   "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' -d "{\"email\":\"test.person.$RUN@college.edu\",\"password\":\"resetpass123\"}")"
check "roles endpoint feeds the dropdown"    "200"   "$(status GET /api/users/roles "$ADMIN")"

# ---------------------------------------------------------------------------
section "REQUESTS"

WIFI_CAT=$(field "$(body GET '/api/categories?activeOnly=true' "$ADMIN")" id)
NEW_REQ=$(body POST /api/requests "$STUDENT" "{\"title\":\"Smoke test request $RUN\",\"description\":\"Created by the smoke test to verify the whole flow works.\",\"categoryId\":$WIFI_CAT,\"locationId\":$LOC_ID,\"priority\":\"MEDIUM\"}")
REQ_ID=$(field "$NEW_REQ" id)
check "student can report a problem"         "true"  "$([ -n "$REQ_ID" ] && echo true || echo false)"
contains "it gets a readable number"         "CF-"   "$NEW_REQ"
contains "it starts OPEN"                    '"status":"OPEN"' "$NEW_REQ"
contains "the category chose the department" '"departmentName"' "$NEW_REQ"

check "student cannot pick CRITICAL"         "422"   "$(status POST /api/requests "$STUDENT" "{\"title\":\"Urgent thing\",\"description\":\"This is very urgent indeed.\",\"categoryId\":$WIFI_CAT,\"priority\":\"CRITICAL\"}")"
check "staff cannot report here"             "422"   "$(status POST /api/requests "$TECH" "{\"title\":\"Staff report\",\"description\":\"Should not be allowed through this route.\",\"categoryId\":$WIFI_CAT,\"priority\":\"LOW\"}")"
check "short title is invalid"               "400"   "$(status POST /api/requests "$STUDENT" "{\"title\":\"Hi\",\"description\":\"Long enough description here.\",\"categoryId\":$WIFI_CAT,\"priority\":\"LOW\"}")"
check "unknown category is not found"        "404"   "$(status POST /api/requests "$STUDENT" '{"title":"Ghost category","description":"Long enough description here.","categoryId":99999,"priority":"LOW"}')"

check "owner can read it"                    "200"   "$(status GET "/api/requests/$REQ_ID" "$STUDENT")"
check "another student gets 404, not 403"    "404"   "$(status GET "/api/requests/$REQ_ID" "$OTHER_STUDENT")"
check "the owning head can read it"          "200"   "$(status GET "/api/requests/$REQ_ID" "$HEAD")"
check "another head gets 404"                "404"   "$(status GET "/api/requests/$REQ_ID" "$ELEC_HEAD")"
check "admin can read it"                    "200"   "$(status GET "/api/requests/$REQ_ID" "$ADMIN")"

LIST=$(body GET "/api/requests?size=50" "$ADMIN")
contains "list is paginated"                 '"totalElements"' "$LIST"
check "filter by status"                     "200"   "$(status GET "/api/requests?status=OPEN" "$ADMIN")"
check "filter by priority"                   "200"   "$(status GET "/api/requests?priority=HIGH" "$ADMIN")"
check "unassigned filter"                    "200"   "$(status GET "/api/requests?unassignedOnly=true" "$HEAD")"
check "search finds the new request"         "1"     "$(field "$(body GET "/api/requests?search=request%20$RUN" "$STUDENT")" totalElements)"
check "search is case-insensitive"           "1"     "$(field "$(body GET "/api/requests?search=REQUEST%20$RUN" "$STUDENT")" totalElements)"
check "wildcard is escaped"                  "0"     "$(field "$(body GET '/api/requests?search=%25' "$ADMIN")" totalElements)"
check "search cannot cross scopes"           "0"     "$(field "$(body GET "/api/requests?search=request%20$RUN" "$OTHER_STUDENT")" totalElements)"
check "sorting works"                        "200"   "$(status GET "/api/requests?sort=dueAt,asc" "$ADMIN")"
check "unknown sort field is rejected"       "400"   "$(status GET "/api/requests?sort=password" "$ADMIN")"
check "priorities feed the form"             "200"   "$(status GET /api/requests/priorities "$STUDENT")"
check "statuses feed the filter"             "200"   "$(status GET /api/requests/statuses "$STUDENT")"

# ---------------------------------------------------------------------------
section "ASSIGNMENT"

TECHS=$(body GET "/api/requests/$REQ_ID/assignable-technicians" "$HEAD")
contains "head sees assignable technicians"  "Amit Sharma" "$TECHS"
contains "with their current workload"       "openRequests" "$TECHS"
# Must be the technician whose token the workflow checks below use, not just
# whoever happens to come first in the list.
TECH_ID=$(pick_id_where "$TECHS" fullName "Amit Sharma")

# A technician in a different department, looked up rather than guessed.
OUTSIDER=$(pick_id_where_not "$(body GET '/api/users?role=TECHNICIAN&activeOnly=true' "$ADMIN")" departmentName "IT Support")

check "student cannot assign"                "404"   "$(status POST "/api/requests/$REQ_ID/assign" "$STUDENT" "{\"technicianId\":$TECH_ID}")"
check "technician cannot assign"             "404"   "$(status POST "/api/requests/$REQ_ID/assign" "$TECH" "{\"technicianId\":$TECH_ID}")"
check "another head cannot assign"           "404"   "$(status POST "/api/requests/$REQ_ID/assign" "$ELEC_HEAD" "{\"technicianId\":$TECH_ID}")"

ASSIGNED=$(body POST "/api/requests/$REQ_ID/assign" "$HEAD" "{\"technicianId\":$TECH_ID,\"note\":\"Smoke test assignment\"}")
contains "head can assign"                   '"status":"ASSIGNED"' "$ASSIGNED"
check "cannot assign to the same person"     "422"   "$(status POST "/api/requests/$REQ_ID/assign" "$HEAD" "{\"technicianId\":$TECH_ID}")"
check "cannot assign outside the department" "422"   "$(status POST "/api/requests/$REQ_ID/assign" "$HEAD" "{\"technicianId\":$OUTSIDER}")"

HISTORY=$(body GET "/api/requests/$REQ_ID/assignments" "$HEAD")
contains "assignment history is recorded"    "Smoke test assignment" "$HISTORY"
check "assigned technician now sees it"      "200"   "$(status GET "/api/requests/$REQ_ID" "$TECH")"

# ---------------------------------------------------------------------------
section "STATUS WORKFLOW"

ACTIONS=$(body GET "/api/requests/$REQ_ID/available-actions" "$TECH")
contains "technician is offered Start work"  "START" "$ACTIONS"
check "student is offered nothing yet"       "[]"    "$(body GET "/api/requests/$REQ_ID/available-actions" "$STUDENT")"

check "cannot resolve before starting"       "422"   "$(status POST "/api/requests/$REQ_ID/resolve" "$TECH" '{"note":"Skipping ahead"}')"
contains "technician starts work"            '"status":"IN_PROGRESS"' "$(body POST "/api/requests/$REQ_ID/start" "$TECH" '{}')"
check "resolving needs a note"               "422"   "$(status POST "/api/requests/$REQ_ID/resolve" "$TECH" '{}')"
contains "technician resolves it"            '"status":"RESOLVED"' "$(body POST "/api/requests/$REQ_ID/resolve" "$TECH" '{"note":"Replaced the part"}')"

check "technician cannot confirm the fix"    "404"   "$(status POST "/api/requests/$REQ_ID/confirm" "$TECH" '{}')"
check "admin cannot confirm it either"       "404"   "$(status POST "/api/requests/$REQ_ID/confirm" "$ADMIN" '{}')"
contains "the student reopens it"            '"status":"REOPENED"' "$(body POST "/api/requests/$REQ_ID/reopen" "$STUDENT" '{"note":"Still broken"}')"
contains "work restarts"                     '"status":"IN_PROGRESS"' "$(body POST "/api/requests/$REQ_ID/start" "$TECH" '{}')"
contains "resolved again"                    '"status":"RESOLVED"' "$(body POST "/api/requests/$REQ_ID/resolve" "$TECH" '{"note":"Properly fixed now"}')"
CLOSED=$(body POST "/api/requests/$REQ_ID/confirm" "$STUDENT" '{}')
contains "the student closes it"             '"status":"CLOSED"' "$CLOSED"
check "a closed request cannot restart"      "422"   "$(status POST "/api/requests/$REQ_ID/start" "$TECH" '{}')"
check "a closed request cannot be assigned"  "422"   "$(status POST "/api/requests/$REQ_ID/assign" "$HEAD" "{\"technicianId\":$TECH_ID}")"

TIMELINE=$(body GET "/api/requests/$REQ_ID/timeline" "$STUDENT")
contains "timeline records the report"       "reported this problem" "$TIMELINE"
contains "timeline records the assignment"   "assigned this to" "$TIMELINE"
contains "timeline records the reopen"       "Still broken" "$TIMELINE"
contains "timeline records the close"        "confirmed" "$TIMELINE"

# ---------------------------------------------------------------------------
section "ATTACHMENTS"

TMP=$(mktemp -d)
ORIGINAL_DIR=$(pwd)
# Run from inside the temp directory and reference the files by relative name.
# Git Bash rewrites an absolute path when "; type=" is appended to it, and curl
# then cannot open the file at all — which looks like a server failure.
cd "$TMP" || exit 1
printf '\x89PNG\r\n\x1a\n' > photo.png; head -c 3000 /dev/urandom >> photo.png
printf '<?php system($_GET["c"]); ?>' > shell.jpg

OPEN_REQ=$(field "$(body GET '/api/requests?status=OPEN&size=1' "$ADMIN")" id)
UPLOAD=$(curl -s -X POST "$BASE/api/requests/$OPEN_REQ/attachments" -H "Authorization: Bearer $ADMIN" -F "file=@photo.png")
ATT_ID=$(field "$UPLOAD" id)
check "a real image uploads"                 "true"  "$([ -n "$ATT_ID" ] && echo true || echo false)"
contains "type detected from the bytes"      "image/png" "$UPLOAD"
check "no storage key is exposed"            "false" "$(grep -q storageKey <<< "$UPLOAD" && echo true || echo false)"

# The file is named .jpg and declares image/jpeg. Only the bytes give it away.
check "a renamed script is refused"          "400"   "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/requests/$OPEN_REQ/attachments" -H "Authorization: Bearer $ADMIN" -F "file=@shell.jpg;type=image/jpeg")"
check "cannot attach to a closed request"    "422"   "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/requests/$REQ_ID/attachments" -H "Authorization: Bearer $ADMIN" -F "file=@photo.png")"

HEADERS=$(curl -s -D - -o /dev/null "$BASE/api/requests/$OPEN_REQ/attachments/$ATT_ID" -H "Authorization: Bearer $ADMIN")
contains "download forces a save"            "Content-Disposition: attachment" "$HEADERS"
contains "download sets nosniff"             "X-Content-Type-Options: nosniff" "$HEADERS"
check "outsider cannot download"             "404"   "$(status GET "/api/requests/$OPEN_REQ/attachments/$ATT_ID" "$STUDENT")"
check "uploader can remove it"               "204"   "$(status DELETE "/api/requests/$OPEN_REQ/attachments/$ATT_ID" "$ADMIN")"
cd "$ORIGINAL_DIR" || exit 1
rm -rf "$TMP"

# ---------------------------------------------------------------------------
section "SLA AND ESCALATION"

check "admin reads SLA targets"              "200"   "$(status GET /api/sla "$ADMIN")"
check "head cannot change SLA targets"       "403"   "$(status PUT /api/sla/LOW "$HEAD" '{"durationHours":48,"warningPercentage":75}')"
check "admin changes a target"               "200"   "$(status PUT /api/sla/LOW "$ADMIN" '{"durationHours":72,"warningPercentage":75}')"
check "an impossible target is rejected"     "400"   "$(status PUT /api/sla/LOW "$ADMIN" '{"durationHours":0,"warningPercentage":150}')"
contains "the overdue check runs"            "escalated" "$(body POST /api/sla/check-now "$ADMIN" '{}')"
check "escalations are readable"             "200"   "$(status GET "/api/requests/$OPEN_REQ/escalations" "$ADMIN")"

# ---------------------------------------------------------------------------
section "REPORTS"

REPORT=$(body GET "/api/reports?days=30" "$ADMIN")
contains "report covers all departments"     "All departments" "$REPORT"
contains "it counts requests"                "totalRequests" "$REPORT"
contains "it measures compliance"            "slaCompliancePercent" "$REPORT"
contains "it lists departments"              "departmentName" "$REPORT"
contains "it ranks categories"               "topCategories" "$REPORT"

HEAD_REPORT=$(body GET "/api/reports?days=30" "$HEAD")
contains "head report is scoped"             "IT Support" "$HEAD_REPORT"
check "head sees only one department"        "1"     "$(grep -o '"departmentId"' <<< "$HEAD_REPORT" | wc -l | tr -d ' ')"
check "an odd window is rejected"            "400"   "$(status GET "/api/reports?days=13" "$ADMIN")"
check "all-time window works"                "200"   "$(status GET "/api/reports?days=0" "$ADMIN")"

# ---------------------------------------------------------------------------
section "STATIC PAGES"

for page in "" login.html index.html requests.html request-new.html request-detail.html \
            departments.html categories.html locations.html users.html sla.html \
            reports.html password.html css/app.css js/api.js js/crud.js js/pages/reports.js; do
    check "GET /$page" "200" "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/$page")"
done

section "HEALTH"
check "health check answers without a token"  "200" "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/actuator/health")"
contains "and reports UP"                     '"status":"UP"' "$(curl -s "$BASE/actuator/health")"
# Only health is exposed. The rest describe the application to anyone who asks.
check "other actuator endpoints are closed"   "401" "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/actuator/beans")"

# ---------------------------------------------------------------------------
echo
echo "=============================================="
printf " %d passed, %d failed\n" "$PASS" "$FAIL"
echo "=============================================="
if [ "$FAIL" -gt 0 ]; then
    printf "%b\n" "$FAILURES"
    exit 1
fi

# CampusFix Product Specification

## 1. Problem

College maintenance and service problems are often reported through WhatsApp, calls, verbal communication, or faculty members. This creates poor visibility:

- Who received the request?
- Who is responsible?
- What is its current status?
- How long has it been pending?
- Has the problem actually been fixed?
- Was the resolution accepted by the student?
- Which departments are missing their service targets?

CampusFix creates a structured workflow for these requests.

## 2. Product goal

Provide a simple system for:

- reporting
- assignment
- tracking
- resolution
- escalation
- verification
- feedback
- accountability

## 3. Terminology

Use "Service Request" in the application rather than "Complaint" wherever possible.

A request represents an issue or service need submitted by a student.

Examples:
- Wi-Fi not working
- projector failure
- broken classroom fan
- lab computer issue
- hostel maintenance
- library issue
- transport issue
- lost/found item

## 4. Roles

### STUDENT
Can:
- register/login
- create requests
- upload supporting image
- view own requests
- filter own requests
- add comments
- view request timeline
- confirm resolution
- reopen a request after resolution if still unresolved
- rate a closed request

### TECHNICIAN
Can:
- view assigned requests
- accept/start work
- update status according to allowed transitions
- add work notes
- mark request resolved

### DEPARTMENT_HEAD
Can:
- view department requests
- assign/reassign technicians
- monitor SLA
- handle escalations
- view department metrics

### ADMIN
Can:
- manage users
- manage departments
- manage categories
- manage locations
- configure SLA rules
- view system-wide metrics

## 5. Request lifecycle

Primary flow:

OPEN → ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED

Alternative flows:

OPEN → REJECTED

RESOLVED → REOPENED → IN_PROGRESS

Do not allow arbitrary status changes.

## 6. Status rules

### OPEN
Created but not assigned.

### ASSIGNED
A responsible technician has been assigned.

### IN_PROGRESS
Technician has started work.

### RESOLVED
Technician says the issue has been fixed.

### CLOSED
Student has confirmed the resolution.

### REOPENED
Student says the issue is still unresolved after it was marked resolved.

### REJECTED
Request is invalid/out of scope/duplicate/etc. Only authorized staff can reject.

## 7. Priority

Initial priorities:
- LOW
- MEDIUM
- HIGH
- CRITICAL

Do not assume these values are universal college policy. They are configurable product defaults.

A student may select LOW/MEDIUM/HIGH. Authorized staff may adjust priority, including CRITICAL.

## 8. SLA

Initial default configuration:

| Priority | Default SLA |
|---|---:|
| LOW | 72 hours |
| MEDIUM | 48 hours |
| HIGH | 24 hours |
| CRITICAL | 4 hours |

These are product defaults, not real-world standards.

Each request stores a due time derived from its priority/SLA configuration at creation.

SLA state:
- ON_TRACK
- DUE_SOON
- BREACHED

Suggested warning threshold: 75% of SLA elapsed.

## 9. Escalation

When SLA is breached and the request is unresolved:
TECHNICIAN → DEPARTMENT_HEAD

If still unresolved after an additional configurable escalation period:
DEPARTMENT_HEAD → ADMIN

Every escalation must be recorded in the activity timeline.

## 10. Category and department mapping

Students choose a category, not a department.

Example defaults:

| Category | Department |
|---|---|
| Wi-Fi | IT Support |
| Computer Lab | IT Support |
| Projector | AV Support |
| Fan/Light | Electrical |
| Hostel Maintenance | Hostel Maintenance |
| Library | Library |
| Transport | Transport |
| Sanitation | Facilities |

The mapping must be data-driven and manageable by Admin.

## 11. Location

A request should identify a structured location where possible:

- campus
- building
- floor
- room/lab

Do not force every issue to have a room number. Lost/found and transport requests may use different location information.

## 12. Activity timeline

Every meaningful event should be recorded.

Examples:
- request created
- category changed
- priority changed
- assigned
- reassigned
- status changed
- comment added
- SLA warning
- SLA breach
- escalation
- resolved
- reopened
- closed

## 13. Resolution confirmation

When a technician marks a request RESOLVED:
- student is notified
- student can confirm → CLOSED
- or report still unresolved → REOPENED

Reopening should require a reason.

## 14. Feedback

After closure:
- 1–5 rating
- optional comment

Only one final rating per request.

## 15. Attachments

First implementation:
- image attachments only
- validate file type
- validate file size
- store metadata/path in database
- local filesystem for development

Production-safe object storage can be introduced later.

## 16. Analytics

Admin:
- total requests
- open requests
- in-progress requests
- SLA breached
- resolved today
- average resolution time
- SLA compliance
- requests by department/category

Department Head:
- assigned requests
- pending requests
- due soon
- breached
- average resolution time
- rating

Student:
- own request counts

Do not manufacture impressive metrics.

## 17. Out of scope for initial release

- microservices
- Kafka
- Kubernetes
- mobile app
- AI chatbot
- ML-based classification
- automatic NLP duplicate detection
- complex chat system

These can be future ideas, not core requirements.

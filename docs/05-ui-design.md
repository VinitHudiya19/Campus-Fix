# CampusFix UI Design System

## Visual goal

Human-developed college operations software.

It should look polished but believable.

## Theme

Light theme.

Use:
- white cards
- soft gray page background
- dark readable text
- restrained blue as primary accent
- red/orange only for warnings/errors
- green for success

Do not overuse color.

## Avoid

- glassmorphism
- neon gradients
- giant rounded cards
- excessive shadows
- animated blobs
- fake 3D illustrations
- huge dashboard numbers everywhere
- excessive icon decoration
- AI-generated "SaaS landing page" aesthetics

## Layout

Desktop-first because college staff will mostly use laptops.

Typical:
- left sidebar
- top header
- content area
- responsive collapse for smaller screens

## Student pages

### Dashboard
- greeting
- active request summary
- recent requests
- quick "Create Request"
- simple status cards

### Create request
Fields:
- category
- location
- title
- description
- priority
- attachment

Keep the form simple.

### Request details
- request number
- status badge
- priority
- location
- assigned staff
- SLA/due time
- timeline
- comments
- attachment preview
- confirmation/reopen actions

## Technician pages

Focus on work:
- assigned requests table
- filters
- request details
- status actions
- work/resolution notes
- SLA indicator

## Admin pages

Operational rather than decorative:
- requests overview
- departments
- categories
- users
- SLA settings
- analytics

## Tables

Tables should support:
- readable columns
- status badges
- search/filter
- pagination
- empty state
- loading state
- error state

## Frontend implementation

Use:
- semantic HTML
- CSS variables
- reusable CSS classes
- vanilla JS modules
- fetch API
- Bootstrap only where it saves time

Do not build a fake component framework.

## UI copy

Use human labels:
- "Create Request"
- "Assign Technician"
- "Mark as Resolved"
- "Confirm Resolution"

Avoid:
- "Execute Workflow"
- "Initiate Resolution Protocol"
- overly technical wording

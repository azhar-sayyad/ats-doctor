# ATS Doctor — Project Documentation

Living, status-updateable implementation documentation for **ATS Doctor**, a
local-first, AI-provider-agnostic, single-user resume optimization system.

**Source of truth:** [`ATS-Doctor-PRD.md`](./ATS-Doctor-PRD.md) — PRD v1.2.
This documentation set derives from it and must never contradict it.

---

## Document map

| # | Document | Purpose |
|---|----------|---------|
| — | [`README.md`](./README.md) | This file — how the set works |
| 01 | [`01-product-scope.md`](./01-product-scope.md) | What we build and, importantly, what we do NOT |
| 02 | [`02-epics.md`](./02-epics.md) | Major product capabilities (EPIC-###) |
| 03 | [`03-features.md`](./03-features.md) | Implementable features (FEAT-###) |
| 04 | [`04-sprints.md`](./04-sprints.md) | Roadmap with measurable exit criteria (SPRINT-##) |
| 05 | [`05-backlog.md`](./05-backlog.md) | **Control plane** — master list of every item |
| 06 | [`06-technical-tasks.md`](./06-technical-tasks.md) | Engineering tasks (TASK-###) |
| 07 | [`07-api-contract.md`](./07-api-contract.md) | `/api/v1` endpoint contract |
| 08 | [`08-database-schema.md`](./08-database-schema.md) | PostgreSQL schema |
| 09 | [`09-ai-specification.md`](./09-ai-specification.md) | AI abstraction layer + per-task spec |
| 10 | [`10-ui-ux-specification.md`](./10-ui-ux-specification.md) | Screens, states, flows |
| 11 | [`11-testing-strategy.md`](./11-testing-strategy.md) | Unit/integration/AI-eval strategy |
| 12 | [`12-release-plan.md`](./12-release-plan.md) | MVP v0.1 → future |
| 13 | [`13-project-status.md`](./13-project-status.md) | **Dashboard** — derived from the backlog |
| 14 | [`14-decision-log.md`](./14-decision-log.md) | Architectural/product decisions (DEC-###) |
| 15 | [`15-change-log.md`](./15-change-log.md) | Every material change (CL-###) |

## Source-of-truth hierarchy

```text
PRD v1.2                 ← product requirements win here
    ↓
Decision Log             ← explicit architectural decisions win here
    ↓
Epics → Features → Sprints → Tasks
    ↓
Implementation
```

Conflict rules:
1. PRD v1.2 wins for product requirements.
2. Decision Log wins for explicit architectural decisions.
3. Lower-level documents may elaborate but must not contradict higher levels.
4. Record conflicts in the Change Log rather than silently resolving them.

## ID conventions

| Prefix | Meaning | Example |
|--------|---------|---------|
| `EPIC-###` | Major capability | `EPIC-002` |
| `FEAT-###` | Feature within an epic | `FEAT-014` |
| `TASK-###` | Engineering task within a feature | `TASK-033` |
| `SPRINT-##` | Sprint | `SPRINT-03` |
| `DEC-###` | Architectural/product decision | `DEC-006` |
| `CL-###` | Change-log entry | `CL-003` |
| `BUG-###` / `SPIKE-###` | Defects / research spikes (added as needed) | `BUG-001` |

Rules:
- IDs are **stable and unique**. Never reuse, renumber, or version an ID.
- Never delete a historical ID — update its status or mark it `CANCELLED`/`DEFERRED`.
- Do not create `FEAT-014-new` / `FEAT-014-v2`. Update `FEAT-014`.

## Status conventions

Implementation statuses (Epics/Features/Tasks/Stories):

```text
BACKLOG     Not yet planned
PLANNED     Assigned to a future sprint
READY       Dependencies complete; implementation can begin
IN_PROGRESS Currently being implemented
BLOCKED     Blocked by a dependency/problem
IN_REVIEW   Implementation complete, awaiting review
TESTING     Being tested
DONE        Acceptance criteria satisfied
CANCELLED   Will not be implemented
DEFERRED    Intentionally moved out of scope
```

Sprint statuses:

```text
PLANNED | ACTIVE | COMPLETED | BLOCKED | CANCELLED
```

Status truth lives in `05-backlog.md`. `13-project-status.md` is **derived**
from it — do not edit the dashboard numbers by hand.

## How to update status

1. Edit the item in `06-technical-tasks.md` (or `03-features.md` / `02-epics.md`).
2. Update the matching row in `05-backlog.md` (same ID).
3. Append/update `updated_at` and a one-line status history if useful.
4. Refresh `13-project-status.md` from the backlog (counts, blockers, next up).
5. For non-trivial status changes, add a `CL-###` entry in `15-change-log.md`.

Example request → action:

> "Mark FEAT-014 as done."
> → set `FEAT-014 status: DONE` + `updated_at` in `03-features.md`, update
> `05-backlog.md`, move its tasks to DONE where satisfied, refresh `13-project-status.md`.

## How to add a feature

1. Assign the next free `FEAT-###`.
2. Ensure it maps to an existing `EPIC-###` and a `SPRINT-##`.
3. Add the full entry to `03-features.md` and a row to `05-backlog.md`.
4. Add its tasks to `06-technical-tasks.md` (each task maps back to the feature).
5. Update `02-epics.md` (feature list) and `04-sprints.md` (feature list) if applicable.

## How to add a task

1. Assign the next free `TASK-###`.
2. Map it to an existing `FEAT-###` and `SPRINT-##`.
3. Add the full entry to `06-technical-tasks.md` and a row to `05-backlog.md`.

## How to move work between sprints

1. Update the `sprint:` field on the item and on all of its child tasks.
2. Update `05-backlog.md` sprint column for every affected row.
3. Update `04-sprints.md` (remove/add from both sprint's feature/task lists).
4. Add a `CL-###` entry noting the move.

## How to record an architectural decision

1. Assign the next free `DEC-###` in `14-decision-log.md` with date/context/options/chosen/reason/impact.
2. If it supersedes an earlier decision, mark the earlier one `superseded by DEC-###` — do not rewrite history.
3. Reference the `DEC-###` from affected features/tasks.

## How to record a change

1. Assign the next free `CL-###` in `15-change-log.md` (date, what, why, affected docs, impact).
2. Apply the change to the affected documents.
3. Update `13-project-status.md` "Change Summary" section.

> **Never delete historical IDs. Update their status or mark them CANCELLED/DEFERRED.**

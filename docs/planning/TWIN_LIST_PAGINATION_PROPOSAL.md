# System Twin List Pagination Proposal

**Status: PROPOSED - NOT ADOPTED - DOCUMENTATION ONLY - NO IMPLEMENTATION AUTHORIZATION**

This document is a design proposal for bounded pagination on the System Twin
entity and relationship list endpoints. It is the deliverable requested by
[Issue #52](https://github.com/soubhagya-behera/systivex/issues/52). It does
not modify, and does not authorize modification of, runtime behavior.

## Document control

| Item | Value |
|---|---|
| Repository | `soubhagya-behera/systivex` |
| Issue | [#52 - design: propose bounded pagination for System Twin list endpoints](https://github.com/soubhagya-behera/systivex/issues/52) |
| Contribution type | Documentation / design only |
| File | `docs/planning/TWIN_LIST_PAGINATION_PROPOSAL.md` |
| Review baseline | `main` at `166f6c7` (fork fast-forwarded from upstream) |
| Proposal date | 2026-11-26 |
| Decision state | Open - maintainer review required |
| Implementation state | Not authorized by this document |

### How to read this document

Every statement below is labelled by kind:

- **Verified** - read directly from the repository at the review baseline.
  File references are given; no runtime execution was performed for this
  documentation-only task.
- **Recommended** - a design position proposed for maintainer review.
- **Alternative** - an option considered but not recommended as the first
  step.
- **Unresolved** - a decision that belongs to the maintainer, not to this
  proposal.

Nothing in this document is a performance measurement. No query plans,
timings, row counts, or production volumes were captured.

## 1. Purpose and scope

### 1.1 Problem

The System Twin list endpoints currently return every row in the collection
with no bound and no caller control over how much is returned. As the twin
grows, a single `GET` can transfer an unbounded payload and force the server
to materialize the whole table. The endpoints also have no documented ordering
contract, so a client cannot rely on stable page boundaries even if it slices
the response itself.

### 1.2 Goal

Define an explicit, reviewable pagination contract for:

- `GET /api/v1/twin/entities`
- `GET /api/v1/twin/relationships`

covering request parameters, deterministic ordering, response shape,
backward-compatibility impact, PostgreSQL/JPA cost considerations, testing,
and rollout.

### 1.3 Non-goals

This proposal does not:

- implement pagination, change controllers, services, repositories, DTOs,
  routes, security, configuration, schema, or migrations;
- change the bounded graph snapshot (`readGraphSnapshot()`) used by impact,
  risk, simulation, policy, and evidence assembly;
- add filtering, search, sorting parameters, or projection parameters to the
  list endpoints;
- add names or other fields to the relationship response;
- authorize frontend work;
- authorize implementation. A separate maintainer decision is required
  before any code change.

## 2. Verified current behavior

All of the following was verified by reading the repository at the review
baseline. No application was started, no database was queried, and no HTTP
request was sent.

### 2.1 Endpoint surface

| Endpoint | Handler | Parameters | Response type | Authorization |
|---|---|---|---|---|
| `GET /api/v1/twin/entities` | `TwinController.listEntities` (lines 41-44) | none | `List<SystemEntityResponse>` | `TWIN_READ` |
| `GET /api/v1/twin/relationships` | `TwinController.listRelationships` (lines 58-63) | none | `List<SystemRelationshipResponse>` | `TWIN_READ` |

Source: [TwinController.java](../../backend/src/main/java/com/soubhagya/systivex/twin/api/TwinController.java).

The controller class comment states: "Minimal Phase 1 twin surface: entity and
relationship CRUD used to prove the persistence layer. No filtering,
pagination, or traversal yet." (lines 16-19).

Both handlers map the service result to response DTOs with
`stream().map(...).toList()`. Neither method declares `@RequestParam`, and
unknown query parameters are not read anywhere in the handler.

### 2.2 Service behavior

- `TwinService.listEntities()` (lines 55-58) is `@Transactional(readOnly =
  true)` and returns `entities.findAll()`.
- `TwinService.listRelationships()` (lines 79-82) is
  `@Transactional(readOnly = true)` and returns `relationships.findAll()`.

Source: [TwinService.java](../../backend/src/main/java/com/soubhagya/systivex/twin/service/TwinService.java).

Neither method accepts a `Pageable`, `Sort`, page number, or size. Neither
method applies a sort. The lists are returned in whatever order the database
and JPA produce, which is not a documented contract.

### 2.3 Repository behavior

`SystemEntityRepository` (lines 8-11) extends `JpaRepository<SystemEntity,
UUID>` and declares only `findByExternalRef(String)`. The `findAll()`,
`findAll(Pageable)`, and `count()` methods are inherited from Spring Data, not
declared in this repository.

`SystemRelationshipRepository` (lines 11-29):

- overrides the no-argument `findAll()` with
  `@EntityGraph(attributePaths = {"source", "target"})` (lines 13-15);
- overrides `findById(UUID)` with the same entity graph (lines 17-19);
- declares `existsBySourceIdAndTargetIdAndRelationshipType(...)` (lines
  21-22);
- declares `findBySourceId(UUID)` and `findByTargetId(UUID)`, both with the
  entity graph (lines 24-28).

Sources:
[SystemEntityRepository.java](../../backend/src/main/java/com/soubhagya/systivex/twin/repository/SystemEntityRepository.java),
[SystemRelationshipRepository.java](../../backend/src/main/java/com/soubhagya/systivex/twin/repository/SystemRelationshipRepository.java).

Two consequences matter for this proposal:

1. A paged relationship read would use the inherited
   `findAll(Pageable)` signature. The existing `@EntityGraph` is attached only
   to the overridden no-argument `findAll()`; it is not inherited by the
   `Pageable` overload. A paged implementation would need its own entity graph
   or a projection.
2. `SystemRelationship.source` and `.target` are declared
   `FetchType.LAZY` (lines 38-50 of `SystemRelationship.java`), so relationship
   loading behavior is a real design decision, not an implementation detail.

### 2.4 Response DTOs

`SystemEntityResponse` carries `id`, `type`, `name`, `externalRef`,
`environment`, `metadata`, `createdAt`, and `updatedAt`.

`SystemRelationshipResponse` carries only `id`, `sourceId`, `targetId`,
`type`, and `createdAt`. It does **not** carry source or target names,
external refs, or full endpoint objects.

Sources:
[SystemEntityResponse.java](../../backend/src/main/java/com/soubhagya/systivex/twin/api/SystemEntityResponse.java),
[SystemRelationshipResponse.java](../../backend/src/main/java/com/soubhagya/systivex/twin/api/SystemRelationshipResponse.java).

### 2.5 Bounded graph snapshot is a different mechanism

`TwinService.readGraphSnapshot()` (lines 84-191) reads the twin with
`PageRequest` and deterministic sorts:

- entities: `Sort.by(asc("externalRef").nullsLast(), asc("id"))`, probe size
  `501`, accepted maximum `500`;
- relationships: `Sort.by(asc("source.id"), asc("target.id"),
  asc("relationshipType"), asc("id"))`, probe size `2001`, accepted maximum
  `2000`.

If either probe reaches its limit, the snapshot is marked truncated and
`availableForAnalysis` becomes `false`; analysis is refused rather than
receiving a partial graph (lines 156-190).

This is a **whole-graph bounded read for deterministic analysis**, not a
general list-pagination contract. It returns a snapshot object, not a page,
and it deliberately refuses analysis above the cap. The issue explicitly says
this mechanism "must not be assumed to define the list API pagination
contract"; the implementation confirms that they serve different purposes.

### 2.6 Persistence and index facts

Verified from
[V1__create_system_twin.sql](../../backend/src/main/resources/db/migration/V1__create_system_twin.sql):

`system_entity`:

- `id UUID NOT NULL PRIMARY KEY`
- `type VARCHAR(32) NOT NULL`
- `name VARCHAR(256) NOT NULL`
- `external_ref VARCHAR(512)` (nullable)
- `environment VARCHAR(64)` (nullable)
- `metadata JSONB NOT NULL DEFAULT '{}'::jsonb`
- `created_at`, `updated_at` `TIMESTAMPTZ NOT NULL DEFAULT now()`
- `CONSTRAINT uq_system_entity_external_ref UNIQUE (external_ref)`
- `CREATE INDEX idx_system_entity_type ON system_entity (type)`
- `CREATE INDEX idx_system_entity_environment ON system_entity (environment)`

`system_relationship`:

- `id UUID NOT NULL PRIMARY KEY`
- `source_entity_id UUID NOT NULL`, FK to `system_entity(id)` with
  `ON DELETE CASCADE`
- `target_entity_id UUID NOT NULL`, FK to `system_entity(id)` with
  `ON DELETE CASCADE`
- `relationship_type VARCHAR(32) NOT NULL`
- `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- `CONSTRAINT chk_system_relationship_no_self_ref CHECK (source_entity_id <>
  target_entity_id)`
- `CONSTRAINT uq_system_relationship UNIQUE (source_entity_id,
  target_entity_id, relationship_type)`
- `CREATE INDEX idx_system_relationship_source ON
  system_relationship (source_entity_id)`
- `CREATE INDEX idx_system_relationship_target ON
  system_relationship (target_entity_id)`

What is **not** present at the baseline:

- no index on `system_entity.name`, `created_at`, or `updated_at`;
- no explicit composite index on `(external_ref, id)`;
- no explicit composite index on
  `(source_entity_id, target_entity_id, relationship_type, id)`;
- no index on `relationship_type` alone.

PostgreSQL creates indexes for primary keys and unique constraints. Whether a
given query uses them is a planner decision; this document makes no claim
about actual plans.

Two PostgreSQL semantics used below are documented behavior, not measured
results:

- a unique constraint on a nullable column permits multiple rows whose value
  is `NULL` (so `external_ref` does not identify rows whose ref is null);
- `ORDER BY ... ASC` places `NULL` values last by default in PostgreSQL.

### 2.7 Existing error contract

`RestExceptionHandler` maps `HttpMessageNotReadableException`,
`MethodArgumentTypeMismatchException`, and `IllegalArgumentException` to
`400 Bad Request` and returns `ErrorResponse` with fields `timestamp`,
`status`, `error`, `message`, `path`, and `fieldErrors`.

Source:
[RestExceptionHandler.java](../../backend/src/main/java/com/soubhagya/systivex/twin/api/RestExceptionHandler.java).

Therefore a non-numeric `page` or `size` supplied to a future
`@RequestParam int` would already produce the project's stable 400 envelope
through the existing handler, if the handler order is unchanged.

### 2.8 Existing pagination precedent in the repository

The project already has one `page`/`size` contract, for proposals:

- `ProposalController.list` declares
  `@RequestParam(defaultValue = "0") int page` and
  `@RequestParam(defaultValue = "20") int size` (lines 130-132).
- `ProposalService` defines `MAX_PAGE_SIZE = 100` and
  `DEFAULT_PAGE_SIZE = 20` (lines 43-45) and clamps:
  `safePage = Math.max(page, 0)`; `safeSize = Math.min(Math.max(size, 1),
  MAX_PAGE_SIZE)`; `size <= 0` becomes `DEFAULT_PAGE_SIZE` (lines 152-157).
- `ProposalListResponse` is `record ProposalListResponse(List<ProposalResponse>
  items, int page, int size, long total)`.
- `DECISIONS.md` D36 documents this contract as "0-based page; default size
  20; maximum 100; the backend clamps values".

Sources:
[ProposalController.java](../../backend/src/main/java/com/soubhagya/systivex/proposal/api/ProposalController.java),
[ProposalService.java](../../backend/src/main/java/com/soubhagya/systivex/proposal/service/ProposalService.java),
[ProposalListResponse.java](../../backend/src/main/java/com/soubhagya/systivex/proposal/api/ProposalListResponse.java),
[DECISIONS.md](../../DECISIONS.md) (D36).

This is the strongest available convention for the twin list endpoints. It is
a precedent, not a binding decision for twin lists.

### 2.9 Frontend consumption (compatibility fact)

Verified in the frontend:

- `frontend/src/api/twin.js` wraps both list calls in `expectArray()`, which
  throws an `ApiError(ErrorKind.UNEXPECTED)` when the response is not an
  array.
- `frontend/src/components/TwinConsole.jsx` loads both collections together
  with `Promise.all([twinApi.listEntities(), twinApi.listRelationships()])`,
  builds `entitiesById` from the entity array, and derives the navigation
  counts from `entities.length` and `relationships.length`.
- `frontend/src/views/Relationships.jsx` resolves relationship endpoint names
  through `entitiesById.get(id)?.name ?? id`, falling back to the raw UUID.
- `frontend/src/test/twin.test.js` asserts the array contract and asserts that
  a non-array payload is rejected.

Consequence: any response-shape change from array to object breaks the
current console and its tests until the frontend is updated in coordination.

### 2.10 Documentation statements at the baseline

- `ARCHITECTURE.md` section 7 says the twin CRUD surface has "no
  filtering/pagination yet".
- `PROJECT_STATUS.md` records a live verification in which
  `GET /api/v1/twin/entities` returned 15 entities and
  `GET /api/v1/twin/relationships` returned 18 relationships. These are the
  documented local verification counts, not a current measurement and not a
  capacity statement.
- `THREAT_MODEL.md` describes evidence assembly as using bounded reads and
  explicitly says evidence is never assembled "via unbounded twin list
  methods". That is a consumer constraint; it does not define the list API.

### 2.11 What was not verified

- No HTTP calls were made; the endpoints were not exercised at runtime.
- No PostgreSQL query plans or timings were captured.
- No production or representative data volume is known.
- No frontend test run or build was performed for this document.
- Docker is required by the project's integration tests but was not used here;
  this proposal changes no code, so no test run was required.

## 3. Design requirements and constraints

Derived from Issue #52 and the maintainer's comment:

1. Page/size or a justified alternative; default and maximum size; defined
   behavior for missing, negative, and excessive values.
2. Deterministic entity ordering and relationship ordering with unique
   tie-breakers and stable page boundaries.
3. Content structure, pagination metadata, total-count behavior, empty and
   out-of-range pages, and backward compatibility when arrays become
   paginated responses.
4. PostgreSQL performance: Page versus Slice, count-query cost, relationship
   loading including source/target, query and index considerations.
5. Testing strategy: first, middle, final, empty pages; maximum page size;
   invalid parameters; stable ordering; relationship integrity; regression
   and compatibility.
6. Rollout: contract review, implementation stages, verification gates.

Binding constraints from the issue:

- documentation only; no Java source, tests, controllers, services,
  repositories, routes, schema, migrations, dependencies, security,
  configuration, or other documentation changes;
- no implementation based on this proposal alone;
- do not invent current behavior, indexes, constraints, or performance
  measurements;
- distinguish verified behavior, recommendations, alternatives, and
  unresolved decisions.

## 4. Request contract

### 4.1 Recommended convention: `page` and `size`

**Recommended:** 0-based `page` plus `size`, matching the existing
`ProposalController`/`ProposalService`/`ProposalListResponse` convention.

```
GET /api/v1/twin/entities?page=0&size=20
GET /api/v1/twin/relationships?page=0&size=20
```

Rationale:

- It is already implemented and documented for proposals (D36), so the API
  stays internally consistent and reviewers do not need a second mental
  model.
- Both endpoints are unordered full-collection reads today; offset paging is
  a direct fit.
- It keeps `page`/`size`/`total` semantics identical across list resources.
- It is simple for the local console to consume and easy to test.

### 4.2 Recommended parameters and bounds

| Parameter | Type | Required | Default | Accepted range | Recommended handling |
|---|---|---|---|---|---|
| `page` | integer | No | `0` | `>= 0` | Missing: `0`. Negative: clamp to `0`. |
| `size` | integer | No | `20` | `1..100` | Missing: `20`. `<= 0`: `20`. `> 100`: `100`. |

These values deliberately mirror the proposal list contract
(`DEFAULT_PAGE_SIZE = 20`, `MAX_PAGE_SIZE = 100`, clamp behavior).

**Recommended:** the response echoes the effective values after clamping, not
the raw caller values. A caller that sends `size=1000` receives
`"size": 100`, so the page it actually got is unambiguous.

### 4.3 Invalid and edge-case handling

| Input | Recommended result |
|---|---|
| `page` missing | 200; page `0` |
| `size` missing | 200; size `20` |
| `page=-1` | 200; page `0` (clamped, consistent with proposal list) |
| `size=0` | 200; size `20` |
| `size=-5` | 200; size `20` |
| `size=101` | 200; size `100` |
| `page=abc` or `size=abc` | 400 with the existing `ErrorResponse` envelope |
| `page` beyond the last page | 200 with `items: []` and unchanged `total` |
| unknown query parameters | ignored (Spring MVC default; no handler reads them) |
| repeated parameter (`page=1&page=2`) | undefined by this proposal; see UD-9 |

**Alternative:** reject every invalid value with 400 instead of clamping.
That is a stricter contract and makes caller mistakes visible, but it diverges
from the existing proposal-list behavior and from D36, and it would surprise
clients that already learned the clamping convention. Not recommended for the
first implementation.

**Unresolved (UD-2):** clamp versus reject is a public-contract decision.
Clamping is recommended for consistency; rejection is defensible on strictness
grounds.

### 4.4 Extremely large `page` values

`page` is an `int`. The offset is `page * size`. A very large page can produce
an enormous offset and an expensive database skip, and extremely large values
can approach integer/long boundary behavior in the persistence layer.

**Recommended:** if the project wants a hard ceiling, define one explicitly
(for example a documented maximum page) and return 400 above it, rather than
relying on overflow or database behavior.

**Unresolved (UD-3):** whether to add a maximum page at all, and what it should
be. This proposal does not invent one. Returned empty pages are the correct
semantic for an out-of-range page within the accepted range.

### 4.5 Alternatives to `page`/`size`

| Option | Assessment |
|---|---|
| `offset`/`limit` | Same cost profile as `page`/`size`; diverges from the existing proposal convention; no benefit. Not recommended. |
| Cursor / keyset | Better for deep paging and concurrent writes; requires a unique, immutable, indexed cursor; sorting by mutable fields such as `external_ref` weakens it; larger contract change. Not recommended as the first step; see UD-8. |
| `from`/`limit` (like impact's `from`) | `from` is already a root identifier in impact/risk; reusing the name for an offset would be confusing. Not recommended. |
| No parameters, server-side cap only | Bounds payload size but silently truncates the collection and gives the client no way to read the rest. Not acceptable as a list contract. |

## 5. Deterministic ordering

A page is only stable if the ordering is total and unique. The recommended
orders intentionally reuse the orders already used by
`readGraphSnapshot()`, so the project has one documented ordering convention
for twin rows, even though the snapshot and the list endpoints remain
different mechanisms.

### 5.1 Entities

**Recommended:**

```sql
ORDER BY external_ref ASC NULLS LAST, id ASC
```

Equivalent Spring Data sort:

```java
Sort.by(Sort.Order.asc("externalRef").nullsLast(), Sort.Order.asc("id"))
```

Why:

- `external_ref` is the stable, human-facing identifier used by the twin
  connector (`target-*` refs) and by the bounded snapshot.
- `external_ref` is **not** unique for null values: PostgreSQL unique
  constraints permit multiple `NULL`s. `id` is the primary key and is unique,
  so `(external_ref, id)` is a total order.
- `NULLS LAST` is explicit in the sort and matches PostgreSQL's default
  placement for ascending order. Entities without an external ref therefore
  appear after all entities that have one.
- It matches the snapshot order, reducing the number of ordering contracts a
  maintainer must reason about.

Alternatives:

- `name ASC, id ASC`: more readable for an operator, but `name` is not unique
  and has no index at the baseline; it would still be deterministic thanks to
  `id`, but it would introduce a second twin ordering and a likely sort cost.
- `id ASC` only: fully unique and cheap (primary key), but arbitrary to a
  human and inconsistent with the snapshot.
- `created_at DESC, id ASC`: newest first, but `created_at` has no index and
  can tie; it also changes meaning if rows are updated.

**Unresolved (UD-4):** whether `externalRef` or `name` is the preferred
human-facing sort for the entity list. `externalRef` is recommended because it
matches the existing snapshot and connector identity.

### 5.2 Relationships

**Recommended:**

```sql
ORDER BY source_entity_id ASC,
         target_entity_id ASC,
         relationship_type ASC,
         id ASC
```

Equivalent Spring Data sort:

```java
Sort.by(
    Sort.Order.asc("source.id"),
    Sort.Order.asc("target.id"),
    Sort.Order.asc("relationshipType"),
    Sort.Order.asc("id"))
```

Why:

- It matches the bounded snapshot order exactly.
- The unique constraint `uq_system_relationship (source_entity_id,
  target_entity_id, relationship_type)` already makes the first three columns
  unique per row. `id` is therefore a defensive final tie-breaker rather than
  a functional necessity; keeping it makes the rule uniform with entities and
  protects the contract if the unique constraint is ever reconsidered.
- Sorting on the foreign-key columns does not require joining
  `system_entity`; source/target entity loading is a separate concern
  (section 7).
- `relationship_type` is persisted as `VARCHAR(32)` via
  `@Enumerated(EnumType.STRING)`, so the order is database text order
  (`CALLS`, `CONFIGURES`, `DEPENDS_ON`, `DEPLOYED_AS`, `EXPOSES`, `READS`,
  `WRITES`), not enum declaration order. That must be documented as the
  contract if this order is adopted.

Alternatives:

- `id ASC` only: unique and index-backed, but meaningless grouping for an
  operator.
- `created_at DESC, id ASC`: newest first; no index and ties are likely.
- Sort by endpoint names: requires joining `system_entity` twice and sorting
  on non-unique text; not recommended.

### 5.3 Stable page boundaries

The ordering above guarantees determinism **within a single query**: repeated
execution against the same data returns the same sequence, so `OFFSET`/`LIMIT`
boundaries are consistent for a static data set.

It does **not** make offset pages a consistent snapshot across requests. If a
row is inserted or deleted between page 1 and page 2, later pages can shift
and a client can see a duplicate or miss a row. This is inherent to offset
pagination; it is not fixed by a tie-breaker. Keyset pagination would address
it but is out of scope for the first step (UD-8).

**Recommended:** document this limitation in the API contract rather than
implying that pages are a snapshot.

## 6. Response contract

### 6.1 Recommended shape

**Recommended:** a page envelope, named consistently with
`ProposalListResponse`:

```json
{
  "items": [
    {
      "id": "3f1c...",
      "type": "SERVICE",
      "name": "orders-api",
      "externalRef": "target-service:order-service",
      "environment": "dev",
      "metadata": {},
      "createdAt": "2026-11-26T10:15:30Z",
      "updatedAt": "2026-11-26T10:15:30Z"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 15
}
```

For relationships, `items` contains the existing
`SystemRelationshipResponse` shape:

```json
{
  "items": [
    {
      "id": "8a2d...",
      "sourceId": "3f1c...",
      "targetId": "9b7e...",
      "type": "CALLS",
      "createdAt": "2026-11-26T10:16:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 18
}
```

Field semantics:

| Field | Type | Meaning |
|---|---|---|
| `items` | array | The requested page, in deterministic order. Item fields are unchanged from today's DTOs. |
| `page` | int | Effective 0-based page after clamping. |
| `size` | int | Effective page size after clamping. |
| `total` | long | Total number of rows in the collection at read time, not the number of items on this page. |

Naming note: the issue asks for "content structure". This proposal recommends
`items` because that is the established name in `ProposalListResponse`. Spring
Data's own serialized `Page` uses `content`, `pageable`, `totalPages`,
`first`, `last`, `numberOfElements`, and similar fields; exposing that shape
directly is not recommended because it couples the public API to a framework
type.

### 6.2 Empty and out-of-range pages

**Recommended:** always return `200 OK` with a well-formed envelope:

```json
{
  "items": [],
  "page": 7,
  "size": 20,
  "total": 15
}
```

An empty collection and an out-of-range page are not client errors; they are
empty pages. This also matches the S4 console behavior recorded in
`PROJECT_STATUS.md`: "Out-of-range pages show an empty state with previous-page
navigation."

No `404` for an out-of-range page, and no `204` (a `204` would carry no
metadata).

### 6.3 Total-count behavior

**Recommended:** `total` is a `long` count of all rows in the collection,
computed in the same read-only transaction as the page query.

Limitations to document:

- `total` and `items` are two queries under `READ COMMITTED`, not a globally
  consistent snapshot. A concurrent insert or delete can make `total` differ
  from the count implied by the page.
- `total` is not an authorization-filtered count today because the read is
  not filtered by caller; it is the collection count.
- `total` must not be described as exact under concurrent writes.

**Alternative:** omit `total` and use a `Slice`-style response with
`hasNext`. That avoids the count query but removes page-count UI and diverges
from the proposal-list contract. See section 7.1 and UD-5.

### 6.4 Backward compatibility when arrays become objects

This is the highest-risk part of the proposal. Today both endpoints return a
JSON array. The console validates `Array.isArray(value)` and throws otherwise;
the API tests deserialize into `SystemEntityResponse[]` and
`SystemRelationshipResponse[]`. A change to an object envelope is therefore a
breaking change for every existing client, including the project's own
console and tests.

Options:

| Option | Shape | Pros | Cons |
|---|---|---|---|
| A. Always return the envelope at the existing URL | object always | One contract; bounded by default; consistent with proposals | Breaking change; console and API tests must be updated in the same coordinated change; external callers break |
| B. Array when no page/size is supplied, envelope when either is supplied | mixed by request | Old clients keep working without changes | The same URL returns two different JSON types; typed clients and generated SDKs break on opt-in; ambiguous contract; the default remains unbounded, so the boundedness goal is only partially met |
| C. Add a new paginated route or version (for example a sibling path or `/api/v2`), keep the old arrays for now | separate routes | Non-breaking; clean migration path | Duplicate surface; the old route stays unbounded until deprecated; new route must be added to security matchers and docs; more work |
| D. Content negotiation (`Accept` profile or vendor media type) | negotiated | HTTP-correct versioning | Unusual for this codebase; frontend and test complexity; not justified by the current client set |

**Recommended:** Option A, coordinated as a single breaking change with the
frontend and tests updated in the same implementation effort. Rationale:

- the project is pre-1.0 and the only known client is the local
  development-only console, which is in the same repository;
- it produces one contract instead of two;
- it satisfies the actual goal (bounded reads by default) rather than leaving
  an unbounded default path;
- it matches the existing proposal-list shape.

**Alternative if compatibility is mandatory:** Option C. It is the only
option that preserves the old array contract without a mixed-type URL. It
requires an explicit deprecation plan for the array endpoints, otherwise the
unbounded path remains available indefinitely.

**Unresolved (UD-1):** the maintainer must choose A, B, C, or D. This is a
public API and governance decision, not a documentation detail. Option B is
listed for completeness but is not recommended.

### 6.5 Relationship endpoint display integrity

Verified today:

- `SystemRelationshipResponse` contains only IDs, not endpoint names.
- The console resolves names by looking up `sourceId`/`targetId` in the
  complete entity array it loaded separately.
- If a lookup fails, the console falls back to displaying the UUID.

With paginated entities, the full entity set is no longer loaded, so endpoint
names for relationships on the current relationship page may be absent from
the current entity page. The API response itself remains correct: `sourceId`
and `targetId` are still valid and non-null because the database enforces
`NOT NULL` foreign keys. What breaks is the console's display convenience.

Options:

1. Accept UUID fallback for endpoints not present in the loaded entity page.
2. Fetch each missing endpoint by ID on demand (`GET /api/v1/twin/entities/{id}`).
3. Enrich the relationship response with endpoint name/external ref.
4. Keep a separate, bounded entity lookup endpoint for relationship context.

Option 3 changes the relationship DTO beyond pagination and is out of scope
for Issue #52. Option 2 adds request fan-out. Option 4 adds a new endpoint.
Option 1 is the smallest change but degrades the UI.

**Recommended:** treat this as a frontend follow-up decision after the list
contract is settled. Do not quietly add endpoint fields to
`SystemRelationshipResponse` as part of pagination.

**Unresolved (UD-6):** how the console resolves relationship endpoint names
once entities are paginated.

## 7. PostgreSQL and JPA performance considerations

No measurements were taken. The following is mechanism-level reasoning, not
benchmark evidence.

### 7.1 `Page` versus `Slice`

| Aspect | `Page<T>` | `Slice<T>` |
|---|---|---|
| Count query | Executed as part of Page semantics | Not executed |
| `total` | Available | Not available |
| `hasNext` | Derivable | Available directly |
| Cost | Content query plus count query | Content query only (typically fetches `size + 1` rows) |
| Contract fit | Matches `ProposalListResponse` (`items`/`page`/`size`/`total`) | Requires a different response contract |

**Recommended:** `Page` for the first implementation, because the project's
existing list contract exposes `total` and the console displays totals. This
is a consistency choice, not a claim that counting is cheap.

**Alternative:** `Slice` if the maintainer decides `total` is not required.
That removes a full-collection count from every list request. It should be a
deliberate contract decision, not an optimization hidden behind a changed
response shape.

### 7.2 Count-query cost

Both list endpoints read unfiltered collections. A `Page` request therefore
asks PostgreSQL to count every row in the table, in addition to returning the
page.

- The count is not free and grows with table size.
- PostgreSQL may satisfy a count with a sequential scan or an index-only scan
  depending on the planner and visibility map; this proposal makes no claim
  about which plan occurs.
- No caching, approximate count, or materialized count exists at the
  baseline, and this proposal does not add one.
- `readGraphSnapshot()` already uses `Page`, so the project has an existing
  pattern of count queries for bounded reads; that does not make the count
  free.

**Unresolved (UD-5):** whether `total` is required for the twin list contract.

### 7.3 Offset and deep-page cost

`PageRequest.of(page, size)` translates to `OFFSET page * size LIMIT size`.

- PostgreSQL must skip the offset rows before returning the page. Deep pages
  are progressively more expensive than the first page, even when an index
  provides the order.
- If the requested order is not supported by an index, the database must sort
  the full result set before applying the offset.
- A stable tie-breaker does not reduce this cost; it only makes the order
  deterministic.
- Keyset pagination avoids the skip, but requires a cursor contract and is not
  proposed for the first step.

**Recommended:** document that deep offset pages are supported but not
guaranteed to be cheap, and revisit keyset pagination only if real usage
requires it.

### 7.4 Relationship loading and source/target preservation

Current behavior:

- `SystemRelationship.source` and `.target` are `ManyToOne(fetch = LAZY)`.
- The overridden no-argument `findAll()` applies an entity graph for `source`
  and `target`, so the existing list path is intended to load both endpoints
  with the relationship rows.
- `SystemRelationshipResponse.from(...)` reads `getSource().getId()` and
  `getTarget().getId()`.
- The service transaction ends before the controller maps the DTO because
  only the service method is transactional. `spring.jpa.open-in-view=false` is
  configured in `application.example.properties`.

A paged implementation has three viable loading strategies:

| Strategy | How | Trade-offs |
|---|---|---|
| A. Paged entity graph | Override `Page<SystemRelationship> findAll(Pageable)` with `@EntityGraph(attributePaths = {"source", "target"})` | Smallest conceptual change; loads both endpoint entities per row; both are to-one joins, so row multiplication is not a concern; wider rows than a projection. |
| B. Closed DTO projection | Query only `id`, `source_entity_id`, `target_entity_id`, `relationship_type`, `created_at` | Lowest data transfer and no entity initialization; no join needed for the response fields; adds a query/projection type and diverges from entity-graph reuse. |
| C. Page relationships, then batch-load endpoints | Page rows, collect IDs, fetch entities in one `IN` query | Avoids per-row lazy loads but adds a second query and mapping complexity; unnecessary for a DTO that only needs IDs. |

**Recommended:** Strategy A for the first implementation if the existing
entity-graph style is preferred, or Strategy B if the maintainer prefers the
narrowest possible read. Both preserve the response contract. Avoid relying on
detached lazy proxies: `open-in-view=false` means the DTO mapping happens
outside the persistence context, and JPA does not guarantee lazy behavior
after the session closes. An uninitialized proxy may expose an identifier in
practice, but the contract should not depend on that.

If Strategy A is chosen, the entity graph must be declared on the `Pageable`
overload explicitly. The existing `@EntityGraph` on the no-argument
`findAll()` does not apply to `findAll(Pageable)`.

### 7.5 Query and index considerations

Entity ordering `(external_ref, id)`:

- the unique constraint on `external_ref` creates a unique index that can
  support ordering by `external_ref`;
- rows with `NULL` external refs still need the `id` tie-breaker, which may
  require additional sorting for that group;
- there is no explicit composite `(external_ref, id)` index; whether one is
  warranted is a future decision based on real data, not this proposal.

Relationship ordering `(source_entity_id, target_entity_id,
relationship_type, id)`:

- the unique constraint `uq_system_relationship` on the first three columns
  creates a composite index whose order matches the sort prefix;
- the final `id` tie-breaker is redundant for uniqueness because the triple is
  already unique, and is retained only as a contract safeguard;
- `idx_system_relationship_source` and `idx_system_relationship_target` serve
  endpoint lookups, not this composite list order.

Other observations:

- Sorting by `name` or `created_at` would have no supporting index at the
  baseline and would require a separate index decision.
- Adding an index requires a Flyway migration, which is outside this issue's
  constraints and would need its own design and authorization.
- This proposal adds no index and makes no claim that the existing indexes are
  used by any particular query plan.

**Recommended:** implement with the existing schema, then evaluate index needs
only if real measurements show a problem. Do not add speculative indexes in
the pagination implementation.

## 8. Testing strategy

### 8.1 Test levels

- **Service unit tests** (`TwinServiceTest` style, Mockito): parameter
  normalization, `PageRequest` construction, sort order, DTO mapping.
- **Repository integration tests** (`TwinRepositoryTest` style,
  Testcontainers PostgreSQL): paged queries, ordering, count, entity-graph or
  projection behavior.
- **API integration tests** (`TwinApiTest` style,
  `@SpringBootTest(RANDOM_PORT)`): status codes, JSON shape, error envelope,
  defaults, bounds, empty and out-of-range pages.
- **Frontend contract tests** (`twin.test.js`, `TwinConsole` tests): envelope
  parsing, counts, paging controls, relationship endpoint fallback.
- **Regression tests**: existing CRUD, bounded snapshot, security matchers,
  and all unaffected twin behavior.

### 8.2 Backend cases

Seed a deterministic fixture, for example 25 entities and 30 relationships
with known external refs and endpoints, including at least two entities with a
null `external_ref` to prove the null placement and tie-breaker.

| Case | Expected |
|---|---|
| No parameters | `200`, page `0`, size `20`, at most 20 items, correct `total` |
| First page (`page=0`, `size=10`) | 10 items, first item is the smallest `external_ref` (or the documented null rule) |
| Middle page (`page=1`, `size=10`) | The next 10 items; no overlap with page 0 |
| Final page (partial) | Remaining items; fewer than `size`; no overlap with earlier pages |
| Page concatenation | All pages in order produce exactly `total` unique IDs with no gaps or duplicates on a static fixture |
| `page` out of range | `200`, `items: []`, echoed `page`/`size`, unchanged `total` |
| Empty collection | `200`, `items: []`, `total: 0` |
| Maximum size (`size=100`) | At most 100 items; `size: 100` echoed |
| Excessive size (`size=101`) | `size: 100` after clamping (or 400 if UD-2 chooses rejection) |
| `size=0` and negative size | `size: 20` (or 400 if UD-2 chooses rejection) |
| Negative page | `page: 0` (or 400 if UD-2 chooses rejection) |
| Non-numeric `page`/`size` | `400` with `ErrorResponse` fields, including the request path |
| Stable ordering | Two identical requests return the same ID sequence |
| Stable ordering with null refs | Null-ref entities appear last, ordered by `id` |
| Relationship page integrity | Every item has non-null `sourceId` and `targetId`; both IDs exist in the entity fixture; no duplicate relationship IDs across pages |
| Relationship ordering | Sequence follows source ID, target ID, type text, then ID |
| Relationship total | `total` equals the fixture relationship count, independent of page size |
| No snapshot coupling | List endpoints do not call `readGraphSnapshot()`; snapshot truncation behavior is unchanged |
| Authorization | A `TWIN_READ` identity can read pages; no new role or matcher is introduced |
| Unknown query parameter | Ignored, or 400 only if the maintainer explicitly chooses strict parameter handling (UD-9) |

### 8.3 Regression and compatibility coverage

- Update the existing array-based API tests (`listEntitiesContainsCreated`,
  `createAndListRelationship`) to the chosen response shape in the same
  implementation change; do not leave them passing against the old contract.
- Keep all existing CRUD, validation, duplicate, self-reference, cascade, and
  error-envelope tests unchanged in behavior.
- Keep `TwinServiceTest` coverage of `readGraphSnapshot()` unchanged; add a
  test proving the list methods do not depend on the snapshot.
- If Option C (separate route) is chosen, add explicit tests for both routes
  and a deprecation test/documentation check.
- Update `frontend/src/test/twin.test.js` and any `TwinConsole` tests that
  assume arrays. Add a test that a malformed envelope is rejected rather than
  silently treated as an empty page.
- Add a test that the console's displayed counts use the paginated total, not
  the page length.

### 8.4 Optional query-count guard

A deterministic guard (for example Hibernate `Statistics`) can assert that a
relationship page issues a bounded number of statements rather than one query
per relationship. This is a structural N+1 check, not a timing benchmark, and
it should not assert specific SQL or a specific count query if `Slice` is
chosen.

**Recommended:** include this only if the maintainer considers it stable
enough for CI; otherwise rely on the relationship integrity tests.

### 8.5 Execution requirements

Per `CONTRIBUTING.md`, the full backend suite uses Testcontainers PostgreSQL
and requires Docker. The frontend uses Node 24 LTS. A future implementation PR
must report the actual commands run and any environment blocker rather than
skipping tests silently.

## 9. Recommended rollout

### 9.1 Stage 0 - contract review

The maintainer reviews this proposal and resolves at least UD-1 through UD-5.
This proposal does not authorize implementation. The outcome should be
recorded in the project's decision log before code changes begin.

### 9.2 Stage 1 - repository and service layer

- Add paged read methods with the agreed deterministic sorts.
- Add service-level parameter normalization and mapping.
- Add service and repository tests.
- Do not change the controller or response shape yet.
- Keep the existing list methods available until the contract switch is
  authorized.

Verification: focused service/repository tests pass; no schema, dependency, or
security change.

### 9.3 Stage 2 - controller and response contract

- Add `page`/`size` request parameters and the agreed response envelope.
- Reuse the existing error envelope for non-numeric values.
- Update API tests and frontend contract code in the same coordinated change
  (or the same short-lived series, if the project requires separate PRs).
- Update the documentation that describes the twin API (only after the
  behavior is authorized and implemented).

Verification: full backend suite; frontend suite and build if the console is
updated; explicit first/middle/final/empty/out-of-range checks.

### 9.4 Stage 3 - frontend pagination (separate authorization)

- Add paging controls and use the server-provided `total`.
- Resolve relationship endpoint names using the chosen approach (UD-6).
- Keep the console read-only and local-development-only.
- Do not introduce filtering, sorting, or new endpoints without a separate
  decision.

Verification: frontend tests, build, and a manual local check with more rows
than one page.

### 9.5 Stage 4 - optional legacy route retirement

Only if Option C was chosen:

- deprecate the array endpoints in documentation;
- announce a removal point;
- remove them only after the console and known clients use the paginated
  contract.

### 9.6 Verification gates

| Gate | Requirement |
|---|---|
| Contract review | UD-1, UD-2, UD-4, UD-5 resolved and recorded |
| Scope | Only the files authorized for the implementation change; no schema, migration, dependency, security, or configuration change unless separately authorized |
| Ordering | Deterministic total order proven by repeated-request and page-concatenation tests |
| Bounds | Defaults, maximum, negative, zero, excessive, and non-numeric cases covered |
| Relationship integrity | Non-null endpoints, existing endpoint IDs, no duplicates across pages |
| Compatibility | Chosen shape applied consistently; console and tests updated or compatibility route covered |
| Backend tests | Full suite reported, not only focused tests |
| Frontend tests | Suite and build reported if the console changes |
| Diff hygiene | `git diff --check` clean; no unrelated changes |

## 10. Risks and limitations

1. **Breaking change risk.** Converting arrays to envelopes breaks every
   existing client until updated. Option A is clean but deliberately breaking;
   Option C adds surface but preserves compatibility.
2. **Console coupling.** The console loads both full collections together and
   derives counts and relationship names from them. Pagination requires a
   coordinated frontend change.
3. **Count cost.** `Page` adds a full-collection count per request. At small
   data volumes this is irrelevant; at large volumes it is not free. No
   measurement exists to quantify it.
4. **Deep-page cost.** Offset pagination degrades with page depth, and page
   drift under concurrent writes is inherent.
5. **Ordering contract drift.** If the sort fields are changed later, clients
   that cache pages or rely on boundaries can see duplicates or gaps.
6. **Lazy-loading regression.** A paged relationship query without an entity
   graph or projection can change loading behavior, especially with
   `open-in-view=false`.
7. **Index assumptions.** The proposal does not add indexes and does not claim
   that existing indexes fully support the chosen order; a future measurement
   may justify an index migration.
8. **Total accuracy.** `total` is not a snapshot guarantee under concurrent
   writes.
9. **Scope creep.** Adding filtering, sorting parameters, endpoint
   enrichment, or keyset pagination would turn a bounded contract change into
   a larger redesign.

## 11. Alternatives considered

| Area | Alternative | Why not recommended first |
|---|---|---|
| Request | `offset`/`limit` | Same cost, new convention, no benefit over `page`/`size`. |
| Request | Cursor/keyset | Better deep paging, but requires a stable indexed cursor and a larger contract change. |
| Request | Strict 400 for all invalid values | Clearer errors, but diverges from the existing proposal-list clamp precedent. |
| Ordering | Entity `name, id` | More human-friendly, but no index and a second twin ordering. |
| Ordering | Entity `id` only | Cheap and unique, but arbitrary and inconsistent with the snapshot. |
| Ordering | Relationship `created_at DESC` | No index, ties likely, and does not match the snapshot. |
| Response | `content` envelope (Spring `Page` serialization) | Couples the API to framework internals and exposes unstable fields. |
| Response | `Slice`/`hasNext` without `total` | Removes the count cost but breaks parity with proposals and the UI's total display. |
| Response | Mixed array/envelope by parameter presence | Same URL returns different types; ambiguous contract; default remains unbounded. |
| Response | New sibling route or `/api/v2` | Non-breaking, but duplicates surface and leaves the unbounded path alive without a deprecation plan. |
| Loading | Lazy proxies and ID access | Relies on behavior outside the JPA guarantee with `open-in-view=false`. |
| Loading | Per-row entity fetch | N+1 queries; unacceptable for a list endpoint. |
| Performance | Add indexes now | No measurements, and schema changes are outside this issue and require separate authorization. |

## 12. Unresolved maintainer decisions

| ID | Decision | Recommendation | Impact if unresolved |
|---|---|---|---|
| UD-1 | Keep the array contract, switch to an envelope, or add a new route/version? | Always-envelope at the existing URL (Option A), coordinated with the console and tests | Public API shape and compatibility strategy; blocks implementation |
| UD-2 | Clamp invalid `page`/`size` or reject with 400? | Clamp, matching the proposal list precedent | Error behavior and tests |
| UD-3 | Is there a maximum `page` or additional deep-page guard? | Add an explicit documented ceiling only if deep paging matters; do not invent a number in this proposal | Validation behavior and offset safety |
| UD-4 | Entity sort key: `externalRef` or `name`? | `externalRef ASC NULLS LAST, id ASC` | Ordering contract, index needs, UI expectations |
| UD-5 | Is `total` required (`Page`) or is `hasNext` sufficient (`Slice`)? | `Page` + `total`, for contract consistency | Response shape and count cost |
| UD-6 | How does the console resolve relationship endpoint names after entity pagination? | Frontend follow-up; do not change the relationship DTO as part of pagination | UI behavior and scope boundary |
| UD-7 | Should the relationship response ever include endpoint names or external refs? | Out of scope for Issue #52; separate design if desired | Scope and DTO compatibility |
| UD-8 | Is keyset pagination required instead of offset paging? | Not for the first step; revisit with real usage data | Contract and implementation complexity |
| UD-9 | How should unknown or repeated query parameters be handled? | Ignore unknown parameters; treat repeated parameters as out of scope unless the project wants strict validation | Validation specificity |
| UD-10 | Does a pagination implementation need a recorded decision entry before code? | Yes, given the D36 precedent for a list contract | Governance and review sequencing |

## 13. Acceptance criteria mapping

| Issue requirement | Where addressed |
|---|---|
| Request contract (`page`/`size` or justified alternative) | Section 4 |
| Default and maximum page size | Section 4.2 |
| Missing, negative, excessive values | Sections 4.2-4.3 |
| Deterministic entity ordering | Section 5.1 |
| Deterministic relationship ordering | Section 5.2 |
| Unique tie-breakers and stable boundaries | Section 5.3 |
| Content structure and metadata | Sections 6.1-6.2 |
| Total-count behavior | Section 6.3 |
| Empty and out-of-range pages | Section 6.2 |
| Backward compatibility | Section 6.4 |
| Page vs Slice | Section 7.1 |
| Count-query cost | Section 7.2 |
| Relationship and endpoint loading | Section 7.4 |
| Query and index considerations | Section 7.5 |
| Testing: page positions, empty, max, invalid, ordering, integrity, regression | Section 8 |
| Rollout: review, stages, gates | Section 9 |
| Verified vs recommended vs alternatives vs unresolved | Labelling in section 1, plus sections 11-12 |
| Documentation-only, no implementation authorization | Sections 1.3 and 14 |

## 14. Implementation authorization status

**This proposal is not adopted and authorizes no implementation.**

In particular, it does not authorize:

- Java source or test changes;
- controller, service, repository, or DTO changes;
- API route, response, security, or authorization changes;
- schema, migration, index, or dependency changes;
- configuration changes;
- frontend changes;
- graph snapshot limit or semantic changes;
- any other documentation change.

Any implementation requires a separate maintainer decision after this
proposal is reviewed, with the unresolved decisions in section 12 resolved
first.

## 15. References

Repository documents:

- [CONTRIBUTING.md](../../CONTRIBUTING.md)
- [ARCHITECTURE.md](../../ARCHITECTURE.md)
- [DECISIONS.md](../../DECISIONS.md)
- [PROJECT_STATUS.md](../../PROJECT_STATUS.md)
- [THREAT_MODEL.md](../../THREAT_MODEL.md)

Implementation files read for this proposal:

- [TwinController.java](../../backend/src/main/java/com/soubhagya/systivex/twin/api/TwinController.java)
- [TwinService.java](../../backend/src/main/java/com/soubhagya/systivex/twin/service/TwinService.java)
- [SystemEntityRepository.java](../../backend/src/main/java/com/soubhagya/systivex/twin/repository/SystemEntityRepository.java)
- [SystemRelationshipRepository.java](../../backend/src/main/java/com/soubhagya/systivex/twin/repository/SystemRelationshipRepository.java)
- [SystemEntity.java](../../backend/src/main/java/com/soubhagya/systivex/twin/model/SystemEntity.java)
- [SystemRelationship.java](../../backend/src/main/java/com/soubhagya/systivex/twin/model/SystemRelationship.java)
- [SystemEntityResponse.java](../../backend/src/main/java/com/soubhagya/systivex/twin/api/SystemEntityResponse.java)
- [SystemRelationshipResponse.java](../../backend/src/main/java/com/soubhagya/systivex/twin/api/SystemRelationshipResponse.java)
- [RestExceptionHandler.java](../../backend/src/main/java/com/soubhagya/systivex/twin/api/RestExceptionHandler.java)
- [SecurityConfig.java](../../backend/src/main/java/com/soubhagya/systivex/security/SecurityConfig.java)
- [V1__create_system_twin.sql](../../backend/src/main/resources/db/migration/V1__create_system_twin.sql)
- [ProposalController.java](../../backend/src/main/java/com/soubhagya/systivex/proposal/api/ProposalController.java)
- [ProposalService.java](../../backend/src/main/java/com/soubhagya/systivex/proposal/service/ProposalService.java)
- [ProposalListResponse.java](../../backend/src/main/java/com/soubhagya/systivex/proposal/api/ProposalListResponse.java)
- [TwinApiTest.java](../../backend/src/test/java/com/soubhagya/systivex/twin/TwinApiTest.java)
- [TwinServiceTest.java](../../backend/src/test/java/com/soubhagya/systivex/twin/TwinServiceTest.java)
- [TwinRepositoryTest.java](../../backend/src/test/java/com/soubhagya/systivex/twin/TwinRepositoryTest.java)
- [twin.js](../../frontend/src/api/twin.js)
- [TwinConsole.jsx](../../frontend/src/components/TwinConsole.jsx)
- [Relationships.jsx](../../frontend/src/views/Relationships.jsx)
- [twin.test.js](../../frontend/src/test/twin.test.js)

Issue:

- [Issue #52](https://github.com/soubhagya-behera/systivex/issues/52)

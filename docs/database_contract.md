# Member 2 database contract

## Authority and verification boundary

The supplied **EcoRoute_Relational_Schema (1).docx** defines relation names, fields, keys, subtype associations and delete behavior. The **Member 2 Java Backend JDBC Workplan** describes responsibilities. The user's implementation request takes priority where it differs: standard Maven paths, no algorithms/GUI, no production DDL, no commits or database changes during implementation.

There was no production DDL in `database/` or `docs/` when this backend was implemented. No SQL types, ID generation, statuses, password format, routines or view definitions have been confirmed by Member 1. `src/test/resources/member2_fixture.sql` is a **provisional test fixture only**. Passing H2 tests establishes backend behavior against that fixture, not MySQL or production-schema compatibility. The opt-in MySQL tests also exercise the fixture, not eventual production DDL.

## Provisional mapping

| Data | Proposed SQL | Java |
| --- | --- | --- |
| Surrogate keys and foreign keys | signed BIGINT; surrogate PKs AUTO_INCREMENT | `Long`; null surrogate ID means unsaved |
| REQUEST_WASTE key | `(request_id, category_id)` composite PK | two required Long fields |
| Subtype key | `generator_id` inherited PK/FK, never independently generated | Long |
| Quantities and capacity | DECIMAL(12,3), kg | BigDecimal |
| Latitude / longitude | DECIMAL(9,6), nullable | BigDecimal |
| `created_date` everywhere, `request_date`, `completion_date` | DATETIME(6) | LocalDateTime |
| Route/preferred dates and subtype expiry dates | DATE | LocalDate |
| Stop arrival/completion | DATETIME(6), nullable | LocalDateTime |
| Active flags | BOOLEAN, not null, default true | boolean |
| Stop sequence / flats | INT | int |
| Names, status, role, subtype, hazard | VARCHAR | String |

SQL NULL maps to Java null for optional references, strings, dates and quantities. In particular, `actual_quantity = NULL` means unmeasured; zero means measured zero. BigDecimal estimates/capacities must be positive, actuals nonnegative, and inputs must fit the provisional precision without rounding. Coordinates allow six decimal places and geographic ranges.

Every generated-key DAO insert requires a null model ID and returns the database-generated `long`; it does not modify the caller's model. Rollback can leave ID gaps. There is no `MAX(id)+1` strategy. If Member 1 uses sequences or assigned IDs, the insert/key retrieval contract must be adapted before integration. Composite/subtype keys are supplied explicitly.

Proposed VARCHAR lengths appear in the fixture: names 100 or 200; category/zone/username 100; vehicle number/phone 40; email 254; descriptions/address/remarks 1000; password hash 255; subtype identifiers 100; roles/statuses/hazard 20; generator type 30. These are assumptions, not approved production limits.

`DATETIME` carries no offset. The application currently uses its local clock, so all application instances must use the same agreed business time zone (suggested `Asia/Kolkata`). Confirm the time zone and SQL date types with Member 1; changing to TIMESTAMP or DATE requires a deliberate mapping review. Test assertions avoid nanosecond equality because DATETIME(6) stores microseconds.

Table names retain their supplied uppercase spelling. `USER` is always quoted with MySQL backticks. No `PICKUP_REQUEST.zone_id` or stored route total columns have been added. Request zone is derived through WASTE_GENERATOR. Subtype details stay in HOSPITAL, HOUSING_SOCIETY and FACTORY.

## Confirmed structural rules preserved

- Unique natural keys: zone name, username, category name, vehicle number.
- ROUTE_STOP has its own stop_id, globally unique request_id, and UNIQUE(route_id, stop_sequence).
- REQUEST_WASTE has its composite primary key; a category cannot repeat in a request.
- GENERATOR users require a generator_id. ADMIN/OPERATOR users require a null generator_id.
- Subtype → generator, ROUTE_STOP → route, REQUEST_WASTE → request: ON DELETE CASCADE.
- VEHICLE.assigned_zone_id → zone: ON DELETE SET NULL.
- All other supplied foreign keys: ON DELETE RESTRICT.
- Category deletion is a real DELETE; referenced categories are protected by the FK. Cancellation is a status update.

## Proposed lifecycle and roles

Values are centralized in `DatabaseContract`. The fixture mirrors them to test database enforcement; keep both aligned when the contract is finalized.

| Entity | Allowed service transitions |
| --- | --- |
| Pickup | created as PENDING; PENDING → CANCELLED or ASSIGNED; ASSIGNED → COMPLETED |
| Stop | created as PENDING; PENDING → COMPLETED |
| Route | created as PLANNED; PLANNED → COMPLETED once every stop completes |
| Vehicle | AVAILABLE → IN_USE on assignment; IN_USE → AVAILABLE after last stop; admin may change AVAILABLE ↔ MAINTENANCE |
| Hazard | proposed LOW, MEDIUM, HIGH; no routing/safety algorithm is inferred from these values |

Generator types and user roles use the exact values in the supplied schema. No route cancellation/reassignment or partial collection workflow is implemented: the schema permits only one stop per request across its lifetime. Terminal pickup states cannot be reopened through services.

A vehicle is reserved immediately when a plan is saved, even for a future date. It may have **one unfinished route at a time**, and may be reused when that route finishes. Home zone is optional; when set, the route must match it. A route cannot precede today, the request date or its preferred pickup date. Stops must arrive in ascending positive, unique sequence order; sequences need not be contiguous. The backend does not reorder the plan.

Both estimated route total and cumulative measured actual weight must fit the vehicle capacity. Actuals may exceed individual estimates if the vehicle still has room. An over-capacity completion is rejected atomically; an operational exception/correction workflow needs team agreement. All categories must be measured together, including explicit zeros. Arrival cannot precede request creation or the route date; completion cannot precede arrival or lie in the future. Actuals are immutable after completion.

Generator creation always creates an active record. Hospital/housing/factory generators require exactly the matching subtype row. Other types have no subtype row. Expiry dates are required but expired licenses do not yet block operations; regulatory policy is unresolved. Subtype discriminator and subtype records are not edited through the current base-profile update. Generator zone changes require no request history, because moving the generator would also change historical requests' derived zone. Collection also rejects inactive generators if their flag was changed outside these services. Deactivation is blocked while requests are assigned; pending requests can be cancelled but cannot be assigned while inactive.

Permissions:

- Any authenticated user may read zones and categories.
- GENERATOR may create, read and cancel its own pending requests, and read its own profile.
- ADMIN/OPERATOR may manage request workflows across generators, save supplied routes, collect, and read reports/fleet/profiles.
- ADMIN alone maintains zones, categories, generators and vehicles.
- Services reload the session user's active status, role and generator association on each operation. Inactive or changed accounts are rejected. `UserSession` is immutable and issued by AuthService; no password hash is exposed.
- DAOs are trusted internal persistence tools and do not authorize callers. GUI/algorithm integration must use services. This is an in-process desktop backend, not a remote security boundary against arbitrary code running with database credentials.

## Authentication proposal

Proposed encoding: `pbkdf2-sha256$600000$<base64 16-byte salt>$<base64 32-byte hash>`.

`PasswordHasher` uses Java's PBKDF2WithHmacSHA256, a fresh SecureRandom salt, 600,000 iterations and constant-time derived-hash comparison. New passwords are 12–1024 characters. Verification accepts this format with 600,000–2,000,000 iterations, bounds the work factor, and rejects malformed/unknown formats. There is no plaintext or unsalted-SHA fallback. Callers clear password char arrays; hashes never appear in model/session toString methods.

PBKDF2's work factor follows the [OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html). The JDK implementation avoids adding a password library to this student project. Login checks inactive accounts and performs a dummy derivation for unknown usernames. Rate limiting, account provisioning and password resets are outside this contribution.

Member 1 must confirm this exact format or provide the actual supported format for an explicit verifier migration. No compatibility with existing hashes is claimed. There is no production user insertion/seeding workflow; Member 1 provisions initial accounts using agreed hashes. `PasswordHasher.hash(char[])` is available for that controlled handoff.

## Connection and transaction rules

- `DatabaseConnection` reads ECOROUTE_DB_URL/USER/PASSWORD and opens a fresh connection each time. It does not load `.env` or cache a global Connection.
- `Database` accepts an injectable ConnectionProvider. Providers must return newly owned connections.
- Service operations with writes use `Database.transaction`: READ_COMMITTED isolation, auto-commit off, one shared Connection for all DAOs, commit only after all steps, rollback on SQL/runtime errors, close afterward.
- DAOs close statements/result sets only; they never commit/rollback/close the borrowed connection.
- Services own request+lines, generator+subtype, route+stops+statuses, and collection+timing+statuses transactions.
- Assignment locks vehicle and then requests in increasing ID order using SELECT FOR UPDATE. Generator rows are also locked for eligibility checks. Collection locks vehicle, route, request, generator, then stop. Vehicle locking serializes cumulative capacity checks and release.
- The unique request_id/sequence constraints remain the final protection against duplicate assignment. Concurrent conflicts/deadlocks are returned as database errors; after refreshing state, a caller may retry the whole operation. There is no silent automatic retry.
- Keep Connector/J `useAffectedRows=false` (its default): updates check exactly one **matched** row, including idempotent edits. Conditional status updates include the expected old status.
- Commit/connection failures can leave the caller uncertain about the commit outcome. Inspect current state before retrying a create. There are no idempotency keys in the supplied schema.
- Top-level database messages omit credentials and retain original causes. The GUI should display only the safe top-level message; do not print raw JDBC causes, connection URLs or password arrays to user-facing logs.

These ownership rules follow the [JDBC Connection contract](https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/Connection.html).

## Reports and derived views

ReportDAO implements named SELECT queries directly over the supplied relations. No production views/routines are created and no stored procedure names are invented. Member 1 owns V_ROUTE_WEIGHT, V_PICKUP_REQUEST_ZONE, procedures, functions and triggers. Their exact SQL definitions and null semantics are still required before using them directly.

All intervals are `[from, to)` and all quantities are **kg**:

| Query | Date filter | Definition |
| --- | --- | --- |
| collectionVolume | request completion timestamp | Actual kg per category for COMPLETED requests, plus measured-line count |
| requestStatuses | request creation timestamp | Count pickup requests per status without joining waste lines |
| routeWeights | route_date | Sum each stop's request waste exactly once; estimated kg, nullable actual kg, measured and total line counts |
| vehicleUtilization | route_date | One row per route; estimated/actual kg divided by that route's vehicle capacity; multiply ratio by 100 for percent |
| estimatedVersusActual | request creation timestamp | Category totals across requests, nullable actual kg and measured/total line counts |

Utilization is load utilization per route, not distance/time/fleet availability. The denominator is not multiplied by the number of stops or waste lines. Unmeasured actual sums stay NULL; a partly collected route shows its known partial sum and measured-line count. Do not present a partial actual total as final. An empty route has estimated total zero and actual total NULL. Empty collections return empty lists.

## Member 1 confirmation checklist

1. Exact CREATE TABLE statements: types, lengths, scales, defaults, checks, collation, capitalization and InnoDB usage; MySQL version with enforced CHECK constraints.
2. AUTO_INCREMENT versus another ID strategy, generated-key support, and signed BIGINT ranges.
3. Date/time types, precision and business time zone.
4. All status/hazard values and allowed transitions; home-zone restriction; future reservation and vehicle reuse rules.
5. Capacity exception/partial collection handling, preferred-date meaning, generator zone/history and deactivation rules, subtype expiry policy.
6. Password encoding and initial account provisioning; confirm existing hashes explicitly.
7. View definitions and NULL behavior, routines' actual names/parameters/results, and any triggers that also change statuses. Double-writing trigger-owned transitions must be resolved before integration.
8. Least-privilege application credentials and a separate empty disposable test schema/account. Confirm normal database access separately; no production connection has been verified.

Report conflicts between actual DDL and this relational design before changing mappings. Do not silently rename fields or widen the task into Member 1's implementation.

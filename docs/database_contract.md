# Member 2 database contract

## Authority and verification boundary

**EcoRoute_FINAL_FREEZED_Relational_Schema.docx** is the authoritative design contract for this migration. The user's request controls Member 2 scope: update the existing backend, preserve architecture, and implement no routing algorithms, GUI, production DDL or stored routines.

No executable Member 1 production DDL was found in this workspace. `src/test/resources/member2_fixture.sql` now follows the frozen relations, suggested types, lengths and nullability, but is **Member 2's test fixture only**. H2 tests establish behavior against that fixture. Even eventual MySQL fixture tests will not establish compatibility with Member 1's actual SQL.

## Confirmed by FINAL FREEZED schema

The 15 relations are ZONE, WASTE_GENERATOR, HOSPITAL, HOUSING_SOCIETY, FACTORY, USER, GENERATOR_USER, STAFF_USER, VEHICLE, DISPOSAL_SITE, ROUTE, ROUTE_STOP, PICKUP_REQUEST, WASTE_CATEGORY and REQUEST_WASTE.

| Mapping | Frozen design / fixture mapping | Java |
| --- | --- | --- |
| IDs and foreign keys | INT; marker/subtype keys reuse their parent's ID | Existing Long/long APIs retained; these represent all signed INT values |
| REQUEST_WASTE key | Composite `(request_id, category_id)` | Two required Long fields |
| Waste quantities / disposal capacity | DECIMAL(12,2) | BigDecimal |
| Vehicle capacity | DECIMAL(10,2) | BigDecimal |
| Coordinates | DECIMAL(10,7); generator nullable, disposal site required | BigDecimal |
| Creation/request/completion dates | Design permits DATE/DATETIME; fixture retains DATETIME(6) | LocalDateTime |
| Preferred pickup date / route date | DATE NOT NULL | LocalDate; creation validates required values |
| Subtype expiry dates | DATE NULL | LocalDate |
| Stop arrival/completion | DATETIME NULL; fixture retains microseconds | LocalDateTime |
| Housing number_of_flats | INT NULL | Integer (previously int lost NULL as zero) |
| ROUTE.site_id | Nullable FK to DISPOSAL_SITE.site_id | Long, null until supplied |
| Active flags | BOOLEAN NOT NULL | boolean |

Frozen lengths: zone/category/username 100; generator/site name 150; generator/site type 50; contact person 100; phone 20; email 150; address/description 255; remarks 500; vehicle number 30; password hash 255; role/status/hazard 30; generator subtype text 100. WASTE_GENERATOR.address is required. Generator subtype detail fields are nullable, including license/registration/consent identifiers and expiry dates. A matching subtype object is still required for the existing specialized generator types, but its optional details may be null.

SQL NULL remains Java null. `actual_quantity = NULL` means unmeasured; zero means measured zero. Service quantities must fit two decimal places and the applicable precision without rounding; trailing zeros are accepted. Coordinates allow seven decimal places and retain geographic range validation. Optional flat counts retain positive-if-present validation.

Confirmed relationships and keys:

- ZONE → generators, routes and optionally assigned vehicles; generator → users and pickup requests.
- WASTE_GENERATOR specialization is disjoint/partial: HOSPITAL, HOUSING_SOCIETY and FACTORY reuse generator_id.
- USER specialization is disjoint/total: exactly one GENERATOR_USER or STAFF_USER marker, containing only user_id.
- VEHICLE → routes; DISPOSAL_SITE → routes through nullable site_id; route → ordered stops.
- ROUTE_STOP.request_id is globally UNIQUE: one request belongs to at most one stop.
- REQUEST_WASTE resolves request/category M:N with its composite primary key.
- Unique names/numbers: zone_name, username, vehicle_number, site_name, category_name.
- PICKUP_REQUEST has no zone_id. Its zone is derived through WASTE_GENERATOR.
- Route weights are derived from REQUEST_WASTE through stops, never stored as base ROUTE fields.

Table names keep uppercase spelling; USER is quoted with MySQL backticks. Result mappings use column names. The generated-key result is read positionally using the existing JDBC getGeneratedKeys contract, which is independent of added table columns.

## Still requires Member 1 SQL confirmation

The document labels SQL types as suggested and permits DATE/DATETIME alternatives. Confirm the actual executable DDL before production integration, including:

- Exact SQL types, lengths, scales, timestamp precision, defaults, collation, case handling and SQL mode (no silent truncation/rounding).
- AUTO_INCREMENT/generated-key choices. The fixture preserves AUTO_INCREMENT; Java inserts return generated long IDs and require null model IDs. No MAX(id)+1 strategy is used.
- FK ON DELETE and ON UPDATE actions. Existing fixture CASCADE/RESTRICT/SET NULL behavior is retained, but the frozen document does not confirm it. New marker FKs use fixture-only CASCADE; ROUTE.site_id uses fixture-only RESTRICT.
- **UNIQUE(route_id, stop_sequence)**: retained from the old fixture and application behavior, **not confirmed by the frozen document**. Application sequences remain positive, unique and ascending even if production SQL differs.
- Role values and their subtype/generator associations. Existing GENERATOR maps to GENERATOR_USER and requires generator_id; ADMIN/OPERATOR map to STAFF_USER and require null generator_id. These are preserved application conventions, not a frozen role-value list.
- How provisioning/SQL will guarantee disjoint/total user specialization. FK/PK constraints alone do not enforce exactly one marker across two tables. AuthService and session revalidation reject missing, dual or role-mismatched markers; no trigger contract is invented.
- Status, generator-type and hazard vocabularies, checks and lifecycle policies below. Existing values are preserved. Disposal site status/type values, suitability, capacity meaning and availability policy are unspecified. Retrieval supports an exact parameterized status filter; saving a destination validates positive ID and existence only, without capacity reservations or nearest-site selection. `OPEN` and `PROCESSING` in tests are sample data, not a production vocabulary.
- Indexes, check constraints, views, procedures, functions, triggers and any server-owned state changes. No production routines are assumed.
- Password-hash format and initial account provisioning; separate disposable test database and least-privilege application credentials.

DATETIME has no offset. Applications must share an agreed business time zone (proposed Asia/Kolkata). DATE instead of DATETIME requires an explicit mapping review. Existing Long APIs are retained to avoid unnecessary public API changes; persisted IDs must fit Member 1's INT columns. HousingSociety's nullable numberOfFlats now uses Integer in its constructor/getter/setter; existing nonnull int callers auto-box/unbox, and readers must handle null.

Category deletion remains a real DELETE protected by FKs. Cancellation is a status update. The fixture's additional constraints test retained behavior without claiming that the frozen document specified them.

## Proposed lifecycle and roles

Values are centralized in `DatabaseContract`. The fixture mirrors them to test database enforcement; keep both aligned when the contract is finalized.

| Entity | Allowed service transitions |
| --- | --- |
| Pickup | created as PENDING; PENDING → CANCELLED or ASSIGNED; ASSIGNED → COMPLETED |
| Stop | created as PENDING; PENDING → COMPLETED |
| Route | created as PLANNED; PLANNED → COMPLETED once every stop completes |
| Vehicle | AVAILABLE → IN_USE on assignment; IN_USE → AVAILABLE after last stop; admin may change AVAILABLE ↔ MAINTENANCE |
| Hazard | proposed LOW, MEDIUM, HIGH; no routing/safety algorithm is inferred from these values |

Generator types and user roles retain the existing application values; the frozen schema does not enumerate them. No route cancellation/reassignment or partial collection workflow is implemented: the schema permits only one stop per request across its lifetime. Terminal pickup states cannot be reopened through services.

A vehicle is reserved immediately when a plan is saved, even for a future date. It may have **one unfinished route at a time**, and may be reused when that route finishes. Home zone is optional; when set, the route must match it. A route cannot precede today, the request date or its preferred pickup date. Stops must arrive in ascending positive, unique sequence order; sequences need not be contiguous. The backend does not reorder the plan.

Both estimated route total and cumulative measured actual weight must fit the vehicle capacity. Actuals may exceed individual estimates if the vehicle still has room. An over-capacity completion is rejected atomically; an operational exception/correction workflow needs team agreement. All categories must be measured together, including explicit zeros. Arrival cannot precede request creation or the route date; completion cannot precede arrival or lie in the future. Actuals are immutable after completion.

Generator creation always creates an active record. Hospital/housing/factory generators require exactly the matching subtype row. Other types have no subtype row. Expiry dates are optional; expired licenses do not yet block operations and regulatory policy is unresolved. Subtype discriminator and subtype records are not edited through the current base-profile update. Generator zone changes require no request history, because moving the generator would also change historical requests' derived zone. Collection also rejects inactive generators if their flag was changed outside these services. Deactivation is blocked while requests are assigned; pending requests can be cancelled but cannot be assigned while inactive.

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

Member 1 must confirm this exact format or provide the actual supported format for an explicit verifier migration. No compatibility with existing hashes is claimed. There is no production user insertion/seeding workflow; Member 1 must provision USER plus exactly one matching marker atomically using agreed hashes. UserDAO checks markers without adding empty marker model classes; AuthService and Access use this check. Test provisioning also inserts parent and marker in one transaction, with rollback coverage. `PasswordHasher.hash(char[])` is available for that controlled handoff.

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
2. AUTO_INCREMENT versus another ID strategy, generated-key support, and signed INT ranges.
3. Date/time types, precision and business time zone.
4. Role values/subtype mapping, cross-table disjoint/total enforcement, and all status/hazard values and allowed transitions; home-zone restriction; future reservation and vehicle reuse rules.
5. Disposal status/type/suitability/capacity policy; sequence uniqueness and FK actions; capacity exception/partial collection handling, preferred-date meaning, generator zone/history and deactivation rules, subtype expiry policy.
6. Password encoding and initial account provisioning; confirm existing hashes explicitly.
7. View definitions and NULL behavior, routines' actual names/parameters/results, and any triggers that also change statuses. Double-writing trigger-owned transitions must be resolved before integration.
8. Least-privilege application credentials and a separate empty disposable test schema/account. Confirm normal database access separately; no production connection has been verified.

Report conflicts between actual DDL and this relational design before changing mappings. Do not silently rename fields or widen the task into Member 1's implementation.

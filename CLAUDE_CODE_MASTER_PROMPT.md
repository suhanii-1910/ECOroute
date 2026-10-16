# ECOroute — Autonomous End-to-End Implementation Master Prompt for Claude Code

You are acting as the **sole senior engineer, architect, database designer, Java developer, algorithms engineer, Swing UI developer, test engineer, and integration owner** of ECOroute. Your objective is to **finish the existing application in this repository**, not produce advice, a skeleton, a plan-only answer, or four isolated team modules.

## Mission and non-negotiable outcome

**Project:** ECOroute — Waste Management and Pickup Routing System.
**Repository:** https://github.com/suhanii-1910/ECOroute
**Stack:** Java 17 compatibility, Maven, Swing desktop GUI, JDBC, MySQL 8.0.16+ (prefer current MySQL 8), JUnit 5. Keep working Java packages and good existing code.

Build ONE cohesive, launchable Java desktop application covering authentication, users/generators, categories and vehicles, pickup requests, capacity-aware routing and stop ordering, actual collections, authorized disposal facility selection, unloading receipts, dashboards and reports. Implement the final DBMS deliverables (DDL, seed data, constraints, views, procedures, functions, triggers, queries), with secure role-based access, reproducible setup, meaningful demos, and verified tests.

**Do the implementation yourself, across all former member responsibilities.** Progress from repository review to code changes, compiling, testing, launching and verifying. Do not end after a roadmap. You may use available coding tools and parallel independent code research, but one integrated build and one verified schema must result. Work in focused increments, keep running a concise progress log, and continue past intermediate milestones. Ask the user only for truly unavailable secrets, environment permissions or an irreversible operation. Do not claim a test or GUI flow passed unless actually executed.

## Inputs / source of truth

FIRST open and read these reference materials in `docs/reference/` (provided with this prompt as a starter pack):
1. `EcoRoute_Relational_Schema_v3_Implementation_Corrected.docx` — **AUTHORITATIVE** for the final 18 base relations, column names, SQL types, PK/FK/UK, defaults, CHECK lists, nullability, delete actions, generated column, derived views, and integrity addendum.
2. `ECOroute_Member_1_DBMS_Workplan_v4_Corrected.docx` — implementation, expected SQL artifacts, test cases and demonstration scope. It was written for a member, but YOU are now responsible for all those deliverables.
3. `final-eer.png` — final approved EER diagram (generator and disposal-facility specializations).
4. `er-disposal-lifecycle.jpeg` — supporting approved ER/disposal-lifecycle diagram.
5. The actual `README.md`, `pom.xml`, source code, test code, and `docs/` already in the local repository.

**Priority on conflicts:** corrected relational schema and its implementation addendum > approved diagrams for conceptual interpretation > corrected DBMS work plan for deliverables > existing README/docs > old Java fixture and old code. Where a policy remains unspecified, select the smallest safe, demo-appropriate policy, document it explicitly, and test it. Do not silently alter the 18-table schema. If a crucial conflict cannot be resolved without changing the approved schema, isolate and report that conflict without fabricating a schema change. No external paid APIs are required.

If the CLI cannot directly read DOCX, extract paragraphs AND tables with an available local tool (e.g., Python `python-docx`, macOS `textutil`, or Word XML via `zipfile`), and inspect the EER images with a suitable available viewer; do not pretend an unread source was inspected. If any reference file is missing, identify exactly which file is unavailable. Continue with code you can safely inspect, but DO NOT guess exact database field definitions or claim schema parity without the file.

## Verified GitHub starting point — RECHECK LOCALLY

The known remote branches (as reviewed on 2026-10-13) are:
- `main` — earlier backend baseline.
- `member2-jdbc` — later JDBC/service migration built against an **obsolete 15-relation design**; contains useful implementation and automated tests.
- `member3-algorithms` — real implementations for `ecoroute.algorithm`, `ecoroute.planning`, plus `RoutePlanningService`, `RequestPlanningService`, `VehiclePlanningService`, and algorithm tests.
- `member4-swing` — at reviewed revision, does not contain Swing GUI files; effectively an older backend state.
- No `member1-dbms` branch was found in the reviewed remote branch list.

Do not assume branches are unchanged: run `pwd`, `git status --short --branch`, `git remote -v`, `git fetch --all --prune`, inspect current heads, logs/diffs/tree, Java/Maven availability, and current reference file inventory. **Check `git status` immediately before checkout, branch creation, merge, or push.** If unrelated local changes exist, preserve them and ask rather than overwrite. Never reset, force-push, delete branches, rewrite history, or silently discard work. Work on a NEW dedicated integration branch (e.g., `integration/ecoroute-complete`) based on the latest appropriate working baseline, normally `origin/member2-jdbc`. Incorporate the actual algorithm branch by safe merge/cherry-pick or carefully port its changes, resolving conflicts consciously; do not blindly overwrite backend services. Document which commits/branches were incorporated. `member4-swing` should not be mistaken for a completed GUI. No automatic push or merge to `main` without my explicit approval; local verified commits on the new branch are fine.

Before implementing, inventory all production classes and tests; identify what to RETAIN, MIGRATE, REUSE FROM ALGORITHM BRANCH, or BUILD. Create `docs/integration_gap_analysis.md` showing concrete file-level mismatches and key decisions. Then implement; do not wait for further instructions.

## Final exact relational baseline — 18 base tables only

Core (9): `ZONE`, `WASTE_GENERATOR`, `USER`, `WASTE_CATEGORY`, `VEHICLE`, `PICKUP_REQUEST`, `REQUEST_WASTE`, `ROUTE`, `ROUTE_STOP`.
Disposal module (3): `DISPOSAL_FACILITY`, `FACILITY_ACCEPTED_WASTE`, `DISPOSAL_RECORD`.
EER subtypes (6): `HOSPITAL`, `HOUSING_SOCIETY`, `FACTORY`, `RECYCLING_PLANT`, `PROCESSING_PLANT`, `HAZARDOUS_DISPOSAL_SITE`.

Absolutely DO NOT retain these obsolete assumptions in the final production implementation:
- No `DISPOSAL_SITE` table or `DisposalSite` production model/DAO.
- No `ROUTE.site_id`, no route-level optional destination column; the disposal destination is represented by `DISPOSAL_RECORD` after unloading.
- No `GENERATOR_USER` or `STAFF_USER` tables or membership checks. Authentication uses ONE `USER` table with role and generator relationship.
- Do not place `PICKUP_REQUEST.zone_id` or stored route total-weight columns in production.
- Do not treat `src/test/resources/member2_fixture.sql` as authoritative MySQL DDL.

Important exact rules to preserve from the corrected schema:
- `USER.role`: `GENERATOR`, `ADMIN`, `OPERATOR`, `DRIVER`; `generator_id` is required iff the role is `GENERATOR`, otherwise NULL; quote the SQL table identifier as backtick-`USER`.
- `WASTE_CATEGORY.hazard_level`: `LOW`, `MEDIUM`, `HIGH`, `BIOMEDICAL` (default `LOW`).
- `VEHICLE.status`: `AVAILABLE`, `IN_ROUTE`, `MAINTENANCE`; its nullable `assigned_zone_id` FK uses `ON DELETE SET NULL`. No `IN_USE` value.
- `PICKUP_REQUEST.status`: `PENDING`, `ASSIGNED`, `IN_ROUTE`, `COMPLETED`, `CANCELLED`; `preferred_pickup_date` NOT NULL.
- `ROUTE.status`: `PLANNED`, `IN_PROGRESS`, `DISPATCHED_TO_DISPOSAL`, `COMPLETED`, `CANCELLED`.
- `ROUTE_STOP.status`: `PENDING`, `COMPLETED`, `SKIPPED`; `ROUTE_STOP.request_id` is NOT NULL UNIQUE and `UNIQUE(route_id, stop_sequence)` must exist.
- `REQUEST_WASTE` composite PK `(request_id, category_id)`; estimated quantity NOT NULL, actual quantity nullable (NULL means not yet recorded, not zero).
- `DISPOSAL_FACILITY.facility_type`: `RECYCLING_PLANT`, `PROCESSING_PLANT`, `HAZARDOUS_DISPOSAL_SITE`, `LANDFILL`. It has an active flag, zone, location, and `capacity_tonnes_per_day` (TONNES per day, while other mass measurements are kg).
- `FACILITY_ACCEPTED_WASTE` composite PK `(facility_id, category_id)` and nonnegative processing rate. It defines category acceptance.
- `DISPOSAL_RECORD` supports multiple records per route, unique receipt number, gross and tare weights, `net_disposed_weight_kg` as MySQL STORED GENERATED `(gross_weight_kg - tare_weight_kg)`, and status `VERIFIED`, `DISCREPANCY`, `REJECTED`; do not insert a value into the generated column.
- Generator subtypes and facility subtypes are **disjoint, partial** specializations. `LANDFILL` and generator types such as `HOTEL`/`SCHOOL` require NO subtype row.
- Honor all column lengths, DECIMAL scales, nullability, foreign-key RESTRICT/CASCADE/SET NULL behaviors from the DOCX, including NOT NULL required subtype credentials and optional subtype attributes. Do not guess based on older Java fields.
- Add the nonnegative/positive domain CHECKs listed in the relational schema addendum. MySQL version must enforce CHECK constraints.
- `V_ROUTE_WEIGHT` derives estimated and actual weight through `ROUTE` -> `ROUTE_STOP` -> `REQUEST_WASTE`. Distinguish incomplete/partial actual quantities from complete totals in the UI/reports.
- `V_PICKUP_REQUEST_ZONE` derives `zone_id` from request -> generator -> zone.
- Enforce cross-table rules in controlled service/procedure workflows: requests contain at least one waste line; dispatchable routes contain at least one stop; matched route/request zones; allowed date/status transitions; no duplicate routing of a request; legal vehicle capacity/availability.

## Workstream A — Authoritative MySQL database

Produce executable, restartable-from-clean-TEST-database SQL deliverables corresponding to the work plan:
`sql/01_create_database.sql`, `sql/02_create_tables.sql`, `sql/03_seed_data.sql`, `sql/04_views.sql`, `sql/05_queries.sql`, `sql/06_procedures.sql`, `sql/07_functions.sql`, `sql/08_triggers.sql`, plus `docs/db_setup.md` and `docs/db_test_cases.md`.

All 18 tables must be created in dependency order, with correct PK/FK/UK/NOT NULL/DEFAULT/CHECK rules, AUTO_INCREMENT, generated weights, exact role/status vocabularies, and delete actions. Explicitly select an application timezone/calendar-day convention. Seed reproducible zones, all generator/facility subtypes, ordinary non-subtype generators, a LANDFILL, all four user roles with valid secure password hashes, realistic vehicles, categories including biomedical/high-hazard, requests/waste lines, routes/stops and valid disposal records. Use no plaintext passwords in SQL repository history; if demo credentials are needed, make them clearly dev-only and provisioned via a documented safe local script or controlled seeded hash, not reusable production credentials. Ensure seed data cannot violate triggers or CHECKs.

Implement and test BOTH views. Implement meaningful SELECT/JOIN/aggregate/GROUP BY/HAVING/report queries covering pickup, zones, categories, hazard volumes, routes, staff/vehicle utilization, disposal totals, accepted categories, discrepancies and collection status. Implement useful MySQL stored procedures/functions including required `sp_record_disposal(route_id, facility_id, gross_weight_kg, tare_weight_kg, receipt_number)`, plus an appropriate zone summary/status procedure and derived-utilization function. Implement ALL SIX subtype BEFORE INSERT triggers and BOTH supertype discriminator BEFORE UPDATE triggers, with disjointness and match checking; protect subtype PK/FK changes consistently. Use clear SQLSTATE error messages.

`sp_record_disposal` is the application's controlled disposal write path. Validate route existence and permitted lifecycle, facility activity and permitted state, every category on the route being accepted by the facility, relevant hazard/clearance policy, nonnegative weights, gross >= tare, unique receipt number, and daily net capacity. Convert facility TONNES to KG for comparisons. Protect concurrent capacity checks with transaction-safe, consistent facility-row locking and an explicit transaction ownership strategy that does not accidentally commit unrelated Java operations. Handle rejected attempts atomically with clear failure; do not assume a route has only one disposal receipt. Test exact capacity boundary and over-capacity, and, if the local database permits, concurrent inserts.

Provide a safe clean-database bootstrap and verification command/script. Never run destructive DROP/RESET against an existing non-test database or erase user data without explicit consent. Production schema setup and disposable test setup must be distinguishable. Use parameterized Java SQL; only use SQL scripts and prepared queries, not string-concatenated user input.

## Workstream B — Migrate and strengthen existing Java 17 + JDBC backend

Reuse proven `ecoroute.db`, `ecoroute.dao`, `ecoroute.model`, `ecoroute.service`, validation, transaction ownership, `PasswordHasher`, exception handling, security checks and tests where correct. **Migrate** every obsolete mapping and validation to the final 18-table contract.

Specifically review/refactor `DatabaseContract`, `UserDAO`, `AuthService`, `Access`, `RouteDAO`, `Route`, `RoutePersistenceService`, `CollectionService`, `ReportDAO`, `ReportService`, `CatalogService`, generator subtype DAO/services, request/vehicle logic and fixtures. Eliminate old USER subtype-table queries, replace `DisposalSite` with `DisposalFacility` and matching DAOs/models, remove route `siteId` overloads, update all status transitions and authorization rules, and add `DRIVER` role and `BIOMEDICAL` hazards. Adopt exact SQL DECIMAL precision/scale instead of obsolete assumptions. Preserve parameterized `PreparedStatement`, correct `ResultSet` mappings, `BigDecimal` mass arithmetic and transaction rollback semantics.

Add DAO/model/service support for `DISPOSAL_FACILITY`, three facility subtypes, `FACILITY_ACCEPTED_WASTE` and `DISPOSAL_RECORD`; build a `DisposalService` calling the stored procedure with `CallableStatement` where appropriate. Clearly distinguish staff-admin/operator/driver/generator permissions, enforce generator ownership and authenticated session changes, keep password hashes compatible with provisioning, and never expose raw SQL/DB error details or secrets in Swing. Ensure route lifecycle ends AFTER verified or appropriately handled disposal, not merely after the last pickup stop. Any status transition changes must be coherent and tested, including skipped stops and discrepancy handling. Keep multiple disposal events per route valid. No hidden in-memory production state or mocked DB for real workflows.

Expose a stable service-level API for the GUI. GUI code must NEVER issue JDBC SQL or directly mutate DAOs. Use one `Database`/connection provider configuration, scoped connections per operation and transactions around multi-table writes. Preserve the existing `ECOROUTE_DB_URL`, `ECOROUTE_DB_USER`, `ECOROUTE_DB_PASSWORD` contract unless a verified reason to revise it; document environment loading. Never commit `.env` or secrets. Preserve isolated test DB configuration with `ECOROUTE_TEST_DB_URL`, `ECOROUTE_TEST_DB_USER`, `ECOROUTE_TEST_DB_PASSWORD`, `ECOROUTE_TEST_DB_DISPOSABLE=YES`, and the guard against a real application database. The existing `mvn -Pmysql-it verify` profile must run meaningful tests against the FINAL MySQL DDL, not only an obsolete H2 fixture.

## Workstream C — Reuse, integrate and verify routing algorithms

Do not rewrite tested code merely for aesthetics. Safely incorporate the actual `member3-algorithms` implementations: `ZoneGrouper`, `VehicleAssignment`, `RouteStrategy`, `NearestNeighborRoute`, `RequestOrderRoute`, `Distance`, `RoutePlanningService`, `RequestPlanningService`, `VehiclePlanningService`, planning DTOs and their tests. Preserve separation of pure algorithms and JDBC services.

Build the missing adapter/orchestration that fetches pending eligible requests, derived zones, generator coordinates, summed waste quantities and available vehicles through authenticated services; creates `RequestView` and `VehicleView`; calls `planDay(...)`; displays proposed plans and reasons for unassigned/ineligible requests; and persists selected routes and ordered `ROUTE_STOP` entries atomically through the migrated backend. Recheck all concurrency-sensitive eligibility/capacity rules at save time. A vehicle cannot be assigned twice; a pickup request cannot be assigned to multiple routes. Confirm coordinate-less generators are not silently dropped and stop sequences are valid.

Keep capacity-constrained greedy vehicle allocation and nearest-neighbour stop ordering (heuristics, NOT guaranteed optimal). Add nearest-**suitable** disposal-facility selection based on the last known route/stop coordinate when available and candidate facility locations, filtered by active state, category authorization and adequate remaining daily capacity; use deterministic fallback/manual selection when coordinates are insufficient. Explain distance method and limitations; don't add commercial maps, live GPS, AI, or internet-dependent APIs. Saving a planned route must NOT prematurely create an unloading record. Actual unload is a separate step.

Important: Some algorithm test classes currently contain `public static void main(String[] args)` using `ecoroute.harness.Check`, which Maven Surefire/standard JUnit may not execute. Convert/add JUnit 5 tests OR add an explicit reliable Maven-bound harness execution. The final test report must count tests that actually executed, including regression tests for all previously covered algorithm behavior.

## Workstream D — Complete working Java Swing frontend (not a mockup)

Create a runnable desktop launcher (e.g. `ecoroute.Main` or `ecoroute.ui.EcoRouteApp`) with sane Maven execution/packaging guidance. Design a polished, modern, calm and readable Swing app called **ECOroute**, with a restrained eco green/teal/navy palette, clear navigation/sidebar, informative empty states, validation, keyboard accessibility, sensible text contrast, consistent typography, tables, responsive layouts and dialogs. Use only justified lightweight dependencies (FlatLaf is optional if it genuinely simplifies polished Swing). No JavaFX/web application replacement, Figma-only output, or pictures pretending to be working UI.

Provide role-appropriate working screens:
- Login/logout/session and safe invalid-credential handling.
- Dashboard with correct data from MySQL: pending requests, route status, active vehicles, completed pickups, waste totals and disposal summaries.
- Generator flow: see their own info/request history, create a pickup request with preferred date and one or more category + estimated-kg lines, track status, cancel only when allowed. Never access other generators' requests.
- Admin flow: maintain zones, waste categories, generators and subtype details, generator/user provisioning through secure admin-supported flows (if feasible), vehicles, disposal facilities/subtypes and accepted categories. Provide functional CRUD only where allowed by FK/status rules; otherwise show a specific safe restriction rather than a dead button.
- Operator flow: filter pending requests, preview assigned/unassigned, plan by date/zone, view capacity allocations and ordered stops, save plan transactionally, view vehicles/routes, dispatch routes and monitor statuses.
- Driver/collection flow: see the routes operationally available to authorized drivers, their ordered stops, mark arrival/completion/skip under explicit rules, enter actual kg for each category and timestamps, see route capacity and transition to disposal after collection. IMPORTANT: the approved schema has NO driver-to-route assignment FK; do not falsely promise per-driver ownership or silently add a 19th table. If all authorized drivers can view active routes in this prototype, label/document that limitation explicitly and enforce the permissions the real schema supports.
- Disposal flow: display eligible nearest suitable facilities, accepted waste categories and capacity, select facility, enter gross/tare/receipt, invoke the MySQL procedure, display verified/discrepancy/rejected status and history. Provide truthful error feedback on incompatible categories/capacity exhaustion/duplicates.
- Reports: request/collection trends and statuses, zone/category/hazard aggregates, estimated vs actual, vehicle utilization, route/stop sequence and disposal records/receipts. Offer practical CSV export if straightforward. Reports must use actual database outputs with units and NULL semantics, not hardcoded mock values.

Use `SwingWorker` or another well-managed background mechanism for DB work. All UI mutations belong on the Event Dispatch Thread; never block the EDT with JDBC. Use validation and enabled-state controls, friendly errors, loading feedback, date pickers or valid date entry, and confirmation for destructive operations. Provide one realistic demo path through the entire system.

## Workstream E — Tests, reproducibility, documentation and academic evidence

Implement a test pyramid:
1. Pure unit tests for validation, algorithm determinism, grouping, capacity, edge cases, role access rules, status-transition logic, coordinate gaps and specializations.
2. DAO/service tests on H2 only where its SQL semantics are representative; never mistake H2 for authoritative MySQL.
3. REAL MySQL integration tests on a disposable schema populated with the finalized production DDL: all 18 relations, views, triggers, constraints, procedures, generated column and actual JDBC CRUD/transactions.
4. System-level scenario tests covering login -> generator request -> eligible planning -> route save -> staff collection -> facility selection -> stored-procedure disposal -> reports, plus intentional rejection cases.
5. GUI smoke validation: actually launch the desktop app when a display is available; otherwise verify compilation/automation and explicitly say GUI execution is unverified. Test on the local operating system where possible.

Required rejection/boundary cases include wrong-role access, other generator's data, invalid hash/credentials, invalid generator_id/role combinations, missing/wrong/multiple subtype membership, forbidden discriminator updates, FK/UNIQUE/CHECK failures, negative/over-precision masses, zero capacity, NULL vs zero actual quantities, duplicate request assignment, duplicate stop sequence, cross-zone route, wrong dates, route/vehicle state conflicts, over-capacity vehicle, unauthorized category at facility, inactive facility, gross < tare, duplicate receipt, daily capacity boundary and overflow, two disposal receipts for one route where allowed, and failed-transaction rollback. Prove `V_ROUTE_WEIGHT` and `V_PICKUP_REQUEST_ZONE` and all eight mandatory subtype/discriminator triggers. Do not fabricate screenshots or test outputs.

Run actual commands whenever tooling allows: `mvn clean test`, `mvn package`, `mvn -Pmysql-it verify` (with dedicated disposable MySQL environment), then launch the app with demo credentials and verify at least one complete user journey. Fix failures, rerun all relevant suites, and record command output/exit status. If MySQL or a graphical display is unavailable in this environment, still deliver functioning code and scripted setup, clearly mark this as a **BLOCKED/UNVERIFIED environmental check**, and state the exact dependency or user action required. Never say 'complete' just because code was generated.

Prepare/update:
- `README.md`: overview, architecture, prerequisites, one-setup/one-launch steps for macOS/Windows/Linux where realistic, safe local DB/bootstrap, available demo roles, workflows, screenshots that are real, and troubleshooting.
- `docs/architecture.md`: GUI -> service -> DAO -> JDBC -> MySQL, algorithm adapter, disposal lifecycle and permissions.
- `docs/database_contract.md`: FINAL 18-table schema, no outdated tables.
- `docs/integration_gap_analysis.md`: previous branches/current class reuse and migration.
- `docs/testing_and_verification.md`: commands actually run, pass/fail/blocked evidence.
- `docs/demo_walkthrough.md`: deterministic 5–10 minute end-to-end demo with sample data.
- `docs/db_setup.md`, `docs/db_test_cases.md`, `docs/normalization_and_viva.md`: DBMS normalization/constraints/queries/views/procedures/functions/triggers and Java OOP/JDBC/algorithm/Swing explanations, grounded in implemented code.
- `sql/` scripts as above; an install/bootstrap script and simple application launch command.
- If screenshots can be captured from the actual app or query output, save in appropriately named docs assets; otherwise do not invent them.

## Execution discipline / quality gates

1. Start with environment/repo/ref audit and `git status`. Print a concise factual inventory, then continue automatically.
2. Establish safe integration branch and preserve existing local changes.
3. Integrate useful algorithm code and stabilize compilation.
4. Implement exact final MySQL DDL, seed data, views, procedures, functions, triggers and tests.
5. Migrate JDBC/models/services/auth/transactions to final schema. Delete obsolete production references only once their replacements are verified.
6. Wire algorithms, planning persistence and disposal selection.
7. Build functional Swing launcher and complete all major role-based workflows.
8. Run verification, fix failures and re-run. Review resource leaks, security, race conditions, SQL injection, DB locking, password handling and UI responsiveness.
9. Update reference-aligned docs and demo evidence; run `git diff --check` and `git status`. Make small, descriptive local commits on integration branch after tests. Do not push or merge without approval.

If time/context is limited, finish the smallest complete end-to-end vertical slice first, then expand. Maintain `docs/implementation_progress.md` with file paths, actual verified results, remaining TODOs, known blockers and the next executable step so a resumed coding session can continue without redoing the audit. Do NOT leave placeholder methods, empty UI handlers, fake data pretending to be production data, disabled required features, broken builds or unimplemented TODOs while claiming delivery. Avoid unnecessary full rewrites, unrelated refactors, heavyweight dependencies and scope creep. Keep implementation realistic and inspectable for a college viva.

## Definition of done — do not declare success early

A project is DONE only when:
- The project builds on the supported Java configuration.
- One real Swing application opens with functional role-specific screens.
- All 18 exact production base relations, 2 views, required stored procedures/functions/triggers and constraints exist and run on compatible MySQL.
- The complete pickup -> planning -> route -> collection -> disposal -> reporting flow works using real JDBC and a seeded local DB.
- All algorithm work is integrated, including executable tests, not merely copied files.
- Obsolete 15-table assumptions and `site_id` mapping are removed from production.
- Authorization, transactions, negative cases and integrity enforcement are exercised.
- Reproducible setup, testing, demo and viva documentation exists, with honest verification evidence.

**Final reply after coding:** Show the branch and new commits, files/modules added, the exact commands executed with results, counts of automated tests actually run, one confirmed full-system workflow, any unverified environmental dependencies, and concise exact launch steps. If any acceptance criterion was not met, label it NOT DONE or BLOCKED rather than presenting an aspirational report.

**Begin working in the open ECOroute repository NOW. Do not merely restate these instructions or stop after a plan.**

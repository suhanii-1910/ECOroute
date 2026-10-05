# Member 2 frozen-schema migration verification

Date: 2026-10-05 (Asia/Kolkata). Workspace: `/Users/suhanigarg670/ECOroute`.

## Scope and environment

- Inspected the existing backend, all listed docs, configuration, tests and the supplied `EcoRoute_FINAL_FREEZED_Relational_Schema.docx` before editing.
- Initial `git status`, `git branch --show-current` and `git diff`: branch `member2-jdbc`, clean working tree, no diff. The branch was retained; no commit, push, merge or switch was performed.
- Java command: Oracle Java 26.0.1. Maven 3.9.16 uses Homebrew JDK 27. `pom.xml` still targets Java 17; dependencies and plugins are unchanged.
- 45 production Java sources compile. All 55 generated production class files have major version 61 (Java 17). Runtime execution under an installed JDK 17 was not tested.
- No executable Member 1 production DDL was found. No production database was accessed or modified.
- ECOROUTE_TEST_DB_URL, ECOROUTE_TEST_DB_USER, ECOROUTE_TEST_DB_PASSWORD and ECOROUTE_TEST_DB_DISPOSABLE were all absent. Only presence was checked; no secret values were printed.

## Final results

Commands executed from the project root:

```sh
git diff --check
mvn clean test
mvn package
git status
```

| Suite | Run | Passed | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| BackendSmokeTest | 23 | 23 | 0 | 0 | 0 |
| DatabaseTransactionTest | 4 | 4 | 0 | 0 | 0 |
| SecurityAndConfigurationTest | 3 | 3 | 0 | 0 | 0 |
| Total per Maven run | **30** | **30** | **0** | **0** | **0** |

- `mvn clean test`: **BUILD SUCCESS**.
- `mvn package`: **BUILD SUCCESS**, including rerunning all 30 tests.
- Artifact: `target/ecoroute-1.0-SNAPSHOT.jar` (backend library, no GUI/Main-Class).
- `git diff --check`: passed.
- Surefire XML/text evidence: `target/surefire-reports/`.
- MySqlBackendIT is opt-in, not included in the default suite; it was **not run**, rather than counted as a skipped default test.

The first migrated-fixture run preserved all 23 pre-existing tests. Seven focused migration tests were then added. One intermediate test compilation failed on JUnit overload inference for generic database results; explicit local Long/boolean variables fixed it. Final clean-test and package builds pass without test suppression.

## Migration tests added to BackendSmokeTest

1. `disposalSiteRetrievalPreservesAllFieldsAndFiltersStatusAsData`: every site field maps correctly, including seven-digit coordinates and DECIMAL(12,2) capacity; ID/all/status retrieval, absent ID, empty list, SQL-looking status data and staff authorization.
2. `routeDestinationIsOptionalAndMappedOnEveryReadPath`: existing save signature stores NULL; new overload stores a valid site; ID/vehicle/zone/locking reads retain it, stop sequence is preserved, referenced-site deletion is FK-protected under the fixture's retained policy.
3. `invalidDestinationAndLateFailureLeaveNoPartialAssignment`: zero/negative/missing site rejection, actual FK failure with an in-range nonexistent ID and preserved SQLException cause; injected failure after route/stops/request changes rolls everything back and leaves vehicle available.
4. `preferredPickupDateIsRequiredByServiceDaoAndFixture`: service, DAO and direct fixture SQL reject NULL, with no request or waste rows left behind.
5. `userSubtypeMustBeExactlyOneAndMatchExistingRole`: valid parent/marker seeds, missing/wrong/dual marker rejection at login and session checks, marker PK/FK enforcement, and test-provisioning rollback when subtype insertion fails. There is still no production user-creation API.
6. `nullableGeneratorSubtypeDetailsAndSevenDigitCoordinatesRoundTrip`: optional hospital, housing and factory details persist/read as NULL; nullable flats remain NULL rather than zero, seven-digit coordinates survive, and missing generator address is rejected.
7. `frozenDecimalLimitsRejectRoundingAndDistinguishVehicleCapacity`: waste DECIMAL(12,2) versus vehicle DECIMAL(10,2) boundaries, fractional precision rejection before rounding, and actual quantity remaining NULL after rejected collection before a valid two-decimal measurement.

## Preserved regression coverage

All original scenarios remain and use valid required dates/addresses so they still reach the intended checks:

- CRUD, generated keys, affected-row errors, missing rows, parameter binding and borrowed-connection ownership.
- REQUEST_WASTE composite PK; ROUTE_STOP unique request and retained unique route/sequence constraints.
- Null actual quantities, measured zero, partial reports and correct aggregation/utilization denominators.
- Authentication, password/hash handling, inactive accounts/generators, ownership and changed sessions.
- Positive estimates, duplicate categories, cancellation/assignment/completion rules, route eligibility, zones, ordering and capacity.
- Atomic generator+subtype, request+lines, route+stops+state and collection workflows; failure injection, rollback, commit/connection failures and original causes.
- Duplicate assignment protection, including concurrent route attempts; vehicle release and reuse after collection.

MySqlBackendIT inherits all 23 smoke scenarios and now clears marker/site tables in FK-safe order after its existing empty-disposable-schema guard. Its code compiles; it was not executed against a MySQL server.

## Self-review and integration limits

Searched the complete source/docs for ROUTE, site_id, preferred_pickup_date, USER, GENERATOR_USER, STAFF_USER and DISPOSAL_SITE; reviewed INSERT columns/placeholders and ResultSet mappings. Route insert has six explicitly named columns and six bound values. Result mappings use column names; existing generated-key reads and a test-only COUNT scalar legitimately use column index 1. No obsolete DECIMAL(12,3)/DECIMAL(9,6)/BIGINT fixture mapping remains. No SQL was added to services, and no GUI, routing algorithm, stored routine or production DDL was introduced.

The test fixture includes all 15 relations and frozen nullability/type changes. Retained additional checks, delete actions, AUTO_INCREMENT, role/status conventions and route-sequence uniqueness are explicitly labelled as assumptions requiring Member 1 confirmation in `database_contract.md`. Marker PK/FKs alone cannot enforce total/disjoint specialization across two tables: test provisioning is atomic and authentication fails closed on invalid markers; production provisioning/enforcement remains Member 1's responsibility.

**MySQL integration test not run because Member 1/live test database prerequisites are not available.** Live MySQL integration and Member 1 database verification are not claimed. H2 success verifies the Member 2 code migration against the fixture only.

Working-tree changes are intentionally uncommitted on `member2-jdbc`. Environment configuration, `.env` ignore rules, Maven dependencies and the Java 17 target remain unchanged.

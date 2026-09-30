# Member 2 verification record

Date: 2026-09-30. Workspace: `/Users/suhanigarg670/ECOroute`.

## Environment inspected

- `java -version`: Oracle Java 26.0.1.
- `mvn -version`: Maven 3.9.16 using Homebrew JDK 27.
- `pom.xml`: compilation explicitly targets Java 17.
- No existing Java sources, database DDL, docs content or applicable AGENTS.md were present in this workspace at the start. The existing README/team responsibilities and Maven coordinates were preserved.
- This workspace has no `.git` directory. No repository initialization, commits, pushes or history changes were performed.
- None of ECOROUTE_TEST_DB_URL, ECOROUTE_TEST_DB_USER, ECOROUTE_TEST_DB_PASSWORD or ECOROUTE_TEST_DB_DISPOSABLE was present. Secret values were not printed.

## Final results

| Check | Result |
| --- | --- |
| Java compilation with `--release 17` | Passed: 43 production source files |
| BackendSmokeTest | Passed: 16 tests using real JDBC against isolated H2 fixtures |
| DatabaseTransactionTest | Passed: 4 connection/commit/rollback/failure tests |
| SecurityAndConfigurationTest | Passed: 3 password/configuration/test-database guard tests |
| Default suite total | **23 tests, 0 failures, 0 errors, 0 skipped** |
| JAR packaging | Passed: `target/ecoroute-1.0-SNAPSHOT.jar` |
| Bytecode target inspection | All production class files have major version 61 (Java 17) |
| MySqlBackendIT | **Not run**: separate explicit disposable-database configuration was absent |
| Member 1 production DDL / views / routines / existing hashes | **Unverified**: actual implementation contracts were not supplied |
| Execution under an installed JDK 17 | **Not run**: tests ran on Maven's JDK 27; release-17 compilation and bytecode were checked |

The JAR is a backend library for the shared project. It has no GUI/Main-Class and is not an executable `java -jar` application. Maven supplies dependencies to consuming project code.

Final full verification command (the temporary Maven repository avoids writing outside the permitted workspace/cache locations):

```sh
mvn -B -ntp -o -Dmaven.repo.local=/tmp/ecoroute-m2-repository clean test package
```

Earlier clean test commands also passed. The final command recompiles sources, runs all 23 default tests and rebuilds the JAR. Surefire XML/text reports are in `target/surefire-reports/`. No live MySQL integration is implied by this result.

## Scenarios covered

- Valid fixture connection, missing environment configuration, multi-row SELECT and absent-row Optional.
- Parameterized CRUD, affected-row failures, real category DELETE and protection of referenced categories.
- UNIQUE/FK constraints, REQUEST_WASTE composite key, duplicate stop request and sequence constraints.
- Null actual quantity before collection and measured zero afterward.
- Request creation with all waste lines, invalid/duplicate/nonpositive estimates, ownership and inactive generators.
- Salted password verification, malformed/unknown hash rejection, inactive users, wrong credentials, SQL-looking usernames and changed sessions.
- Cancellation/assignment/completion state transitions; capacity, zone, order and staff role checks.
- Generator+hospital insertion and rollback after a subtype SQL error.
- Route order retrieval, duplicate assignments and concurrent assignment attempts on different vehicles.
- Collection timing, exact category coverage, negative/over-capacity actuals, externally deactivated generators, completion and vehicle release/reuse.
- Report totals across multiple stops/categories, correct utilization denominator and partial/null measurement semantics.
- Injected late JDBC errors during actual request, route and collection service calls: earlier writes and statuses roll back.
- Borrowed DAO connections stay open and uncommitted; outer transaction commit/rollback/close behavior; original SQL causes and suppressed rollback errors are preserved.

## Resolved tooling failures

The first dependency fetch failed because the sandbox could not resolve Maven Central. An offline attempt also failed because the clean/test plugins were not cached. Authorized Maven dependency downloads succeeded, and later offline full builds passed.

One packaging permission review timed out before launching Maven; the allowed single retry was approved and packaging passed. This was an approval-review timeout, not a reported code/test failure or safety rejection. No tooling blocker remains for the implemented backend.

## Next commands

From the project root in a normal developer shell:

```sh
mvn clean test
mvn package
```

To reuse this session's downloaded dependencies while `/tmp/ecoroute-m2-repository` still exists:

```sh
mvn -o -Dmaven.repo.local=/tmp/ecoroute-m2-repository clean test package
```

After Member 1 provisions a new empty disposable schema and you export the four variables described in [jdbc_setup.md](jdbc_setup.md):

```sh
mvn -Pmysql-it verify
```

Review [database_contract.md](database_contract.md) with Member 1 before application integration. Members 3 and 4 can use the service contracts and examples in [member2_handoff.md](member2_handoff.md) while those database details are finalized.

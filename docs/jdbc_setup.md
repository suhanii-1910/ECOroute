# Member 2 JDBC setup

## Prerequisites

- JDK 17+ and Maven 3.9.x. The compiler uses `maven.compiler.release=17` regardless of Maven's runtime JDK.
- Maven Central access for first dependency download. Pinned dependencies: MySQL Connector/J 9.2.0, JUnit Jupiter 5.11.4, H2 2.3.232 (test only). Plugin versions are pinned in pom.xml.
- For actual integration: a team-managed MySQL server (provisional fixture targets MySQL 8.0.16+ with enforced CHECK constraints and InnoDB), agreed Member 1 DDL and credentials.

Check locally:

```sh
java -version
mvn -version
mvn clean test
mvn package
```

The default tests create isolated H2 databases in memory. They require no MySQL, credentials, network service, Docker or installed database software. Maven may need network access to obtain dependencies. H2 is a test substitute, not proof of MySQL compatibility.

## Application configuration

```sh
cp .env.example .env
# Edit .env locally, then explicitly load it into your current shell:
source .env
```

Java does **not** automatically load `.env` or `.env.example`. Alternatively configure these environment variables in your IDE's run configuration:

```sh
export ECOROUTE_DB_URL='jdbc:mysql://localhost:3306/ecoroute'
export ECOROUTE_DB_USER='ecoroute_app'
export ECOROUTE_DB_PASSWORD='your-local-password'
```

Do not put credentials in Java, commit `.env`, or embed credentials in JDBC URLs. `.env` is ignored. Password must be present in the environment; an explicitly empty value is accepted for controlled local accounts. `DatabaseConnection.fromEnvironment()` gives a clear configuration error when variables are missing. Loading configuration does not open a connection; `Database` opens and closes connections around operations.

Member 1 supplies the production schema and initial users. **Do not load the test fixture into the application database.** No launcher/GUI is included in this Member 2 module. Members 3/4 construct the services as shown in [member2_handoff.md](member2_handoff.md).

## Opt-in MySQL tests

Ask Member 1 to provision a **new, empty** database named `ecoroute_member2_test_<suffix>` and a separate test account with CREATE, SELECT, INSERT, UPDATE and DELETE privileges within that database. The tests do not create/drop databases, install MySQL, or access ECOROUTE_DB_* as a fallback.

Set all four test variables explicitly:

```sh
export ECOROUTE_TEST_DB_URL='jdbc:mysql://localhost:3306/ecoroute_member2_test_run1'
export ECOROUTE_TEST_DB_USER='ecoroute_test'
export ECOROUTE_TEST_DB_PASSWORD='your-test-password'
export ECOROUTE_TEST_DB_DISPOSABLE='YES'
mvn -Pmysql-it verify
```

The profile runs the default tests and `MySqlBackendIT` through Failsafe. MySqlBackendIT inherits every BackendSmokeTest scenario. It refuses a non-test database name, a matching application database name, missing acknowledgment, or a schema that initially contains tables/views. The first test creates the labelled fixture; subsequent tests delete only fixture rows in FK order. It leaves its test tables behind for inspection, so provision a fresh empty database for the next run (or have Member 1 explicitly reset that disposable schema). Never disable the guard to reuse application data.

No live MySQL tests run during `mvn test`. Reports are in `target/surefire-reports/` (default) and `target/failsafe-reports/` (MySQL profile). Passing fixture tests does not replace a review/test against Member 1's final DDL.

Run selected default checks:

```sh
mvn -Dtest=BackendSmokeTest test
mvn -Dtest=DatabaseTransactionTest,SecurityAndConfigurationTest test
```

## Troubleshooting

| Symptom | Action |
| --- | --- |
| Missing environment message | Export variables in the same shell/process as the app; check IDE environment settings. Do not print the password. |
| Unknown host / dependency resolution error | Allow Maven Central access and retry. Offline mode works only after dependencies/plugins are cached. |
| Connection refused / access denied | Ask Member 1 to verify host, port, account grants and server availability; configuration alone does not verify a connection. |
| Unknown table/column, invalid enum, no generated key | Compare actual DDL with database_contract.md. Do not auto-create/rename production objects. |
| Zero affected rows | ID is missing or expected status changed; reload the record. Keep `useAffectedRows=false` in Connector/J. |
| Duplicate/FK error | Keep the original cause for restricted debugging; show the safe top-level message to the GUI. Refresh or correct input. |
| Deadlock or lock timeout | The transaction is rolled back where possible. Refresh and retry the entire operation, not just the failed DAO call. |
| Failed commit/connection loss | Commit outcome may be uncertain; read current state before retrying a create operation. |
| Date shift or truncation | Agree on DATETIME/DATE mapping and one business time zone; confirm precision and VARCHAR lengths with Member 1. |
| Login fails with existing data | Confirm PBKDF2 format, role association and active flag. No fallback to plaintext or unknown hashes is implemented. |
| MySQL profile refuses schema | Use a new empty disposable schema with the required prefix and acknowledgment. |

## Verification in this workspace

See [member2_verification.md](member2_verification.md) for actual commands/results and remaining integration gaps. The environment inspected during implementation had `java` 26.0.1, Maven 3.9.16 running JDK 27. Compilation targets Java 17; execution under an actual JDK 17 remains a separate compatibility check.

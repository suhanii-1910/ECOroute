# ECOroute

## Waste Management and Pickup Routing System

ECOroute is a Java and MySQL based academic project for managing
waste pickup requests, vehicle assignment and collection routes.

## Technology Stack

- Java
- Maven
- MySQL
- JDBC
- Java Swing

## Team Responsibilities

- Member 1 — DBMS
- Member 2 — Java Backend + JDBC
- Member 3 — OOP + Algorithms
- Member 4 — Swing GUI + Integration

## Member 2 — Java Backend + JDBC

The backend uses plain Java 17 and JDBC. It includes table models, parameterized
DAOs, authenticated services, transactional request/route/collection workflows,
reports and backend tests. Member 3 supplies route order; Member 4 calls services
from Swing. Team ownership above remains unchanged.

### Build and test

```sh
java -version
mvn -version
mvn clean test
mvn package
```

Default tests run without MySQL using an isolated, provisional H2 fixture.
Compilation explicitly targets Java 17 even when Maven runs on a newer JDK.

For the application, export `ECOROUTE_DB_URL`, `ECOROUTE_DB_USER` and
`ECOROUTE_DB_PASSWORD`. [`.env.example`](.env.example) is a template;
Java does **not** load it automatically. Member 1 must supply the production DDL
and compatible initial user hashes before live integration.

Opt-in MySQL tests use four separate `ECOROUTE_TEST_DB_*` variables, a new empty
disposable test schema and `mvn -Pmysql-it verify`. Read the setup guide first.
The tests never use normal application configuration as a fallback.

### Handoff and documentation

- [JDBC setup and test commands](docs/jdbc_setup.md)
- [Database contract and Member 1 confirmations](docs/database_contract.md)
- [Public services and examples for Members 3 and 4](docs/member2_handoff.md)
- [Member 2 viva notes](docs/member2_viva.md)
- [Actual verification results](docs/member2_verification.md)

`database/` remains reserved for Member 1. The SQL under `src/test/resources/`
is only a Member 2 test fixture, not the production schema. Live MySQL and final
DDL compatibility have not been verified. No GUI, routing algorithm or production
views/procedures/triggers are implemented in this contribution.

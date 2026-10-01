[README-2.md](https://github.com/user-attachments/files/32919612/README-2.md)
# ♻️ ECOroute

## Waste Management and Pickup Routing System

ECOroute is a Java + MySQL academic project for managing waste generators, pickup requests, vehicles, routes, route stops, waste quantities, and collection reports.

The project is divided into four member responsibilities so that each layer can be developed independently and integrated through clear interfaces.

---

## 👥 Team Responsibilities

| Member | Responsibility |
|---|---|
| Member 1 | MySQL database, tables, PK/FK constraints, seed data, views, SQL queries, procedures, functions, triggers |
| **Member 2** | **Java backend + JDBC, models, DAO layer, CRUD, services, validation, transactions, exceptions, backend testing** |
| Member 3 | Zone grouping, greedy vehicle assignment, nearest-neighbour route generation, route-planning algorithms |
| Member 4 | Swing GUI, event handling, GUI-to-backend integration, final end-to-end integration |

### Dependency flow

```text
Member 1 — MySQL Database
          ↓
Member 2 — Java Backend + JDBC
          ↓
Member 3 — Algorithms / Route Planning
          ↓
Member 4 — Swing GUI / Final Integration
```

The work can still proceed in parallel once interfaces are agreed.

---

# ✅ Member 2 — Current Status

The Java backend for Member 2 has been implemented.

### Completed

- Java model classes
- Waste-generator subtype models
- Central JDBC configuration
- Connection provider abstraction
- DAO layer
- Parameterized SQL with `PreparedStatement`
- `ResultSet` mapping
- CRUD operations
- Authentication service
- Pickup-request service
- Vehicle service
- Generator service
- Collection service
- Route persistence service
- Report service
- Validation
- Role and ownership checks
- Transactions and rollback handling
- Database exception handling
- Backend unit tests
- MySQL integration-test support
- Test SQL fixture
- JDBC setup documentation
- Database contract documentation
- Team handoff documentation
- Viva documentation

---

# 🧱 Project Structure

```text
ECOroute/
│
├── docs/
│   ├── database_contract.md
│   ├── jdbc_setup.md
│   ├── member2_handoff.md
│   ├── member2_verification.md
│   └── member2_viva.md
│
├── src/
│   ├── main/
│   │   └── java/
│   │       └── ecoroute/
│   │           ├── dao/
│   │           ├── db/
│   │           ├── exception/
│   │           ├── model/
│   │           └── service/
│   │
│   └── test/
│       ├── java/
│       │   └── ecoroute/
│       └── resources/
│           └── member2_fixture.sql
│
├── .env.example
├── .gitignore
├── pom.xml
└── README.md
```

---

# 🗄️ Database Contract

The backend follows the finalized ECOroute relational schema.

### Main relations

```text
ZONE
WASTE_GENERATOR
USER
WASTE_CATEGORY
VEHICLE
ROUTE
PICKUP_REQUEST
ROUTE_STOP
REQUEST_WASTE
```

### Generator subtype relations

```text
HOSPITAL
HOUSING_SOCIETY
FACTORY
```

### Important schema rules

- `WASTE_GENERATOR.zone_id` is a mandatory foreign key to `ZONE`.
- `PICKUP_REQUEST` does **not** store `zone_id`; request zone is derived through the generator.
- `REQUEST_WASTE` uses composite primary key `(request_id, category_id)`.
- `REQUEST_WASTE.actual_quantity` may remain `NULL` until collection is completed.
- `ROUTE_STOP.request_id` is unique.
- `(route_id, stop_sequence)` is unique.
- Route total estimated and actual weights are derived, not stored in the base `ROUTE` table.
- `VEHICLE.assigned_zone_id` may be null.

See:

```text
docs/database_contract.md
```

---

# 🔌 JDBC Configuration

The backend reads database configuration from environment variables.

```bash
ECOROUTE_DB_URL
ECOROUTE_DB_USER
ECOROUTE_DB_PASSWORD
```

Example:

```bash
export ECOROUTE_DB_URL="jdbc:mysql://localhost:3306/ecoroute"
export ECOROUTE_DB_USER="your_username"
export ECOROUTE_DB_PASSWORD="your_password"
```

Do not commit real database credentials.

`.env.example` is only a reference file.

For full setup instructions:

```text
docs/jdbc_setup.md
```

---

# 🧪 Backend Verification

Run:

```bash
mvn clean test
```

Current verified local result:

```text
Tests run: 23
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Package the project with:

```bash
mvn package
```

Current packaging result:

```text
BUILD SUCCESS
```

Generated JAR:

```text
target/ecoroute-1.0-SNAPSHOT.jar
```

---

# ⚠️ MySQL Integration Status

The Member 2 Java backend is implemented and tested.

However, final compatibility with **Member 1's real MySQL database is still pending**.

The current file:

```text
src/test/resources/member2_fixture.sql
```

is only a clearly labelled backend test fixture.

It is **not** Member 1's final database implementation.

When Member 1 provides the final database DDL, Member 2 must verify:

- exact SQL data types
- generated-ID / `AUTO_INCREMENT` strategy
- status values
- FK and delete behavior
- password-hash format
- views
- procedures/functions if used
- lifecycle rules
- compatibility of all DAO SQL

Then run:

```bash
mvn -Pmysql-it verify
```

Use a separate disposable MySQL test database for integration testing.

---

# 🤝 Member 1 → Member 2 Handoff

Member 1 should provide the final database files, ideally in a structure like:

```text
database/
├── schema.sql
├── sample_data.sql
├── views.sql
├── queries.sql
├── procedures.sql
├── functions.sql
└── triggers.sql
```

Member 1 should confirm:

```text
Database name
Exact table names
Exact column names
SQL data types
Primary keys
Foreign keys
Generated IDs / AUTO_INCREMENT
Status values
Password format
View definitions
Procedure/function signatures
Delete behavior
```

Member 2 will then connect the Java backend to the final MySQL database and run the integration verification.

---

# 🧠 Member 3 Integration

Member 3 owns the routing and assignment logic.

Member 3 should handle:

```text
Zone grouping
Greedy capacity-constrained vehicle assignment
Nearest-neighbour route generation
Route planning
```

Member 2 does **not** compute route order.

Member 3 should supply an already ordered route plan to the backend.

Conceptually:

```text
Vehicle + Zone + Ordered Requests
              ↓
   RoutePersistenceService
              ↓
      RouteDAO + RouteStopDAO
              ↓
             MySQL
```

The backend validates and atomically persists the supplied route.

For exact service usage and handoff details:

```text
docs/member2_handoff.md
```

---

# 🖥️ Member 4 Integration

Member 4 owns the Swing GUI.

The GUI should **not execute SQL directly**.

Do not put JDBC code such as this inside Swing screens:

```java
Connection connection = ...;
PreparedStatement statement = ...;
ResultSet resultSet = ...;
```

Use this architecture instead:

```text
Swing GUI
   ↓
Service Layer
   ↓
DAO Layer
   ↓
JDBC
   ↓
MySQL
```

Member 4 should call the backend services for:

```text
Authentication
Pickup requests
Vehicles
Generators
Collections
Routes
Reports
```

For exact service signatures and examples:

```text
docs/member2_handoff.md
```

---

# 🔄 Example Flow

## Create Pickup Request

```text
Swing Form
   ↓
PickupRequestService
   ↓
RequestDAO + RequestWasteDAO
   ↓
JDBC Transaction
   ↓
MySQL
```

The pickup request and its waste lines are saved atomically.

## Route Persistence

```text
Pending Requests
   ↓
Member 3 Algorithm
   ↓
Vehicle Assignment
   ↓
Nearest-Neighbour Ordering
   ↓
Ordered Route Plan
   ↓
RoutePersistenceService
   ↓
RouteDAO + RouteStopDAO
   ↓
MySQL
```

## Complete Collection

```text
Swing
   ↓
CollectionService
   ↓
Actual quantities + timing + statuses
   ↓
Transaction
   ↓
MySQL
```

---

# 📚 Documentation

### JDBC Setup

```text
docs/jdbc_setup.md
```

Contains database configuration, environment-variable setup, build commands, test commands, and troubleshooting.

### Database Contract

```text
docs/database_contract.md
```

Contains Java ↔ database assumptions and unresolved items that Member 1 must confirm.

### Team Handoff

```text
docs/member2_handoff.md
```

**Members 3 and 4 should read this before integrating their code.**

### Verification

```text
docs/member2_verification.md
```

Contains backend verification information.

### Viva Preparation

```text
docs/member2_viva.md
```

Explains JDBC, DAO, `PreparedStatement`, `ResultSet`, CRUD, composite keys, transactions, and exceptions.

---

# 🌿 GitHub Workflow

The team should use one repository with separate branches.

```text
main
├── member1-dbms
├── member2-jdbc
├── member3-algorithms
└── member4-swing
```

## Important Rule

**Do not develop directly on `main`.**

Each member should work on their own branch.

---

## Clone Repository

```bash
git clone https://github.com/suhanii-1910/ECOroute.git
cd ECOroute
```

## Member 1

```bash
git checkout main
git pull origin main
git checkout -b member1-dbms
git push -u origin member1-dbms
```

## Member 3

```bash
git checkout main
git pull origin main
git checkout -b member3-algorithms
git push -u origin member3-algorithms
```

## Member 4

```bash
git checkout main
git pull origin main
git checkout -b member4-swing
git push -u origin member4-swing
```

---

# 📌 Normal Git Workflow

Before starting:

```bash
git status
git pull
```

After making changes:

```bash
git add .
git commit -m "Describe your change"
git push
```

Merge into `main` only after checking that the project still builds and tests pass.

---

# 🚦 Overall Project Status

```text
Relational Schema              ✅ Finalized

Member 1 Database              🚧 Pending / In progress

Member 2 Java Backend          ✅ Implemented
Member 2 Backend Tests         ✅ 23 passing
Member 2 Maven Build           ✅ Passing
Member 2 Packaging             ✅ Passing
Member 2 GitHub Branch         ✅ member2-jdbc
Member 2 Real MySQL Check      ⏳ Waiting for Member 1 final DB

Member 3 Algorithms            🚧 To integrate
Member 4 Swing GUI             🚧 To integrate

Final End-to-End App           ⏳ Pending team integration
```

---

# 🎯 Current Next Step

```text
Member 1 Final MySQL Database
              ↓
Member 2 JDBC Integration Verification
              ↓
Member 3 Algorithm Integration
              ↓
Member 4 GUI Integration
              ↓
Final ECOroute Application
```

For backend integration questions, start with:

```text
docs/member2_handoff.md
```

The backend should remain the single Java-to-database access layer for ECOroute.

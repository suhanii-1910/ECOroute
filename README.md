# ♻️ ECOroute

## Waste Management and Pickup Routing System

ECOroute is a Java 17 and MySQL based academic project for managing waste generators, pickup requests, vehicles, collection routes, route stops, waste quantities, and operational reports.

The project is structured across four member responsibilities so that each layer is developed independently and integrated cleanly through well-defined service and data contracts.

---

## 👥 Team Responsibilities

| Member | Responsibility |
|---|---|
| Member 1 | MySQL database, tables, PK/FK constraints, seed data, views, SQL queries, procedures, functions, triggers |
| **Member 2** | **Java backend + JDBC, models, DAO layer, CRUD, services, validation, transactions, exceptions, backend testing** |
| Member 3 | Zone grouping, greedy capacity-constrained vehicle assignment, nearest-neighbour route generation, route-planning algorithms |
| Member 4 | Swing GUI, event handling, GUI-to-backend integration, final end-to-end integration |

### Dependency Flow

```text
Member 1 — MySQL Database
          ↓
Member 2 — Java Backend + JDBC
          ↓
Member 3 — Algorithms / Route Planning
          ↓
Member 4 — Swing GUI / Final Integration
```

The backend uses plain Java 17 and JDBC. It includes relational table models, parameterized DAOs, authenticated services, transactional request/route/collection workflows, operational reports, and automated test fixtures. Disposal facility retrieval, multi-category mixed-waste check-ins, and generator subtype models follow the frozen schema. Member 3 supplies route-planning and stop sequencing; Member 4 provides the Swing GUI calling backend services. Team ownership above remains preserved.

---

# 🖥️ Desktop Application & Integration

The integrated ECOroute desktop application features a modern Swing GUI built with the FlatLaf dark theme, implementing strict Role-Based Access Control (RBAC) across four operational user roles:

1. **Administrator (`ADMIN`)**: System administration, zone/vehicle/category/facility management, user activation/deactivation, planning & dispatch, driver portal, disposal check-in, and operational audit reports.
2. **Operator (`OPERATOR`)**: Route planning & dispatch, fleet overview, driver portal, disposal check-in, and operational reports.
3. **Driver (`DRIVER`)**: Active operational route execution, stop-by-stop sequencing, arrival/completion timestamps, weight verification with human-readable category names, and stop skipping with reason notes.
4. **Generator (`GENERATOR`)**: Generator self-service portal, facility profile, multi-category pickup request submission with preferred dates, and request cancellation.

### Embedded Demo Mode (Zero Setup Required)

When launched without external MySQL environment variables, ECOroute automatically initializes an isolated, in-memory **H2 database** pre-seeded with complete sample data:
- 5 operational zones (North, South, East, West, Central)
- 6 waste categories (General, Organic, Recyclable, Electronic, Chemical, Biomedical)
- 5 generators across subtypes (Hospital, Housing Society, Factory, Hotel, School)
- 5 fleet vehicles with realistic capacities (1,000 kg – 3,000 kg)
- 4 disposal facilities with accepted category constraints
- Active and historical pickup requests, planned routes, completed collections, and verified disposal receipts

#### Default Demo Credentials (Password: `Password@123`)

| Username | Role | Description |
|---|---|---|
| `admin` | Administrator | System Administrator with full console access |
| `operator1` | Operator | Dispatcher & route planner |
| `driver1` | Driver | Collection driver |
| `hospital_gen` | Generator | City General Hospital |
| `society_gen` | Generator | Green Valley Housing Society |

---

# 🧱 Project Structure

```text
ECOroute/
│
├── docs/
│   ├── database_contract.md
│   ├── implementation_progress.md
│   ├── jdbc_setup.md
│   ├── member2_handoff.md
│   ├── member2_verification.md
│   └── member2_viva.md
│
├── sql/
│   ├── 01_schema.sql
│   ├── 02_views.sql
│   ├── 03_triggers.sql
│   ├── 04_stored_procedures.sql
│   └── 05_seed_data.sql
│
├── src/
│   ├── main/
│   │   └── java/
│   │       └── ecoroute/
│   │           ├── algorithm/
│   │           ├── dao/
│   │           ├── db/
│   │           ├── exception/
│   │           ├── model/
│   │           ├── service/
│   │           └── ui/
│   │
│   └── test/
│       ├── java/
│       │   └── ecoroute/
│       └── resources/
│           ├── member2_fixture.sql
│           └── schema_h2.sql
│
├── .env.example
├── .gitignore
├── pom.xml
└── README.md
```

---

# 🗄️ Database Contract

The backend adheres strictly to the frozen ECOroute relational schema.

### Main Relations

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
DISPOSAL_FACILITY
DISPOSAL_RECORD
FACILITY_ACCEPTED_CATEGORY
```

### Generator Subtype Relations

```text
HOSPITAL
HOUSING_SOCIETY
FACTORY
```

### Core Schema Rules

- `WASTE_GENERATOR.zone_id` is a mandatory foreign key to `ZONE`.
- `PICKUP_REQUEST` does **not** store `zone_id`; request zone is derived through the linked generator.
- `REQUEST_WASTE` uses composite primary key `(request_id, category_id)`.
- `REQUEST_WASTE.actual_quantity_kg` remains `NULL` until physical collection is recorded by the driver.
- `ROUTE_STOP.request_id` is unique (`uq_route_stop_request`).
- `(route_id, stop_sequence)` is unique.
- Total route weights are aggregated from `ROUTE_STOP` and `REQUEST_WASTE`, not duplicated as mutable columns in `ROUTE`.
- Mixed-waste routes carrying categories requiring different disposal facilities support partial disposal check-ins across multiple facilities before route completion.

See: `docs/database_contract.md`

---

# 🔌 JDBC Configuration

The backend reads database configuration from environment variables. If these variables are omitted, ECOroute automatically launches in standalone embedded H2 demo mode.

```bash
ECOROUTE_DB_URL
ECOROUTE_DB_USER
ECOROUTE_DB_PASSWORD
```

Example for connecting to a live MySQL instance:

```bash
export ECOROUTE_DB_URL="jdbc:mysql://localhost:3306/ecoroute_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
export ECOROUTE_DB_USER="ecoroute_app"
export ECOROUTE_DB_PASSWORD="your_password"
```

Do not commit real database credentials to version control. [`.env.example`](.env.example) serves as a template; Java does not load `.env` files automatically.

For detailed setup instructions: `docs/jdbc_setup.md`

---

# 🚀 Build, Test & Run

Compilation explicitly targets **Java 17** even when Maven runs on a newer JDK.

### Run Unit & Integration Tests

```bash
mvn clean test
```

Default tests run without an external MySQL dependency using an isolated, compliant H2 database.

To execute the full verification suite including UI Event Dispatch Thread (EDT) headless tests:

```bash
mvn clean verify
```

### Run the Application

#### Option 1: Direct Maven Execution

```bash
mvn exec:java
```

#### Option 2: Run Standalone Uber JAR

```bash
mvn clean package
java -jar target/ecoroute-1.0-SNAPSHOT.jar
```

The executable JAR packages all dependencies (FlatLaf, H2, MySQL Connector/J) via `maven-shade-plugin`.

---

# ⚠️ MySQL Integration Status

The Java backend, Member 3 routing algorithms, and Member 4 Swing GUI are completely implemented and verified with automated test suites (**125 automated tests passing, 0 failures, 0 errors**).

However, **live MySQL integration remains unverified** against an external MySQL instance in this environment.

- Embedded H2 tests and demo mode use an isolated schema fixture conforming to the relational contract.
- External MySQL integration tests in `MySqlBackendIT.java` are safely guarded behind the environment variable `ECOROUTE_TEST_DB_DISPOSABLE=YES` to prevent destructive operations on shared or production databases.
- When an isolated, disposable MySQL test instance is available, run:

```bash
export ECOROUTE_TEST_DB_DISPOSABLE=YES
export ECOROUTE_TEST_DB_URL="jdbc:mysql://localhost:3306/ecoroute_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
export ECOROUTE_TEST_DB_USER="test_user"
export ECOROUTE_TEST_DB_PASSWORD="test_password"
mvn -Pmysql-it verify
```

---

# 🧠 Member 3 Algorithm Integration

Member 3's routing and assignment algorithms are integrated into `ecoroute.service.RoutePlanningAdapterService`:

```text
Pending Requests
       ↓
ZoneGrouper (group eligible requests by zone)
       ↓
VehicleAssignment (greedy First-Fit-Decreasing bin packing)
       ↓
NearestNeighborRoute (TSP route optimization from depot)
       ↓
RoutePersistenceService (atomic persistence to ROUTE & ROUTE_STOP)
```

The planning pipeline surfaces actionable unassigned reason codes (e.g., `FLEET_CAPACITY_FULL`, `EXCEEDS_LARGEST_VEHICLE`) when requests cannot be accommodated.

---

# 🖥️ Member 4 UI Architecture

Member 4's Swing GUI communicates strictly through the backend service layer:

```text
Swing GUI (ecoroute.ui.*)
          ↓
Service Layer (ecoroute.service.*)
          ↓
DAO Layer (ecoroute.dao.*)
          ↓
JDBC Abstraction (ecoroute.db.Jdbc)
          ↓
Relational Database (H2 / MySQL)
```

Swing components never execute raw SQL or handle `ResultSet` objects directly. All asynchronous operations execute off the Event Dispatch Thread via `SwingWorker` with structured logging.

---

# 🔄 Operational Lifecycle Flow

### 1. Request Creation
A generator creates a pickup request with estimated waste weights across multiple categories. `PickupRequestService` saves the request and waste line items atomically.

### 2. Route Planning & Dispatch
An operator previews optimized routes generated by `RoutePlanningService`. Saving the plan writes `ROUTE` and `ROUTE_STOP` records. Dispatching transitions the route to `IN_PROGRESS` and vehicle to `IN_ROUTE`.

### 3. Driver Collection Execution
The driver inspects ordered stops, starts the route, and records actual collected weights per category. When all stops are completed or skipped, the route automatically transitions to `DISPATCHED_TO_DISPOSAL`.

### 4. Multi-Facility Disposal Check-in
At the disposal facility, the operator records gross and tare weights. The system evaluates facility compatibility against remaining waste streams, inserts a verified `DISPOSAL_RECORD`, and transitions the route to `COMPLETED` and vehicle to `AVAILABLE` once all categories are cleared.

### 5. Operational Reporting & Audit
Auditors and operators inspect volume aggregations, route weights, vehicle utilization ratios, and estimated-versus-actual weight discrepancy reports, with one-click CSV export.

---

# 📚 Documentation Index

- **Database Contract & Schema Details**: [`docs/database_contract.md`](docs/database_contract.md)
- **JDBC Setup & Configuration Guide**: [`docs/jdbc_setup.md`](docs/jdbc_setup.md)
- **Team Service Handoff Reference**: [`docs/member2_handoff.md`](docs/member2_handoff.md)
- **Backend Verification Notes**: [`docs/member2_verification.md`](docs/member2_verification.md)
- **Member 2 Viva Reference**: [`docs/member2_viva.md`](docs/member2_viva.md)
- **Implementation Progress & Audit Matrix**: [`docs/implementation_progress.md`](docs/implementation_progress.md)

---

# 🚦 Overall Project Status

```text
Relational Schema & DDL        ✅ Finalized (sql/ directory)
Member 2 Java Backend          ✅ Implemented & tested
Member 3 Route Planning        ✅ Implemented & tested (ZoneGrouper, FFD, TSP)
Member 4 Swing GUI             ✅ Implemented & tested (FlatLaf Desktop App)
Embedded H2 Demo Mode          ✅ Fully functional with pre-seeded demo data
Test Suite                     ✅ 125 automated tests passing (0 failures, 0 errors)
Standalone Uber JAR Packaging  ✅ ecoroute-1.0-SNAPSHOT.jar built
Live MySQL Verification        ⏳ Unverified (requires disposable MySQL instance)
```

[README-3.md](https://github.com/user-attachments/files/33892606/README-3.md)
# ♻️ ECOroute

**Waste Management and Pickup Routing System**

ECOroute is a Java desktop application for managing the complete waste-collection workflow—from pickup requests and vehicle allocation to route planning, collection, disposal, and operational reporting.

Built with **Java 17**, **Swing**, **JDBC**, and **MySQL**, the application also provides an embedded **H2 demo mode** for exploring the system without configuring an external database.

## Features

- **Role-based access:** Separate workflows for administrators, operators, drivers, and waste generators.
- **Pickup management:** Create pickup requests with multiple waste categories, estimated quantities, and preferred dates.
- **Fleet and route planning:** Group requests by zone, allocate vehicles based on capacity, and generate ordered pickup stops.
- **Driver workflow:** View assigned routes, record arrival and completion, enter actual collected weights, and skip stops with reasons.
- **Disposal tracking:** Validate facility compatibility, record disposal receipts, and support waste streams requiring different facilities.
- **Reports and audit:** Review collection totals, route statistics, vehicle utilization, disposal records, and estimated-versus-actual quantities.
- **Data integrity:** Relational constraints, parameterized SQL queries, validation, transactions, and role-based authorization.
- **Standalone demo:** Explore the desktop application using automatically populated sample data.

## Technology Stack

| Area | Technology |
| --- | --- |
| Language | Java 17 |
| Desktop UI | Java Swing, FlatLaf |
| Database access | JDBC, MySQL Connector/J |
| Relational database | MySQL |
| Embedded demo database | H2 |
| Build and dependencies | Apache Maven |
| Testing | Automated Java tests |

## Application Architecture

```text
Swing desktop interface
          |
          v
Service layer (validation and business rules)
          |
          v
DAO layer (SQL and persistence)
          |
          v
JDBC connection and transaction management
          |
          v
MySQL or embedded H2 database
```

The application separates presentation, business logic, and persistence so each part can be maintained and tested independently. Java model classes represent the application's entities, including users, vehicles, requests, routes, and disposal records.

## Getting Started

### Prerequisites

- JDK 17 or newer
- Apache Maven
- MySQL, **only if you want to use an external database**

### Build and run

```bash
mvn clean package
java -jar target/ecoroute-1.0-SNAPSHOT.jar
```

To run through Maven instead:

```bash
mvn exec:java
```

### Embedded demo mode

When the application is started without an external MySQL configuration, it can initialize an in-memory H2 database containing sample zones, waste categories, generators, vehicles, pickup requests, routes, and disposal records.

**Demo credentials** (for local testing only):

| Username | Role | Password |
| --- | --- | --- |
| `admin` | Administrator | `Password@123` |
| `operator1` | Operator | `Password@123` |
| `driver1` | Driver | `Password@123` |
| `hospital_gen` | Waste generator | `Password@123` |
| `society_gen` | Waste generator | `Password@123` |

> The embedded database is intended for demonstration. In-memory data is not a substitute for a persistent production database. Change demo credentials before deploying in a real environment.

### Connect to MySQL

Create and initialize the database using the scripts in `sql/`, then supply the JDBC configuration through your terminal environment:

```bash
export ECOROUTE_DB_URL='jdbc:mysql://localhost:3306/ecoroute_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export ECOROUTE_DB_USER='your_mysql_username'
read -rs 'ECOROUTE_DB_PASSWORD?MySQL password: '
echo
export ECOROUTE_DB_PASSWORD

java -jar target/ecoroute-1.0-SNAPSHOT.jar
```

The interactive password command above is for **zsh**, the default shell on modern macOS. For other shells, set the environment variable using their supported syntax.

Do not commit passwords or secret configuration to Git. The `.env.example` file is a template; Java does not automatically load a `.env` file.

## How the System Works

1. **Request:** A waste generator submits a pickup request with waste types and estimated quantities.
2. **Plan:** The operator reviews requests, groups them by zone, and assigns eligible loads to available vehicles.
3. **Route:** Pickup stops are ordered using a nearest-neighbour route heuristic.
4. **Collect:** The driver records collection progress and actual waste quantities.
5. **Dispose:** Waste is checked in at compatible disposal facilities, and the relevant records are updated.
6. **Report:** The application summarizes requests, collected waste, completed routes, vehicle usage, and disposals.

### Route planning approach

The planning pipeline uses zone grouping, a greedy capacity-aware vehicle assignment strategy, and nearest-neighbour stop ordering. These methods generate practical plans without claiming to find a globally optimal route.

## Project Structure

```text
ECOroute/
├── docs/                  # Architecture, setup, and reference documentation
├── sql/                   # Schema, views, triggers, procedures, and seed data
├── src/
│   ├── main/java/ecoroute/
│   │   ├── algorithm/     # Vehicle assignment and route planning
│   │   ├── dao/           # Database access objects
│   │   ├── db/            # JDBC connections and transactions
│   │   ├── exception/     # Application-specific exceptions
│   │   ├── model/         # Java data models
│   │   ├── service/       # Business logic and validation
│   │   └── ui/            # Swing screens and components
│   └── test/              # Automated tests and test resources
├── .env.example           # Environment variable examples
├── pom.xml                # Maven configuration
└── README.md
```

## Testing

Run the automated tests:

```bash
mvn clean test
```

Run Maven's verification lifecycle:

```bash
mvn clean verify
```

Some MySQL integration checks require a separately configured disposable test database. Never run destructive test scripts against a database containing information you want to keep.

## Notes

- MySQL normally uses port **3306**; Swing itself does not require a web server or HTTP port.
- SQL statements are handled through the backend DAO layer rather than directly inside GUI components.
- The application supports an H2 demo database and an external MySQL configuration; these are separate databases.
- Demo credentials and sample data are meant for local development and demonstrations only.

## License

No open-source license is declared in this README. Add a `LICENSE` file if you want to specify how others may use or distribute the project.

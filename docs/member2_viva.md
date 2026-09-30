# Member 2 viva notes

## What did I implement?

“I implemented the Java layer between the GUI and MySQL. Models carry data, services check rules and permissions, and DAOs run SQL. The service starts one transaction for operations that must succeed together. Member 3 supplies the already ordered route; my code validates and saves it.”

A request follows this path:

```text
Swing → PickupRequestService → Database.transaction
                            → RequestDAO + RequestWasteDAO
                            → one JDBC Connection → database
```

## Models

A model represents one relation: Zone, WasteGenerator, User, WasteCategory, Vehicle, PickupRequest, RequestWaste, Route or RouteStop. Private fields, constructors and getters/setters keep data access clear. Hospital, HousingSociety and Factory represent separate subtype tables; their key is the same generator_id as the parent.

Model objects are data holders; setters alone do not validate a workflow. Service methods validate user input and database state. A null Long ID means a new object. The database provisionally generates its key; DAO insert returns that key. We never choose a key with MAX(id)+1, which would race between users.

BigDecimal stores decimal kg exactly at the agreed precision. Using double could introduce binary floating-point rounding. `actualQuantity == null` means no measurement; `BigDecimal.ZERO` means the staff measured zero.

LocalDate is a date without time. LocalDateTime matches the proposed DATETIME columns, which carry no time zone. These are provisional mappings until Member 1 supplies exact SQL types.

## DAO and JDBC

DAO means Data Access Object. It keeps SQL out of Swing and out of the route algorithm. ZoneDAO.findAll returns a List<Zone>; findById returns Optional<Zone>. An absent row is normal. A database failure throws DatabaseOperationException and must not be disguised as “no rows”.

JDBC is Java's database interface. Connector/J supplies MySQL's JDBC driver. DriverManager opens a Connection using environment configuration. We do not maintain a single global mutable connection.

- Connection: one database session and transaction context.
- PreparedStatement: SQL with `?` placeholders and bound values.
- ResultSet: rows returned by SELECT.
- executeQuery: produces a ResultSet.
- executeUpdate: returns a row count for INSERT/UPDATE/DELETE.
- getGeneratedKeys: retrieves the database's new surrogate ID.

## PreparedStatement versus Statement

PreparedStatement separates SQL structure from values. For example, `WHERE username = ?` binds the entered username as data, even if it contains quotes or SQL-looking text. Concatenating user input into SQL could change the query and allow SQL injection.

We use explicit column names, not SELECT *, so mappings are visible. PreparedStatement placeholders bind values, not table names. Production table/column identifiers are fixed in source. The fixture loader uses Statement only for fixed test DDL.

## ResultSet navigation and nulls

A ResultSet begins before the first row. `while (result.next())` visits every result; `if (result.next())` is enough for a known single-row result. We read columns by name and construct models.

`getBigDecimal` returns null for SQL NULL. Primitive getters need care: `getLong` returns zero for NULL, so the helper calls `wasNull()` and returns a nullable Long. Dates use `getObject(column, LocalDate.class)` or LocalDateTime.

Try-with-resources closes statements and result sets even after an exception. A DAO does not close a borrowed Connection because another DAO may still need it in the same transaction. Database closes the connection once the service operation finishes.

## Composite and candidate keys

REQUEST_WASTE uses `(request_id, category_id)` as its primary key. Each request may have multiple categories and each category may appear in many requests, but the same pair may occur only once. Updating actual quantity uses both key columns.

ROUTE_STOP has a separate stop_id PK. Its request_id is also UNIQUE, which prevents one pickup from appearing on two stops. UNIQUE(route_id, stop_sequence) prevents duplicate positions within the same route.

The request does not store zone_id. Its zone comes through its generator. Route total weights are sums of waste lines, not stored fields that could become stale.

## Transactions

Auto-commit normally commits each SQL statement separately. That would be wrong if the pickup row was inserted but its waste lines failed. The service disables auto-commit, gives every DAO the same Connection, and commits only after every step succeeds.

If a later line fails, rollback removes the request and earlier lines together. The same principle applies to generator+subtype, route+stops+statuses, and completion quantities+timing+statuses.

Atomic means all or nothing. Consistency means constraints and rules hold. Isolation controls interaction with concurrent transactions. Durability means confirmed commits persist. Our proposed isolation is READ_COMMITTED. SELECT FOR UPDATE holds row locks until commit or rollback, so concurrent assignments cannot both act on the same current request/vehicle state. Unique constraints provide another layer of protection.

We lock requests by ID order to reduce deadlocks. Deadlocks can still happen across workflows: the database aborts one transaction and the caller can refresh and retry the whole action. A failed commit can have an uncertain outcome if the network dies; callers must inspect state before creating duplicates.

## Validation and state transitions

The service checks required fields, active users/generators, positive estimates, nonnegative actuals, category uniqueness and ownership. Route persistence also checks home/request zones, available vehicle, date eligibility, capacity and supplied stop order.

A pending request can be cancelled or assigned. An assigned request completes through collection. Completing an already completed stop is rejected. Last-stop completion releases the vehicle. The allowed status strings and operational policies are proposals documented for Member 1's approval.

## Exceptions

- InvalidRequestException: invalid input, unauthorized operation or invalid lifecycle transition.
- VehicleUnavailableException: missing/unavailable vehicle or trying to edit an in-use vehicle.
- DatabaseOperationException: JDBC/constraint/connection failure or unexpected affected-row count.

The original SQLException is preserved as the cause for debugging. The outer message is safe for the GUI. We do not log passwords/hashes or display raw JDBC causes. The transaction rolls back before the exception reaches the caller, where possible.

## Authentication

The proposed format uses PBKDF2-HMAC-SHA256 with a random salt and work factor. A salt makes identical passwords produce different stored hashes. Repeated derivation makes guessing more expensive than a fast SHA hash. Login derives the candidate hash and compares it in constant time; it never compares plaintext passwords.

The returned session has only user ID, username, role and generator association. Services recheck the active account and ownership. Hiding a Swing button alone would not prevent unauthorized calls. Member 1 must agree on the encoding before existing database users can authenticate.

## Reports without double-counting

Joining requests to multiple waste lines duplicates the request row. Therefore requestStatuses counts from PICKUP_REQUEST directly. Route weight queries sum each waste line once through its single stop. Utilization divides total route kg by vehicle capacity once per route, not once per line.

SUM(actual_quantity) ignores null measurements. If all are null the actual sum stays null. Measured/total line counts tell the UI whether a sum is partial. Collection reports filter completed requests; all report date intervals include the start and exclude the end.

## What is actually verified?

Default tests exercise real JDBC and transactions against a labelled H2 fixture. They include failure injection after writes, FK/unique/composite constraints, authentication, permissions, report arithmetic, null-versus-zero behavior and concurrent assignment. Small connection tests verify commit/rollback/close behavior.

The optional MySQL profile reruns smoke scenarios against a new empty disposable MySQL schema after explicit configuration. No MySQL test was run during this implementation because test credentials were absent. H2 success is not MySQL compatibility, and even fixture MySQL success would not establish compatibility with Member 1's unseen final DDL.

## Useful demonstration sequence

1. Run `mvn clean test` and show the test reports.
2. Show one model and its nullable ID/BigDecimal fields.
3. Explain RequestDAO.insert and RequestWasteDAO.findByRequest.
4. Walk through PickupRequestService.create's shared connection.
5. Show the rollback and concurrency tests.
6. Show category DELETE failing for a referenced category.
7. Explain how Member 3 passes a plan and Member 4 calls services.
8. State the unverified production assumptions honestly.

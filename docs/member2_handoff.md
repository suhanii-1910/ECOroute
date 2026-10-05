# Member 2 handoff

## Boundary

This module supplies models, JDBC persistence, authenticated services, validation, reports and transaction handling. Member 1 owns production SQL and initial users. Member 3 computes zone groups, assignments and stop order. Member 4 builds Swing screens. No GUI, route algorithm, REST endpoint or RoutePlanningService is included.

The services are the integration boundary. Do not have Swing execute SQL or call mutating DAOs directly. The models are ordinary mutable data holders; pass stable inputs and do not mutate them from another thread while a service call is running. DTO records used for inputs/reports are immutable; input collections are copied before writes.

## Construct once, connect per operation

```java
import ecoroute.db.*;
import ecoroute.service.*;

Database db = new Database(DatabaseConnection.fromEnvironment());
AuthService auth = new AuthService(db);
PickupRequestService requests = new PickupRequestService(db);
GeneratorService generators = new GeneratorService(db);
CatalogService catalog = new CatalogService(db);
VehicleService vehicles = new VehicleService(db);
RoutePersistenceService routeStore = new RoutePersistenceService(db);
CollectionService collections = new CollectionService(db);
ReportService reports = new ReportService(db);
```

Each operation opens a fresh connection. Constructors do not contact MySQL. For tests, inject a `ConnectionProvider` returning a fresh test Connection. Do not pass a provider that returns the same shared connection repeatedly.

## Public service signatures

Abbreviations below are Java types: `session` is UserSession; `id` values are long; dates are java.time types; quantities are BigDecimal. Read results use Optional for one absent row and List for collections. Errors are exceptions, never fake empty successes.

### Authentication

```java
UserSession AuthService.login(String username, char[] password)
String PasswordHasher.hash(char[] password)
boolean PasswordHasher.verify(char[] password, String encodedHash)
```

`UserSession` exposes `getUserId()`, `getUsername()`, `getRole()`, `getGeneratorId()`. Only AuthService issues sessions; keep the session returned by login. It contains no hash. Services recheck the current account. Clear the caller's password array in a finally block. `hash` is for Member 1's agreed account provisioning, not plaintext comparison or a new registration UI.

### Pickup requests

```java
long create(UserSession session, long generatorId, LocalDate preferredDate,
            String remarks, List<PickupRequestService.WasteLine> lines)
List<PickupRequest> findByGenerator(UserSession session, long generatorId)
List<PickupRequest> findPending(UserSession session)
Optional<PickupRequest> findById(UserSession session, long requestId)
List<RequestWaste> findWaste(UserSession session, long requestId)
void cancel(UserSession session, long requestId)
```

`WasteLine(long categoryId, BigDecimal estimatedQuantity)` supplies estimates in kg. `preferredDate` is required (today or later); remarks may be null. Estimates/actuals use DECIMAL(12,2) kg and vehicle capacity DECIMAL(10,2), without rounding. The service sets generated ID, creation date, initial status and null actuals. Generator users are restricted to their own generator ID. Pending requests require staff access. Cancellation only accepts PENDING.

### Supplied route persistence

```java
long save(UserSession session, long vehicleId, long zoneId, LocalDate date,
          List<RoutePersistenceService.PlannedStop> stops)
long save(UserSession session, long vehicleId, long zoneId, LocalDate date,
          List<RoutePersistenceService.PlannedStop> stops, Long siteId)
Optional<Route> findById(UserSession session, long routeId)
List<Route> findByVehicle(UserSession session, long vehicleId)
List<Route> findByZone(UserSession session, long zoneId)
List<RouteStop> findStops(UserSession session, long routeId)
```

`PlannedStop(long requestId, int sequence)` has positive ascending unique sequences. The list is already ordered by Member 3. The service validates eligibility, zones, capacity, vehicle state and duplicate assignment. It saves route/stops and updates pickup/vehicle statuses in one transaction. It does not group, choose vehicles, calculate distances or reorder stops. All these methods require ADMIN/OPERATOR. The original save overload delegates with null siteId. The new overload stores a disposal-site ID selected by Member 3 or the caller and validates its positive ID and existence. It does not select the nearest site or infer status/capacity suitability. Route.getSiteId() returns the nullable destination. Destination selection is supported at save time; later destination editing is not exposed in this migration.

### Collection

```java
void complete(UserSession session, long stopId, Map<Long, BigDecimal> actualByCategory,
              LocalDateTime arrival, LocalDateTime completion)
```

Pass precisely all categories on the pickup. Every actual must be nonnegative and nonnull; use BigDecimal.ZERO for measured zero. Quantities, timing, stop/request status, and last-stop route/vehicle updates commit together. ADMIN/OPERATOR only.

### Vehicles

```java
List<Vehicle> findAll(UserSession session)
List<Vehicle> findAvailable(UserSession session)
Optional<Vehicle> findById(UserSession session, long id)
long create(UserSession session, String number, BigDecimal capacity, Long homeZone)
void updateStatus(UserSession session, long id, String next)
void updateHomeZone(UserSession session, long id, Long zoneId)
```

Reads require staff; writes require admin. Nullable home zone means no home-zone restriction. Manual status changes are AVAILABLE ↔ MAINTENANCE only. Assignment/completion owns IN_USE transitions.

### Generators and subtypes

```java
long create(UserSession session, GeneratorService.Profile profile)
List<WasteGenerator> findAll(UserSession session)
List<WasteGenerator> findByZone(UserSession session, long zoneId)
Optional<GeneratorService.Profile> findById(UserSession session, long id)
void update(UserSession session, WasteGenerator generator)
void setActive(UserSession session, long id, boolean active)
```

`Profile(WasteGenerator generator, Hospital hospital, HousingSociety housingSociety, Factory factory)` combines separate table models for handoff. For specialized types, supply exactly the corresponding subtype object; other subtype values are null. For OFFICE/OTHER/etc. all subtype values are null. On creation the base ID is null, creation time is service-owned, active is true and the generated ID is shared with the subtype. The base address is required; subtype detail fields and expiry dates may be null. HousingSociety.getNumberOfFlats() now returns Integer and may be null. The input objects remain unchanged if the transaction succeeds or fails.

Admin owns writes; staff can list; generator users can read their own profile. Base updates preserve generator type, active flag, creation time and subtype rows. Subtype editing is not exposed. Zone changes are blocked once any pickup history exists.

### Catalog

```java
List<Zone> zones(UserSession session)
List<WasteCategory> categories(UserSession session)
List<DisposalSite> disposalSites(UserSession session)
Optional<DisposalSite> disposalSite(UserSession session, long siteId)
List<DisposalSite> disposalSitesByStatus(UserSession session, String status)
long saveZone(UserSession session, Zone zone)
long saveCategory(UserSession session, WasteCategory category)
void deleteUnreferencedCategory(UserSession session, long id)
```

Zone/category reads require authentication; disposal-site reads require staff; writes require admin. Disposal-site status filtering is exact; the agreed production status values still need Member 1 confirmation. Null ID inserts; a positive ID updates. Category DELETE is protected by FKs.

### Reports

Every ReportService method takes `(UserSession session, LocalDate from, LocalDate to)` and requires staff. `from` is inclusive and `to` exclusive.

| Method | Result DTO in ReportDAO |
| --- | --- |
| collectionVolume | List<CollectionVolume(categoryId, categoryName, actualKg, measuredLines)> |
| requestStatuses | List<RequestStatus(status, requestCount)> |
| routeWeights | List<RouteWeight(routeId, estimatedKg, actualKg, measuredLines, totalLines)> |
| vehicleUtilization | List<VehicleUtilization(routeId, vehicleId, capacityKg, estimatedRatio, actualRatio)> |
| estimatedVersusActual | List<QuantityComparison(categoryId, categoryName, estimatedKg, actualKg, measuredLines, totalLines)> |

DTOs are Java records, so access fields with `row.actualKg()`, etc. Display null actuals as “Not collected”, not zero. Partial actual totals have fewer measured lines than total lines. Multiply utilization ratios by 100 to display a percentage. See database_contract.md for exact denominators/date meanings.

## Member 3 example

After authenticating an ADMIN or OPERATOR and computing a plan:

```java
// `session`, `selectedVehicleId`, `zoneId`, `firstRequestId`, and `secondRequestId`
// come from authenticated services and Member 3's algorithm output.
Long selectedSiteId = null; // Or a disposal-site ID already selected by Member 3.
long routeId = routeStore.save(session, selectedVehicleId, zoneId, LocalDate.now(),
        List.of(new RoutePersistenceService.PlannedStop(firstRequestId, 1),
                new RoutePersistenceService.PlannedStop(secondRequestId, 2)), selectedSiteId);
List<RouteStop> persistedOrder = routeStore.findStops(session, routeId);
```

Use `catalog.disposalSites(session)` or `catalog.disposalSitesByStatus(session, agreedStatus)` for destination input. Member 3 sends the already chosen vehicle ID, zone ID, date, ordered request IDs/sequences and optional site ID. Member 2 validates and persists the supplied plan atomically; route and nearest-disposal-site calculations stay with Member 3.

Use `requests.findPending(session)`, `generators.findByZone(session, zoneId)`, `requests.findWaste(session, requestId)` and `vehicles.findAvailable(session)` as algorithm inputs. Request zones come from generators; they are not a pickup field. The service revalidates when saving because another staff member may have assigned a request since the algorithm read it. Refresh on conflict; do not assume a previously available vehicle is still free.

## Member 4 examples

Authentication (the username and password come from your login form):

```java
UserSession session;
try {
    session = auth.login(username, passwordChars);
} finally {
    java.util.Arrays.fill(passwordChars, '\0');
}
```

Creating a generator's request:

```java
long requestId = requests.create(session, session.getGeneratorId(),
        LocalDate.now().plusDays(1), "Collect at reception",
        List.of(new PickupRequestService.WasteLine(selectedCategoryId, new BigDecimal("12.50"))));
```

Collection by a staff session, using actual quantities entered for every category:

```java
collections.complete(staffSession, stopId, actualKgByCategory,
        recordedArrivalTime, recordedCompletionTime);
```

For a route destination selector, call `catalog.disposalSites(staffSession)` and pass the selected nullable ID to `routeStore.save(...)`. To display a saved destination, read `routeStore.findById(...)`, then `catalog.disposalSite(...)` when getSiteId() is nonnull. A null destination means “Not selected”. Swing calls services only: no raw SQL, JDBC connections or direct DAO calls.

Call JDBC services from SwingWorker/background work so the UI event thread stays responsive. Transfer returned data onto the Swing event thread for display. The backend does not update Swing components. Keep transaction scope inside one service call; do not hold a Connection while a user fills out a form.

## DAO and connection ownership

All DAOs accept `new XxxDAO(Connection connection)` and borrow that connection. `findById` returns Optional, lists are empty when there are no rows, generated inserts return long, and updates/deletes return void or throw if exactly one row was not matched. Status writes take `(id, expected, next)` to detect conflicts; RequestDAO.cancel uses PENDING → CANCELLED. GeneratorSubtypeDAO handles separate subtype inserts/reads. UserDAO exposes findById/findByUsername plus hasValidSubtype for internal authentication checks. No user-creation API is added. Member 1 must provision USER and exactly one matching marker atomically. GENERATOR uses GENERATOR_USER; existing ADMIN/OPERATOR roles use STAFF_USER. Missing, dual and mismatched markers invalidate login and existing sessions.

Use DAOs directly only for trusted backend composition inside `Database.read` or `Database.transaction`. Services own authorization, validation, lifecycle, commit and rollback. DAO locks (`lockById`) require an active transaction to protect anything beyond a single statement.

## Exceptions to handle

| Exception | UI response |
| --- | --- |
| InvalidRequestException | Show message, correct input, sign in again or refresh state. Includes access-denied and authentication failures. |
| VehicleUnavailableException | Refresh available vehicles; have Member 3 choose a valid assignment. |
| DatabaseOperationException | Show the safe message; avoid logging raw causes/credentials. Inspect configuration/schema/constraints as appropriate. |

Do not catch a database failure and return an empty list, zero ID or success banner. Failed commit outcomes need inspection before retry. All production schema mappings and password compatibility still require Member 1 confirmation.

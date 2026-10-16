package ecoroute.ui;

import ecoroute.db.Database;
import ecoroute.service.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared application context holding the database connection,
 * backend services, and active authenticated session.
 */
public final class AppContext {

    private final Database database;
    private final String connectionInfo;

    private final AuthService authService;
    private final CatalogService catalogService;
    private final GeneratorService generatorService;
    private final VehicleService vehicleService;
    private final PickupRequestService pickupRequestService;
    private final RoutePersistenceService routePersistenceService;
    private final RoutePlanningAdapterService routePlanningAdapterService;
    private final CollectionService collectionService;
    private final DisposalService disposalService;
    private final ReportService reportService;

    private UserSession currentSession;
    private final List<SessionListener> sessionListeners = new ArrayList<>();

    public interface SessionListener {
        void onSessionChanged(UserSession session);
    }

    public AppContext(Database database, String connectionInfo) {
        this.database = database;
        this.connectionInfo = connectionInfo != null ? connectionInfo : "Connected";

        this.authService = new AuthService(database);
        this.catalogService = new CatalogService(database);
        this.generatorService = new GeneratorService(database);
        this.vehicleService = new VehicleService(database);
        this.pickupRequestService = new PickupRequestService(database);
        this.routePersistenceService = new RoutePersistenceService(database);
        this.routePlanningAdapterService = new RoutePlanningAdapterService(database);
        this.collectionService = new CollectionService(database);
        this.disposalService = new DisposalService(database);
        this.reportService = new ReportService(database);
    }

    public Database getDatabase() {
        return database;
    }

    public String getConnectionInfo() {
        return connectionInfo;
    }

    public AuthService getAuthService() {
        return authService;
    }

    public CatalogService getCatalogService() {
        return catalogService;
    }

    public GeneratorService getGeneratorService() {
        return generatorService;
    }

    public VehicleService getVehicleService() {
        return vehicleService;
    }

    public PickupRequestService getPickupRequestService() {
        return pickupRequestService;
    }

    public RoutePersistenceService getRoutePersistenceService() {
        return routePersistenceService;
    }

    public RoutePlanningAdapterService getRoutePlanningAdapterService() {
        return routePlanningAdapterService;
    }

    public CollectionService getCollectionService() {
        return collectionService;
    }

    public DisposalService getDisposalService() {
        return disposalService;
    }

    public ReportService getReportService() {
        return reportService;
    }

    public synchronized UserSession getCurrentSession() {
        return currentSession;
    }

    public synchronized void setCurrentSession(UserSession session) {
        this.currentSession = session;
        notifySessionListeners(session);
    }

    public synchronized void logout() {
        setCurrentSession(null);
    }

    public synchronized void addSessionListener(SessionListener listener) {
        if (listener != null && !sessionListeners.contains(listener)) {
            sessionListeners.add(listener);
        }
    }

    public synchronized void removeSessionListener(SessionListener listener) {
        sessionListeners.remove(listener);
    }

    private void notifySessionListeners(UserSession session) {
        List<SessionListener> copy;
        synchronized (this) {
            copy = new ArrayList<>(sessionListeners);
        }
        for (SessionListener l : copy) {
            l.onSessionChanged(session);
        }
    }
}

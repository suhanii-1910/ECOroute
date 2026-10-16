package ecoroute.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** JDBC data holder. A null surrogate ID means it has not been inserted yet. */
public class Route {
    private Long routeId;
    private Long vehicleId;
    private Long zoneId;
    private LocalDate routeDate;
    private String status;
    private LocalDateTime createdDate;

    public Route() {}

    public Route(Long routeId, Long vehicleId, Long zoneId, LocalDate routeDate, String status, LocalDateTime createdDate) {
        this.routeId = routeId;
        this.vehicleId = vehicleId;
        this.zoneId = zoneId;
        this.routeDate = routeDate;
        this.status = status;
        this.createdDate = createdDate;
    }

    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }

    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }

    public Long getZoneId() { return zoneId; }
    public void setZoneId(Long zoneId) { this.zoneId = zoneId; }

    public LocalDate getRouteDate() { return routeDate; }
    public void setRouteDate(LocalDate routeDate) { this.routeDate = routeDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }

    @Override
    public String toString() {
        return "Route{" + routeId + ", " + vehicleId + "}";
    }
}

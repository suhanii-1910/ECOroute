package ecoroute.model;

import java.time.LocalDateTime;

/** JDBC data holder. A null surrogate ID means it has not been inserted yet. */
public class RouteStop {
    private Long stopId;
    private Long routeId;
    private Long requestId;
    private int stopSequence;
    private String status;
    private LocalDateTime arrivalTime;
    private LocalDateTime completionTime;

    public RouteStop() {}

    public RouteStop(Long stopId, Long routeId, Long requestId, int stopSequence, String status, LocalDateTime arrivalTime, LocalDateTime completionTime) {
        this.stopId = stopId;
        this.routeId = routeId;
        this.requestId = requestId;
        this.stopSequence = stopSequence;
        this.status = status;
        this.arrivalTime = arrivalTime;
        this.completionTime = completionTime;
    }

    public Long getStopId() { return stopId; }
    public void setStopId(Long stopId) { this.stopId = stopId; }

    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }

    public int getStopSequence() { return stopSequence; }
    public void setStopSequence(int stopSequence) { this.stopSequence = stopSequence; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(LocalDateTime arrivalTime) { this.arrivalTime = arrivalTime; }

    public LocalDateTime getCompletionTime() { return completionTime; }
    public void setCompletionTime(LocalDateTime completionTime) { this.completionTime = completionTime; }

    @Override
    public String toString() {
        return "RouteStop{" + stopId + ", " + routeId + "}";
    }
}
